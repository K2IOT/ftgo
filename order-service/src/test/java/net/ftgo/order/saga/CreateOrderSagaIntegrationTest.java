package net.ftgo.order.saga;

import io.eventuate.common.json.mapper.JSonMapper;
import io.eventuate.tram.commands.common.Command;
import io.eventuate.tram.commands.common.CommandMessageHeaders;
import io.eventuate.tram.commands.consumer.CommandDispatcher;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.messaging.producer.MessageBuilder;
import io.eventuate.tram.messaging.producer.MessageProducer;
import io.eventuate.tram.sagas.orchestration.SagaInstance;
import io.eventuate.tram.sagas.orchestration.SagaInstanceFactory;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.OrderState;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.repository.OrderRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@Import(TestParticipantConfiguration.class)
class CreateOrderSagaIntegrationTest extends OrderServiceIntegrationTestBase {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CreateOrderSaga createOrderSaga;

    @Autowired
    private SagaInstanceFactory sagaInstanceFactory;

    @Autowired
    @Qualifier("createOrderSagaCommandDispatcher")
    private CommandDispatcher createOrderSagaCommandDispatcher;

    @SpyBean
    private MessageProducer messageProducer;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        clearInvocations(messageProducer);
    }

    @Test
    void successfulCreateSagaPreparesResourcesAndWaitsForRestaurant() {
        Order order = createApprovalPendingOrder(200L);

        SagaInstance sagaInstance = sagaInstanceFactory.create(createOrderSaga, toSagaData(order));

        assertNotNull(sagaInstance);
        assertNotNull(sagaInstance.getId());
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertEquals(OrderState.AWAITING_RESTAURANT_ACCEPTANCE, finalOrder.getState());
            assertEquals(999L, finalOrder.getTicketId());
            assertEquals(888L, finalOrder.getAuthorizationId());
            assertEquals(777L, finalOrder.getCreditReservationId());
        });

        ArgumentCaptor<String> destinations = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Message> messages = ArgumentCaptor.forClass(Message.class);
        verify(messageProducer, timeout(5000).atLeastOnce()).send(
            destinations.capture(),
            messages.capture()
        );
        assertCommandSent(
            destinations.getAllValues(),
            messages.getAllValues(),
            ChannelNames.RESTAURANT_SERVICE_COMMAND_CHANNEL,
            "ValidateOrderMenuCommand"
        );
        assertCommandSent(
            destinations.getAllValues(),
            messages.getAllValues(),
            ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL,
            "ReserveConsumerCreditCommand"
        );
    }

    @Test
    void ticketCreationFailureCompensatesAndRejectsOrder() {
        Order order = createApprovalPendingOrder(999L);

        SagaInstance sagaInstance = sagaInstanceFactory.create(createOrderSaga, toSagaData(order));

        assertNotNull(sagaInstance);
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertEquals(OrderState.REJECTED, finalOrder.getState());
            assertNull(finalOrder.getTicketId());
            assertNull(finalOrder.getAuthorizationId());
        });
    }

    @Test
    void legacyApproveOrderCommandRemainsAvailableDuringRollingDeployment() {
        Order order = createApprovalPendingOrder(200L);

        createOrderSagaCommandDispatcher.messageHandler(
            commandMessage(new CreateOrderSagaLocalSteps.ApproveOrderCommand(
                order.getId(),
                999L,
                888L
            ))
        );

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertEquals(OrderState.APPROVED, finalOrder.getState());
            assertEquals(999L, finalOrder.getTicketId());
            assertEquals(888L, finalOrder.getAuthorizationId());
        });
    }

    @Test
    void rejectOrderCompensationIsIdempotent() {
        Order order = createApprovalPendingOrder(200L);
        Message command = commandMessage(
            new CreateOrderSagaLocalSteps.RejectOrderCommand(order.getId())
        );

        createOrderSagaCommandDispatcher.messageHandler(command);
        createOrderSagaCommandDispatcher.messageHandler(command);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertEquals(OrderState.REJECTED, finalOrder.getState());
            assertNull(finalOrder.getTicketId());
            assertNull(finalOrder.getAuthorizationId());
        });
    }

    private Order createApprovalPendingOrder(Long restaurantId) {
        Order order = new Order(
            100L,
            restaurantId,
            Arrays.asList(
                new OrderLineItem(1L, "Burger", new Money(BigDecimal.valueOf(12.99)), 2),
                new OrderLineItem(2L, "Fries", new Money(BigDecimal.valueOf(4.99)), 1)
            ),
            new DeliveryInfo("123 Main St", LocalDateTime.now().plusHours(1)),
            new PaymentInfo("tok_test_123")
        );
        return orderRepository.save(order);
    }

    private CreateOrderSagaData toSagaData(Order order) {
        return new CreateOrderSagaData(
            order.getId(),
            order.getConsumerId(),
            order.getRestaurantId(),
            order.getLineItems(),
            order.getOrderTotal()
        );
    }

    private Message commandMessage(Command command) {
        return MessageBuilder.withPayload(JSonMapper.toJson(command))
            .withHeader(Message.ID, UUID.randomUUID().toString())
            .withHeader(CommandMessageHeaders.COMMAND_TYPE, command.getClass().getName())
            .withHeader(CommandMessageHeaders.DESTINATION, ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL)
            .withHeader(CommandMessageHeaders.REPLY_TO, "testReplyChannel")
            .build();
    }

    private void assertCommandSent(
        List<String> destinations,
        List<Message> messages,
        String expectedDestination,
        String expectedCommandTypeFragment
    ) {
        for (int i = 0; i < destinations.size(); i++) {
            String commandType = messages.get(i).getHeaders().get(CommandMessageHeaders.COMMAND_TYPE);
            if (expectedDestination.equals(destinations.get(i))
                && commandType != null
                && commandType.contains(expectedCommandTypeFragment)) {
                return;
            }
        }
        fail("Expected command was not sent. destination=" + expectedDestination
            + ", commandType contains=" + expectedCommandTypeFragment);
    }
}

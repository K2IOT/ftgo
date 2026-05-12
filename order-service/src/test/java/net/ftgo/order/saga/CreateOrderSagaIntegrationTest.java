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
import org.awaitility.Awaitility;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.OrderState;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Qualifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    private Order createApprovalPendingOrder(Long restaurantId) {
        Long consumerId = 100L;

        List<OrderLineItem> lineItems = Arrays.asList(
            new OrderLineItem(1L, "Burger", new Money(BigDecimal.valueOf(12.99)), 2),
            new OrderLineItem(2L, "Fries", new Money(BigDecimal.valueOf(4.99)), 1)
        );

        DeliveryInfo deliveryInfo = new DeliveryInfo("123 Main St", LocalDateTime.now().plusHours(1));
        PaymentInfo paymentInfo = new PaymentInfo("tok_test_123");

        Order order = new Order(
            consumerId,
            restaurantId,
            lineItems,
            deliveryInfo,
            paymentInfo
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

    @Test
    void testCreateOrderSaga_StartsSagaAndSendsVerifyConsumerCommand() {
        Order order = createApprovalPendingOrder(200L);
        CreateOrderSagaData sagaData = toSagaData(order);

        SagaInstance sagaInstance = sagaInstanceFactory.create(createOrderSaga, sagaData);
        assertNotNull(sagaInstance);
        assertNotNull(sagaInstance.getId());
        assertEquals(OrderState.APPROVAL_PENDING, orderRepository.findById(order.getId()).orElseThrow().getState());

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageProducer, timeout(5000).atLeastOnce()).send(destinationCaptor.capture(), messageCaptor.capture());

        assertCommandSent(
            destinationCaptor.getAllValues(),
            messageCaptor.getAllValues(),
            ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL,
            "VerifyConsumerCommand"
        );
    }

    @Test
    void testCreateOrderSaga_InIsolationOrderRemainsApprovalPending() {
        Order order = createApprovalPendingOrder(999L);
        CreateOrderSagaData sagaData = toSagaData(order);

        SagaInstance sagaInstance = sagaInstanceFactory.create(createOrderSaga, sagaData);
        assertNotNull(sagaInstance);
        assertNotNull(sagaInstance.getId());
        assertEquals(OrderState.APPROVAL_PENDING, orderRepository.findById(order.getId()).orElseThrow().getState());

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageProducer, timeout(5000).atLeastOnce()).send(destinationCaptor.capture(), messageCaptor.capture());

        assertCommandSent(
            destinationCaptor.getAllValues(),
            messageCaptor.getAllValues(),
            ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL,
            "VerifyConsumerCommand"
        );
    }

    @Test
    void testOrderServiceKafkaConsumer_ApproveOrderCommand() {
        Order order = createApprovalPendingOrder(200L);

        createOrderSagaCommandDispatcher.messageHandler(
            commandMessage(new CreateOrderSagaLocalSteps.ApproveOrderCommand(order.getId(), 999L, 888L))
        );

        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
                assertEquals(OrderState.APPROVED, finalOrder.getState());
                assertEquals(999L, finalOrder.getTicketId());
                assertEquals(888L, finalOrder.getAuthorizationId());
            });
    }

    @Test
    void testOrderServiceKafkaConsumer_RejectOrderCommand() {
        Order order = createApprovalPendingOrder(200L);

        createOrderSagaCommandDispatcher.messageHandler(
            commandMessage(new CreateOrderSagaLocalSteps.RejectOrderCommand(order.getId()))
        );

        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
                assertEquals(OrderState.REJECTED, finalOrder.getState());
                assertNull(finalOrder.getTicketId());
                assertNull(finalOrder.getAuthorizationId());
            });
    }

    private Message commandMessage(Command command) {
        return MessageBuilder.withPayload(JSonMapper.toJson(command))
            .withHeader(Message.ID, UUID.randomUUID().toString())
            .withHeader(CommandMessageHeaders.COMMAND_TYPE, command.getClass().getName())
            .withHeader(CommandMessageHeaders.DESTINATION, ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL)
            .withHeader(CommandMessageHeaders.REPLY_TO, "testReplyChannel")
            .build();
    }

    private void assertCommandSent(List<String> destinations, List<Message> messages,
                                   String expectedDestination, String expectedCommandTypeFragment) {
        for (int i = 0; i < destinations.size(); i++) {
            String destination = destinations.get(i);
            Message message = messages.get(i);
            String commandType = message.getHeaders().get(CommandMessageHeaders.COMMAND_TYPE);

            if (expectedDestination.equals(destination)
                && commandType != null
                && commandType.contains(expectedCommandTypeFragment)) {
                return;
            }
        }

        fail("Expected command was not sent. destination=" + expectedDestination
            + ", commandType contains=" + expectedCommandTypeFragment
            + ", captured destinations=" + destinations
            + ", captured command types=" + messages.stream()
                .map(m -> m.getHeaders().get(CommandMessageHeaders.COMMAND_TYPE))
                .toList());
    }
}

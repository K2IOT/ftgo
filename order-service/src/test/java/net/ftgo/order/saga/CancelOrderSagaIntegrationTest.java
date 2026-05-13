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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Import(TestParticipantConfiguration.class)
class CancelOrderSagaIntegrationTest extends OrderServiceIntegrationTestBase {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CancelOrderSaga cancelOrderSaga;

    @Autowired
    private SagaInstanceFactory sagaInstanceFactory;

    @Autowired
    @Qualifier("cancelOrderSagaCommandDispatcher")
    private CommandDispatcher cancelOrderSagaCommandDispatcher;

    @SpyBean
    private MessageProducer messageProducer;

    @SpyBean
    private CancelOrderSagaLocalSteps cancelOrderSagaLocalSteps;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        clearInvocations(messageProducer, cancelOrderSagaLocalSteps);
    }

    private Order createApprovedOrder(Long ticketId, Long authorizationId) {
        List<OrderLineItem> lineItems = Arrays.asList(
            new OrderLineItem(1L, "Burger", new Money(BigDecimal.valueOf(12.99)), 2),
            new OrderLineItem(2L, "Fries", new Money(BigDecimal.valueOf(4.99)), 1)
        );

        Order order = new Order(
            100L,
            200L,
            lineItems,
            new DeliveryInfo("123 Main St", LocalDateTime.now().plusHours(1)),
            new PaymentInfo("tok_test_123")
        );
        order.approve();
        order.setTicketId(ticketId);
        order.setAuthorizationId(authorizationId);
        return orderRepository.save(order);
    }

    private CancelOrderSagaData toSagaData(Order order) {
        return new CancelOrderSagaData(
            order.getId(),
            order.getConsumerId(),
            order.getTicketId(),
            order.getAuthorizationId()
        );
    }

    @Test
    void testCancelOrderSaga_StartsAndSendsParticipantCommands() {
        Order order = createApprovedOrder(999L, 888L);
        CancelOrderSagaData sagaData = toSagaData(order);

        SagaInstance sagaInstance = sagaInstanceFactory.create(cancelOrderSaga, sagaData);
        assertNotNull(sagaInstance);
        assertNotNull(sagaInstance.getId());
        assertEquals(OrderState.CANCEL_PENDING,
            orderRepository.findById(order.getId()).orElseThrow().getState(),
            "Order must enter CANCEL_PENDING before Kitchen receives cancellation work");

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageProducer, timeout(5000).atLeastOnce()).send(destinationCaptor.capture(), messageCaptor.capture());

        List<String> destinations = destinationCaptor.getAllValues();
        List<Message> messages = messageCaptor.getAllValues();

        int beginTicketCancelIndex = findCommandIndex(
            destinations,
            messages,
            ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL,
            "BeginCancelTicketCommand"
        );

        assertTrue(beginTicketCancelIndex >= 0, "Expected BeginCancelTicketCommand to Kitchen Service");
    }

    @Test
    void testOrderServiceKafkaConsumer_BeginCancelCommand() {
        Order order = createApprovedOrder(999L, 888L);

        cancelOrderSagaCommandDispatcher.messageHandler(
            commandMessage(new CancelOrderSagaLocalSteps.BeginCancelCommand(order.getId()))
        );

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertEquals(OrderState.CANCEL_PENDING, finalOrder.getState());
        });

        verify(cancelOrderSagaLocalSteps, atLeastOnce()).beginCancel(any());
    }

    @Test
    void testOrderServiceKafkaConsumer_ConfirmCancelCommand() {
        Order order = createApprovedOrder(999L, 888L);
        order.beginCancel();
        orderRepository.save(order);

        cancelOrderSagaCommandDispatcher.messageHandler(
            commandMessage(new CancelOrderSagaLocalSteps.ConfirmCancelCommand(order.getId()))
        );

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertEquals(OrderState.CANCELLED, finalOrder.getState());
        });

        verify(cancelOrderSagaLocalSteps, atLeastOnce()).confirmCancel(any());
    }

    @Test
    void testOrderServiceKafkaConsumer_UndoCancelCommand() {
        Order order = createApprovedOrder(999L, 888L);
        order.beginCancel();
        orderRepository.save(order);

        cancelOrderSagaCommandDispatcher.messageHandler(
            commandMessage(new CancelOrderSagaLocalSteps.UndoCancelCommand(order.getId()))
        );

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertEquals(OrderState.APPROVED, finalOrder.getState());
        });

        verify(cancelOrderSagaLocalSteps, atLeastOnce()).undoCancel(any());
    }

    private Message commandMessage(Command command) {
        return MessageBuilder.withPayload(JSonMapper.toJson(command))
            .withHeader(Message.ID, UUID.randomUUID().toString())
            .withHeader(CommandMessageHeaders.COMMAND_TYPE, command.getClass().getName())
            .withHeader(CommandMessageHeaders.DESTINATION, ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL)
            .withHeader(CommandMessageHeaders.REPLY_TO, "testReplyChannel")
            .build();
    }

    private int findCommandIndex(List<String> destinations, List<Message> messages,
                                 String expectedDestination, String expectedCommandTypeFragment) {
        for (int i = 0; i < destinations.size(); i++) {
            String destination = destinations.get(i);
            Message message = messages.get(i);
            String commandType = message.getHeaders().get(CommandMessageHeaders.COMMAND_TYPE);

            if (expectedDestination.equals(destination)
                && commandType != null
                && commandType.contains(expectedCommandTypeFragment)) {
                return i;
            }
        }
        return -1;
    }
}

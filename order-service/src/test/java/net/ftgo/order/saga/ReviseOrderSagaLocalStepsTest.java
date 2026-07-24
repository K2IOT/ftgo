package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.events.OrderRevised;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.OrderState;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviseOrderSagaLocalStepsTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private CommandMessage<ReviseOrderSagaLocalSteps.BeginReviseCommand> beginReviseCommandMessage;

    @Mock
    private CommandMessage<ReviseOrderSagaLocalSteps.UndoReviseCommand> undoReviseCommandMessage;

    @Mock
    private CommandMessage<ReviseOrderSagaLocalSteps.ConfirmReviseCommand> confirmReviseCommandMessage;

    private ReviseOrderSagaLocalSteps localSteps;

    @BeforeEach
    void setUp() {
        localSteps = new ReviseOrderSagaLocalSteps(
            orderRepository,
            eventPublisher,
            new SimpleMeterRegistry()
        );
    }

    @Test
    void testBeginRevise_TransitionsOrderToRevisionPending() {
        Long orderId = 1L;
        Order order = createTestOrder(orderId);
        order.approve();
        var command = new ReviseOrderSagaLocalSteps.BeginReviseCommand(orderId);
        when(beginReviseCommandMessage.getCommand()).thenReturn(command);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Message result = localSteps.beginRevise(beginReviseCommandMessage);

        assertNotNull(result);
        assertEquals(OrderState.REVISION_PENDING, order.getState());
        verify(orderRepository).save(order);
    }

    @Test
    void testUndoRevise_RestoresOrderToApproved() {
        Long orderId = 1L;
        Order order = createTestOrder(orderId);
        order.approve();
        order.setAuthorizationId(123L);
        order.beginRevise();
        var command = new ReviseOrderSagaLocalSteps.UndoReviseCommand(orderId);
        when(undoReviseCommandMessage.getCommand()).thenReturn(command);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Message result = localSteps.undoRevise(undoReviseCommandMessage);

        assertNotNull(result);
        assertEquals(OrderState.APPROVED, order.getState());
        verify(orderRepository).save(order);
    }

    @Test
    void testConfirmRevise_UpdatesOrderAndPublishesVersionedEvent() {
        Long orderId = 1L;
        Order order = createTestOrder(orderId);
        order.approve();
        order.beginRevise();
        List<OrderLineItem> revisedLineItems = Arrays.asList(
            new OrderLineItem(1L, "Burger", new Money(BigDecimal.valueOf(12.99)), 3),
            new OrderLineItem(2L, "Fries", new Money(BigDecimal.valueOf(4.99)), 2)
        );
        var command = new ReviseOrderSagaLocalSteps.ConfirmReviseCommand(
            orderId,
            revisedLineItems,
            456L
        );
        when(confirmReviseCommandMessage.getCommand()).thenReturn(command);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Message result = localSteps.confirmRevise(confirmReviseCommandMessage);

        assertNotNull(result);
        assertEquals(OrderState.APPROVED, order.getState());
        assertEquals(456L, order.getAuthorizationId());
        assertEquals(3, order.getLineItems().get(0).getQuantity());
        verify(orderRepository).saveAndFlush(order);
        verify(eventPublisher).publishOrderEvent(
            eq(orderId),
            eq(order.getVersion().longValue()),
            any(OrderRevised.class)
        );
    }

    @Test
    void testUndoRevise_KeepsOriginalLineItemsAndAuthorization() {
        Long orderId = 1L;
        Order order = createTestOrder(orderId);
        order.approve();
        order.setAuthorizationId(123L);
        order.beginRevise();
        var command = new ReviseOrderSagaLocalSteps.UndoReviseCommand(orderId);
        when(undoReviseCommandMessage.getCommand()).thenReturn(command);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Message result = localSteps.undoRevise(undoReviseCommandMessage);

        assertNotNull(result);
        assertEquals(OrderState.APPROVED, order.getState());
        assertEquals(123L, order.getAuthorizationId());
        assertEquals(2, order.getLineItems().get(0).getQuantity());
        assertEquals("Fries", order.getLineItems().get(1).getName());
        verify(orderRepository).save(order);
    }

    @Test
    void testBeginRevise_ThrowsExceptionWhenOrderNotFound() {
        Long orderId = 999L;
        var command = new ReviseOrderSagaLocalSteps.BeginReviseCommand(orderId);
        when(beginReviseCommandMessage.getCommand()).thenReturn(command);
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(
            IllegalArgumentException.class,
            () -> localSteps.beginRevise(beginReviseCommandMessage)
        );
    }

    @Test
    void testCommandHandlersAreConfigured() {
        assertNotNull(localSteps.commandHandlers());
    }

    private Order createTestOrder(Long orderId) {
        List<OrderLineItem> lineItems = Arrays.asList(
            new OrderLineItem(1L, "Burger", new Money(BigDecimal.valueOf(12.99)), 2),
            new OrderLineItem(2L, "Fries", new Money(BigDecimal.valueOf(4.99)), 1)
        );
        DeliveryInfo deliveryInfo = new DeliveryInfo(
            "123 Main St",
            LocalDateTime.now().plusHours(1)
        );
        Order order = new Order(
            1L,
            100L,
            lineItems,
            deliveryInfo,
            new PaymentInfo("token-123")
        );
        try {
            var idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, orderId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set order ID", e);
        }
        return order;
    }
}

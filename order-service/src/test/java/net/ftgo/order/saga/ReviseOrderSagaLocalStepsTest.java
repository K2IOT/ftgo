package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.common.Money;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.OrderState;
import net.ftgo.order.domain.DeliveryInfo;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReviseOrderSagaLocalSteps.
 * 
 * Tests the local saga step handlers for order revision.
 */
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
    private SimpleMeterRegistry meterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        localSteps = new ReviseOrderSagaLocalSteps(orderRepository, eventPublisher, meterRegistry);
    }
    
    @Test
    void testBeginRevise_TransitionsOrderToRevisionPending() {
        // Arrange
        Long orderId = 1L;
        Order order = createTestOrder(orderId);
        order.approve(); // Start in APPROVED state
        
        ReviseOrderSagaLocalSteps.BeginReviseCommand command = 
            new ReviseOrderSagaLocalSteps.BeginReviseCommand(orderId);
        
        when(beginReviseCommandMessage.getCommand()).thenReturn(command);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        
        // Act
        Message result = localSteps.beginRevise(beginReviseCommandMessage);
        
        // Assert
        assertNotNull(result);
        assertEquals(OrderState.REVISION_PENDING, order.getState());
        verify(orderRepository).save(order);
    }
    
    @Test
    void testUndoRevise_RestoresOrderToApproved() {
        // Arrange
        Long orderId = 1L;
        Order order = createTestOrder(orderId);
        order.approve();
        order.beginRevise(); // Move to REVISION_PENDING
        
        ReviseOrderSagaLocalSteps.UndoReviseCommand command = 
            new ReviseOrderSagaLocalSteps.UndoReviseCommand(orderId);
        
        when(undoReviseCommandMessage.getCommand()).thenReturn(command);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        
        // Act
        Message result = localSteps.undoRevise(undoReviseCommandMessage);
        
        // Assert
        assertNotNull(result);
        assertEquals(OrderState.APPROVED, order.getState());
        verify(orderRepository).save(order);
    }
    
    @Test
    void testConfirmRevise_UpdatesOrderAndPublishesEvent() {
        // Arrange
        Long orderId = 1L;
        Order order = createTestOrder(orderId);
        order.approve();
        order.beginRevise(); // Move to REVISION_PENDING
        
        List<OrderLineItem> revisedLineItems = Arrays.asList(
            new OrderLineItem(1L, "Burger", new Money(BigDecimal.valueOf(12.99)), 3),
            new OrderLineItem(2L, "Fries", new Money(BigDecimal.valueOf(4.99)), 2)
        );
        
        ReviseOrderSagaLocalSteps.ConfirmReviseCommand command = 
            new ReviseOrderSagaLocalSteps.ConfirmReviseCommand(orderId, revisedLineItems);
        
        when(confirmReviseCommandMessage.getCommand()).thenReturn(command);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        
        // Act
        Message result = localSteps.confirmRevise(confirmReviseCommandMessage);
        
        // Assert
        assertNotNull(result);
        assertEquals(OrderState.APPROVED, order.getState());
        verify(orderRepository).save(order);
        verify(eventPublisher).publishOrderEvent(eq(orderId), any());
    }
    
    @Test
    void testBeginRevise_ThrowsExceptionWhenOrderNotFound() {
        // Arrange
        Long orderId = 999L;
        ReviseOrderSagaLocalSteps.BeginReviseCommand command = 
            new ReviseOrderSagaLocalSteps.BeginReviseCommand(orderId);
        
        when(beginReviseCommandMessage.getCommand()).thenReturn(command);
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            localSteps.beginRevise(beginReviseCommandMessage);
        });
    }
    
    @Test
    void testCommandHandlersAreConfigured() {
        // Act
        var handlers = localSteps.commandHandlers();
        
        // Assert
        assertNotNull(handlers);
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
        
        PaymentInfo paymentInfo = new PaymentInfo("token-123");
        
        Order order = new Order(1L, 100L, lineItems, deliveryInfo, paymentInfo);
        
        // Use reflection to set the ID since it's normally set by JPA
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

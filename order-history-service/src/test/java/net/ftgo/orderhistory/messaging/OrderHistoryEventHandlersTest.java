package net.ftgo.orderhistory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.common.orderflow.events.OrderCancelled;
import net.ftgo.common.orderflow.events.OrderCreated;
import net.ftgo.common.orderflow.events.OrderRejected;
import net.ftgo.common.orderflow.events.OrderRevised;
import net.ftgo.orderhistory.domain.LineItem;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.domain.ProcessedMessage;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import net.ftgo.orderhistory.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderHistoryEventHandlers.
 * 
 * Tests:
 * - OrderCreated creates new record
 * - Subsequent events update existing record
 * - Idempotency (processing same event twice produces same state)
 */
@ExtendWith(MockitoExtension.class)
class OrderHistoryEventHandlersTest {
    
    @Mock
    private OrderHistoryRepository orderHistoryRepository;
    
    @Mock
    private ProcessedMessageRepository processedMessageRepository;
    
    private ObjectMapper objectMapper;
    private OrderHistoryEventHandlers eventHandlers;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JavaTimeModule for LocalDateTime
        eventHandlers = new OrderHistoryEventHandlers(
            orderHistoryRepository,
            processedMessageRepository,
            objectMapper
        );
    }

    @Test
    void sharedOrderCreatedThenOrderApprovedUpdatesSameHistoryRecord() throws Exception {
        LocalDateTime deliveryTime = LocalDateTime.now().plusHours(1);
        LocalDateTime createdAt = LocalDateTime.now();
        net.ftgo.common.orderflow.events.OrderCreated orderCreated =
            new net.ftgo.common.orderflow.events.OrderCreated(
                123L,
                456L,
                789L,
                "APPROVAL_PENDING",
                new Money("30.97"),
                List.of(
                    new net.ftgo.common.orderflow.events.OrderCreated.LineItem(
                        1L,
                        "Burger",
                        new Money("12.99"),
                        2
                    ),
                    new net.ftgo.common.orderflow.events.OrderCreated.LineItem(
                        2L,
                        "Fries",
                        new Money("4.99"),
                        1
                    )
                ),
                "123 Main St",
                deliveryTime,
                createdAt
            );

        AtomicReference<OrderHistoryRecord> savedRecord = new AtomicReference<>();
        when(processedMessageRepository.existsById(anyString())).thenReturn(false);
        when(orderHistoryRepository.save(any(OrderHistoryRecord.class))).thenAnswer(invocation -> {
            OrderHistoryRecord record = invocation.getArgument(0);
            savedRecord.set(record);
            return record;
        });
        when(orderHistoryRepository.findById("123")).thenAnswer(invocation -> Optional.ofNullable(savedRecord.get()));

        eventHandlers.handleOrderEvent(
            objectMapper.writeValueAsString(orderCreated),
            "Order#123",
            "OrderCreated"
        );
        eventHandlers.handleOrderEvent(
            objectMapper.writeValueAsString(new OrderApproved(123L, 456L, 789L, new Money("30.97"), 99L, 88L)),
            "Order#123",
            "OrderApproved"
        );

        OrderHistoryRecord record = savedRecord.get();
        assertEquals("123", record.getOrderId());
        assertEquals(456L, record.getConsumerId());
        assertEquals(789L, record.getRestaurantId());
        assertEquals("APPROVED", record.getStatus());
        assertEquals(new BigDecimal("30.97"), record.getOrderTotal());
        assertEquals("123 Main St", record.getDeliveryAddress());
        assertEquals(deliveryTime, record.getDeliveryTime());
        assertEquals(createdAt, record.getCreationDate());
        assertEquals(2, record.getLineItems().size());
        assertEquals("Burger", record.getLineItems().get(0).getName());
    }

    @Test
    void duplicateSharedOrderCreatedDeliveryIsIdempotent() throws Exception {
        net.ftgo.common.orderflow.events.OrderCreated orderCreated =
            new net.ftgo.common.orderflow.events.OrderCreated(
                123L,
                456L,
                789L,
                "APPROVAL_PENDING",
                new Money("12.99"),
                List.of(new net.ftgo.common.orderflow.events.OrderCreated.LineItem(
                    1L,
                    "Burger",
                    new Money("12.99"),
                    1
                )),
                "123 Main St",
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now()
            );
        String payload = objectMapper.writeValueAsString(orderCreated);
        String key = "Order#123";
        String eventType = "OrderCreated";
        String messageId = key + "-" + eventType;

        when(processedMessageRepository.existsById(messageId)).thenReturn(false, true);

        eventHandlers.handleOrderEvent(payload, key, eventType);
        eventHandlers.handleOrderEvent(payload, key, eventType);

        verify(orderHistoryRepository, times(1)).save(any(OrderHistoryRecord.class));
        verify(processedMessageRepository, times(1)).save(any(ProcessedMessage.class));
    }

    @Test
    void sharedOrderRejectedUpdatesExistingHistoryRecord() throws Exception {
        OrderRejected event = new OrderRejected(123L, 456L, 789L, "Consumer verification failed");
        String payload = objectMapper.writeValueAsString(event);
        String key = "Order#123";
        String eventType = "OrderRejected";
        String messageId = key + "-" + eventType;

        OrderHistoryRecord existingRecord = new OrderHistoryRecord("123");
        existingRecord.setStatus("APPROVAL_PENDING");

        when(processedMessageRepository.existsById(messageId)).thenReturn(false);
        when(orderHistoryRepository.findById("123")).thenReturn(Optional.of(existingRecord));

        eventHandlers.handleOrderEvent(payload, key, eventType);

        ArgumentCaptor<OrderHistoryRecord> recordCaptor = ArgumentCaptor.forClass(OrderHistoryRecord.class);
        verify(orderHistoryRepository).save(recordCaptor.capture());
        assertEquals("REJECTED", recordCaptor.getValue().getStatus());
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }
    
    @Test
    void testOrderCreatedCreatesNewRecord() throws Exception {
        // Given
        OrderCreated event = new OrderCreated(
            123L,
            456L,
            789L,
            "APPROVAL_PENDING",
            new Money("45.99"),
            Arrays.asList(
                new OrderCreated.LineItem(1L, "Burger", new Money("12.99"), 2),
                new OrderCreated.LineItem(2L, "Fries", new Money("4.99"), 1)
            ),
            "123 Main St",
            LocalDateTime.now().plusHours(1),
            LocalDateTime.now()
        );
        
        String payload = objectMapper.writeValueAsString(event);
        String key = "Order#123";
        String eventType = "OrderCreated";
        String messageId = key + "-" + eventType;
        
        when(processedMessageRepository.existsById(messageId)).thenReturn(false);
        
        // When
        eventHandlers.handleOrderEvent(payload, key, eventType);
        
        // Then
        ArgumentCaptor<OrderHistoryRecord> recordCaptor = ArgumentCaptor.forClass(OrderHistoryRecord.class);
        verify(orderHistoryRepository).save(recordCaptor.capture());
        
        OrderHistoryRecord savedRecord = recordCaptor.getValue();
        assertEquals("123", savedRecord.getOrderId());
        assertEquals(456L, savedRecord.getConsumerId());
        assertEquals(789L, savedRecord.getRestaurantId());
        assertEquals("APPROVAL_PENDING", savedRecord.getStatus());
        assertEquals(new BigDecimal("45.99"), savedRecord.getOrderTotal());
        assertEquals("123 Main St", savedRecord.getDeliveryAddress());
        assertEquals(2, savedRecord.getLineItems().size());
        
        LineItem savedLineItem1 = savedRecord.getLineItems().get(0);
        assertEquals(1L, savedLineItem1.getMenuItemId());
        assertEquals("Burger", savedLineItem1.getName());
        assertEquals(new BigDecimal("12.99"), savedLineItem1.getPrice());
        assertEquals(2, savedLineItem1.getQuantity());
        
        // Verify keywords extracted
        assertTrue(savedRecord.getKeywords().contains("burger"));
        assertTrue(savedRecord.getKeywords().contains("fries"));
        
        // Verify message marked as processed
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }
    
    @Test
    void testOrderApprovedUpdatesExistingRecord() throws Exception {
        // Given
        OrderApproved event = new OrderApproved(123L, 456L, 789L, new Money("45.99"), 999L, 555L);
        String payload = objectMapper.writeValueAsString(event);
        String key = "Order#123";
        String eventType = "OrderApproved";
        String messageId = key + "-" + eventType;
        
        OrderHistoryRecord existingRecord = new OrderHistoryRecord("123");
        existingRecord.setStatus("APPROVAL_PENDING");
        
        when(processedMessageRepository.existsById(messageId)).thenReturn(false);
        when(orderHistoryRepository.findById("123")).thenReturn(Optional.of(existingRecord));
        
        // When
        eventHandlers.handleOrderEvent(payload, key, eventType);
        
        // Then
        ArgumentCaptor<OrderHistoryRecord> recordCaptor = ArgumentCaptor.forClass(OrderHistoryRecord.class);
        verify(orderHistoryRepository).save(recordCaptor.capture());
        
        OrderHistoryRecord updatedRecord = recordCaptor.getValue();
        assertEquals("APPROVED", updatedRecord.getStatus());
        assertEquals("APPROVED", updatedRecord.getAuthorizationStatus());
        
        // Verify message marked as processed
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }
    
    @Test
    void testOrderCancelledUpdatesExistingRecord() throws Exception {
        // Given
        OrderCancelled event = new OrderCancelled(123L, 456L, 789L);
        String payload = objectMapper.writeValueAsString(event);
        String key = "Order#123";
        String eventType = "OrderCancelled";
        String messageId = key + "-" + eventType;
        
        OrderHistoryRecord existingRecord = new OrderHistoryRecord("123");
        existingRecord.setStatus("APPROVED");
        
        when(processedMessageRepository.existsById(messageId)).thenReturn(false);
        when(orderHistoryRepository.findById("123")).thenReturn(Optional.of(existingRecord));
        
        // When
        eventHandlers.handleOrderEvent(payload, key, eventType);
        
        // Then
        ArgumentCaptor<OrderHistoryRecord> recordCaptor = ArgumentCaptor.forClass(OrderHistoryRecord.class);
        verify(orderHistoryRepository).save(recordCaptor.capture());
        
        OrderHistoryRecord updatedRecord = recordCaptor.getValue();
        assertEquals("CANCELLED", updatedRecord.getStatus());
        
        // Verify message marked as processed
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }
    
    @Test
    void testOrderRevisedUpdatesOrderDetailsAndLineItems() throws Exception {
        // Given
        OrderRevised event = new OrderRevised(
            123L,
            456L,
            789L,
            Arrays.asList(new OrderCreated.LineItem(3L, "Pizza", new Money("15.99"), 3)),
            new Money("55.99")
        );
        
        String payload = objectMapper.writeValueAsString(event);
        String key = "Order#123";
        String eventType = "OrderRevised";
        String messageId = key + "-" + eventType;
        
        OrderHistoryRecord existingRecord = new OrderHistoryRecord("123");
        existingRecord.setOrderTotal(new BigDecimal("45.99"));
        
        when(processedMessageRepository.existsById(messageId)).thenReturn(false);
        when(orderHistoryRepository.findById("123")).thenReturn(Optional.of(existingRecord));
        
        // When
        eventHandlers.handleOrderEvent(payload, key, eventType);
        
        // Then
        ArgumentCaptor<OrderHistoryRecord> recordCaptor = ArgumentCaptor.forClass(OrderHistoryRecord.class);
        verify(orderHistoryRepository).save(recordCaptor.capture());
        
        OrderHistoryRecord updatedRecord = recordCaptor.getValue();
        assertEquals(new BigDecimal("55.99"), updatedRecord.getOrderTotal());
        assertEquals(1, updatedRecord.getLineItems().size());
        assertEquals("Pizza", updatedRecord.getLineItems().get(0).getName());
        assertTrue(updatedRecord.getKeywords().contains("pizza"));
        
        // Verify message marked as processed
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }
    
    @Test
    void testTicketAcceptedUpdatesTicketStatus() throws Exception {
        // Given
        TicketAcceptedEvent event = new TicketAcceptedEvent();
        event.setTicketId(999L);
        event.setOrderId(123L);
        
        String payload = objectMapper.writeValueAsString(event);
        String key = "Ticket#999";
        String eventType = "TicketAcceptedEvent";
        String messageId = key + "-" + eventType;
        
        OrderHistoryRecord existingRecord = new OrderHistoryRecord("123");
        
        when(processedMessageRepository.existsById(messageId)).thenReturn(false);
        when(orderHistoryRepository.findById("123")).thenReturn(Optional.of(existingRecord));
        
        // When
        eventHandlers.handleTicketEvent(payload, key, eventType);
        
        // Then
        ArgumentCaptor<OrderHistoryRecord> recordCaptor = ArgumentCaptor.forClass(OrderHistoryRecord.class);
        verify(orderHistoryRepository).save(recordCaptor.capture());
        
        OrderHistoryRecord updatedRecord = recordCaptor.getValue();
        assertEquals("ACCEPTED", updatedRecord.getTicketStatus());
        
        // Verify message marked as processed
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }
    
    @Test
    void testTicketReadyUpdatesTicketStatus() throws Exception {
        // Given
        TicketReadyEvent event = new TicketReadyEvent();
        event.setTicketId(999L);
        event.setOrderId(123L);
        
        String payload = objectMapper.writeValueAsString(event);
        String key = "Ticket#999";
        String eventType = "TicketReadyEvent";
        String messageId = key + "-" + eventType;
        
        OrderHistoryRecord existingRecord = new OrderHistoryRecord("123");
        existingRecord.setTicketStatus("ACCEPTED");
        
        when(processedMessageRepository.existsById(messageId)).thenReturn(false);
        when(orderHistoryRepository.findById("123")).thenReturn(Optional.of(existingRecord));
        
        // When
        eventHandlers.handleTicketEvent(payload, key, eventType);
        
        // Then
        ArgumentCaptor<OrderHistoryRecord> recordCaptor = ArgumentCaptor.forClass(OrderHistoryRecord.class);
        verify(orderHistoryRepository).save(recordCaptor.capture());
        
        OrderHistoryRecord updatedRecord = recordCaptor.getValue();
        assertEquals("READY", updatedRecord.getTicketStatus());
        
        // Verify message marked as processed
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }
    
    @Test
    void testDeliveryPickedUpUpdatesDeliveryStatus() throws Exception {
        // Given
        DeliveryPickedUpEvent event = new DeliveryPickedUpEvent();
        event.setDeliveryId(888L);
        event.setOrderId(123L);
        
        String payload = objectMapper.writeValueAsString(event);
        String key = "Delivery#888";
        String eventType = "DeliveryPickedUpEvent";
        String messageId = key + "-" + eventType;
        
        OrderHistoryRecord existingRecord = new OrderHistoryRecord("123");
        
        when(processedMessageRepository.existsById(messageId)).thenReturn(false);
        when(orderHistoryRepository.findById("123")).thenReturn(Optional.of(existingRecord));
        
        // When
        eventHandlers.handleDeliveryEvent(payload, key, eventType);
        
        // Then
        ArgumentCaptor<OrderHistoryRecord> recordCaptor = ArgumentCaptor.forClass(OrderHistoryRecord.class);
        verify(orderHistoryRepository).save(recordCaptor.capture());
        
        OrderHistoryRecord updatedRecord = recordCaptor.getValue();
        assertEquals("PICKED_UP", updatedRecord.getDeliveryStatus());
        
        // Verify message marked as processed
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }
    
    @Test
    void testDeliveryDeliveredUpdatesDeliveryStatus() throws Exception {
        // Given
        DeliveryDeliveredEvent event = new DeliveryDeliveredEvent();
        event.setDeliveryId(888L);
        event.setOrderId(123L);
        
        String payload = objectMapper.writeValueAsString(event);
        String key = "Delivery#888";
        String eventType = "DeliveryDeliveredEvent";
        String messageId = key + "-" + eventType;
        
        OrderHistoryRecord existingRecord = new OrderHistoryRecord("123");
        existingRecord.setDeliveryStatus("PICKED_UP");
        
        when(processedMessageRepository.existsById(messageId)).thenReturn(false);
        when(orderHistoryRepository.findById("123")).thenReturn(Optional.of(existingRecord));
        
        // When
        eventHandlers.handleDeliveryEvent(payload, key, eventType);
        
        // Then
        ArgumentCaptor<OrderHistoryRecord> recordCaptor = ArgumentCaptor.forClass(OrderHistoryRecord.class);
        verify(orderHistoryRepository).save(recordCaptor.capture());
        
        OrderHistoryRecord updatedRecord = recordCaptor.getValue();
        assertEquals("DELIVERED", updatedRecord.getDeliveryStatus());
        
        // Verify message marked as processed
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }
    
    @Test
    void testCardAuthorizedDoesNotUpdateAuthorizationStatus() throws Exception {
        // Given
        CardAuthorizedEvent event = new CardAuthorizedEvent();
        event.setAccountId(777L);
        event.setAuthorizationId(555L);
        event.setRequestId("req-123");
        event.setAmount(new BigDecimal("45.99"));
        event.setOrderId(123L);
        
        String payload = objectMapper.writeValueAsString(event);
        String key = "Account#777";
        String eventType = "CardAuthorizedEvent";
        String messageId = key + "-" + eventType;
        
        when(processedMessageRepository.existsById(messageId)).thenReturn(false);
        
        // When
        eventHandlers.handleAccountEvent(payload, key, eventType);
        
        // Then: account events no longer drive authorization status correlation
        verify(orderHistoryRepository, never()).save(any(OrderHistoryRecord.class));
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
    }
    
    @Test
    void testIdempotencyProcessingSameEventTwiceProducesSameState() throws Exception {
        // Given
        OrderApproved event = new OrderApproved(123L, 456L, 789L, new Money("45.99"), 999L, 555L);
        String payload = objectMapper.writeValueAsString(event);
        String key = "Order#123";
        String eventType = "OrderApproved";
        String messageId = key + "-" + eventType;
        
        // First processing
        when(processedMessageRepository.existsById(messageId)).thenReturn(false);
        OrderHistoryRecord existingRecord = new OrderHistoryRecord("123");
        existingRecord.setStatus("APPROVAL_PENDING");
        when(orderHistoryRepository.findById("123")).thenReturn(Optional.of(existingRecord));
        
        eventHandlers.handleOrderEvent(payload, key, eventType);
        
        // Verify first processing
        verify(orderHistoryRepository, times(1)).save(any(OrderHistoryRecord.class));
        verify(processedMessageRepository, times(1)).save(any(ProcessedMessage.class));
        
        // Second processing (duplicate)
        when(processedMessageRepository.existsById(messageId)).thenReturn(true);
        
        eventHandlers.handleOrderEvent(payload, key, eventType);
        
        // Verify second processing was skipped
        verify(orderHistoryRepository, times(1)).save(any(OrderHistoryRecord.class)); // Still only 1 call
        verify(processedMessageRepository, times(1)).save(any(ProcessedMessage.class)); // Still only 1 call
    }
    
    @Test
    void testEventProcessingWhenOrderNotFound() throws Exception {
        // Given
        OrderApproved event = new OrderApproved(999L, 456L, 789L, new Money("45.99"), 999L, 555L);
        String payload = objectMapper.writeValueAsString(event);
        String key = "Order#999";
        String eventType = "OrderApproved";
        String messageId = key + "-" + eventType;
        
        when(processedMessageRepository.existsById(messageId)).thenReturn(false);
        when(orderHistoryRepository.findById("999")).thenReturn(Optional.empty());
        
        // When
        eventHandlers.handleOrderEvent(payload, key, eventType);
        
        // Then - should not throw exception, just log warning
        verify(orderHistoryRepository, never()).save(any(OrderHistoryRecord.class));
        verify(processedMessageRepository).save(any(ProcessedMessage.class)); // Still mark as processed
    }
}

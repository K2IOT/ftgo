package net.ftgo.orderhistory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import net.ftgo.orderhistory.repository.ProcessedMessageRepository;
import net.jqwik.api.*;
import net.jqwik.api.arbitraries.ListArbitrary;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Event Processing Idempotency.
 * 
 * Property 6: Event Processing Idempotency
 * Validates: Requirements 9.9, 11.8
 * 
 * Tests that processing the same event multiple times produces the same final state.
 * This is a critical property for eventual consistency in distributed systems.
 * 
 * The test verifies:
 * 1. Processing an event sequence once produces a final state
 * 2. Processing the same event sequence multiple times (with duplicates) produces the same final state
 * 3. The idempotency mechanism (processed_messages table) prevents duplicate processing
 * 4. The final state is deterministic regardless of duplicate events
 */
class EventProcessingIdempotencyPropertyTest {
    
    private ObjectMapper objectMapper;
    
    /**
     * Property: Processing the same event multiple times produces the same final state.
     * 
     * For any sequence of events:
     * - Process the sequence once → produces state S1
     * - Process the sequence again (with duplicates) → produces state S2
     * - S1 must equal S2 (idempotent processing)
     * 
     * This property ensures that:
     * - At-least-once Kafka delivery semantics don't cause data corruption
     * - Event replay for debugging/recovery produces consistent results
     * - CQRS read model remains eventually consistent
     */
    @Property(tries = 100)
    void processingEventMultipleTimesProducesSameFinalState(
        @ForAll("eventSequences") List<DomainEvent> events
    ) throws Exception {
        // Initialize ObjectMapper for this property
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        // Skip empty sequences
        Assume.that(!events.isEmpty());
        
        // First processing: process each event once
        OrderHistoryRepository repo1 = mock(OrderHistoryRepository.class);
        ProcessedMessageRepository processedRepo1 = mock(ProcessedMessageRepository.class);
        OrderHistoryEventHandlers handlers1 = new OrderHistoryEventHandlers(repo1, processedRepo1, objectMapper);
        
        Map<String, OrderHistoryRecord> state1 = new HashMap<>();
        setupMockRepositoryBehavior(repo1, processedRepo1, state1, new HashSet<>());
        
        for (DomainEvent event : events) {
            processEvent(handlers1, event);
        }
        
        // Second processing: process each event multiple times (simulate duplicates)
        OrderHistoryRepository repo2 = mock(OrderHistoryRepository.class);
        ProcessedMessageRepository processedRepo2 = mock(ProcessedMessageRepository.class);
        OrderHistoryEventHandlers handlers2 = new OrderHistoryEventHandlers(repo2, processedRepo2, objectMapper);
        
        Map<String, OrderHistoryRecord> state2 = new HashMap<>();
        setupMockRepositoryBehavior(repo2, processedRepo2, state2, new HashSet<>());
        
        // Process each event 2-3 times (simulate Kafka at-least-once delivery)
        Random random = new Random(42); // Fixed seed for reproducibility
        for (DomainEvent event : events) {
            int duplicates = 1 + random.nextInt(3); // 1-3 times
            for (int i = 0; i < duplicates; i++) {
                processEvent(handlers2, event);
            }
        }
        
        // Assert: Both processing runs produce the same final state
        assertEquals(state1.size(), state2.size(), 
            "Number of order history records should be the same");
        
        for (String orderId : state1.keySet()) {
            OrderHistoryRecord record1 = state1.get(orderId);
            OrderHistoryRecord record2 = state2.get(orderId);
            
            assertNotNull(record2, "Order " + orderId + " should exist in second processing");
            assertRecordsEqual(record1, record2, orderId);
        }
    }
    
    /**
     * Property: Processing events in different orders with duplicates produces consistent state.
     * 
     * This tests a stronger form of idempotency:
     * - Events may arrive out of order (Kafka partition rebalancing)
     * - Events may be duplicated (at-least-once delivery)
     * - Final state should still be consistent
     * 
     * Note: This property assumes events for the same order are processed in order
     * (guaranteed by Kafka partition key), but events for different orders can interleave.
     */
    @Property(tries = 100)
    void processingEventsWithDuplicatesProducesConsistentState(
        @ForAll("eventSequences") List<DomainEvent> events
    ) throws Exception {
        // Initialize ObjectMapper for this property
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        Assume.that(!events.isEmpty());
        
        // Group events by order ID to maintain per-order ordering
        Map<Long, List<DomainEvent>> eventsByOrder = events.stream()
            .collect(Collectors.groupingBy(DomainEvent::getOrderId));
        
        // First processing: process events in original order
        OrderHistoryRepository repo1 = mock(OrderHistoryRepository.class);
        ProcessedMessageRepository processedRepo1 = mock(ProcessedMessageRepository.class);
        OrderHistoryEventHandlers handlers1 = new OrderHistoryEventHandlers(repo1, processedRepo1, objectMapper);
        
        Map<String, OrderHistoryRecord> state1 = new HashMap<>();
        setupMockRepositoryBehavior(repo1, processedRepo1, state1, new HashSet<>());
        
        for (DomainEvent event : events) {
            processEvent(handlers1, event);
        }
        
        // Second processing: interleave events from different orders, with duplicates
        OrderHistoryRepository repo2 = mock(OrderHistoryRepository.class);
        ProcessedMessageRepository processedRepo2 = mock(ProcessedMessageRepository.class);
        OrderHistoryEventHandlers handlers2 = new OrderHistoryEventHandlers(repo2, processedRepo2, objectMapper);
        
        Map<String, OrderHistoryRecord> state2 = new HashMap<>();
        setupMockRepositoryBehavior(repo2, processedRepo2, state2, new HashSet<>());
        
        // Create interleaved sequence with duplicates
        List<DomainEvent> interleavedEvents = new ArrayList<>();
        List<List<DomainEvent>> orderEventLists = new ArrayList<>(eventsByOrder.values());
        
        // Round-robin through orders, adding duplicates randomly
        Random random = new Random(42);
        int[] indices = new int[orderEventLists.size()];
        boolean hasMore = true;
        
        while (hasMore) {
            hasMore = false;
            for (int i = 0; i < orderEventLists.size(); i++) {
                if (indices[i] < orderEventLists.get(i).size()) {
                    DomainEvent event = orderEventLists.get(i).get(indices[i]);
                    interleavedEvents.add(event);
                    
                    // Randomly add duplicate
                    if (random.nextBoolean()) {
                        interleavedEvents.add(event);
                    }
                    
                    indices[i]++;
                    hasMore = true;
                }
            }
        }
        
        for (DomainEvent event : interleavedEvents) {
            processEvent(handlers2, event);
        }
        
        // Assert: Both processing runs produce the same final state
        assertEquals(state1.size(), state2.size());
        
        for (String orderId : state1.keySet()) {
            OrderHistoryRecord record1 = state1.get(orderId);
            OrderHistoryRecord record2 = state2.get(orderId);
            
            assertNotNull(record2);
            assertRecordsEqual(record1, record2, orderId);
        }
    }
    
    // ========== Helper Methods ==========
    
    private void setupMockRepositoryBehavior(
        OrderHistoryRepository orderHistoryRepo,
        ProcessedMessageRepository processedMessageRepo,
        Map<String, OrderHistoryRecord> state,
        Set<String> processedMessages
    ) {
        // Mock existsById to check if message was processed
        when(processedMessageRepo.existsById(anyString())).thenAnswer(invocation -> {
            String messageId = invocation.getArgument(0);
            return processedMessages.contains(messageId);
        });
        
        // Mock save to track processed messages
        when(processedMessageRepo.save(any())).thenAnswer(invocation -> {
            net.ftgo.orderhistory.domain.ProcessedMessage msg = invocation.getArgument(0);
            processedMessages.add(msg.getMessageId());
            return msg;
        });
        
        // Mock findById to return current state
        when(orderHistoryRepo.findById(anyString())).thenAnswer(invocation -> {
            String orderId = invocation.getArgument(0);
            return Optional.ofNullable(state.get(orderId));
        });
        
        // Mock save to update state
        when(orderHistoryRepo.save(any(OrderHistoryRecord.class))).thenAnswer(invocation -> {
            OrderHistoryRecord record = invocation.getArgument(0);
            // Deep copy to avoid reference issues
            OrderHistoryRecord copy = deepCopyRecord(record);
            state.put(record.getOrderId(), copy);
            return record;
        });
    }
    
    private void processEvent(OrderHistoryEventHandlers handlers, DomainEvent event) throws Exception {
        String payload = objectMapper.writeValueAsString(event.toEventObject());
        String key = event.getKey();
        String eventType = event.getEventType();
        
        if (event instanceof OrderDomainEvent) {
            handlers.handleOrderEvent(payload, key, eventType);
        } else if (event instanceof TicketDomainEvent) {
            handlers.handleTicketEvent(payload, key, eventType);
        } else if (event instanceof DeliveryDomainEvent) {
            handlers.handleDeliveryEvent(payload, key, eventType);
        } else if (event instanceof AccountDomainEvent) {
            handlers.handleAccountEvent(payload, key, eventType);
        }
    }
    
    private OrderHistoryRecord deepCopyRecord(OrderHistoryRecord original) {
        OrderHistoryRecord copy = new OrderHistoryRecord(original.getOrderId());
        copy.setConsumerId(original.getConsumerId());
        copy.setRestaurantId(original.getRestaurantId());
        copy.setStatus(original.getStatus());
        copy.setOrderTotal(original.getOrderTotal());
        copy.setDeliveryAddress(original.getDeliveryAddress());
        copy.setDeliveryTime(original.getDeliveryTime());
        copy.setCreationDate(original.getCreationDate());
        copy.setTicketStatus(original.getTicketStatus());
        copy.setDeliveryStatus(original.getDeliveryStatus());
        copy.setAuthorizationStatus(original.getAuthorizationStatus());
        
        if (original.getLineItems() != null) {
            copy.setLineItems(new ArrayList<>(original.getLineItems()));
        }
        if (original.getKeywords() != null) {
            copy.setKeywords(new HashSet<>(original.getKeywords()));
        }
        
        return copy;
    }
    
    private void assertRecordsEqual(OrderHistoryRecord expected, OrderHistoryRecord actual, String orderId) {
        assertEquals(expected.getOrderId(), actual.getOrderId(), 
            "Order ID mismatch for " + orderId);
        assertEquals(expected.getConsumerId(), actual.getConsumerId(), 
            "Consumer ID mismatch for " + orderId);
        assertEquals(expected.getRestaurantId(), actual.getRestaurantId(), 
            "Restaurant ID mismatch for " + orderId);
        assertEquals(expected.getStatus(), actual.getStatus(), 
            "Status mismatch for " + orderId);
        assertEquals(expected.getOrderTotal(), actual.getOrderTotal(), 
            "Order total mismatch for " + orderId);
        assertEquals(expected.getTicketStatus(), actual.getTicketStatus(), 
            "Ticket status mismatch for " + orderId);
        assertEquals(expected.getDeliveryStatus(), actual.getDeliveryStatus(), 
            "Delivery status mismatch for " + orderId);
        assertEquals(expected.getAuthorizationStatus(), actual.getAuthorizationStatus(), 
            "Authorization status mismatch for " + orderId);
        
        if (expected.getLineItems() != null && actual.getLineItems() != null) {
            assertEquals(expected.getLineItems().size(), actual.getLineItems().size(), 
                "Line items count mismatch for " + orderId);
        }
    }
    
    // ========== Arbitraries (Test Data Generators) ==========
    
    @Provide
    Arbitrary<List<DomainEvent>> eventSequences() {
        return Arbitraries.integers().between(1, 5).flatMap(numOrders -> {
            // Generate events for multiple orders
            ListArbitrary<List<DomainEvent>> orderEventLists = Arbitraries.integers()
                .between(1, numOrders)
                .map(orderId -> generateEventsForOrder(orderId.longValue()))
                .list().ofSize(numOrders);
            
            return orderEventLists.map(lists -> 
                lists.stream().flatMap(List::stream).collect(Collectors.toList())
            );
        });
    }
    
    private List<DomainEvent> generateEventsForOrder(Long orderId) {
        List<DomainEvent> events = new ArrayList<>();
        
        // Always start with OrderCreated
        events.add(new OrderCreatedDomainEvent(orderId));
        
        // Randomly add lifecycle events
        Random random = new Random(orderId); // Deterministic per order
        
        // OrderApproved (80% chance)
        if (random.nextDouble() < 0.8) {
            events.add(new OrderApprovedDomainEvent(orderId));
            
            // TicketAccepted (70% chance if approved)
            if (random.nextDouble() < 0.7) {
                events.add(new TicketAcceptedDomainEvent(orderId));
                
                // TicketReady (60% chance if accepted)
                if (random.nextDouble() < 0.6) {
                    events.add(new TicketReadyDomainEvent(orderId));
                }
            }
            
            // CardAuthorized (90% chance if approved)
            if (random.nextDouble() < 0.9) {
                events.add(new CardAuthorizedDomainEvent(orderId));
            }
            
            // DeliveryPickedUp (50% chance if approved)
            if (random.nextDouble() < 0.5) {
                events.add(new DeliveryPickedUpDomainEvent(orderId));
                
                // DeliveryDelivered (80% chance if picked up)
                if (random.nextDouble() < 0.8) {
                    events.add(new DeliveryDeliveredDomainEvent(orderId));
                }
            }
            
            // OrderRevised (20% chance if approved)
            if (random.nextDouble() < 0.2) {
                events.add(new OrderRevisedDomainEvent(orderId));
            }
        } else {
            // OrderCancelled (if not approved)
            events.add(new OrderCancelledDomainEvent(orderId));
        }
        
        return events;
    }
    
    // ========== Domain Event Wrappers ==========
    
    interface DomainEvent {
        Long getOrderId();
        String getKey();
        String getEventType();
        Object toEventObject();
    }
    
    interface OrderDomainEvent extends DomainEvent {}
    interface TicketDomainEvent extends DomainEvent {}
    interface DeliveryDomainEvent extends DomainEvent {}
    interface AccountDomainEvent extends DomainEvent {}
    
    static class OrderCreatedDomainEvent implements OrderDomainEvent {
        private final Long orderId;
        
        OrderCreatedDomainEvent(Long orderId) {
            this.orderId = orderId;
        }
        
        @Override
        public Long getOrderId() {
            return orderId;
        }
        
        @Override
        public String getKey() {
            return "Order#" + orderId;
        }
        
        @Override
        public String getEventType() {
            return "OrderCreatedEvent";
        }
        
        @Override
        public Object toEventObject() {
            OrderCreatedEvent event = new OrderCreatedEvent();
            event.setOrderId(orderId);
            event.setConsumerId(orderId * 10);
            event.setRestaurantId(orderId * 100);
            event.setStatus("APPROVAL_PENDING");
            event.setOrderTotal(new BigDecimal("45.99"));
            event.setDeliveryAddress("123 Main St");
            event.setDeliveryTime(LocalDateTime.now().plusHours(1));
            event.setCreatedAt(LocalDateTime.now());
            
            OrderCreatedEvent.OrderLineItemDto lineItem = new OrderCreatedEvent.OrderLineItemDto();
            lineItem.setMenuItemId(1L);
            lineItem.setName("Burger");
            lineItem.setPrice(new BigDecimal("12.99"));
            lineItem.setQuantity(2);
            
            event.setLineItems(Collections.singletonList(lineItem));
            return event;
        }
    }
    
    static class OrderApprovedDomainEvent implements OrderDomainEvent {
        private final Long orderId;
        
        OrderApprovedDomainEvent(Long orderId) {
            this.orderId = orderId;
        }
        
        @Override
        public Long getOrderId() {
            return orderId;
        }
        
        @Override
        public String getKey() {
            return "Order#" + orderId;
        }
        
        @Override
        public String getEventType() {
            return "OrderApprovedEvent";
        }
        
        @Override
        public Object toEventObject() {
            return new OrderApprovedEvent(orderId);
        }
    }
    
    static class OrderCancelledDomainEvent implements OrderDomainEvent {
        private final Long orderId;
        
        OrderCancelledDomainEvent(Long orderId) {
            this.orderId = orderId;
        }
        
        @Override
        public Long getOrderId() {
            return orderId;
        }
        
        @Override
        public String getKey() {
            return "Order#" + orderId;
        }
        
        @Override
        public String getEventType() {
            return "OrderCancelledEvent";
        }
        
        @Override
        public Object toEventObject() {
            return new OrderCancelledEvent(orderId);
        }
    }
    
    static class OrderRevisedDomainEvent implements OrderDomainEvent {
        private final Long orderId;
        
        OrderRevisedDomainEvent(Long orderId) {
            this.orderId = orderId;
        }
        
        @Override
        public Long getOrderId() {
            return orderId;
        }
        
        @Override
        public String getKey() {
            return "Order#" + orderId;
        }
        
        @Override
        public String getEventType() {
            return "OrderRevisedEvent";
        }
        
        @Override
        public Object toEventObject() {
            OrderRevisedEvent event = new OrderRevisedEvent();
            event.setOrderId(orderId);
            event.setOrderTotal(new BigDecimal("55.99"));
            
            OrderCreatedEvent.OrderLineItemDto lineItem = new OrderCreatedEvent.OrderLineItemDto();
            lineItem.setMenuItemId(2L);
            lineItem.setName("Pizza");
            lineItem.setPrice(new BigDecimal("15.99"));
            lineItem.setQuantity(3);
            
            event.setLineItems(Collections.singletonList(lineItem));
            return event;
        }
    }
    
    static class TicketAcceptedDomainEvent implements TicketDomainEvent {
        private final Long orderId;
        
        TicketAcceptedDomainEvent(Long orderId) {
            this.orderId = orderId;
        }
        
        @Override
        public Long getOrderId() {
            return orderId;
        }
        
        @Override
        public String getKey() {
            return "Ticket#" + (orderId * 1000);
        }
        
        @Override
        public String getEventType() {
            return "TicketAcceptedEvent";
        }
        
        @Override
        public Object toEventObject() {
            TicketAcceptedEvent event = new TicketAcceptedEvent();
            event.setTicketId(orderId * 1000);
            event.setOrderId(orderId);
            return event;
        }
    }
    
    static class TicketReadyDomainEvent implements TicketDomainEvent {
        private final Long orderId;
        
        TicketReadyDomainEvent(Long orderId) {
            this.orderId = orderId;
        }
        
        @Override
        public Long getOrderId() {
            return orderId;
        }
        
        @Override
        public String getKey() {
            return "Ticket#" + (orderId * 1000);
        }
        
        @Override
        public String getEventType() {
            return "TicketReadyEvent";
        }
        
        @Override
        public Object toEventObject() {
            TicketReadyEvent event = new TicketReadyEvent();
            event.setTicketId(orderId * 1000);
            event.setOrderId(orderId);
            return event;
        }
    }
    
    static class DeliveryPickedUpDomainEvent implements DeliveryDomainEvent {
        private final Long orderId;
        
        DeliveryPickedUpDomainEvent(Long orderId) {
            this.orderId = orderId;
        }
        
        @Override
        public Long getOrderId() {
            return orderId;
        }
        
        @Override
        public String getKey() {
            return "Delivery#" + (orderId * 10000);
        }
        
        @Override
        public String getEventType() {
            return "DeliveryPickedUpEvent";
        }
        
        @Override
        public Object toEventObject() {
            DeliveryPickedUpEvent event = new DeliveryPickedUpEvent();
            event.setDeliveryId(orderId * 10000);
            event.setOrderId(orderId);
            return event;
        }
    }
    
    static class DeliveryDeliveredDomainEvent implements DeliveryDomainEvent {
        private final Long orderId;
        
        DeliveryDeliveredDomainEvent(Long orderId) {
            this.orderId = orderId;
        }
        
        @Override
        public Long getOrderId() {
            return orderId;
        }
        
        @Override
        public String getKey() {
            return "Delivery#" + (orderId * 10000);
        }
        
        @Override
        public String getEventType() {
            return "DeliveryDeliveredEvent";
        }
        
        @Override
        public Object toEventObject() {
            DeliveryDeliveredEvent event = new DeliveryDeliveredEvent();
            event.setDeliveryId(orderId * 10000);
            event.setOrderId(orderId);
            return event;
        }
    }
    
    static class CardAuthorizedDomainEvent implements AccountDomainEvent {
        private final Long orderId;
        
        CardAuthorizedDomainEvent(Long orderId) {
            this.orderId = orderId;
        }
        
        @Override
        public Long getOrderId() {
            return orderId;
        }
        
        @Override
        public String getKey() {
            return "Account#" + (orderId * 100000);
        }
        
        @Override
        public String getEventType() {
            return "CardAuthorizedEvent";
        }
        
        @Override
        public Object toEventObject() {
            CardAuthorizedEvent event = new CardAuthorizedEvent();
            event.setAccountId(orderId * 100000);
            event.setAuthorizationId(orderId * 1000000);
            event.setRequestId("req-" + orderId);
            event.setAmount(new BigDecimal("45.99"));
            event.setOrderId(orderId);
            return event;
        }
    }
}

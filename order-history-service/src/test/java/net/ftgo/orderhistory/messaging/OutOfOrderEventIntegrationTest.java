package net.ftgo.orderhistory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Money;
import net.ftgo.common.messaging.DomainEventEnvelope;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.common.orderflow.events.OrderCreated;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import net.ftgo.orderhistory.service.InMemoryPendingOrderEventStore;
import net.ftgo.orderhistory.service.OrderHistoryProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutOfOrderEventIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, OrderHistoryRecord> records = new HashMap<>();
    private InMemoryPendingOrderEventStore pendingStore;
    private OrderHistoryProjectionService projectionService;

    @BeforeEach
    void setUp() {
        OrderHistoryRepository repository = mock(OrderHistoryRepository.class);
        when(repository.findById(any())).thenAnswer(invocation ->
            Optional.ofNullable(records.get(invocation.getArgument(0))));
        when(repository.save(any(OrderHistoryRecord.class))).thenAnswer(invocation -> {
            OrderHistoryRecord record = invocation.getArgument(0);
            records.put(record.getOrderId(), record);
            return record;
        });
        pendingStore = new InMemoryPendingOrderEventStore();
        projectionService = new OrderHistoryProjectionService(
            repository,
            pendingStore,
            objectMapper
        );
    }

    @Test
    void orderApprovedBeforeOrderCreatedIsRecovered() throws Exception {
        projectionService.apply(envelope(
            UUID.randomUUID(),
            "OrderApproved",
            2,
            new OrderApproved(123L, 456L, 789L, new Money("30.00"), 9L, 8L)
        ));
        assertEquals(1, pendingStore.findByOrderId("123").size());

        projectionService.apply(envelope(
            UUID.randomUUID(),
            "OrderCreated",
            1,
            orderCreated()
        ));

        assertEquals("APPROVED", records.get("123").getStatus());
        assertTrue(pendingStore.findByOrderId("123").isEmpty());
    }

    @Test
    void ticketReadyWaitsForTicketAcceptedAndDuplicatePendingIsCollapsed() throws Exception {
        projectionService.apply(envelope(UUID.randomUUID(), "OrderCreated", 1, orderCreated()));
        UUID readyId = UUID.randomUUID();
        String ready = envelope(readyId, "TicketReadyEvent", 3, ticketReady());

        projectionService.apply(ready);
        projectionService.apply(ready);
        assertEquals(1, pendingStore.findByOrderId("123").size());

        projectionService.apply(envelope(
            UUID.randomUUID(),
            "TicketAcceptedEvent",
            2,
            ticketAccepted()
        ));

        assertEquals("READY", records.get("123").getTicketStatus());
        assertTrue(pendingStore.findByOrderId("123").isEmpty());
    }

    @Test
    void deliveryDeliveredBeforeHistoryRecordIsRecovered() throws Exception {
        projectionService.apply(envelope(
            UUID.randomUUID(),
            "DeliveryDeliveredEvent",
            2,
            deliveryDelivered()
        ));
        projectionService.apply(envelope(UUID.randomUUID(), "OrderCreated", 1, orderCreated()));

        assertEquals("DELIVERED", records.get("123").getDeliveryStatus());
        assertTrue(pendingStore.findByOrderId("123").isEmpty());
    }

    private TicketReadyEvent ticketReady() {
        TicketReadyEvent event = new TicketReadyEvent();
        event.setTicketId(55L);
        event.setOrderId(123L);
        return event;
    }

    private TicketAcceptedEvent ticketAccepted() {
        TicketAcceptedEvent event = new TicketAcceptedEvent();
        event.setTicketId(55L);
        event.setOrderId(123L);
        return event;
    }

    private DeliveryDeliveredEvent deliveryDelivered() {
        DeliveryDeliveredEvent event = new DeliveryDeliveredEvent();
        event.setDeliveryId(77L);
        event.setOrderId(123L);
        return event;
    }

    private OrderCreated orderCreated() {
        return new OrderCreated(
            123L,
            456L,
            789L,
            "APPROVAL_PENDING",
            new Money("30.00"),
            List.of(new OrderCreated.LineItem(1L, "Burger", new Money("30.00"), 1)),
            "1 Main St",
            LocalDateTime.now().plusHours(1),
            LocalDateTime.now()
        );
    }

    private String envelope(UUID eventId, String eventType, long version, Object payload)
        throws Exception {
        return objectMapper.writeValueAsString(new DomainEventEnvelope<>(
            eventId,
            eventType,
            1,
            aggregateType(eventType),
            "123",
            version,
            Instant.parse("2026-07-25T05:00:00Z").plusSeconds(version),
            "correlation-123",
            "causation-123",
            null,
            payload
        ));
    }

    private String aggregateType(String eventType) {
        if (eventType.startsWith("Ticket")) return "Ticket";
        if (eventType.startsWith("Delivery")) return "Delivery";
        return "Order";
    }
}

package net.ftgo.order.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.messaging.DomainEventMetadata;
import net.ftgo.common.messaging.TraceContext;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.common.orderflow.events.OrderCancelled;
import net.ftgo.common.orderflow.events.OrderCreated;
import net.ftgo.common.orderflow.events.OrderRevised;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DomainEventPublisherContractTest {

    private OutboxRepository outboxRepository;
    private ObjectMapper objectMapper;
    private DomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OutboxRepository.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        publisher = new DomainEventPublisher(outboxRepository, objectMapper);
    }

    @Test
    void publishOrderApprovedPersistsVersionedEnvelopeAndStableOutboxIdentity() throws Exception {
        Address pickupAddress = new Address("123 Restaurant St", "San Francisco", "CA", "94102");
        Address deliveryAddress = new Address("456 Consumer Ave", "San Francisco", "CA", "94103");
        OrderApproved event = new OrderApproved(
            101L,
            202L,
            303L,
            new Money("30.97"),
            404L,
            505L,
            pickupAddress,
            deliveryAddress,
            LocalDateTime.of(2026, 5, 18, 10, 45)
        );
        DomainEventMetadata metadata = new DomainEventMetadata(
            "correlation-123",
            "causation-456",
            new TraceContext(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
                true
            )
        );

        publisher.publishOrderEvent(101L, 7L, metadata, event);

        ArgumentCaptor<OutboxEntry> outboxCaptor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        OutboxEntry outboxEntry = outboxCaptor.getValue();

        assertEquals("Order", outboxEntry.getAggregateType());
        assertEquals("101", outboxEntry.getAggregateId());
        assertEquals("OrderApproved", outboxEntry.getEventType());
        assertNotNull(outboxEntry.getEventId());
        assertEquals(1, outboxEntry.getSchemaVersion());
        assertEquals(7L, outboxEntry.getAggregateVersion());

        JsonNode envelope = objectMapper.readTree(outboxEntry.getPayload());
        assertEquals(outboxEntry.getEventId().toString(), envelope.path("eventId").asText());
        assertEquals("OrderApproved", envelope.path("eventType").asText());
        assertEquals(1, envelope.path("schemaVersion").asInt());
        assertEquals("Order", envelope.path("aggregateType").asText());
        assertEquals("101", envelope.path("aggregateId").asText());
        assertEquals(7L, envelope.path("aggregateVersion").asLong());
        assertNotNull(Instant.parse(envelope.path("occurredAt").asText()));
        assertEquals("correlation-123", envelope.path("correlationId").asText());
        assertEquals("causation-456", envelope.path("causationId").asText());
        assertEquals(
            "4bf92f3577b34da6a3ce929d0e0e4736",
            envelope.path("trace").path("traceId").asText()
        );
        assertEquals(
            "123 Restaurant St",
            envelope.path("payload").path("pickupAddress").path("street").asText()
        );
        assertEquals(
            "456 Consumer Ave",
            envelope.path("payload").path("deliveryAddress").path("street").asText()
        );
    }

    @Test
    void publishOrderCreatedPreservesCurrentEmittedEventTypeName() {
        OrderCreated event = new OrderCreated(
            111L,
            222L,
            333L,
            "APPROVAL_PENDING",
            new Money("12.99"),
            List.of(new OrderCreated.LineItem(1L, "Burger", new Money("12.99"), 1)),
            "123 Main St",
            LocalDateTime.of(2026, 5, 18, 11, 0),
            LocalDateTime.of(2026, 5, 18, 10, 50)
        );

        publisher.publishOrderEvent(111L, event);

        ArgumentCaptor<OutboxEntry> outboxCaptor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("OrderCreated", outboxCaptor.getValue().getEventType());
    }

    @Test
    void publishOrderCancelledPreservesCurrentEmittedEventTypeName() {
        OrderCancelled event = new OrderCancelled(111L, 222L, 333L);

        publisher.publishOrderEvent(111L, event);

        ArgumentCaptor<OutboxEntry> outboxCaptor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("OrderCancelled", outboxCaptor.getValue().getEventType());
    }

    @Test
    void publishOrderRevisedPreservesCurrentEmittedEventTypeName() {
        OrderRevised event = new OrderRevised(
            111L,
            222L,
            333L,
            List.of(new OrderCreated.LineItem(1L, "Burger", new Money("12.99"), 1)),
            new Money("12.99")
        );

        publisher.publishOrderEvent(111L, event);

        ArgumentCaptor<OutboxEntry> outboxCaptor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertEquals("OrderRevised", outboxCaptor.getValue().getEventType());
    }
}

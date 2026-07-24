package net.ftgo.order.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.common.orderflow.events.OrderCancelled;
import net.ftgo.common.orderflow.events.OrderCreated;
import net.ftgo.common.orderflow.events.OrderRevised;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    void publishOrderApprovedPreservesCurrentEmittedEventTypeAndContractPayload() throws Exception {
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

        publisher.publishOrderEvent(101L, event);

        ArgumentCaptor<OutboxEntry> outboxCaptor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        OutboxEntry outboxEntry = outboxCaptor.getValue();

        assertEquals("Order", outboxEntry.getAggregateType());
        assertEquals("101", outboxEntry.getAggregateId());
        assertEquals("OrderApproved", outboxEntry.getEventType());

        JsonNode payload = objectMapper.readTree(outboxEntry.getPayload());
        assertNotNull(payload.get("pickupAddress"));
        assertNotNull(payload.get("deliveryAddress"));
        assertNotNull(payload.get("deliveryTime"));
        assertEquals("123 Restaurant St", payload.path("pickupAddress").path("street").asText());
        assertEquals("456 Consumer Ave", payload.path("deliveryAddress").path("street").asText());
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

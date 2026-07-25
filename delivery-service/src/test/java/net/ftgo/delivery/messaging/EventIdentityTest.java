package net.ftgo.delivery.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.messaging.DomainEventEnvelope;
import net.ftgo.common.messaging.EventIdentityExtractor;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.repository.DeliveryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventIdentityTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private ProcessedMessageRepository processedMessageRepository;

    private ObjectMapper objectMapper;
    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new OrderEventConsumer(
            deliveryRepository,
            processedMessageRepository,
            objectMapper
        );
    }

    @Test
    void rejectsKafkaHeaderThatDoesNotMatchEnvelopeEventId() throws Exception {
        UUID envelopeId = UUID.randomUUID();
        UUID headerId = UUID.randomUUID();
        ConsumerRecord<String, String> record = orderRecord(
            "101",
            envelope(envelopeId),
            headerId
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> EventIdentityExtractor.eventId(record.headers(), record.value(), objectMapper)
        );
    }

    @Test
    void sameAggregateKeyWithDifferentEventIdsAreTrackedIndependently() throws Exception {
        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        String firstPayload = envelope(firstEventId);
        String secondPayload = envelope(secondEventId);
        ConsumerRecord<String, String> first = orderRecord("101", firstPayload, firstEventId);
        ConsumerRecord<String, String> second = orderRecord("101", secondPayload, secondEventId);
        ConsumerRecord<String, String> duplicateFirst = orderRecord("101", firstPayload, firstEventId);

        Address pickup = new Address("1 Restaurant St", "Hanoi", "HN", "10000");
        Address destination = new Address("2 Consumer St", "Hanoi", "HN", "10000");
        Delivery existing = new Delivery(101L, pickup, destination, LocalDateTime.of(2026, 7, 25, 12, 0));

        when(processedMessageRepository.existsById(firstEventId.toString())).thenReturn(false, true);
        when(processedMessageRepository.existsById(secondEventId.toString())).thenReturn(false);
        when(deliveryRepository.findByOrderId(101L)).thenReturn(Optional.empty(), Optional.of(existing));

        consumer.handleOrderEvent(first);
        consumer.handleOrderEvent(second);
        consumer.handleOrderEvent(duplicateFirst);

        verify(deliveryRepository, times(1)).save(any(Delivery.class));
        ArgumentCaptor<ProcessedMessage> processed = ArgumentCaptor.forClass(ProcessedMessage.class);
        verify(processedMessageRepository, times(2)).save(processed.capture());
        Set<String> processedIds = processed.getAllValues().stream()
            .map(ProcessedMessage::getMessageId)
            .collect(Collectors.toSet());
        assertEquals(Set.of(firstEventId.toString(), secondEventId.toString()), processedIds);
    }

    private String envelope(UUID eventId) throws Exception {
        OrderApproved event = new OrderApproved(
            101L,
            202L,
            303L,
            new Money("30.00"),
            404L,
            505L,
            new Address("1 Restaurant St", "Hanoi", "HN", "10000"),
            new Address("2 Consumer St", "Hanoi", "HN", "10000"),
            LocalDateTime.of(2026, 7, 25, 12, 0)
        );
        return objectMapper.writeValueAsString(new DomainEventEnvelope<>(
            eventId,
            "OrderApproved",
            1,
            "Order",
            "101",
            2L,
            Instant.parse("2026-07-25T05:00:00Z"),
            "correlation-1",
            "causation-1",
            null,
            event
        ));
    }

    private ConsumerRecord<String, String> orderRecord(String key, String payload, UUID eventId) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "net.ftgo.orderservice.domain.Order",
            0,
            0L,
            key,
            payload
        );
        record.headers().add("eventType", "OrderApproved".getBytes(StandardCharsets.UTF_8));
        record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
        return record;
    }
}

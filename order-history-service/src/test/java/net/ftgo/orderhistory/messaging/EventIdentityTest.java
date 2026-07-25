package net.ftgo.orderhistory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Money;
import net.ftgo.common.messaging.DomainEventEnvelope;
import net.ftgo.common.orderflow.events.OrderCreated;
import net.ftgo.common.orderflow.events.OrderRevised;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventIdentityTest {

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @Mock
    private ProcessedMessageRepository processedMessageRepository;

    private ObjectMapper objectMapper;
    private OrderHistoryEventHandlers handlers;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        handlers = new OrderHistoryEventHandlers(
            orderHistoryRepository,
            processedMessageRepository,
            objectMapper
        );
    }

    @Test
    void twoSameTypeEventsForOneAggregateBothApplyOnce() throws Exception {
        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        OrderHistoryRecord record = new OrderHistoryRecord("123");

        when(orderHistoryRepository.findById("123")).thenReturn(Optional.of(record));
        when(processedMessageRepository.existsById(firstEventId.toString())).thenReturn(false, true);
        when(processedMessageRepository.existsById(secondEventId.toString())).thenReturn(false);

        handlers.handleOrderEvent(
            envelope(firstEventId, 2L, new Money("20.00")),
            "123",
            "OrderRevised",
            firstEventId.toString()
        );
        handlers.handleOrderEvent(
            envelope(secondEventId, 3L, new Money("25.00")),
            "123",
            "OrderRevised",
            secondEventId.toString()
        );
        handlers.handleOrderEvent(
            envelope(firstEventId, 2L, new Money("20.00")),
            "123",
            "OrderRevised",
            firstEventId.toString()
        );

        verify(orderHistoryRepository, times(2)).save(record);
        ArgumentCaptor<ProcessedMessage> processed = ArgumentCaptor.forClass(ProcessedMessage.class);
        verify(processedMessageRepository, times(2)).save(processed.capture());
        Set<String> ids = processed.getAllValues().stream()
            .map(ProcessedMessage::getMessageId)
            .collect(Collectors.toSet());
        assertEquals(Set.of(firstEventId.toString(), secondEventId.toString()), ids);
        assertEquals(new java.math.BigDecimal("25.00"), record.getOrderTotal());
    }

    private String envelope(UUID eventId, long aggregateVersion, Money total) throws Exception {
        OrderRevised event = new OrderRevised(
            123L,
            456L,
            789L,
            List.of(new OrderCreated.LineItem(1L, "Burger", total, 1)),
            total
        );
        return objectMapper.writeValueAsString(new DomainEventEnvelope<>(
            eventId,
            "OrderRevised",
            1,
            "Order",
            "123",
            aggregateVersion,
            Instant.parse("2026-07-25T05:00:00Z").plusSeconds(aggregateVersion),
            "correlation-123",
            "causation-123",
            null,
            event
        ));
    }
}

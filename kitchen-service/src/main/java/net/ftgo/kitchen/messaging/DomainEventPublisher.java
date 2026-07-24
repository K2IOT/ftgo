package net.ftgo.kitchen.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.messaging.DomainEventEnvelope;
import net.ftgo.common.messaging.DomainEventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Publisher for Ticket domain events using the Transactional Outbox pattern. */
@Component("kitchenOutboxDomainEventPublisher")
public class DomainEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(DomainEventPublisher.class);
    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DomainEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void publishTicketEvent(Long ticketId, Object event) {
        publishTicketEvent(ticketId, 0L, DomainEventMetadata.empty(), event);
    }

    @Transactional
    public void publishTicketEvent(Long ticketId, long aggregateVersion, Object event) {
        publishTicketEvent(ticketId, aggregateVersion, DomainEventMetadata.empty(), event);
    }

    @Transactional
    public void publishTicketEvent(
        Long ticketId,
        long aggregateVersion,
        DomainEventMetadata metadata,
        Object event
    ) {
        try {
            String eventType = event.getClass().getSimpleName();
            DomainEventEnvelope<Object> envelope = DomainEventEnvelope.create(
                eventType,
                CURRENT_SCHEMA_VERSION,
                "Ticket",
                ticketId.toString(),
                aggregateVersion,
                metadata,
                event
            );
            String payload = objectMapper.writeValueAsString(envelope);
            OutboxEntry entry = new OutboxEntry(
                envelope.eventId().toString(),
                envelope.schemaVersion(),
                envelope.aggregateVersion(),
                envelope.aggregateType(),
                envelope.aggregateId(),
                envelope.eventType(),
                payload,
                ChannelNames.TICKET_EVENT_TOPIC
            );
            outboxRepository.save(entry);
            logger.info("Published {} event {} for Ticket {} at version {}",
                eventType, envelope.eventId(), ticketId, aggregateVersion);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event for Ticket {}", ticketId, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}

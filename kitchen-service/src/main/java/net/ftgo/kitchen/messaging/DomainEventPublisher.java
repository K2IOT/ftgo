package net.ftgo.kitchen.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.messaging.DomainEventEnvelope;
import net.ftgo.common.messaging.DomainEventMetadata;
import net.ftgo.common.messaging.OutboxMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("kitchenOutboxDomainEventPublisher")
public class DomainEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(DomainEventPublisher.class);
    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final OutboxMetrics outboxMetrics;

    public DomainEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this(outboxRepository, objectMapper, OutboxMetrics.noop());
    }

    @Autowired
    public DomainEventPublisher(
        OutboxRepository outboxRepository,
        ObjectMapper objectMapper,
        OutboxMetrics outboxMetrics
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.outboxMetrics = outboxMetrics;
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
        String eventType = event.getClass().getSimpleName();
        try {
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
            outboxRepository.save(new OutboxEntry(
                envelope.eventId().toString(),
                envelope.schemaVersion(),
                envelope.aggregateVersion(),
                envelope.aggregateType(),
                envelope.aggregateId(),
                envelope.eventType(),
                payload,
                ChannelNames.TICKET_EVENT_TOPIC
            ));
            logger.info(
                "Published eventType={} eventId={} for Ticket {} at version {}",
                eventType,
                envelope.eventId(),
                ticketId,
                aggregateVersion
            );
        } catch (JsonProcessingException e) {
            outboxMetrics.recordPublishError();
            logger.error("Failed to serialize eventType={} for Ticket {}", eventType, ticketId, e);
            throw new RuntimeException("Failed to publish event", e);
        } catch (RuntimeException e) {
            outboxMetrics.recordPublishError();
            logger.error("Failed to insert eventType={} for Ticket {}", eventType, ticketId, e);
            throw e;
        }
    }
}

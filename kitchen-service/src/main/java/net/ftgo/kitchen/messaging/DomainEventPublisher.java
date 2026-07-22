package net.ftgo.kitchen.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.channels.ChannelNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publisher for domain events using Transactional Outbox pattern.
 *
 * Events are written to the outbox table in the same transaction as business data updates.
 * Debezium CDC publishes events from the outbox table to Kafka.
 */
@Component("kitchenOutboxDomainEventPublisher")
public class DomainEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DomainEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void publishTicketEvent(Long ticketId, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String eventType = event.getClass().getSimpleName();

            OutboxEntry entry = new OutboxEntry(
                    "Ticket",
                    ticketId.toString(),
                    eventType,
                    payload,
                    ChannelNames.TICKET_EVENT_TOPIC
            );

            outboxRepository.save(entry);
            logger.info("Published {} event for ticket {} to outbox", eventType, ticketId);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event for ticket {}", ticketId, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}

package net.ftgo.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.messaging.DomainEventEnvelope;
import net.ftgo.common.messaging.DomainEventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain event publisher using the Transactional Outbox pattern.
 */
@Component("ftgoDomainEventPublisher")
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
    public <T> void publish(String aggregateType, String aggregateId, T event) {
        publish(aggregateType, aggregateId, 0L, DomainEventMetadata.empty(), event);
    }

    @Transactional
    public <T> void publish(
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        DomainEventMetadata metadata,
        T event
    ) {
        try {
            String eventType = event.getClass().getSimpleName();
            DomainEventEnvelope<T> envelope = DomainEventEnvelope.create(
                eventType,
                CURRENT_SCHEMA_VERSION,
                aggregateType,
                aggregateId,
                aggregateVersion,
                metadata,
                event
            );
            String payload = objectMapper.writeValueAsString(envelope);

            OutboxEntry outboxEntry = new OutboxEntry(
                envelope.eventId().toString(),
                envelope.schemaVersion(),
                envelope.aggregateVersion(),
                aggregateType,
                aggregateId,
                eventType,
                payload,
                ChannelNames.ORDER_EVENT_TOPIC
            );

            outboxRepository.save(outboxEntry);

            logger.info(
                "Published event to outbox: eventId={}, aggregateType={}, aggregateId={}, "
                    + "aggregateVersion={}, eventType={}",
                envelope.eventId(),
                aggregateType,
                aggregateId,
                aggregateVersion,
                eventType
            );
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event: {}", event, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    public <T> void publishOrderEvent(Long orderId, T event) {
        publish("Order", orderId.toString(), event);
    }

    public <T> void publishOrderEvent(Long orderId, long aggregateVersion, T event) {
        publish(
            "Order",
            orderId.toString(),
            aggregateVersion,
            DomainEventMetadata.empty(),
            event
        );
    }

    public <T> void publishOrderEvent(
        Long orderId,
        long aggregateVersion,
        DomainEventMetadata metadata,
        T event
    ) {
        publish("Order", orderId.toString(), aggregateVersion, metadata, event);
    }
}

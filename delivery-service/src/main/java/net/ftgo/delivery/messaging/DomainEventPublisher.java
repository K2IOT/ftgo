package net.ftgo.delivery.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.messaging.DomainEventEnvelope;
import net.ftgo.common.messaging.DomainEventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Publisher for Delivery domain events using the Transactional Outbox pattern. */
@Component
public class DomainEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(DomainEventPublisher.class);
    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DomainEventPublisher(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Transactional
    public void publishDeliveryEvent(Long aggregateId, Object event) {
        publishDeliveryEvent(aggregateId, 0L, DomainEventMetadata.empty(), event);
    }

    @Transactional
    public void publishDeliveryEvent(Long aggregateId, long aggregateVersion, Object event) {
        publishDeliveryEvent(aggregateId, aggregateVersion, DomainEventMetadata.empty(), event);
    }

    @Transactional
    public void publishDeliveryEvent(
        Long aggregateId,
        long aggregateVersion,
        DomainEventMetadata metadata,
        Object event
    ) {
        try {
            String eventType = event.getClass().getSimpleName();
            DomainEventEnvelope<Object> envelope = DomainEventEnvelope.create(
                eventType,
                CURRENT_SCHEMA_VERSION,
                "Delivery",
                aggregateId.toString(),
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
                ChannelNames.DELIVERY_EVENT_TOPIC
            );
            outboxRepository.save(entry);
            logger.info("Published {} event {} for Delivery {} at version {}",
                eventType, envelope.eventId(), aggregateId, aggregateVersion);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event for Delivery {}", aggregateId, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}

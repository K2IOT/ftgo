package net.ftgo.restaurant.messaging;

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

/** Publisher for Restaurant domain events using the Transactional Outbox pattern. */
@Component("restaurantDomainEventPublisher")
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
    public void publishRestaurantEvent(Long aggregateId, Object event) {
        publishRestaurantEvent(aggregateId, 0L, DomainEventMetadata.empty(), event);
    }

    @Transactional
    public void publishRestaurantEvent(Long aggregateId, long aggregateVersion, Object event) {
        publishRestaurantEvent(aggregateId, aggregateVersion, DomainEventMetadata.empty(), event);
    }

    @Transactional
    public void publishRestaurantEvent(
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
                "Restaurant",
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
                ChannelNames.RESTAURANT_EVENT_TOPIC
            );
            outboxRepository.save(entry);
            logger.info("Published {} event {} for Restaurant {} at version {}",
                eventType, envelope.eventId(), aggregateId, aggregateVersion);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event for Restaurant {}", aggregateId, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}

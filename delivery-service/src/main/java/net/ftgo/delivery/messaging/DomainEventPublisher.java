package net.ftgo.delivery.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.messaging.DomainEventEnvelope;
import net.ftgo.common.messaging.DomainEventMetadata;
import net.ftgo.common.messaging.OutboxMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DomainEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(DomainEventPublisher.class);
    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final OutboxMetrics outboxMetrics;

    public DomainEventPublisher(OutboxRepository outboxRepository) {
        this(outboxRepository, OutboxMetrics.noop());
    }

    @Autowired
    public DomainEventPublisher(OutboxRepository outboxRepository, OutboxMetrics outboxMetrics) {
        this.outboxRepository = outboxRepository;
        this.outboxMetrics = outboxMetrics;
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
        String eventType = event.getClass().getSimpleName();
        try {
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
            outboxRepository.save(new OutboxEntry(
                envelope.eventId().toString(),
                envelope.schemaVersion(),
                envelope.aggregateVersion(),
                envelope.aggregateType(),
                envelope.aggregateId(),
                envelope.eventType(),
                payload,
                ChannelNames.DELIVERY_EVENT_TOPIC
            ));
            logger.info(
                "Published eventType={} eventId={} for Delivery {} at version {}",
                eventType,
                envelope.eventId(),
                aggregateId,
                aggregateVersion
            );
        } catch (JsonProcessingException e) {
            outboxMetrics.recordPublishError();
            logger.error("Failed to serialize eventType={} for Delivery {}", eventType, aggregateId, e);
            throw new RuntimeException("Failed to publish event", e);
        } catch (RuntimeException e) {
            outboxMetrics.recordPublishError();
            logger.error("Failed to insert eventType={} for Delivery {}", eventType, aggregateId, e);
            throw e;
        }
    }
}

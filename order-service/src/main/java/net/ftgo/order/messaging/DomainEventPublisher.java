package net.ftgo.order.messaging;

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

@Component("ftgoDomainEventPublisher")
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
        String eventType = event.getClass().getSimpleName();
        try {
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
            outboxRepository.save(new OutboxEntry(
                envelope.eventId().toString(),
                envelope.schemaVersion(),
                envelope.aggregateVersion(),
                aggregateType,
                aggregateId,
                eventType,
                payload,
                ChannelNames.ORDER_EVENT_TOPIC
            ));
            logger.info(
                "Published event to outbox: eventId={}, aggregateType={}, aggregateId={}, aggregateVersion={}, eventType={}",
                envelope.eventId(),
                aggregateType,
                aggregateId,
                aggregateVersion,
                eventType
            );
        } catch (JsonProcessingException e) {
            outboxMetrics.recordPublishError();
            logger.error("Failed to serialize eventType={}", eventType, e);
            throw new RuntimeException("Failed to publish event", e);
        } catch (RuntimeException e) {
            outboxMetrics.recordPublishError();
            logger.error("Failed to insert outbox eventType={}", eventType, e);
            throw e;
        }
    }

    public <T> void publishOrderEvent(Long orderId, T event) {
        publish("Order", orderId.toString(), event);
    }

    public <T> void publishOrderEvent(Long orderId, long aggregateVersion, T event) {
        publish("Order", orderId.toString(), aggregateVersion, DomainEventMetadata.empty(), event);
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

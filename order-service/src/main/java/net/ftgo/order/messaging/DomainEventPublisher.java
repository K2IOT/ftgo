package net.ftgo.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.channels.ChannelNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain event publisher using the Transactional Outbox pattern.
 * 
 * Ensures atomic database updates and event publishing by:
 * 1. Inserting events into the outbox table within the same transaction as business data
 * 2. Debezium CDC monitors the outbox table and publishes events to Kafka
 * 3. Guarantees exactly-once event publishing per database transaction
 * 
 * Event Flow:
 * 1. Service updates Order aggregate and calls publishEvent()
 * 2. publishEvent() inserts event into outbox table (same transaction)
 * 3. Transaction commits (both Order and outbox entry persisted atomically)
 * 4. Debezium detects outbox insert via MySQL binlog
 * 5. Debezium publishes event to Kafka topic
 * 6. Debezium marks outbox entry as published
 */
@Component("ftgoDomainEventPublisher")
public class DomainEventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(DomainEventPublisher.class);
    
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    public DomainEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Publishes a domain event via the transactional outbox.
     * 
     * @param aggregateType the aggregate type (e.g., "Order")
     * @param aggregateId the aggregate ID
     * @param event the domain event to publish
     * @param <T> the event type
     */
    @Transactional
    public <T> void publish(String aggregateType, String aggregateId, T event) {
        try {
            String eventType = event.getClass().getSimpleName();
            String payload = objectMapper.writeValueAsString(event);
            String destination = ChannelNames.ORDER_EVENT_TOPIC;
            
            OutboxEntry outboxEntry = new OutboxEntry(
                aggregateType,
                aggregateId,
                eventType,
                payload,
                destination
            );
            
            outboxRepository.save(outboxEntry);
            
            logger.info("Published event to outbox: aggregateType={}, aggregateId={}, eventType={}",
                aggregateType, aggregateId, eventType);
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event: {}", event, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
    
    /**
     * Publishes a domain event for an Order aggregate.
     * 
     * @param orderId the order ID
     * @param event the domain event to publish
     * @param <T> the event type
     */
    public <T> void publishOrderEvent(Long orderId, T event) {
        publish("Order", orderId.toString(), event);
    }
}

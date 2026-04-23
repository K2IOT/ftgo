package net.ftgo.delivery.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.ftgo.common.channels.ChannelNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publisher for domain events using the Transactional Outbox pattern.
 * 
 * Events are written to the outbox table within the same transaction as business data updates.
 * Debezium CDC monitors the outbox table and publishes events to Kafka.
 */
@Component
public class DomainEventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(DomainEventPublisher.class);
    
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    public DomainEventPublisher(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Publishes a domain event for a Delivery aggregate.
     * 
     * @param aggregateId the delivery ID
     * @param event the domain event to publish
     */
    @Transactional
    public void publishDeliveryEvent(Long aggregateId, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String eventType = event.getClass().getSimpleName();
            
            OutboxEntry entry = new OutboxEntry(
                "Delivery",
                aggregateId.toString(),
                eventType,
                payload,
                ChannelNames.DELIVERY_EVENT_TOPIC
            );
            
            outboxRepository.save(entry);
            
            logger.info("Published {} event for Delivery {} to outbox", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event for Delivery {}", aggregateId, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}

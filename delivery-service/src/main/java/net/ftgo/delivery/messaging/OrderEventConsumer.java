package net.ftgo.delivery.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.repository.DeliveryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer for Order domain events.
 * 
 * Handles OrderApproved events to create delivery records.
 * Implements idempotent event processing using processed_messages table.
 */
@Component
public class OrderEventConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);
    
    private final DeliveryRepository deliveryRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;
    
    public OrderEventConsumer(DeliveryRepository deliveryRepository,
                             ProcessedMessageRepository processedMessageRepository) {
        this.deliveryRepository = deliveryRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Handles OrderApproved event by creating a delivery record.
     * 
     * @param record the Kafka consumer record
     */
    @KafkaListener(topics = "net.ftgo.orderservice.domain.Order", groupId = "delivery-service")
    @Transactional
    public void handleOrderEvent(ConsumerRecord<String, String> record) {
        String messageId = record.key();
        String payload = record.value();
        
        logger.info("Received Order event, messageId: {}", messageId);
        
        // Check if message has already been processed (idempotency)
        if (processedMessageRepository.existsById(messageId)) {
            logger.info("Message {} already processed, skipping", messageId);
            return;
        }
        
        try {
            // Parse the event payload to determine event type
            // For now, we'll assume it's an OrderApproved event
            // In a real system, you'd check the event type field
            OrderApproved orderApproved = objectMapper.readValue(payload, OrderApproved.class);
            
            // Create delivery record
            Delivery delivery = new Delivery(
                orderApproved.getOrderId(),
                orderApproved.getPickupAddress(),
                orderApproved.getDeliveryAddress(),
                orderApproved.getScheduledTime()
            );
            
            deliveryRepository.save(delivery);
            
            // Mark message as processed
            processedMessageRepository.save(new ProcessedMessage(messageId));
            
            logger.info("Created delivery {} for order {}", delivery.getId(), orderApproved.getOrderId());
        } catch (Exception e) {
            logger.error("Failed to handle Order event, messageId: {}", messageId, e);
            throw new RuntimeException("Failed to process event", e);
        }
    }
}

package net.ftgo.delivery.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.messaging.OutboxEventPayloadReader;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.repository.DeliveryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Consumer for Order domain events. */
@Component
public class OrderEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);
    private static final String ORDER_APPROVED_EVENT_TYPE = "OrderApproved";

    private final DeliveryRepository deliveryRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(
        DeliveryRepository deliveryRepository,
        ProcessedMessageRepository processedMessageRepository,
        ObjectMapper objectMapper
    ) {
        this.deliveryRepository = deliveryRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "net.ftgo.orderservice.domain.Order", groupId = "delivery-service")
    @Transactional
    public void handleOrderEvent(ConsumerRecord<String, String> record) {
        String messageId = record.key();
        String eventType = eventType(record);

        logger.info("Received Order event, messageId: {}, eventType: {}", messageId, eventType);
        if (!ORDER_APPROVED_EVENT_TYPE.equals(eventType)) {
            logger.info("Ignoring non-OrderApproved event, messageId: {}, eventType: {}",
                messageId, eventType);
            return;
        }
        if (processedMessageRepository.existsById(messageId)) {
            logger.info("Message {} already processed, skipping", messageId);
            return;
        }

        try {
            OrderApproved orderApproved = OutboxEventPayloadReader.read(
                objectMapper,
                record.value(),
                OrderApproved.class
            );

            if (deliveryRepository.findByOrderId(orderApproved.getOrderId()).isPresent()) {
                logger.info("Delivery for order {} already exists, skipping", orderApproved.getOrderId());
                processedMessageRepository.save(new ProcessedMessage(messageId));
                return;
            }
            if (orderApproved.getPickupAddress() == null) {
                throw new IllegalArgumentException(
                    "OrderApproved pickupAddress is required for order " + orderApproved.getOrderId()
                );
            }

            Delivery delivery = new Delivery(
                orderApproved.getOrderId(),
                orderApproved.getPickupAddress(),
                orderApproved.getDeliveryAddress(),
                orderApproved.getDeliveryTime()
            );
            deliveryRepository.save(delivery);
            processedMessageRepository.save(new ProcessedMessage(messageId));
            logger.info("Created delivery {} for order {}", delivery.getId(), orderApproved.getOrderId());
        } catch (Exception e) {
            logger.error("Failed to handle Order event, messageId: {}", messageId, e);
            throw new RuntimeException("Failed to process event", e);
        }
    }

    private String eventType(ConsumerRecord<String, String> record) {
        var header = record.headers().lastHeader("eventType");
        if (header == null) {
            return null;
        }
        return new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
    }
}

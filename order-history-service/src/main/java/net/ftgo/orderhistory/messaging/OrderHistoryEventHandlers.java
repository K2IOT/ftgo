package net.ftgo.orderhistory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.messaging.EventIdentityExtractor;
import net.ftgo.common.messaging.KafkaEventHeaders;
import net.ftgo.orderhistory.domain.ProcessedMessage;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import net.ftgo.orderhistory.repository.ProcessedMessageRepository;
import net.ftgo.orderhistory.service.InMemoryPendingOrderEventStore;
import net.ftgo.orderhistory.service.OrderHistoryProjectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Validates, deduplicates and delegates domain events to the projection service. */
@Component
public class OrderHistoryEventHandlers {

    private static final Logger logger = LoggerFactory.getLogger(OrderHistoryEventHandlers.class);

    private final OrderHistoryProjectionService projectionService;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderHistoryEventHandlers(
        OrderHistoryProjectionService projectionService,
        ProcessedMessageRepository processedMessageRepository,
        ObjectMapper objectMapper
    ) {
        this.projectionService = projectionService;
        this.processedMessageRepository = processedMessageRepository;
        this.objectMapper = objectMapper;
    }

    /** Compatibility constructor retained for focused unit tests. */
    public OrderHistoryEventHandlers(
        OrderHistoryRepository orderHistoryRepository,
        ProcessedMessageRepository processedMessageRepository,
        ObjectMapper objectMapper
    ) {
        this(
            new OrderHistoryProjectionService(
                orderHistoryRepository,
                new InMemoryPendingOrderEventStore(),
                objectMapper
            ),
            processedMessageRepository,
            objectMapper
        );
    }

    @KafkaListener(
        topics = "net.ftgo.orderservice.domain.Order",
        groupId = "order-history-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleOrderEvent(
        @Payload String payload,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(value = KafkaEventHeaders.EVENT_TYPE, required = false) String eventType,
        @Header(KafkaEventHeaders.EVENT_ID) String eventIdHeader
    ) {
        processOnceWithHeader(payload, key, eventType, eventIdHeader);
    }

    public void handleOrderEvent(String payload, String key, String eventType) {
        processOnce(payload, key, eventType, legacyId(key, eventType, payload));
    }

    @KafkaListener(
        topics = "net.ftgo.kitchenservice.domain.Ticket",
        groupId = "order-history-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleTicketEvent(
        @Payload String payload,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(value = KafkaEventHeaders.EVENT_TYPE, required = false) String eventType,
        @Header(KafkaEventHeaders.EVENT_ID) String eventIdHeader
    ) {
        processOnceWithHeader(payload, key, eventType, eventIdHeader);
    }

    public void handleTicketEvent(String payload, String key, String eventType) {
        processOnce(payload, key, eventType, legacyId(key, eventType, payload));
    }

    @KafkaListener(
        topics = "net.ftgo.deliveryservice.domain.Delivery",
        groupId = "order-history-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleDeliveryEvent(
        @Payload String payload,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(value = KafkaEventHeaders.EVENT_TYPE, required = false) String eventType,
        @Header(KafkaEventHeaders.EVENT_ID) String eventIdHeader
    ) {
        processOnceWithHeader(payload, key, eventType, eventIdHeader);
    }

    public void handleDeliveryEvent(String payload, String key, String eventType) {
        processOnce(payload, key, eventType, legacyId(key, eventType, payload));
    }

    @KafkaListener(
        topics = "net.ftgo.accountingservice.domain.Account",
        groupId = "order-history-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleAccountEvent(
        @Payload String payload,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(value = KafkaEventHeaders.EVENT_TYPE, required = false) String eventType,
        @Header(KafkaEventHeaders.EVENT_ID) String eventIdHeader
    ) {
        processOnceWithHeader(payload, key, eventType, eventIdHeader);
    }

    public void handleAccountEvent(String payload, String key, String eventType) {
        processOnce(payload, key, eventType, legacyId(key, eventType, payload));
    }

    private void processOnceWithHeader(
        String payload,
        String aggregateKey,
        String eventType,
        String eventIdHeader
    ) {
        String eventId = EventIdentityExtractor.eventId(
            eventIdHeader,
            payload,
            objectMapper
        ).toString();
        processOnce(payload, aggregateKey, eventType, eventId);
    }

    private void processOnce(
        String payload,
        String aggregateKey,
        String eventType,
        String eventId
    ) {
        logger.info(
            "Received eventId={}, aggregateKey={}, eventType={}",
            eventId,
            aggregateKey,
            eventType
        );
        if (processedMessageRepository.existsById(eventId)) {
            logger.info("Skipping duplicate eventId={}, eventType={}", eventId, eventType);
            return;
        }

        projectionService.apply(payload, eventType, eventId);
        processedMessageRepository.save(new ProcessedMessage(eventId));
        logger.debug("Marked event as accepted: eventId={}", eventId);
    }

    private String legacyId(String key, String eventType, String payload) {
        return String.valueOf(key) + "-" + String.valueOf(eventType);
    }
}

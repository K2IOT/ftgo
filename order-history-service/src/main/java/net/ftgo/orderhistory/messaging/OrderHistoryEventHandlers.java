package net.ftgo.orderhistory.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.messaging.EventIdentityExtractor;
import net.ftgo.common.messaging.KafkaEventHeaders;
import net.ftgo.common.messaging.OutboxEventPayloadReader;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.common.orderflow.events.OrderCancelled;
import net.ftgo.common.orderflow.events.OrderCreated;
import net.ftgo.common.orderflow.events.OrderRejected;
import net.ftgo.common.orderflow.events.OrderRevised;
import net.ftgo.orderhistory.domain.LineItem;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.domain.ProcessedMessage;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import net.ftgo.orderhistory.repository.ProcessedMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Updates the Order History CQRS read model from versioned domain events. */
@Component
public class OrderHistoryEventHandlers {

    private static final Logger logger = LoggerFactory.getLogger(OrderHistoryEventHandlers.class);

    private final OrderHistoryRepository orderHistoryRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;

    public OrderHistoryEventHandlers(
        OrderHistoryRepository orderHistoryRepository,
        ProcessedMessageRepository processedMessageRepository,
        ObjectMapper objectMapper
    ) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.objectMapper = objectMapper;
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
        processOnce(payload, key, eventType, eventIdHeader, () -> {
            if (eventType == null) {
                throw new IllegalArgumentException("Order eventType header is required");
            }
            switch (eventType) {
                case "OrderCreated" -> handleOrderCreated(read(payload, OrderCreated.class));
                case "OrderApproved" -> handleOrderApproved(read(payload, OrderApproved.class).getOrderId());
                case "OrderRejected" -> handleOrderRejected(read(payload, OrderRejected.class).getOrderId());
                case "OrderCancelled" -> handleOrderCancelled(read(payload, OrderCancelled.class).getOrderId());
                case "OrderRevised" -> handleOrderRevised(read(payload, OrderRevised.class));
                default -> throw new IllegalArgumentException("Unknown order event type: " + eventType);
            }
        });
    }

    /** Legacy/direct-call compatibility during one rolling-upgrade window. */
    public void handleOrderEvent(String payload, String key, String eventType) {
        handleOrderEvent(payload, key, eventType, legacyId(key, eventType, payload));
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
        processOnce(payload, key, eventType, eventIdHeader, () -> {
            if (eventType == null) {
                throw new IllegalArgumentException("Ticket eventType header is required");
            }
            switch (eventType) {
                case "TicketAcceptedEvent" -> handleTicketAccepted(read(payload, TicketAcceptedEvent.class));
                case "TicketReadyEvent" -> handleTicketReady(read(payload, TicketReadyEvent.class));
                default -> throw new IllegalArgumentException("Unknown ticket event type: " + eventType);
            }
        });
    }

    public void handleTicketEvent(String payload, String key, String eventType) {
        handleTicketEvent(payload, key, eventType, legacyId(key, eventType, payload));
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
        processOnce(payload, key, eventType, eventIdHeader, () -> {
            if (eventType == null) {
                throw new IllegalArgumentException("Delivery eventType header is required");
            }
            switch (eventType) {
                case "DeliveryPickedUpEvent" -> handleDeliveryPickedUp(read(payload, DeliveryPickedUpEvent.class));
                case "DeliveryDeliveredEvent" -> handleDeliveryDelivered(read(payload, DeliveryDeliveredEvent.class));
                default -> throw new IllegalArgumentException("Unknown delivery event type: " + eventType);
            }
        });
    }

    public void handleDeliveryEvent(String payload, String key, String eventType) {
        handleDeliveryEvent(payload, key, eventType, legacyId(key, eventType, payload));
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
        processOnce(payload, key, eventType, eventIdHeader, () -> {
            if (!"CardAuthorizedEvent".equals(eventType)) {
                throw new IllegalArgumentException("Unknown account event type: " + eventType);
            }
            handleCardAuthorized(read(payload, CardAuthorizedEvent.class));
        });
    }

    public void handleAccountEvent(String payload, String key, String eventType) {
        handleAccountEvent(payload, key, eventType, legacyId(key, eventType, payload));
    }

    private void processOnce(
        String payload,
        String aggregateKey,
        String eventType,
        String eventIdHeader,
        EventAction action
    ) {
        String eventId = EventIdentityExtractor.eventId(
            eventIdHeader,
            payload,
            objectMapper
        ).toString();
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
        try {
            action.run();
            markProcessed(eventId);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse " + eventType + " event", e);
        }
    }

    private <T> T read(String payload, Class<T> eventClass) throws JsonProcessingException {
        return OutboxEventPayloadReader.read(objectMapper, payload, eventClass);
    }

    private String legacyId(String key, String eventType, String payload) {
        return EventIdentityExtractor.legacyEventId(key, eventType, payload).toString();
    }

    private void handleOrderCreated(OrderCreated event) {
        logger.info("Handling OrderCreated: orderId={}", event.getOrderId());
        OrderHistoryRecord record = new OrderHistoryRecord(event.getOrderId().toString());
        record.setConsumerId(event.getConsumerId());
        record.setRestaurantId(event.getRestaurantId());
        record.setStatus(event.getStatus());
        record.setOrderTotal(event.getOrderTotal().getAmount());
        record.setDeliveryAddress(event.getDeliveryAddress());
        record.setDeliveryTime(event.getDeliveryTime());
        record.setCreationDate(event.getCreatedAt());
        record.setLineItems(toLineItems(event.getLineItems()));
        record.setKeywords(toKeywords(event.getLineItems()));
        orderHistoryRepository.save(record);
    }

    private void handleOrderApproved(Long orderId) {
        find(orderId).ifPresentOrElse(record -> {
            record.setStatus("APPROVED");
            record.setAuthorizationStatus("APPROVED");
            orderHistoryRepository.save(record);
        }, () -> logger.warn("Order history record not found for OrderApproved: orderId={}", orderId));
    }

    private void handleOrderRejected(Long orderId) {
        find(orderId).ifPresentOrElse(record -> {
            record.setStatus("REJECTED");
            orderHistoryRepository.save(record);
        }, () -> logger.warn("Order history record not found for OrderRejected: orderId={}", orderId));
    }

    private void handleOrderCancelled(Long orderId) {
        find(orderId).ifPresentOrElse(record -> {
            record.setStatus("CANCELLED");
            orderHistoryRepository.save(record);
        }, () -> logger.warn("Order history record not found for OrderCancelled: orderId={}", orderId));
    }

    private void handleOrderRevised(OrderRevised event) {
        find(event.getOrderId()).ifPresentOrElse(record -> {
            record.setOrderTotal(event.getOrderTotal().getAmount());
            record.setLineItems(toLineItems(event.getLineItems()));
            record.setKeywords(toKeywords(event.getLineItems()));
            orderHistoryRepository.save(record);
        }, () -> logger.warn("Order history record not found for OrderRevised: orderId={}", event.getOrderId()));
    }

    private void handleTicketAccepted(TicketAcceptedEvent event) {
        find(event.getOrderId()).ifPresentOrElse(record -> {
            record.setTicketStatus("ACCEPTED");
            orderHistoryRepository.save(record);
        }, () -> logger.warn("Order history record not found for TicketAccepted: orderId={}", event.getOrderId()));
    }

    private void handleTicketReady(TicketReadyEvent event) {
        find(event.getOrderId()).ifPresentOrElse(record -> {
            record.setTicketStatus("READY");
            orderHistoryRepository.save(record);
        }, () -> logger.warn("Order history record not found for TicketReady: orderId={}", event.getOrderId()));
    }

    private void handleDeliveryPickedUp(DeliveryPickedUpEvent event) {
        find(event.getOrderId()).ifPresentOrElse(record -> {
            record.setDeliveryStatus("PICKED_UP");
            orderHistoryRepository.save(record);
        }, () -> logger.warn("Order history record not found for DeliveryPickedUp: orderId={}", event.getOrderId()));
    }

    private void handleDeliveryDelivered(DeliveryDeliveredEvent event) {
        find(event.getOrderId()).ifPresentOrElse(record -> {
            record.setDeliveryStatus("DELIVERED");
            orderHistoryRepository.save(record);
        }, () -> logger.warn("Order history record not found for DeliveryDelivered: orderId={}", event.getOrderId()));
    }

    private void handleCardAuthorized(CardAuthorizedEvent event) {
        logger.info(
            "Ignoring CardAuthorized for authorization state; derived from OrderApproved: orderId={}",
            event.getOrderId()
        );
    }

    private Optional<OrderHistoryRecord> find(Long orderId) {
        return orderHistoryRepository.findById(orderId.toString());
    }

    private List<LineItem> toLineItems(List<OrderCreated.LineItem> items) {
        return items.stream()
            .map(item -> new LineItem(
                item.getMenuItemId(),
                item.getName(),
                item.getPrice().getAmount(),
                item.getQuantity()
            ))
            .collect(Collectors.toList());
    }

    private HashSet<String> toKeywords(List<OrderCreated.LineItem> items) {
        HashSet<String> keywords = new HashSet<>();
        items.forEach(item -> {
            if (item.getName() != null) {
                keywords.add(item.getName().toLowerCase());
            }
        });
        return keywords;
    }

    private void markProcessed(String eventId) {
        processedMessageRepository.save(new ProcessedMessage(eventId));
        logger.debug("Marked event as processed: eventId={}", eventId);
    }

    @FunctionalInterface
    private interface EventAction {
        void run() throws JsonProcessingException;
    }
}

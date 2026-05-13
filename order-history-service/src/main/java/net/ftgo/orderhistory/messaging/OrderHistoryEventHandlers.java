package net.ftgo.orderhistory.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Money;
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

/**
 * Event handlers for updating the Order History CQRS read model.
 * 
 * Consumes domain events from multiple services:
 * - Order Service: OrderCreated, OrderApproved, OrderCancelled, OrderRevised
 * - Kitchen Service: TicketAccepted, TicketReady
 * - Delivery Service: DeliveryPickedUp, DeliveryDelivered
 * - Accounting Service: CardAuthorized
 * 
 * Implements idempotent event processing using processed_messages table.
 * Each event is processed at most once, ensuring eventual consistency.
 */
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
    
    /**
     * Handles OrderCreated events from Order Service.
     * Creates a new order history record.
     */
    @KafkaListener(
        topics = "net.ftgo.orderservice.domain.Order",
        groupId = "order-history-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleOrderEvent(
        @Payload String payload,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(value = "eventType", required = false) String eventType
    ) {
        try {
            // Parse the event to determine type
            JsonNode eventNode = objectMapper.readTree(payload);
            String messageId = key + "-" + eventType;
            
            // Idempotency check
            if (isDuplicate(messageId)) {
                logger.info("Skipping duplicate event: messageId={}, eventType={}", messageId, eventType);
                return;
            }
            
            // Route to appropriate handler based on event type
            if (eventType == null) {
                // Try to infer event type from payload
                if (eventNode.has("createdAt") && eventNode.has("lineItems")) {
                    eventType = "OrderCreatedEvent";
                } else if (eventNode.has("orderId") && eventNode.size() == 1) {
                    // Could be OrderApproved or OrderCancelled
                    eventType = "OrderApprovedEvent"; // Default assumption
                }
            }
            
            if (eventType == null) {
                logger.warn("Could not determine event type for messageId={}, skipping", messageId);
                return;
            }
            
            switch (eventType) {
                case "OrderCreated":
                    OrderCreated sharedOrderCreated = objectMapper.treeToValue(eventNode, OrderCreated.class);
                    handleOrderCreated(sharedOrderCreated);
                    break;
                case "OrderCreatedEvent":
                    OrderCreatedEvent orderCreated = objectMapper.treeToValue(eventNode, OrderCreatedEvent.class);
                    handleOrderCreated(toSharedOrderCreated(orderCreated));
                    break;
                case "OrderApproved":
                    OrderApproved sharedOrderApproved = objectMapper.treeToValue(eventNode, OrderApproved.class);
                    handleOrderApproved(sharedOrderApproved.getOrderId());
                    break;
                case "OrderApprovedEvent":
                    OrderApprovedEvent orderApproved = objectMapper.treeToValue(eventNode, OrderApprovedEvent.class);
                    handleOrderApproved(orderApproved.getOrderId());
                    break;
                case "OrderRejected":
                    OrderRejected sharedOrderRejected = objectMapper.treeToValue(eventNode, OrderRejected.class);
                    handleOrderRejected(sharedOrderRejected.getOrderId());
                    break;
                case "OrderCancelled":
                    OrderCancelled sharedOrderCancelled = objectMapper.treeToValue(eventNode, OrderCancelled.class);
                    handleOrderCancelled(sharedOrderCancelled.getOrderId());
                    break;
                case "OrderCancelledEvent":
                    OrderCancelledEvent orderCancelled = objectMapper.treeToValue(eventNode, OrderCancelledEvent.class);
                    handleOrderCancelled(orderCancelled.getOrderId());
                    break;
                case "OrderRevised":
                    OrderRevised sharedOrderRevised = objectMapper.treeToValue(eventNode, OrderRevised.class);
                    handleOrderRevised(sharedOrderRevised);
                    break;
                case "OrderRevisedEvent":
                    OrderRevisedEvent orderRevised = objectMapper.treeToValue(eventNode, OrderRevisedEvent.class);
                    handleOrderRevised(toSharedOrderRevised(orderRevised));
                    break;
                default:
                    logger.warn("Unknown event type: {}", eventType);
                    return;
            }
            
            // Mark message as processed
            markProcessed(messageId);
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse order event: {}", payload, e);
            throw new RuntimeException("Failed to process order event", e);
        }
    }
    
    /**
     * Handles TicketAccepted and TicketReady events from Kitchen Service.
     */
    @KafkaListener(
        topics = "net.ftgo.kitchenservice.domain.Ticket",
        groupId = "order-history-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleTicketEvent(
        @Payload String payload,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(value = "eventType", required = false) String eventType
    ) {
        try {
            JsonNode eventNode = objectMapper.readTree(payload);
            String messageId = key + "-" + eventType;
            
            // Idempotency check
            if (isDuplicate(messageId)) {
                logger.info("Skipping duplicate event: messageId={}, eventType={}", messageId, eventType);
                return;
            }
            
            switch (eventType) {
                case "TicketAcceptedEvent":
                    TicketAcceptedEvent ticketAccepted = objectMapper.treeToValue(eventNode, TicketAcceptedEvent.class);
                    handleTicketAccepted(ticketAccepted);
                    break;
                case "TicketReadyEvent":
                    TicketReadyEvent ticketReady = objectMapper.treeToValue(eventNode, TicketReadyEvent.class);
                    handleTicketReady(ticketReady);
                    break;
                default:
                    logger.warn("Unknown ticket event type: {}", eventType);
                    return;
            }
            
            // Mark message as processed
            markProcessed(messageId);
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse ticket event: {}", payload, e);
            throw new RuntimeException("Failed to process ticket event", e);
        }
    }
    
    /**
     * Handles DeliveryPickedUp and DeliveryDelivered events from Delivery Service.
     */
    @KafkaListener(
        topics = "net.ftgo.deliveryservice.domain.Delivery",
        groupId = "order-history-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleDeliveryEvent(
        @Payload String payload,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(value = "eventType", required = false) String eventType
    ) {
        try {
            JsonNode eventNode = objectMapper.readTree(payload);
            String messageId = key + "-" + eventType;
            
            // Idempotency check
            if (isDuplicate(messageId)) {
                logger.info("Skipping duplicate event: messageId={}, eventType={}", messageId, eventType);
                return;
            }
            
            switch (eventType) {
                case "DeliveryPickedUpEvent":
                    DeliveryPickedUpEvent deliveryPickedUp = objectMapper.treeToValue(eventNode, DeliveryPickedUpEvent.class);
                    handleDeliveryPickedUp(deliveryPickedUp);
                    break;
                case "DeliveryDeliveredEvent":
                    DeliveryDeliveredEvent deliveryDelivered = objectMapper.treeToValue(eventNode, DeliveryDeliveredEvent.class);
                    handleDeliveryDelivered(deliveryDelivered);
                    break;
                default:
                    logger.warn("Unknown delivery event type: {}", eventType);
                    return;
            }
            
            // Mark message as processed
            markProcessed(messageId);
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse delivery event: {}", payload, e);
            throw new RuntimeException("Failed to process delivery event", e);
        }
    }
    
    /**
     * Handles CardAuthorized events from Accounting Service.
     */
    @KafkaListener(
        topics = "net.ftgo.accountingservice.domain.Account",
        groupId = "order-history-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleAccountEvent(
        @Payload String payload,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(value = "eventType", required = false) String eventType
    ) {
        try {
            JsonNode eventNode = objectMapper.readTree(payload);
            String messageId = key + "-" + eventType;
            
            // Idempotency check
            if (isDuplicate(messageId)) {
                logger.info("Skipping duplicate event: messageId={}, eventType={}", messageId, eventType);
                return;
            }
            
            if ("CardAuthorizedEvent".equals(eventType)) {
                CardAuthorizedEvent cardAuthorized = objectMapper.treeToValue(eventNode, CardAuthorizedEvent.class);
                handleCardAuthorized(cardAuthorized);
            } else {
                logger.warn("Unknown account event type: {}", eventType);
                return;
            }
            
            // Mark message as processed
            markProcessed(messageId);
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse account event: {}", payload, e);
            throw new RuntimeException("Failed to process account event", e);
        }
    }
    
    // ========== Event Handler Methods ==========
    
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
        
        // Convert line items
        List<LineItem> lineItems = event.getLineItems().stream()
            .map(dto -> new LineItem(dto.getMenuItemId(), dto.getName(), dto.getPrice().getAmount(), dto.getQuantity()))
            .collect(Collectors.toList());
        record.setLineItems(lineItems);
        
        // Extract keywords for search
        HashSet<String> keywords = new HashSet<>();
        event.getLineItems().forEach(item -> {
            if (item.getName() != null) {
                keywords.add(item.getName().toLowerCase());
            }
        });
        record.setKeywords(keywords);
        
        orderHistoryRepository.save(record);
        logger.info("Created order history record: orderId={}", event.getOrderId());
    }
    
    private void handleOrderApproved(Long orderId) {
        logger.info("Handling OrderApproved: orderId={}", orderId);
        
        Optional<OrderHistoryRecord> optionalRecord = orderHistoryRepository.findById(orderId.toString());
        if (optionalRecord.isPresent()) {
            OrderHistoryRecord record = optionalRecord.get();
            record.setStatus("APPROVED");
            orderHistoryRepository.save(record);
            logger.info("Updated order status to APPROVED: orderId={}", orderId);
        } else {
            logger.warn("Order history record not found for OrderApproved: orderId={}", orderId);
        }
    }

    private OrderCreated toSharedOrderCreated(OrderCreatedEvent event) {
        List<OrderCreated.LineItem> lineItems = event.getLineItems().stream()
            .map(item -> new OrderCreated.LineItem(
                item.getMenuItemId(),
                item.getName(),
                new Money(item.getPrice()),
                item.getQuantity()
            ))
            .collect(Collectors.toList());

        return new OrderCreated(
            event.getOrderId(),
            event.getConsumerId(),
            event.getRestaurantId(),
            event.getStatus(),
            new Money(event.getOrderTotal()),
            lineItems,
            event.getDeliveryAddress(),
            event.getDeliveryTime(),
            event.getCreatedAt()
        );
    }
    
    private void handleOrderRejected(Long orderId) {
        logger.info("Handling OrderRejected: orderId={}", orderId);
        
        Optional<OrderHistoryRecord> optionalRecord = orderHistoryRepository.findById(orderId.toString());
        if (optionalRecord.isPresent()) {
            OrderHistoryRecord record = optionalRecord.get();
            record.setStatus("REJECTED");
            orderHistoryRepository.save(record);
            logger.info("Updated order status to REJECTED: orderId={}", orderId);
        } else {
            logger.warn("Order history record not found for OrderRejected: orderId={}", orderId);
        }
    }

    private void handleOrderCancelled(Long orderId) {
        logger.info("Handling OrderCancelled: orderId={}", orderId);
        
        Optional<OrderHistoryRecord> optionalRecord = orderHistoryRepository.findById(orderId.toString());
        if (optionalRecord.isPresent()) {
            OrderHistoryRecord record = optionalRecord.get();
            record.setStatus("CANCELLED");
            orderHistoryRepository.save(record);
            logger.info("Updated order status to CANCELLED: orderId={}", orderId);
        } else {
            logger.warn("Order history record not found for OrderCancelled: orderId={}", orderId);
        }
    }
    
    private void handleOrderRevised(OrderRevised event) {
        logger.info("Handling OrderRevised: orderId={}", event.getOrderId());
        
        Optional<OrderHistoryRecord> optionalRecord = orderHistoryRepository.findById(event.getOrderId().toString());
        if (optionalRecord.isPresent()) {
            OrderHistoryRecord record = optionalRecord.get();
            record.setOrderTotal(event.getOrderTotal().getAmount());
            
            // Update line items
            List<LineItem> lineItems = event.getLineItems().stream()
                .map(dto -> new LineItem(dto.getMenuItemId(), dto.getName(), dto.getPrice().getAmount(), dto.getQuantity()))
                .collect(Collectors.toList());
            record.setLineItems(lineItems);
            
            // Update keywords
            HashSet<String> keywords = new HashSet<>();
            event.getLineItems().forEach(item -> {
                if (item.getName() != null) {
                    keywords.add(item.getName().toLowerCase());
                }
            });
            record.setKeywords(keywords);
            
            orderHistoryRepository.save(record);
            logger.info("Updated order details for revision: orderId={}", event.getOrderId());
        } else {
            logger.warn("Order history record not found for OrderRevised: orderId={}", event.getOrderId());
        }
    }

    private OrderRevised toSharedOrderRevised(OrderRevisedEvent event) {
        List<OrderCreated.LineItem> lineItems = event.getLineItems().stream()
            .map(item -> new OrderCreated.LineItem(
                item.getMenuItemId(),
                item.getName(),
                new Money(item.getPrice()),
                item.getQuantity()
            ))
            .collect(Collectors.toList());

        return new OrderRevised(
            event.getOrderId(),
            null,
            null,
            lineItems,
            new Money(event.getOrderTotal())
        );
    }
    
    private void handleTicketAccepted(TicketAcceptedEvent event) {
        logger.info("Handling TicketAccepted: orderId={}", event.getOrderId());
        
        Optional<OrderHistoryRecord> optionalRecord = orderHistoryRepository.findById(event.getOrderId().toString());
        if (optionalRecord.isPresent()) {
            OrderHistoryRecord record = optionalRecord.get();
            record.setTicketStatus("ACCEPTED");
            orderHistoryRepository.save(record);
            logger.info("Updated ticket status to ACCEPTED: orderId={}", event.getOrderId());
        } else {
            logger.warn("Order history record not found for TicketAccepted: orderId={}", event.getOrderId());
        }
    }
    
    private void handleTicketReady(TicketReadyEvent event) {
        logger.info("Handling TicketReady: orderId={}", event.getOrderId());
        
        Optional<OrderHistoryRecord> optionalRecord = orderHistoryRepository.findById(event.getOrderId().toString());
        if (optionalRecord.isPresent()) {
            OrderHistoryRecord record = optionalRecord.get();
            record.setTicketStatus("READY");
            orderHistoryRepository.save(record);
            logger.info("Updated ticket status to READY: orderId={}", event.getOrderId());
        } else {
            logger.warn("Order history record not found for TicketReady: orderId={}", event.getOrderId());
        }
    }
    
    private void handleDeliveryPickedUp(DeliveryPickedUpEvent event) {
        logger.info("Handling DeliveryPickedUp: orderId={}", event.getOrderId());
        
        Optional<OrderHistoryRecord> optionalRecord = orderHistoryRepository.findById(event.getOrderId().toString());
        if (optionalRecord.isPresent()) {
            OrderHistoryRecord record = optionalRecord.get();
            record.setDeliveryStatus("PICKED_UP");
            orderHistoryRepository.save(record);
            logger.info("Updated delivery status to PICKED_UP: orderId={}", event.getOrderId());
        } else {
            logger.warn("Order history record not found for DeliveryPickedUp: orderId={}", event.getOrderId());
        }
    }
    
    private void handleDeliveryDelivered(DeliveryDeliveredEvent event) {
        logger.info("Handling DeliveryDelivered: orderId={}", event.getOrderId());
        
        Optional<OrderHistoryRecord> optionalRecord = orderHistoryRepository.findById(event.getOrderId().toString());
        if (optionalRecord.isPresent()) {
            OrderHistoryRecord record = optionalRecord.get();
            record.setDeliveryStatus("DELIVERED");
            orderHistoryRepository.save(record);
            logger.info("Updated delivery status to DELIVERED: orderId={}", event.getOrderId());
        } else {
            logger.warn("Order history record not found for DeliveryDelivered: orderId={}", event.getOrderId());
        }
    }
    
    private void handleCardAuthorized(CardAuthorizedEvent event) {
        logger.info("Handling CardAuthorized: orderId={}", event.getOrderId());
        
        if (event.getOrderId() == null) {
            logger.warn("CardAuthorized event missing orderId, cannot correlate with order history");
            return;
        }
        
        Optional<OrderHistoryRecord> optionalRecord = orderHistoryRepository.findById(event.getOrderId().toString());
        if (optionalRecord.isPresent()) {
            OrderHistoryRecord record = optionalRecord.get();
            record.setAuthorizationStatus("APPROVED");
            orderHistoryRepository.save(record);
            logger.info("Updated authorization status to APPROVED: orderId={}", event.getOrderId());
        } else {
            logger.warn("Order history record not found for CardAuthorized: orderId={}", event.getOrderId());
        }
    }
    
    // ========== Idempotency Support ==========
    
    /**
     * Checks if a message has already been processed.
     * 
     * @param messageId the message ID
     * @return true if the message has been processed
     */
    private boolean isDuplicate(String messageId) {
        return processedMessageRepository.existsById(messageId);
    }
    
    /**
     * Marks a message as processed.
     * 
     * @param messageId the message ID
     */
    private void markProcessed(String messageId) {
        ProcessedMessage processedMessage = new ProcessedMessage(messageId);
        processedMessageRepository.save(processedMessage);
        logger.debug("Marked message as processed: messageId={}", messageId);
    }
}

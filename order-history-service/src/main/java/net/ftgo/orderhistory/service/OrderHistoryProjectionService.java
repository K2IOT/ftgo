package net.ftgo.orderhistory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.messaging.NonRetryableEventException;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.common.orderflow.events.OrderCancelled;
import net.ftgo.common.orderflow.events.OrderCreated;
import net.ftgo.common.orderflow.events.OrderRejected;
import net.ftgo.common.orderflow.events.OrderRevised;
import net.ftgo.orderhistory.domain.LineItem;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.domain.PendingOrderEvent;
import net.ftgo.orderhistory.domain.PendingOrderEventKey;
import net.ftgo.orderhistory.messaging.CardAuthorizedEvent;
import net.ftgo.orderhistory.messaging.DeliveryDeliveredEvent;
import net.ftgo.orderhistory.messaging.DeliveryPickedUpEvent;
import net.ftgo.orderhistory.messaging.TicketAcceptedEvent;
import net.ftgo.orderhistory.messaging.TicketReadyEvent;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderHistoryProjectionService {

    public enum ProjectionResult {
        APPLIED,
        PENDING
    }

    private static final int SYNCHRONOUS_DRAIN_LIMIT = 100;

    private final OrderHistoryRepository orderHistoryRepository;
    private final PendingOrderEventStore pendingEventStore;
    private final ObjectMapper objectMapper;

    public OrderHistoryProjectionService(
        OrderHistoryRepository orderHistoryRepository,
        PendingOrderEventStore pendingEventStore,
        ObjectMapper objectMapper
    ) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.pendingEventStore = pendingEventStore;
        this.objectMapper = objectMapper;
    }

    public ProjectionResult apply(String envelope) {
        EventDescriptor descriptor = parse(envelope, null, null);
        return applyDescriptor(descriptor, true);
    }

    public ProjectionResult apply(String message, String eventType, String eventId) {
        EventDescriptor descriptor = parse(message, eventType, eventId);
        return applyDescriptor(descriptor, true);
    }

    public ProjectionResult reconcile(PendingOrderEvent event) {
        ProjectionResult result = applyDescriptor(parse(event.getEnvelope(), null, null), false);
        if (result == ProjectionResult.APPLIED) {
            pendingEventStore.delete(event);
        }
        return result;
    }

    private ProjectionResult applyDescriptor(EventDescriptor descriptor, boolean drainAfterApply) {
        String orderId = descriptor.orderId();
        Optional<OrderHistoryRecord> existing = orderHistoryRepository.findById(orderId);

        if (requiresOrderRecord(descriptor.eventType()) && existing.isEmpty()) {
            storePending(descriptor);
            return ProjectionResult.PENDING;
        }
        if ("TicketReadyEvent".equals(descriptor.eventType())
            && existing.filter(record -> "ACCEPTED".equals(record.getTicketStatus())
                || "READY".equals(record.getTicketStatus())).isEmpty()) {
            storePending(descriptor);
            return ProjectionResult.PENDING;
        }

        applyMutation(descriptor, existing.orElse(null));
        if (drainAfterApply) {
            drainPending(orderId, SYNCHRONOUS_DRAIN_LIMIT);
        }
        return ProjectionResult.APPLIED;
    }

    private void applyMutation(EventDescriptor descriptor, OrderHistoryRecord existing) {
        switch (descriptor.eventType()) {
            case "OrderCreated" -> applyOrderCreated(read(descriptor, OrderCreated.class));
            case "OrderApproved" -> {
                existing.setStatus("APPROVED");
                existing.setAuthorizationStatus("APPROVED");
                orderHistoryRepository.save(existing);
            }
            case "OrderRejected" -> {
                existing.setStatus("REJECTED");
                orderHistoryRepository.save(existing);
            }
            case "OrderCancelled" -> {
                existing.setStatus("CANCELLED");
                orderHistoryRepository.save(existing);
            }
            case "OrderRevised" -> applyOrderRevised(existing, read(descriptor, OrderRevised.class));
            case "TicketAcceptedEvent" -> {
                existing.setTicketStatus("ACCEPTED");
                orderHistoryRepository.save(existing);
            }
            case "TicketReadyEvent" -> {
                existing.setTicketStatus("READY");
                orderHistoryRepository.save(existing);
            }
            case "DeliveryPickedUpEvent" -> {
                existing.setDeliveryStatus("PICKED_UP");
                orderHistoryRepository.save(existing);
            }
            case "DeliveryDeliveredEvent" -> {
                existing.setDeliveryStatus("DELIVERED");
                orderHistoryRepository.save(existing);
            }
            case "CardAuthorizedEvent" -> read(descriptor, CardAuthorizedEvent.class);
            default -> throw new NonRetryableEventException(
                "Unsupported order-history event type: " + descriptor.eventType()
            );
        }
    }

    private void applyOrderCreated(OrderCreated event) {
        OrderHistoryRecord record = orderHistoryRepository.findById(event.getOrderId().toString())
            .orElseGet(() -> new OrderHistoryRecord(event.getOrderId().toString()));
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

    private void applyOrderRevised(OrderHistoryRecord record, OrderRevised event) {
        record.setOrderTotal(event.getOrderTotal().getAmount());
        record.setLineItems(toLineItems(event.getLineItems()));
        record.setKeywords(toKeywords(event.getLineItems()));
        orderHistoryRepository.save(record);
    }

    private void storePending(EventDescriptor descriptor) {
        if (pendingEventStore.contains(descriptor.orderId(), descriptor.eventId())) {
            return;
        }
        Instant now = Instant.now();
        PendingOrderEventKey key = new PendingOrderEventKey(
            descriptor.orderId(),
            descriptor.aggregateVersion(),
            descriptor.occurredAt(),
            descriptor.eventId()
        );
        pendingEventStore.save(new PendingOrderEvent(
            key,
            descriptor.eventType(),
            descriptor.envelope(),
            now
        ));
    }

    private void drainPending(String orderId, int limit) {
        int processed = 0;
        for (PendingOrderEvent pending : pendingEventStore.findByOrderId(orderId)) {
            if (processed++ >= limit) {
                return;
            }
            ProjectionResult result = applyDescriptor(parse(pending.getEnvelope(), null, null), false);
            if (result == ProjectionResult.PENDING) {
                return;
            }
            pendingEventStore.delete(pending);
        }
    }

    private boolean requiresOrderRecord(String eventType) {
        return !"OrderCreated".equals(eventType);
    }

    private <T> T read(EventDescriptor descriptor, Class<T> type) {
        try {
            return objectMapper.treeToValue(descriptor.payload(), type);
        } catch (JsonProcessingException e) {
            throw new NonRetryableEventException(
                "Invalid payload for " + descriptor.eventType(),
                e
            );
        }
    }

    private EventDescriptor parse(String message, String fallbackEventType, String fallbackEventId) {
        try {
            JsonNode root = objectMapper.readTree(message);
            while (root != null && root.isTextual()) {
                root = objectMapper.readTree(root.textValue());
            }
            if (root != null && root.isObject() && root.has("schema") && root.has("payload")) {
                root = root.get("payload");
            }

            boolean envelope = root != null && root.isObject()
                && root.hasNonNull("eventId") && root.has("payload");
            JsonNode payload = envelope ? root.get("payload") : root;
            String eventType = envelope
                ? root.path("eventType").asText()
                : fallbackEventType;
            UUID eventId = envelope
                ? UUID.fromString(root.path("eventId").asText())
                : UUID.fromString(fallbackEventId);
            long aggregateVersion = envelope ? root.path("aggregateVersion").asLong(0L) : 0L;
            Instant occurredAt = envelope && root.hasNonNull("occurredAt")
                ? Instant.parse(root.get("occurredAt").asText())
                : Instant.EPOCH;
            String orderId = payload.path("orderId").asText(null);
            if (eventType == null || eventType.isBlank()) {
                throw new NonRetryableEventException("eventType is required");
            }
            if (orderId == null || orderId.isBlank()) {
                throw new NonRetryableEventException(
                    "orderId is required for " + eventType
                );
            }
            String canonicalEnvelope = envelope
                ? objectMapper.writeValueAsString(root)
                : objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                    put("eventId", eventId.toString());
                    put("eventType", eventType);
                    put("schemaVersion", 1);
                    put("aggregateType", "OrderHistory");
                    put("aggregateId", orderId);
                    put("aggregateVersion", aggregateVersion);
                    put("occurredAt", occurredAt.toString());
                    put("payload", payload);
                }});
            return new EventDescriptor(
                eventId,
                eventType,
                aggregateVersion,
                occurredAt,
                orderId,
                payload,
                canonicalEnvelope
            );
        } catch (JsonProcessingException | IllegalArgumentException e) {
            if (e instanceof NonRetryableEventException nonRetryable) {
                throw nonRetryable;
            }
            throw new NonRetryableEventException("Invalid domain-event envelope", e);
        }
    }

    private List<LineItem> toLineItems(List<OrderCreated.LineItem> items) {
        return items.stream()
            .map(item -> new LineItem(
                item.getMenuItemId(),
                item.getName(),
                item.getPrice().getAmount(),
                item.getQuantity()
            ))
            .toList();
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

    private record EventDescriptor(
        UUID eventId,
        String eventType,
        long aggregateVersion,
        Instant occurredAt,
        String orderId,
        JsonNode payload,
        String envelope
    ) {
    }
}

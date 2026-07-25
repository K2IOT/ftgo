package net.ftgo.orderhistory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.messaging.NonRetryableEventException;
import net.ftgo.common.orderflow.events.OrderCreated;
import net.ftgo.common.orderflow.events.OrderRevised;
import net.ftgo.orderhistory.domain.LineItem;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.domain.PendingOrderEvent;
import net.ftgo.orderhistory.domain.PendingOrderEventKey;
import net.ftgo.orderhistory.messaging.CardAuthorizedEvent;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final OrderHistoryQueryProjectionWriter queryProjectionWriter;

    public OrderHistoryProjectionService(
        OrderHistoryRepository orderHistoryRepository,
        PendingOrderEventStore pendingEventStore,
        ObjectMapper objectMapper
    ) {
        this(orderHistoryRepository, pendingEventStore, objectMapper, null);
    }

    @Autowired
    public OrderHistoryProjectionService(
        OrderHistoryRepository orderHistoryRepository,
        PendingOrderEventStore pendingEventStore,
        ObjectMapper objectMapper,
        OrderHistoryQueryProjectionWriter queryProjectionWriter
    ) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.pendingEventStore = pendingEventStore;
        this.objectMapper = objectMapper;
        this.queryProjectionWriter = queryProjectionWriter;
    }

    public ProjectionResult apply(String envelope) {
        return applyDescriptor(parse(envelope, null, null), true);
    }

    public ProjectionResult apply(String message, String eventType, String eventId) {
        return applyDescriptor(parse(message, eventType, eventId), true);
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
                String previousStatus = existing.getStatus();
                existing.setStatus("APPROVED");
                existing.setAuthorizationStatus("APPROVED");
                saveProjection(existing, previousStatus);
            }
            case "OrderRejected" -> {
                String previousStatus = existing.getStatus();
                existing.setStatus("REJECTED");
                saveProjection(existing, previousStatus);
            }
            case "OrderCancelled" -> {
                String previousStatus = existing.getStatus();
                existing.setStatus("CANCELLED");
                saveProjection(existing, previousStatus);
            }
            case "OrderRevised" -> applyOrderRevised(existing, read(descriptor, OrderRevised.class));
            case "TicketAcceptedEvent" -> {
                existing.setTicketStatus("ACCEPTED");
                saveProjection(existing, existing.getStatus());
            }
            case "TicketReadyEvent" -> {
                existing.setTicketStatus("READY");
                saveProjection(existing, existing.getStatus());
            }
            case "DeliveryPickedUpEvent" -> {
                existing.setDeliveryStatus("PICKED_UP");
                saveProjection(existing, existing.getStatus());
            }
            case "DeliveryDeliveredEvent" -> {
                existing.setDeliveryStatus("DELIVERED");
                saveProjection(existing, existing.getStatus());
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
        String previousStatus = record.getStatus();
        record.setConsumerId(event.getConsumerId());
        record.setRestaurantId(event.getRestaurantId());
        record.setStatus(event.getStatus());
        record.setOrderTotal(event.getOrderTotal().getAmount());
        record.setDeliveryAddress(event.getDeliveryAddress());
        record.setDeliveryTime(event.getDeliveryTime());
        record.setCreationDate(event.getCreatedAt());
        record.setLineItems(toLineItems(event.getLineItems()));
        record.setKeywords(toKeywords(event.getLineItems()));
        saveProjection(record, previousStatus);
    }

    private void applyOrderRevised(OrderHistoryRecord record, OrderRevised event) {
        record.setOrderTotal(event.getOrderTotal().getAmount());
        record.setLineItems(toLineItems(event.getLineItems()));
        record.setKeywords(toKeywords(event.getLineItems()));
        saveProjection(record, record.getStatus());
    }

    private void saveProjection(OrderHistoryRecord record, String previousStatus) {
        OrderHistoryRecord saved = orderHistoryRepository.save(record);
        if (queryProjectionWriter != null) {
            queryProjectionWriter.upsert(saved, previousStatus);
        }
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
            String eventType = envelope ? root.path("eventType").asText() : fallbackEventType;
            UUID eventId = envelope
                ? UUID.fromString(root.path("eventId").asText())
                : fallbackEventId(fallbackEventId);
            long aggregateVersion = envelope ? root.path("aggregateVersion").asLong(0L) : 0L;
            Instant occurredAt = envelope && root.hasNonNull("occurredAt")
                ? Instant.parse(root.get("occurredAt").asText())
                : Instant.EPOCH;
            String orderId = payload.path("orderId").asText(null);
            if (eventType == null || eventType.isBlank()) {
                throw new NonRetryableEventException("eventType is required");
            }
            if (orderId == null || orderId.isBlank()) {
                throw new NonRetryableEventException("orderId is required for " + eventType);
            }

            String canonicalEnvelope;
            if (envelope) {
                canonicalEnvelope = objectMapper.writeValueAsString(root);
            } else {
                Map<String, Object> canonical = new LinkedHashMap<>();
                canonical.put("eventId", eventId.toString());
                canonical.put("eventType", eventType);
                canonical.put("schemaVersion", 1);
                canonical.put("aggregateType", "OrderHistory");
                canonical.put("aggregateId", orderId);
                canonical.put("aggregateVersion", aggregateVersion);
                canonical.put("occurredAt", occurredAt.toString());
                canonical.put("payload", payload);
                canonicalEnvelope = objectMapper.writeValueAsString(canonical);
            }
            return new EventDescriptor(
                eventId,
                eventType,
                aggregateVersion,
                occurredAt,
                orderId,
                payload,
                canonicalEnvelope
            );
        } catch (NonRetryableEventException e) {
            throw e;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new NonRetryableEventException("Invalid domain-event envelope", e);
        }
    }

    private UUID fallbackEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new NonRetryableEventException("eventId is required");
        }
        try {
            return UUID.fromString(eventId);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(eventId.getBytes(StandardCharsets.UTF_8));
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

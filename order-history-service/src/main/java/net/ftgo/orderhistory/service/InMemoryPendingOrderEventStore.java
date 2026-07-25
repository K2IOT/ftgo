package net.ftgo.orderhistory.service;

import net.ftgo.orderhistory.domain.PendingOrderEvent;
import net.ftgo.orderhistory.domain.PendingOrderEventKey;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory implementation used by deterministic contracts and local adapters. */
public class InMemoryPendingOrderEventStore implements PendingOrderEventStore {

    private static final Comparator<PendingOrderEvent> ORDERING = Comparator
        .comparingLong((PendingOrderEvent event) -> event.getKey().getAggregateVersion())
        .thenComparing(event -> event.getKey().getOccurredAt())
        .thenComparing(event -> event.getKey().getEventId());

    private final Map<PendingOrderEventKey, PendingOrderEvent> events = new ConcurrentHashMap<>();

    @Override
    public boolean contains(String orderId, UUID eventId) {
        return events.values().stream().anyMatch(event ->
            event.getKey().getOrderId().equals(orderId)
                && event.getKey().getEventId().equals(eventId));
    }

    @Override
    public PendingOrderEvent save(PendingOrderEvent event) {
        events.put(event.getKey(), event);
        return event;
    }

    @Override
    public List<PendingOrderEvent> findByOrderId(String orderId) {
        return events.values().stream()
            .filter(event -> event.getKey().getOrderId().equals(orderId))
            .sorted(ORDERING)
            .toList();
    }

    @Override
    public List<PendingOrderEvent> findDue(Instant now, int limit) {
        return events.values().stream()
            .filter(event -> !event.getNextAttemptAt().isAfter(now))
            .sorted(Comparator.comparing(PendingOrderEvent::getNextAttemptAt).thenComparing(ORDERING))
            .limit(limit)
            .toList();
    }

    @Override
    public void delete(PendingOrderEvent event) {
        events.remove(event.getKey());
    }
}

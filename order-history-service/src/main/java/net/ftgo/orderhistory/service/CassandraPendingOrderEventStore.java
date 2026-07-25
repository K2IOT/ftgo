package net.ftgo.orderhistory.service;

import net.ftgo.orderhistory.domain.PendingOrderEvent;
import net.ftgo.orderhistory.repository.PendingOrderEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class CassandraPendingOrderEventStore implements PendingOrderEventStore {

    private static final Comparator<PendingOrderEvent> ORDERING = Comparator
        .comparingLong((PendingOrderEvent event) -> event.getKey().getAggregateVersion())
        .thenComparing(event -> event.getKey().getOccurredAt())
        .thenComparing(event -> event.getKey().getEventId());

    private final PendingOrderEventRepository repository;

    public CassandraPendingOrderEventStore(PendingOrderEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean contains(String orderId, UUID eventId) {
        return repository.findByOrderId(orderId).stream()
            .anyMatch(event -> event.getKey().getEventId().equals(eventId));
    }

    @Override
    public PendingOrderEvent save(PendingOrderEvent event) {
        return repository.save(event);
    }

    @Override
    public List<PendingOrderEvent> findByOrderId(String orderId) {
        return repository.findByOrderId(orderId).stream().sorted(ORDERING).toList();
    }

    @Override
    public List<PendingOrderEvent> findDue(Instant now, int limit) {
        return repository.findAll().stream()
            .filter(event -> !event.getNextAttemptAt().isAfter(now))
            .sorted(Comparator.comparing(PendingOrderEvent::getNextAttemptAt).thenComparing(ORDERING))
            .limit(limit)
            .toList();
    }

    @Override
    public void delete(PendingOrderEvent event) {
        repository.delete(event);
    }
}

package net.ftgo.orderhistory.service;

import net.ftgo.orderhistory.domain.PendingOrderEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PendingOrderEventStore {

    boolean contains(String orderId, UUID eventId);

    PendingOrderEvent save(PendingOrderEvent event);

    List<PendingOrderEvent> findByOrderId(String orderId);

    List<PendingOrderEvent> findDue(Instant now, int limit);

    void delete(PendingOrderEvent event);
}

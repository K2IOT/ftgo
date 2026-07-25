package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyType;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@PrimaryKeyClass
public class PendingOrderEventKey implements Serializable {

    @PrimaryKeyColumn(name = "order_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String orderId;

    @PrimaryKeyColumn(
        name = "aggregate_version",
        ordinal = 1,
        type = PrimaryKeyType.CLUSTERED,
        ordering = Ordering.ASCENDING
    )
    private long aggregateVersion;

    @PrimaryKeyColumn(
        name = "occurred_at",
        ordinal = 2,
        type = PrimaryKeyType.CLUSTERED,
        ordering = Ordering.ASCENDING
    )
    private Instant occurredAt;

    @PrimaryKeyColumn(
        name = "event_id",
        ordinal = 3,
        type = PrimaryKeyType.CLUSTERED,
        ordering = Ordering.ASCENDING
    )
    private UUID eventId;

    public PendingOrderEventKey() {
    }

    public PendingOrderEventKey(
        String orderId,
        long aggregateVersion,
        Instant occurredAt,
        UUID eventId
    ) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.aggregateVersion = aggregateVersion;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
    }

    public String getOrderId() { return orderId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getEventId() { return eventId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PendingOrderEventKey that)) return false;
        return aggregateVersion == that.aggregateVersion
            && Objects.equals(orderId, that.orderId)
            && Objects.equals(occurredAt, that.occurredAt)
            && Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, aggregateVersion, occurredAt, eventId);
    }
}

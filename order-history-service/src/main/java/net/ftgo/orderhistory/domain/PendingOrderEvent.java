package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Table("pending_order_events")
public class PendingOrderEvent {

    @PrimaryKey
    private PendingOrderEventKey key;

    private String eventType;
    private String envelope;
    private int retryCount;
    private Instant firstSeenAt;
    private Instant nextAttemptAt;
    private String lastError;

    public PendingOrderEvent() {
    }

    public PendingOrderEvent(
        PendingOrderEventKey key,
        String eventType,
        String envelope,
        Instant now
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.envelope = Objects.requireNonNull(envelope, "envelope");
        this.firstSeenAt = Objects.requireNonNull(now, "now");
        this.nextAttemptAt = now;
    }

    public PendingOrderEventKey getKey() { return key; }
    public String getEventType() { return eventType; }
    public String getEnvelope() { return envelope; }
    public int getRetryCount() { return retryCount; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }

    public void recordRetry(String error, Instant now) {
        retryCount++;
        lastError = error;
        long delaySeconds = Math.min(3_600L, 5L * (1L << Math.min(retryCount, 9)));
        nextAttemptAt = now.plus(Duration.ofSeconds(delaySeconds));
    }

    public boolean isTerminal(Instant now) {
        return retryCount >= 20
            || (firstSeenAt != null && firstSeenAt.plus(Duration.ofHours(24)).isBefore(now));
    }
}

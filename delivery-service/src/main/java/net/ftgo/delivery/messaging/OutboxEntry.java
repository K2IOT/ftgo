package net.ftgo.delivery.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/** Outbox table entry for the Transactional Outbox pattern. */
@Entity
@Table(name = "outbox")
public class OutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @Column(name = "destination", nullable = false)
    private String destination;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published", nullable = false)
    private boolean published;

    protected OutboxEntry() {
    }

    public OutboxEntry(
        String eventId,
        int schemaVersion,
        long aggregateVersion,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        String destination
    ) {
        this.eventId = eventId;
        this.schemaVersion = schemaVersion;
        this.aggregateVersion = aggregateVersion;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.destination = destination;
        this.createdAt = LocalDateTime.now();
        this.published = false;
    }

    public OutboxEntry(
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        String destination
    ) {
        this(UUID.randomUUID().toString(), 1, 0L, aggregateType, aggregateId,
            eventType, payload, destination);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public int getSchemaVersion() { return schemaVersion; }
    public long getAggregateVersion() { return aggregateVersion; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getDestination() { return destination; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isPublished() { return published; }
}

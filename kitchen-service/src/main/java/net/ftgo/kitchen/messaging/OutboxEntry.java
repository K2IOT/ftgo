package net.ftgo.kitchen.messaging;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Outbox entry for Transactional Outbox pattern.
 * Events are written to this table in the same transaction as business data updates.
 * Debezium CDC publishes events from this table to Kafka.
 */
@Entity
@Table(name = "outbox")
public class OutboxEntry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
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
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "published", nullable = false)
    private Boolean published = false;
    
    protected OutboxEntry() {
    }
    
    public OutboxEntry(String aggregateType, String aggregateId, String eventType, 
                      String payload, String destination) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.destination = destination;
        this.createdAt = LocalDateTime.now();
        this.published = false;
    }
    
    public Long getId() {
        return id;
    }
    
    public String getAggregateType() {
        return aggregateType;
    }
    
    public String getAggregateId() {
        return aggregateId;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public String getPayload() {
        return payload;
    }
    
    public String getDestination() {
        return destination;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public Boolean getPublished() {
        return published;
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

package net.ftgo.delivery.messaging;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Outbox table entry for Transactional Outbox pattern.
 * 
 * Events are written to this table within the same transaction as business data updates.
 * Debezium CDC monitors this table and publishes events to Kafka.
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
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "published", nullable = false)
    private boolean published;
    
    /**
     * Default constructor for JPA.
     */
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
    
    public boolean isPublished() {
        return published;
    }
}

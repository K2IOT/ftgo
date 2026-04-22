package net.ftgo.order.messaging;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Outbox entry for the Transactional Outbox pattern.
 * 
 * Each row represents a domain event that needs to be published to Kafka.
 * Debezium CDC monitors this table and publishes events to Kafka.
 * 
 * Table Structure:
 * - id: Primary key
 * - aggregate_type: Type of aggregate (e.g., "Order")
 * - aggregate_id: ID of the aggregate
 * - event_type: Type of event (e.g., "OrderApproved")
 * - payload: JSON payload of the event
 * - destination: Kafka topic to publish to
 * - created_at: Timestamp when event was created
 * - published: Flag indicating if Debezium has published the event
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
    
    /**
     * Default constructor for JPA.
     */
    protected OutboxEntry() {
    }
    
    /**
     * Creates an outbox entry.
     * 
     * @param aggregateType the aggregate type
     * @param aggregateId the aggregate ID
     * @param eventType the event type
     * @param payload the JSON payload
     * @param destination the Kafka topic
     */
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
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (published == null) {
            published = false;
        }
    }
    
    // Getters and setters
    
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
    
    public void setPublished(Boolean published) {
        this.published = published;
    }
}

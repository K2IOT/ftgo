package net.ftgo.delivery.messaging;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entity for tracking processed messages to ensure idempotent event processing.
 */
@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {
    
    @Id
    @Column(name = "message_id")
    private String messageId;
    
    @Column(name = "consumed_at", nullable = false)
    private LocalDateTime consumedAt;
    
    /**
     * Default constructor for JPA.
     */
    protected ProcessedMessage() {
    }
    
    public ProcessedMessage(String messageId) {
        this.messageId = messageId;
        this.consumedAt = LocalDateTime.now();
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }
}

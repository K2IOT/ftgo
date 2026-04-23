package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Tracks processed Kafka messages for idempotent event processing.
 * 
 * With Kafka's at-least-once delivery guarantee, the same event may be
 * delivered multiple times. This table ensures idempotency by tracking
 * message IDs that have already been processed.
 * 
 * Processing flow:
 * 1. Receive event from Kafka with messageId
 * 2. Check if messageId exists in processed_messages
 * 3. If exists, skip processing (already handled)
 * 4. If not exists, process event and insert messageId
 */
@Table("processed_messages")
public class ProcessedMessage {
    
    @PrimaryKey
    private String messageId;
    
    private LocalDateTime consumedAt;
    
    /**
     * Default constructor for Cassandra mapping.
     */
    public ProcessedMessage() {
    }
    
    /**
     * Creates a new ProcessedMessage.
     * 
     * @param messageId the unique message ID from Kafka
     */
    public ProcessedMessage(String messageId) {
        this.messageId = messageId;
        this.consumedAt = LocalDateTime.now();
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }
    
    public void setConsumedAt(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }
    
    @Override
    public String toString() {
        return "ProcessedMessage{" +
               "messageId='" + messageId + '\'' +
               ", consumedAt=" + consumedAt +
               '}';
    }
}

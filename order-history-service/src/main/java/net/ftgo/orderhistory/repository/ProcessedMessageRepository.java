package net.ftgo.orderhistory.repository;

import net.ftgo.orderhistory.domain.ProcessedMessage;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for tracking processed Kafka messages.
 * 
 * Supports idempotent event processing by checking if a message ID
 * has already been processed before applying the event.
 */
@Repository
public interface ProcessedMessageRepository extends CassandraRepository<ProcessedMessage, String> {
    
    /**
     * Checks if a message has already been processed.
     * 
     * @param messageId the message ID
     * @return true if the message exists in the table
     */
    boolean existsById(String messageId);
}

package net.ftgo.delivery.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for tracking processed messages.
 */
@Repository
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {
}

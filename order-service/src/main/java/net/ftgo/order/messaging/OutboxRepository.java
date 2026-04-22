package net.ftgo.order.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for outbox entries.
 * 
 * Used by DomainEventPublisher to insert events into the outbox table.
 * Debezium CDC monitors this table and publishes events to Kafka.
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {
}

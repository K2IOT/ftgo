package net.ftgo.accounting.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Outbox entries.
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {
}

package net.ftgo.accounting.settlement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SettlementReconciliationWorkRepository {

    private static final int MAX_BATCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;

    public SettlementReconciliationWorkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void enqueue(Long authorizationId, Instant dueAt) {
        if (authorizationId == null) {
            throw new IllegalArgumentException("Authorization ID cannot be null");
        }
        Instant normalizedDueAt = micros(dueAt);
        jdbcTemplate.update(
            """
                INSERT INTO settlement_reconciliation_work (
                    authorization_id,
                    next_attempt_at
                ) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE
                    next_attempt_at = LEAST(next_attempt_at, VALUES(next_attempt_at)),
                    last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP(6)
                """,
            authorizationId,
            Timestamp.from(normalizedDueAt)
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<SettlementReconciliationWork> claimDue(
        int batchSize,
        Instant now,
        Duration lease
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        int claimLimit = Math.min(batchSize, MAX_BATCH_SIZE);
        Instant claimedAt = micros(now);
        Instant lockedUntil = micros(claimedAt.plus(lease));
        List<SettlementReconciliationWork> due = jdbcTemplate.query(
            """
                SELECT authorization_id,
                       next_attempt_at,
                       locked_until,
                       attempt_count,
                       last_error,
                       created_at,
                       updated_at
                  FROM settlement_reconciliation_work
                 WHERE next_attempt_at <= ?
                   AND (locked_until IS NULL OR locked_until <= ?)
                 ORDER BY next_attempt_at, authorization_id
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """,
            (rs, rowNum) -> new SettlementReconciliationWork(
                rs.getLong("authorization_id"),
                instant(rs.getTimestamp("next_attempt_at")),
                instant(rs.getTimestamp("locked_until")),
                rs.getInt("attempt_count"),
                rs.getString("last_error"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at"))
            ),
            Timestamp.from(claimedAt),
            Timestamp.from(claimedAt),
            claimLimit
        );
        if (due.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", due.stream().map(ignored -> "?").toList());
        List<Object> parameters = new ArrayList<>();
        parameters.add(Timestamp.from(lockedUntil));
        parameters.add(Timestamp.from(claimedAt));
        due.forEach(work -> parameters.add(work.authorizationId()));
        jdbcTemplate.update(
            "UPDATE settlement_reconciliation_work "
                + "SET locked_until = ?, attempt_count = attempt_count + 1, updated_at = ? "
                + "WHERE authorization_id IN (" + placeholders + ")",
            parameters.toArray()
        );

        return due.stream()
            .map(work -> new SettlementReconciliationWork(
                work.authorizationId(),
                work.nextAttemptAt(),
                lockedUntil,
                work.attemptCount() + 1,
                work.lastError(),
                work.createdAt(),
                claimedAt
            ))
            .toList();
    }

    @Transactional
    public void reschedule(
        SettlementReconciliationWork claim,
        Instant nextAttemptAt,
        Instant completedAt
    ) {
        Instant normalizedCompletedAt = micros(completedAt);
        int updated = jdbcTemplate.update(
            """
                UPDATE settlement_reconciliation_work
                   SET next_attempt_at = ?,
                       locked_until = NULL,
                       last_error = NULL,
                       updated_at = ?
                 WHERE authorization_id = ?
                   AND attempt_count = ?
                   AND updated_at = ?
                """,
            Timestamp.from(micros(nextAttemptAt)),
            Timestamp.from(normalizedCompletedAt),
            claim.authorizationId(),
            claim.attemptCount(),
            Timestamp.from(claim.updatedAt())
        );
        if (updated == 0) {
            releaseCompletedClaim(claim, normalizedCompletedAt);
        }
    }

    @Transactional
    public void recordFailure(
        SettlementReconciliationWork claim,
        String error,
        Instant failedAt
    ) {
        String boundedError = error == null
            ? "Unknown settlement reconciliation failure"
            : error.substring(0, Math.min(error.length(), 1000));
        jdbcTemplate.update(
            """
                UPDATE settlement_reconciliation_work
                   SET last_error = ?,
                       updated_at = ?
                 WHERE authorization_id = ?
                   AND attempt_count = ?
                   AND updated_at = ?
                """,
            boundedError,
            Timestamp.from(micros(failedAt)),
            claim.authorizationId(),
            claim.attemptCount(),
            Timestamp.from(claim.updatedAt())
        );
    }

    private void releaseCompletedClaim(
        SettlementReconciliationWork claim,
        Instant completedAt
    ) {
        jdbcTemplate.update(
            """
                UPDATE settlement_reconciliation_work
                   SET locked_until = NULL,
                       updated_at = ?
                 WHERE authorization_id = ?
                   AND attempt_count = ?
                   AND locked_until = ?
                """,
            Timestamp.from(completedAt),
            claim.authorizationId(),
            claim.attemptCount(),
            Timestamp.from(claim.lockedUntil())
        );
    }

    private static Instant micros(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

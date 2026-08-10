package net.ftgo.accounting.settlement;

import java.time.Instant;

public record SettlementReconciliationWork(
    Long authorizationId,
    Instant nextAttemptAt,
    Instant lockedUntil,
    int attemptCount,
    String lastError,
    Instant createdAt,
    Instant updatedAt
) {
}

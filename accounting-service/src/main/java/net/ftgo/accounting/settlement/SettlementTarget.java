package net.ftgo.accounting.settlement;

import net.ftgo.common.Money;

public record SettlementTarget(
    Long authorizationId,
    Long orderId,
    Money authorizedAmount,
    Money capturedAmount,
    Money refundedAmount,
    ProviderSettlementStatus status
) {
    public SettlementTarget {
        if (authorizationId == null) throw new IllegalArgumentException("Authorization ID cannot be null");
        if (authorizedAmount == null) throw new IllegalArgumentException("Authorized amount cannot be null");
        if (capturedAmount == null) throw new IllegalArgumentException("Captured amount cannot be null");
        if (refundedAmount == null) throw new IllegalArgumentException("Refunded amount cannot be null");
        if (status == null) throw new IllegalArgumentException("Settlement status cannot be null");
    }
}
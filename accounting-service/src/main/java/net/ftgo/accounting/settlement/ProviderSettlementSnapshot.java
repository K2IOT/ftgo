package net.ftgo.accounting.settlement;

import net.ftgo.common.Money;

public record ProviderSettlementSnapshot(
    Long authorizationId,
    Long orderId,
    Money authorizedAmount,
    Money capturedAmount,
    Money refundedAmount,
    ProviderSettlementStatus status,
    String providerReference
) {
}
package net.ftgo.accounting.payment;

import net.ftgo.common.Money;

/** Provider state used by reconciliation; contains references and amounts only. */
public record PaymentProviderSettlementSnapshot(
    String providerAuthorizationId,
    PaymentProviderSettlementStatus status,
    String providerCaptureId,
    String providerVoidId,
    String providerRefundId,
    Money authorizedAmount,
    Money capturedAmount,
    Money refundedAmount
) {

    public static PaymentProviderSettlementSnapshot authorized(
        String providerAuthorizationId,
        Money amount
    ) {
        return new PaymentProviderSettlementSnapshot(
            providerAuthorizationId,
            PaymentProviderSettlementStatus.AUTHORIZED,
            null,
            null,
            null,
            amount,
            Money.ZERO,
            Money.ZERO
        );
    }

    public static PaymentProviderSettlementSnapshot captured(
        String providerAuthorizationId,
        String providerCaptureId,
        Money amount
    ) {
        return new PaymentProviderSettlementSnapshot(
            providerAuthorizationId,
            PaymentProviderSettlementStatus.CAPTURED,
            providerCaptureId,
            null,
            null,
            amount,
            amount,
            Money.ZERO
        );
    }

    public static PaymentProviderSettlementSnapshot voided(
        String providerAuthorizationId,
        String providerVoidId,
        Money amount
    ) {
        return new PaymentProviderSettlementSnapshot(
            providerAuthorizationId,
            PaymentProviderSettlementStatus.VOIDED,
            null,
            providerVoidId,
            null,
            amount,
            Money.ZERO,
            Money.ZERO
        );
    }

    public static PaymentProviderSettlementSnapshot refunded(
        String providerAuthorizationId,
        String providerCaptureId,
        String providerRefundId,
        Money amount
    ) {
        return new PaymentProviderSettlementSnapshot(
            providerAuthorizationId,
            PaymentProviderSettlementStatus.REFUNDED,
            providerCaptureId,
            null,
            providerRefundId,
            amount,
            amount,
            amount
        );
    }

    public static PaymentProviderSettlementSnapshot notFound(
        String providerAuthorizationId
    ) {
        return new PaymentProviderSettlementSnapshot(
            providerAuthorizationId,
            PaymentProviderSettlementStatus.NOT_FOUND,
            null,
            null,
            null,
            Money.ZERO,
            Money.ZERO,
            Money.ZERO
        );
    }
}

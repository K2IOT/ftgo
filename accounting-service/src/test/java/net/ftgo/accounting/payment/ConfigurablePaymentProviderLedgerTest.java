package net.ftgo.accounting.payment;

import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurablePaymentProviderLedgerTest {

    @Test
    void recordsIdempotentAuthorizationCaptureAndRefundSettlement() {
        ConfigurablePaymentAuthorizationGateway provider =
            new ConfigurablePaymentAuthorizationGateway("");
        Money amount = new Money("25.00");

        PaymentAuthorizationDecision authorization =
            provider.authorize("tok_ok", amount, "authorize-1");
        String providerAuthorizationId = authorization.getProviderAuthorizationId();

        PaymentProviderResult firstCapture =
            provider.capture(providerAuthorizationId, amount, "capture-1");
        PaymentProviderResult replayedCapture =
            provider.capture(providerAuthorizationId, amount, "capture-1");

        assertThat(replayedCapture).isEqualTo(firstCapture);
        assertThat(provider.getOperationCount("capture")).isEqualTo(1L);
        assertThat(provider.getSettlement(providerAuthorizationId).status())
            .isEqualTo(PaymentProviderSettlementStatus.CAPTURED);

        PaymentProviderResult firstRefund =
            provider.refund(firstCapture.getProviderReference(), amount, "refund-1");
        PaymentProviderResult replayedRefund =
            provider.refund(firstCapture.getProviderReference(), amount, "refund-1");

        assertThat(replayedRefund).isEqualTo(firstRefund);
        assertThat(provider.getOperationCount("refund")).isEqualTo(1L);
        PaymentProviderSettlementSnapshot snapshot = provider.getSettlement(providerAuthorizationId);
        assertThat(snapshot.status()).isEqualTo(PaymentProviderSettlementStatus.REFUNDED);
        assertThat(snapshot.refundedAmount()).isEqualTo(amount);
    }

    @Test
    void recordsIdempotentVoidSettlement() {
        ConfigurablePaymentAuthorizationGateway provider =
            new ConfigurablePaymentAuthorizationGateway("");
        Money amount = new Money("10.00");

        PaymentAuthorizationDecision authorization =
            provider.authorize("tok_ok", amount, "authorize-void");
        String providerAuthorizationId = authorization.getProviderAuthorizationId();

        PaymentProviderResult firstVoid =
            provider.voidAuthorization(providerAuthorizationId, "void-1");
        PaymentProviderResult replayedVoid =
            provider.voidAuthorization(providerAuthorizationId, "void-1");

        assertThat(replayedVoid).isEqualTo(firstVoid);
        assertThat(provider.getOperationCount("void")).isEqualTo(1L);
        assertThat(provider.getSettlement(providerAuthorizationId).status())
            .isEqualTo(PaymentProviderSettlementStatus.VOIDED);
    }
}

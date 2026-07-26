package net.ftgo.accounting.payment;

import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurablePaymentProviderLedgerTest {

    @Test
    void recordsIdempotentAuthorizationCaptureAndRefundSettlement() {
        ConfigurablePaymentAuthorizationGateway provider =
            new ConfigurablePaymentAuthorizationGateway("");
        Money amount = new Money("25.00");

        PaymentAuthorizationDecision authorization =
            provider.authorize("tok_ok", amount, "authorize-1");
        String providerAuthorizationId = authorization.providerAuthorizationId();

        PaymentProviderResult firstCapture =
            provider.capture(providerAuthorizationId, amount, "capture-1");
        PaymentProviderResult replayedCapture =
            provider.capture(providerAuthorizationId, amount, "capture-1");

        assertThat(replayedCapture).isEqualTo(firstCapture);
        assertThat(provider.getOperationCount("capture")).isEqualTo(1L);
        assertThat(provider.getSettlement(providerAuthorizationId).status())
            .isEqualTo(PaymentProviderSettlementStatus.CAPTURED);

        PaymentProviderResult firstRefund =
            provider.refund(firstCapture.providerReference(), amount, "refund-1");
        PaymentProviderResult replayedRefund =
            provider.refund(firstCapture.providerReference(), amount, "refund-1");

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
        String providerAuthorizationId = authorization.providerAuthorizationId();

        PaymentProviderResult firstVoid =
            provider.voidAuthorization(providerAuthorizationId, "void-1");
        PaymentProviderResult replayedVoid =
            provider.voidAuthorization(providerAuthorizationId, "void-1");

        assertThat(replayedVoid).isEqualTo(firstVoid);
        assertThat(provider.getOperationCount("void")).isEqualTo(1L);
        assertThat(provider.getSettlement(providerAuthorizationId).status())
            .isEqualTo(PaymentProviderSettlementStatus.VOIDED);
    }

    @Test
    void transientCaptureFailureOccursOnceAndSuccessfulRetryCountsOnce() {
        ConfigurablePaymentAuthorizationGateway provider =
            new ConfigurablePaymentAuthorizationGateway("");
        Money amount = new Money("25.00");

        assertThatThrownBy(() -> provider.capture(
            "pa_capture_error_e2e",
            amount,
            "capture-retry-1"
        )).isInstanceOf(RetryablePaymentProviderException.class);

        PaymentProviderResult recovered = provider.capture(
            "pa_capture_error_e2e",
            amount,
            "capture-retry-1"
        );

        assertThat(recovered.successful()).isTrue();
        assertThat(provider.getOperationCount("capture")).isEqualTo(1L);
        assertThat(provider.getSettlement("pa_capture_error_e2e").status())
            .isEqualTo(PaymentProviderSettlementStatus.CAPTURED);
    }
}

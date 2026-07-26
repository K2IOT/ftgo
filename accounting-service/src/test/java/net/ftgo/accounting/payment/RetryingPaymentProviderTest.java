package net.ftgo.accounting.payment;

import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetryingPaymentProviderTest {

    @Test
    void retriesTransientCaptureAndReturnsTheSuccessfulResult() {
        PaymentProvider delegate = mock(PaymentProvider.class);
        Money amount = new Money("25.00");
        when(delegate.capture("pa-retry", amount, "capture-1"))
            .thenThrow(new RetryablePaymentProviderException("temporary outage"))
            .thenReturn(PaymentProviderResult.succeeded("pc-1"));
        RetryingPaymentProvider provider = new RetryingPaymentProvider(delegate, 0L, 0L);

        PaymentProviderResult result = provider.capture("pa-retry", amount, "capture-1");

        assertThat(result.successful()).isTrue();
        assertThat(result.providerReference()).isEqualTo("pc-1");
        verify(delegate, times(2)).capture("pa-retry", amount, "capture-1");
    }

    @Test
    void exhaustedRetryBudgetBecomesDeterministicFailureInsteadOfEscaping() {
        PaymentProvider delegate = mock(PaymentProvider.class);
        Money amount = new Money("25.00");
        when(delegate.capture("pa-down", amount, "capture-2"))
            .thenThrow(new RetryablePaymentProviderException("provider unavailable"));
        RetryingPaymentProvider provider = new RetryingPaymentProvider(delegate, 0L, 0L);

        PaymentProviderResult result = provider.capture("pa-down", amount, "capture-2");

        assertThat(result.successful()).isFalse();
        assertThat(result.failureCode()).isEqualTo(RetryingPaymentProvider.PROVIDER_UNAVAILABLE);
        verify(delegate, times(3)).capture("pa-down", amount, "capture-2");
    }
}

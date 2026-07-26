package net.ftgo.accounting.payment;

import net.ftgo.common.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Finite retry boundary for provider calls made by saga participants.
 * Retryable provider exceptions are contained here so they cannot terminate the
 * Eventuate command consumer. Once the retry budget is exhausted, the call is
 * converted into a deterministic failure result that the saga can compensate.
 */
@Component
@Primary
public class RetryingPaymentProvider implements PaymentProvider {

    public static final String PROVIDER_UNAVAILABLE = "PAYMENT_PROVIDER_UNAVAILABLE";

    private final PaymentProvider delegate;
    private final long[] retryDelaysMillis;

    @Autowired
    public RetryingPaymentProvider(
        @Qualifier("configurablePaymentAuthorizationGateway") PaymentProvider delegate,
        @Value("${ftgo.accounting.payment-provider-retry-delays-ms:1000,5000,30000}")
        String retryDelaysMillis
    ) {
        this(delegate, parseDelays(retryDelaysMillis));
    }

    RetryingPaymentProvider(PaymentProvider delegate, long... retryDelaysMillis) {
        if (delegate == null) {
            throw new IllegalArgumentException("Payment provider delegate is required");
        }
        this.delegate = delegate;
        this.retryDelaysMillis = retryDelaysMillis == null
            ? new long[0]
            : Arrays.copyOf(retryDelaysMillis, retryDelaysMillis.length);
        for (long delay : this.retryDelaysMillis) {
            if (delay < 0) {
                throw new IllegalArgumentException("Payment provider retry delay cannot be negative");
            }
        }
    }

    @Override
    public PaymentAuthorizationDecision authorize(String paymentToken, Money amount) {
        return execute(
            () -> delegate.authorize(paymentToken, amount),
            () -> PaymentAuthorizationDecision.denied(PROVIDER_UNAVAILABLE)
        );
    }

    @Override
    public PaymentAuthorizationDecision authorize(
        String paymentToken,
        Money amount,
        String requestId
    ) {
        return execute(
            () -> delegate.authorize(paymentToken, amount, requestId),
            () -> PaymentAuthorizationDecision.denied(PROVIDER_UNAVAILABLE)
        );
    }

    @Override
    public PaymentProviderResult capture(
        String providerAuthorizationId,
        Money amount,
        String requestId
    ) {
        return execute(
            () -> delegate.capture(providerAuthorizationId, amount, requestId),
            () -> PaymentProviderResult.declined(PROVIDER_UNAVAILABLE)
        );
    }

    @Override
    public PaymentProviderResult voidAuthorization(
        String providerAuthorizationId,
        String requestId
    ) {
        return execute(
            () -> delegate.voidAuthorization(providerAuthorizationId, requestId),
            () -> PaymentProviderResult.declined(PROVIDER_UNAVAILABLE)
        );
    }

    @Override
    public PaymentProviderResult refund(
        String providerCaptureId,
        Money amount,
        String requestId
    ) {
        return execute(
            () -> delegate.refund(providerCaptureId, amount, requestId),
            () -> PaymentProviderResult.declined(PROVIDER_UNAVAILABLE)
        );
    }

    @Override
    public PaymentProviderSettlementSnapshot getSettlement(String providerAuthorizationId) {
        return delegate.getSettlement(providerAuthorizationId);
    }

    @Override
    public List<PaymentProviderCharge> listRecentCharges(Instant since) {
        return delegate.listRecentCharges(since);
    }

    private <T> T execute(Supplier<T> operation, Supplier<T> exhaustedResult) {
        for (int attempt = 0; ; attempt++) {
            try {
                return operation.get();
            } catch (RetryablePaymentProviderException retryable) {
                if (attempt >= retryDelaysMillis.length) {
                    return exhaustedResult.get();
                }
                if (!sleep(retryDelaysMillis[attempt])) {
                    return exhaustedResult.get();
                }
            }
        }
    }

    private boolean sleep(long delayMillis) {
        if (delayMillis == 0) {
            return true;
        }
        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static long[] parseDelays(String configuredDelays) {
        if (configuredDelays == null || configuredDelays.isBlank()) {
            return new long[0];
        }
        try {
            return Arrays.stream(configuredDelays.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .mapToLong(Long::parseLong)
                .toArray();
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(
                "Payment provider retry delays must be comma-separated milliseconds",
                invalid
            );
        }
    }
}

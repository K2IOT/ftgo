package net.ftgo.accounting.payment;

import net.ftgo.common.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic sandbox provider used until a real provider integration is
 * configured. Every operation reference is derived from its idempotency key,
 * so retries after a lost reply return the same provider result.
 */
@Component
public class ConfigurablePaymentAuthorizationGateway implements PaymentProvider {

    public static final String PROVIDER_DECLINED = "PAYMENT_PROVIDER_DECLINED";
    public static final String TOKEN_REQUIRED = "PAYMENT_TOKEN_REQUIRED";
    public static final String CAPTURE_DECLINED = "PAYMENT_CAPTURE_DECLINED";

    private final Set<String> declinedTokens;

    public ConfigurablePaymentAuthorizationGateway(
        @Value("${ftgo.accounting.declined-payment-tokens:}") String declinedPaymentTokens
    ) {
        this.declinedTokens = Arrays.stream(declinedPaymentTokens.split(","))
            .map(String::trim)
            .filter(token -> !token.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public PaymentAuthorizationDecision authorize(String paymentToken, Money amount) {
        return authorize(
            paymentToken,
            amount,
            "legacy-" + stableReference("authorization", String.valueOf(paymentToken) + ":" + amount)
        );
    }

    @Override
    public PaymentAuthorizationDecision authorize(
        String paymentToken,
        Money amount,
        String requestId
    ) {
        requireMoney(amount);
        requireText(requestId, "requestId");
        // Commands produced before the rolling upgrade did not carry a token.
        if (paymentToken == null) {
            return PaymentAuthorizationDecision.allow(stableReference("pa", requestId));
        }
        if (paymentToken.isBlank()) {
            return PaymentAuthorizationDecision.denied(TOKEN_REQUIRED);
        }
        if (declinedTokens.contains(paymentToken)) {
            return PaymentAuthorizationDecision.denied(PROVIDER_DECLINED);
        }
        return PaymentAuthorizationDecision.allow(stableReference("pa", requestId));
    }

    @Override
    public PaymentProviderResult capture(
        String providerAuthorizationId,
        Money amount,
        String requestId
    ) {
        requireText(providerAuthorizationId, "providerAuthorizationId");
        requireMoney(amount);
        requireText(requestId, "requestId");
        if (providerAuthorizationId.startsWith("pa_capture_error_")) {
            throw new RetryablePaymentProviderException("Sandbox capture provider unavailable");
        }
        if (providerAuthorizationId.startsWith("pa_capture_decline_")) {
            return PaymentProviderResult.declined(CAPTURE_DECLINED);
        }
        return PaymentProviderResult.succeeded(stableReference("pc", requestId));
    }

    @Override
    public PaymentProviderResult voidAuthorization(
        String providerAuthorizationId,
        String requestId
    ) {
        requireText(providerAuthorizationId, "providerAuthorizationId");
        requireText(requestId, "requestId");
        if (providerAuthorizationId.startsWith("pa_void_error_")) {
            throw new RetryablePaymentProviderException("Sandbox void provider unavailable");
        }
        return PaymentProviderResult.succeeded(stableReference("pv", requestId));
    }

    @Override
    public PaymentProviderResult refund(
        String providerCaptureId,
        Money amount,
        String requestId
    ) {
        requireText(providerCaptureId, "providerCaptureId");
        requireMoney(amount);
        requireText(requestId, "requestId");
        if (requestId.startsWith("refund-error-")) {
            throw new RetryablePaymentProviderException("Sandbox refund provider unavailable");
        }
        return PaymentProviderResult.succeeded(stableReference("pr", requestId));
    }

    private static String stableReference(String prefix, String idempotencyKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            return prefix + "_" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }

    private static void requireMoney(Money amount) {
        if (amount == null || amount.isZero()) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
    }
}

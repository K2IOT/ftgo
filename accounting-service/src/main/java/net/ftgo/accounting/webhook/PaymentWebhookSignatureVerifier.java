package net.ftgo.accounting.webhook;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/** Verifies HMAC-SHA256(timestamp + '.' + exact raw body) in constant time. */
@Component
public class PaymentWebhookSignatureVerifier {

    private final PaymentWebhookSecretResolver secretResolver;
    private final Clock clock;
    private final Duration replayWindow;

    @Autowired
    public PaymentWebhookSignatureVerifier(
        PaymentWebhookSecretResolver secretResolver,
        @Value("${ftgo.accounting.webhook-replay-window-seconds:300}") long replayWindowSeconds
    ) {
        this(secretResolver, Clock.systemUTC(), Duration.ofSeconds(replayWindowSeconds));
    }

    PaymentWebhookSignatureVerifier(
        PaymentWebhookSecretResolver secretResolver,
        Clock clock,
        Duration replayWindow
    ) {
        if (replayWindow == null || replayWindow.isNegative() || replayWindow.isZero()) {
            throw new IllegalArgumentException("Webhook replay window must be positive");
        }
        this.secretResolver = secretResolver;
        this.clock = clock;
        this.replayWindow = replayWindow;
    }

    public void verify(
        String provider,
        long timestampSeconds,
        String signature,
        byte[] rawBody
    ) {
        if (provider == null || provider.isBlank()) {
            throw new InvalidPaymentWebhookException("Payment provider is required");
        }
        if (signature == null || signature.isBlank()) {
            throw new InvalidPaymentWebhookException("Payment webhook signature is required");
        }
        if (rawBody == null) {
            throw new InvalidPaymentWebhookException("Payment webhook body is required");
        }

        Instant receivedAt = clock.instant();
        Instant signedAt;
        try {
            signedAt = Instant.ofEpochSecond(timestampSeconds);
        } catch (RuntimeException e) {
            throw new InvalidPaymentWebhookException("Invalid payment webhook timestamp", e);
        }
        Duration age = Duration.between(signedAt, receivedAt).abs();
        if (age.compareTo(replayWindow) > 0) {
            throw new InvalidPaymentWebhookException("Payment webhook timestamp is outside replay window");
        }

        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException e) {
            throw new InvalidPaymentWebhookException("Payment webhook signature is not valid hex", e);
        }
        byte[] expected = sign(
            secretResolver.resolveSecret(provider),
            timestampSeconds,
            rawBody
        );
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new InvalidPaymentWebhookException("Payment webhook signature mismatch");
        }
    }

    private byte[] sign(String secret, long timestampSeconds, byte[] rawBody) {
        if (secret == null || secret.isBlank()) {
            throw new InvalidPaymentWebhookException("No webhook secret configured for provider");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(Long.toString(timestampSeconds).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return mac.doFinal(rawBody);
        } catch (Exception e) {
            throw new InvalidPaymentWebhookException("Unable to verify payment webhook", e);
        }
    }
}

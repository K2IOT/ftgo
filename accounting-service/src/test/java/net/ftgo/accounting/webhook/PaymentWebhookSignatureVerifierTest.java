package net.ftgo.accounting.webhook;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentWebhookSignatureVerifierTest {

    private static final String SECRET = "whsec_phase_02b_test";
    private static final Instant NOW = Instant.parse("2026-07-25T16:00:00Z");

    private final PaymentWebhookSignatureVerifier verifier =
        new PaymentWebhookSignatureVerifier(
            provider -> SECRET,
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(5)
        );

    @Test
    void verifiesSignatureOverTimestampAndExactRawBody() {
        byte[] rawBody = "{\"eventId\":\"evt_101\",\"type\":\"PAYMENT_CAPTURED\"}"
            .getBytes(StandardCharsets.UTF_8);
        long timestamp = NOW.getEpochSecond();
        String signature = sign(timestamp, rawBody);

        assertDoesNotThrow(() -> verifier.verify(
            "sandbox",
            timestamp,
            signature,
            rawBody
        ));
    }

    @Test
    void rejectsTamperedRawBody() {
        byte[] signedBody = "{\"amount\":\"25.00\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBody = "{\"amount\":\"250.00\"}".getBytes(StandardCharsets.UTF_8);
        long timestamp = NOW.getEpochSecond();

        assertThrows(InvalidPaymentWebhookException.class, () -> verifier.verify(
            "sandbox",
            timestamp,
            sign(timestamp, signedBody),
            tamperedBody
        ));
    }

    @Test
    void rejectsTimestampOutsideReplayWindow() {
        byte[] rawBody = "{}".getBytes(StandardCharsets.UTF_8);
        long staleTimestamp = NOW.minus(Duration.ofMinutes(5).plusSeconds(1)).getEpochSecond();

        assertThrows(InvalidPaymentWebhookException.class, () -> verifier.verify(
            "sandbox",
            staleTimestamp,
            sign(staleTimestamp, rawBody),
            rawBody
        ));
    }

    private String sign(long timestamp, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (Exception e) {
            throw new AssertionError("Unable to calculate test signature", e);
        }
    }
}

package net.ftgo.orderhistory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Objects;

@Component
public class OrderHistoryPagingTokenCodec {

    private static final int VERSION = 1;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] signingSecret;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public OrderHistoryPagingTokenCodec(
        ObjectMapper objectMapper,
        @Value("${ftgo.order-history.paging-secret:}") String signingSecret,
        @Value("${ftgo.order-history.paging-token-ttl:PT15M}") Duration ttl
    ) {
        this(objectMapper, signingSecret, ttl, Clock.systemUTC());
    }

    OrderHistoryPagingTokenCodec(
        ObjectMapper objectMapper,
        String signingSecret,
        Duration ttl,
        Clock clock
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalArgumentException("Order History paging signing secret is required");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Order History paging token TTL must be positive");
        }
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public String encode(
        OrderHistoryQueryCriteria criteria,
        int pageSize,
        OrderHistoryPageCursor cursor
    ) {
        Objects.requireNonNull(criteria, "criteria is required");
        Objects.requireNonNull(cursor, "cursor is required");
        TokenPayload payload = new TokenPayload(
            VERSION,
            queryFingerprint(criteria),
            pageSize,
            cursor.bucketMonth().toString(),
            cursor.driverPagingState(),
            clock.instant().plus(ttl).getEpochSecond()
        );
        try {
            byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);
            byte[] signature = sign(payloadBytes);
            return URL_ENCODER.encodeToString(payloadBytes)
                + "."
                + URL_ENCODER.encodeToString(signature);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to encode Order History paging token", e);
        }
    }

    public OrderHistoryPageCursor decode(
        String token,
        OrderHistoryQueryCriteria criteria,
        int pageSize
    ) {
        Objects.requireNonNull(criteria, "criteria is required");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Paging token is required");
        }

        try {
            int separator = token.indexOf('.');
            if (separator <= 0 || separator != token.lastIndexOf('.') || separator == token.length() - 1) {
                throw new IllegalArgumentException("Invalid paging token format");
            }

            byte[] payloadBytes = URL_DECODER.decode(token.substring(0, separator));
            byte[] suppliedSignature = URL_DECODER.decode(token.substring(separator + 1));
            byte[] expectedSignature = sign(payloadBytes);
            if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
                throw new IllegalArgumentException("Invalid paging token signature");
            }

            TokenPayload payload = objectMapper.readValue(payloadBytes, TokenPayload.class);
            validatePayload(payload, criteria, pageSize);
            YearMonth bucketMonth = YearMonth.parse(payload.bucketMonth());
            YearMonth currentUtcMonth = YearMonth.now(clock.withZone(ZoneOffset.UTC));
            if (bucketMonth.isAfter(currentUtcMonth)) {
                throw new IllegalArgumentException("Paging token points to a future bucket month");
            }
            return new OrderHistoryPageCursor(bucketMonth, payload.driverPagingState());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException | IOException e) {
            throw new IllegalArgumentException("Invalid paging token", e);
        }
    }

    private void validatePayload(
        TokenPayload payload,
        OrderHistoryQueryCriteria criteria,
        int pageSize
    ) {
        if (payload.version() != VERSION) {
            throw new IllegalArgumentException("Unsupported paging token version");
        }
        if (payload.pageSize() != pageSize) {
            throw new IllegalArgumentException("Paging token page size does not match the query");
        }
        byte[] expectedFingerprint = queryFingerprint(criteria).getBytes(StandardCharsets.UTF_8);
        byte[] suppliedFingerprint = payload.queryFingerprint() == null
            ? new byte[0]
            : payload.queryFingerprint().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedFingerprint, suppliedFingerprint)) {
            throw new IllegalArgumentException("Paging token does not match the query");
        }
        if (payload.expiresAt() <= clock.instant().getEpochSecond()) {
            throw new IllegalArgumentException("Paging token has expired");
        }
        if (payload.bucketMonth() == null || payload.bucketMonth().isBlank()) {
            throw new IllegalArgumentException("Paging token bucket month is required");
        }
    }

    private String queryFingerprint(OrderHistoryQueryCriteria criteria) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateFingerprintField(digest, criteria.consumerId() == null
                ? null
                : criteria.consumerId().toString());
            updateFingerprintField(digest, criteria.status());
            updateFingerprintField(digest, criteria.restaurantId() == null
                ? null
                : criteria.restaurantId().toString());
            updateFingerprintField(digest, criteria.since() == null
                ? null
                : criteria.since().toString());
            return URL_ENCODER.encodeToString(digest.digest());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void updateFingerprintField(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private byte[] sign(byte[] payloadBytes) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret, HMAC_ALGORITHM));
            return mac.doFinal(payloadBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", e);
        }
    }

    private record TokenPayload(
        int version,
        String queryFingerprint,
        int pageSize,
        String bucketMonth,
        String driverPagingState,
        long expiresAt
    ) {
    }
}

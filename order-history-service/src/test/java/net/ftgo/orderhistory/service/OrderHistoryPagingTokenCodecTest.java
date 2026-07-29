package net.ftgo.orderhistory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderHistoryPagingTokenCodecTest {

    private static final String SECRET = "test-order-history-paging-secret-32-bytes";
    private static final Instant NOW = Instant.parse("2026-07-29T04:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final OrderHistoryQueryCriteria CRITERIA = new OrderHistoryQueryCriteria(
        42L,
        "APPROVED",
        null,
        LocalDate.of(2026, 1, 1)
    );

    private final OrderHistoryPagingTokenCodec codec = codec(CLOCK, SECRET);

    @Test
    void roundTripsValidToken() {
        OrderHistoryPageCursor cursor = new OrderHistoryPageCursor(
            YearMonth.of(2026, 7),
            "AQID"
        );

        String token = codec.encode(CRITERIA, 20, cursor);

        assertEquals(cursor, codec.decode(token, CRITERIA, 20));
        assertEquals(2, token.split("\\.", -1).length);
        assertFalse(token.contains("="), "base64url token must omit padding");
    }

    @Test
    void rejectsOneBytePayloadTampering() {
        String token = codec.encode(
            CRITERIA,
            20,
            new OrderHistoryPageCursor(YearMonth.of(2026, 7), "AQID")
        );
        int mutationIndex = token.indexOf('.') - 1;
        char original = token.charAt(mutationIndex);
        char replacement = original == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, mutationIndex)
            + replacement
            + token.substring(mutationIndex + 1);

        assertThrows(
            IllegalArgumentException.class,
            () -> codec.decode(tampered, CRITERIA, 20)
        );
    }

    @Test
    void rejectsExpiredToken() {
        String token = codec.encode(
            CRITERIA,
            20,
            new OrderHistoryPageCursor(YearMonth.of(2026, 7), null)
        );
        OrderHistoryPagingTokenCodec expiredCodec = codec(
            Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC),
            SECRET
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> expiredCodec.decode(token, CRITERIA, 20)
        );
    }

    @Test
    void rejectsTokenReusedAcrossQueryOrPageSize() {
        String token = codec.encode(
            CRITERIA,
            20,
            new OrderHistoryPageCursor(YearMonth.of(2026, 7), null)
        );

        assertThrows(IllegalArgumentException.class, () -> codec.decode(
            token,
            new OrderHistoryQueryCriteria(43L, "APPROVED", null, LocalDate.of(2026, 1, 1)),
            20
        ));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
            token,
            new OrderHistoryQueryCriteria(42L, "REJECTED", null, LocalDate.of(2026, 1, 1)),
            20
        ));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
            token,
            new OrderHistoryQueryCriteria(42L, null, 7L, LocalDate.of(2026, 1, 1)),
            20
        ));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
            token,
            new OrderHistoryQueryCriteria(42L, "APPROVED", null, LocalDate.of(2026, 2, 1)),
            20
        ));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(token, CRITERIA, 21));
    }

    @Test
    void rejectsMalformedBase64() {
        assertThrows(
            IllegalArgumentException.class,
            () -> codec.decode("not-base64!.also-not-base64!", CRITERIA, 20)
        );
    }

    @Test
    void rejectsBlankSigningSecret() {
        assertThrows(
            IllegalArgumentException.class,
            () -> codec(CLOCK, "   ")
        );
    }

    @Test
    void rejectsFutureBucketMonth() {
        String token = codec.encode(
            CRITERIA,
            20,
            new OrderHistoryPageCursor(YearMonth.of(2026, 8), null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> codec.decode(token, CRITERIA, 20)
        );
    }

    private static OrderHistoryPagingTokenCodec codec(Clock clock, String secret) {
        return new OrderHistoryPagingTokenCodec(
            new ObjectMapper().findAndRegisterModules(),
            secret,
            Duration.ofMinutes(15),
            clock
        );
    }
}

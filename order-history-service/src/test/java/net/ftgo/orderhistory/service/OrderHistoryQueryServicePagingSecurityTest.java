package net.ftgo.orderhistory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.orderhistory.api.OrderHistoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderHistoryQueryServicePagingSecurityTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-07-29T04:00:00Z"),
        ZoneOffset.UTC
    );
    private static final String SECRET = "test-order-history-paging-secret-32-bytes";
    private static final OrderHistoryQueryCriteria CRITERIA = new OrderHistoryQueryCriteria(
        42L,
        "APPROVED",
        null,
        LocalDate.of(2026, 1, 1)
    );

    private CountingStore store;
    private OrderHistoryPagingTokenCodec codec;
    private OrderHistoryQueryService service;

    @BeforeEach
    void setUp() {
        store = new CountingStore();
        codec = new OrderHistoryPagingTokenCodec(
            new ObjectMapper().findAndRegisterModules(),
            SECRET,
            Duration.ofMinutes(15),
            CLOCK
        );
        service = new OrderHistoryQueryService(store, codec);
    }

    @Test
    void rejectsTamperedTokenBeforeStoreAccess() {
        String token = codec.encode(
            CRITERIA,
            20,
            new OrderHistoryPageCursor(YearMonth.of(2026, 7), "AQID")
        );
        int mutationIndex = token.indexOf('.') - 1;
        char replacement = token.charAt(mutationIndex) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, mutationIndex)
            + replacement
            + token.substring(mutationIndex + 1);

        assertThrows(
            IllegalArgumentException.class,
            () -> service.query(CRITERIA, 20, tampered)
        );
        assertEquals(0, store.fetchCalls);
    }

    @Test
    void rejectsCrossQueryAndCrossPageSizeTokenBeforeStoreAccess() {
        String token = codec.encode(
            CRITERIA,
            20,
            new OrderHistoryPageCursor(YearMonth.of(2026, 7), null)
        );

        assertThrows(IllegalArgumentException.class, () -> service.query(
            new OrderHistoryQueryCriteria(43L, "APPROVED", null, LocalDate.of(2026, 1, 1)),
            20,
            token
        ));
        assertThrows(IllegalArgumentException.class, () -> service.query(CRITERIA, 21, token));
        assertEquals(0, store.fetchCalls);
    }

    @Test
    void rejectsFutureMonthTokenBeforeStoreAccess() {
        String token = codec.encode(
            CRITERIA,
            20,
            new OrderHistoryPageCursor(YearMonth.of(2026, 8), null)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> service.query(CRITERIA, 20, token)
        );
        assertEquals(0, store.fetchCalls);
    }

    @Test
    void passesValidatedTypedCursorAndSignsContinuation() {
        OrderHistoryPageCursor supplied = new OrderHistoryPageCursor(
            YearMonth.of(2026, 7),
            "AQID"
        );
        store.nextCursor = new OrderHistoryPageCursor(YearMonth.of(2026, 6), null);
        String token = codec.encode(CRITERIA, 20, supplied);

        OrderHistoryResponse response = service.query(CRITERIA, 20, token);

        assertEquals(1, store.fetchCalls);
        assertEquals(supplied, store.lastCursor);
        assertNotNull(response.getNextPagingState());
        assertEquals(
            store.nextCursor,
            codec.decode(response.getNextPagingState(), CRITERIA, 20)
        );
    }

    private static final class CountingStore implements OrderHistoryQueryStore {
        private int fetchCalls;
        private OrderHistoryPageCursor lastCursor;
        private OrderHistoryPageCursor nextCursor;

        @Override
        public QueryPage fetch(
            OrderHistoryQueryCriteria criteria,
            int pageSize,
            OrderHistoryPageCursor cursor
        ) {
            fetchCalls++;
            lastCursor = cursor;
            return new QueryPage(List.of(), nextCursor);
        }
    }
}

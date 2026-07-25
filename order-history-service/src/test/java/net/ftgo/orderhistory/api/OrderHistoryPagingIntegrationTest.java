package net.ftgo.orderhistory.api;

import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.service.InMemoryOrderHistoryQueryStore;
import net.ftgo.orderhistory.service.OrderHistoryQueryCriteria;
import net.ftgo.orderhistory.service.OrderHistoryQueryService;
import net.ftgo.orderhistory.service.UnsupportedOrderHistoryQueryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderHistoryPagingIntegrationTest {

    private OrderHistoryQueryService queryService;

    @BeforeEach
    void setUp() {
        List<OrderHistoryRecord> records = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 7, 25, 12, 0);
        for (int index = 0; index < 55; index++) {
            OrderHistoryRecord record = new OrderHistoryRecord(String.valueOf(1_000 + index));
            record.setConsumerId(42L);
            record.setRestaurantId(index % 2 == 0 ? 7L : 8L);
            record.setStatus(index % 3 == 0 ? "APPROVED" : "APPROVAL_PENDING");
            record.setCreationDate(base.minusMinutes(index));
            records.add(record);
        }
        queryService = new OrderHistoryQueryService(
            new InMemoryOrderHistoryQueryStore(records)
        );
    }

    @Test
    void returnsStableTwentyTwentyFifteenPagesWithoutGapsOrDuplicates() {
        OrderHistoryQueryCriteria criteria = new OrderHistoryQueryCriteria(
            42L,
            null,
            null,
            null
        );

        OrderHistoryResponse first = queryService.query(criteria, 20, null);
        OrderHistoryResponse second = queryService.query(
            criteria,
            20,
            first.getNextPagingState()
        );
        OrderHistoryResponse third = queryService.query(
            criteria,
            20,
            second.getNextPagingState()
        );

        assertEquals(20, first.getOrders().size());
        assertEquals(20, second.getOrders().size());
        assertEquals(15, third.getOrders().size());
        assertTrue(first.isHasMore());
        assertTrue(second.isHasMore());
        assertNull(third.getNextPagingState());
        assertNotEquals(first.getNextPagingState(), second.getNextPagingState());

        List<OrderHistoryRecord> all = new ArrayList<>();
        all.addAll(first.getOrders());
        all.addAll(second.getOrders());
        all.addAll(third.getOrders());
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < all.size(); index++) {
            OrderHistoryRecord record = all.get(index);
            ids.add(record.getOrderId());
            if (index > 0) {
                assertTrue(
                    !all.get(index - 1).getCreationDate().isBefore(record.getCreationDate()),
                    "records must be ordered by creation date descending"
                );
            }
        }
        assertEquals(55, ids.size());
    }

    @Test
    void routesDedicatedStatusAndRestaurantQueriesAndRejectsUnsupportedCombination() {
        OrderHistoryResponse approved = queryService.query(
            new OrderHistoryQueryCriteria(42L, "APPROVED", null, null),
            100,
            null
        );
        assertTrue(approved.getOrders().stream()
            .allMatch(record -> "APPROVED".equals(record.getStatus())));

        OrderHistoryResponse restaurant = queryService.query(
            new OrderHistoryQueryCriteria(42L, null, 7L, null),
            100,
            null
        );
        assertTrue(restaurant.getOrders().stream()
            .allMatch(record -> Long.valueOf(7L).equals(record.getRestaurantId())));

        assertThrows(
            UnsupportedOrderHistoryQueryException.class,
            () -> queryService.query(
                new OrderHistoryQueryCriteria(42L, "APPROVED", 7L, null),
                20,
                null
            )
        );
    }
}

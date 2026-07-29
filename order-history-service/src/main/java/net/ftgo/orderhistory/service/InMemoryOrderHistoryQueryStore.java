package net.ftgo.orderhistory.service;

import net.ftgo.orderhistory.domain.OrderHistoryRecord;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/** Deterministic adapter used by paging contracts. Filtering here represents database access patterns. */
public class InMemoryOrderHistoryQueryStore implements OrderHistoryQueryStore {

    private static final Comparator<OrderHistoryRecord> ORDERING = Comparator
        .comparing(
            OrderHistoryRecord::getCreationDate,
            Comparator.nullsLast(Comparator.reverseOrder())
        )
        .thenComparing(OrderHistoryRecord::getOrderId);

    private final List<OrderHistoryRecord> records;

    public InMemoryOrderHistoryQueryStore(List<OrderHistoryRecord> records) {
        this.records = List.copyOf(records);
    }

    @Override
    public QueryPage fetch(
        OrderHistoryQueryCriteria criteria,
        int pageSize,
        OrderHistoryPageCursor cursor
    ) {
        OrderHistoryQueryCriteria.QueryKind kind = criteria.kind();
        LocalDateTime since = criteria.since() == null
            ? null
            : criteria.since().atStartOfDay();
        List<OrderHistoryRecord> matching = records.stream()
            .filter(record -> criteria.consumerId().equals(record.getConsumerId()))
            .filter(record -> since == null
                || (record.getCreationDate() != null && !record.getCreationDate().isBefore(since)))
            .filter(record -> kind != OrderHistoryQueryCriteria.QueryKind.CONSUMER_STATUS
                || criteria.status().equals(record.getStatus()))
            .filter(record -> kind != OrderHistoryQueryCriteria.QueryKind.CONSUMER_RESTAURANT
                || criteria.restaurantId().equals(record.getRestaurantId()))
            .sorted(ORDERING)
            .toList();

        int offset = decodeOffset(cursor == null ? null : cursor.driverPagingState());
        if (offset > matching.size()) {
            throw new IllegalArgumentException("Paging state is outside the result set");
        }
        int end = Math.min(offset + pageSize, matching.size());
        OrderHistoryPageCursor next = end < matching.size()
            ? new OrderHistoryPageCursor(
                YearMonth.now(ZoneOffset.UTC),
                encodeOffset(end)
            )
            : null;
        return new QueryPage(matching.subList(offset, end), next);
    }

    private String encodeOffset(int offset) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            ("offset:" + offset).getBytes(StandardCharsets.UTF_8)
        );
    }

    private int decodeOffset(String pagingState) {
        if (pagingState == null || pagingState.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(
                Base64.getUrlDecoder().decode(pagingState),
                StandardCharsets.UTF_8
            );
            if (!decoded.startsWith("offset:")) {
                throw new IllegalArgumentException("Invalid paging state");
            }
            return Integer.parseInt(decoded.substring("offset:".length()));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid paging state", e);
        }
    }
}

package net.ftgo.orderhistory.service;

import net.ftgo.orderhistory.domain.OrderHistoryRecord;

import java.util.List;

public interface OrderHistoryQueryStore {

    QueryPage fetch(
        OrderHistoryQueryCriteria criteria,
        int pageSize,
        OrderHistoryPageCursor cursor
    );

    record QueryPage(
        List<OrderHistoryRecord> records,
        OrderHistoryPageCursor nextCursor
    ) {
        public QueryPage {
            records = List.copyOf(records);
        }

        public boolean hasMore() {
            return nextCursor != null;
        }
    }
}

package net.ftgo.orderhistory.service;

import net.ftgo.orderhistory.domain.OrderHistoryRecord;

import java.util.List;

public interface OrderHistoryQueryStore {

    QueryPage fetch(
        OrderHistoryQueryCriteria criteria,
        int pageSize,
        String pagingState
    );

    record QueryPage(
        List<OrderHistoryRecord> records,
        String nextPagingState
    ) {
        public QueryPage {
            records = List.copyOf(records);
        }

        public boolean hasMore() {
            return nextPagingState != null && !nextPagingState.isBlank();
        }
    }
}

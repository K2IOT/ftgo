package net.ftgo.orderhistory.service;

import net.ftgo.orderhistory.api.OrderHistoryResponse;
import org.springframework.stereotype.Service;

@Service
public class OrderHistoryQueryService {

    private final OrderHistoryQueryStore queryStore;

    public OrderHistoryQueryService(OrderHistoryQueryStore queryStore) {
        this.queryStore = queryStore;
    }

    public OrderHistoryResponse query(
        OrderHistoryQueryCriteria criteria,
        int requestedPageSize,
        String pagingState
    ) {
        int pageSize = requestedPageSize;
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
        criteria.kind();
        OrderHistoryQueryStore.QueryPage page = queryStore.fetch(
            criteria,
            pageSize,
            pagingState
        );
        return new OrderHistoryResponse(
            page.records(),
            page.records().size(),
            pageSize,
            page.hasMore(),
            page.nextPagingState()
        );
    }
}

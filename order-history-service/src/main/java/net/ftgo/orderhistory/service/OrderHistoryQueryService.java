package net.ftgo.orderhistory.service;

import net.ftgo.orderhistory.api.OrderHistoryResponse;
import org.springframework.stereotype.Service;

@Service
public class OrderHistoryQueryService {

    private final OrderHistoryQueryStore queryStore;
    private final OrderHistoryPagingTokenCodec pagingTokenCodec;

    public OrderHistoryQueryService(
        OrderHistoryQueryStore queryStore,
        OrderHistoryPagingTokenCodec pagingTokenCodec
    ) {
        this.queryStore = queryStore;
        this.pagingTokenCodec = pagingTokenCodec;
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
        OrderHistoryPageCursor cursor = pagingState == null || pagingState.isBlank()
            ? null
            : pagingTokenCodec.decode(pagingState, criteria, pageSize);
        OrderHistoryQueryStore.QueryPage page = queryStore.fetch(
            criteria,
            pageSize,
            cursor
        );
        String nextPagingState = page.nextCursor() == null
            ? null
            : pagingTokenCodec.encode(criteria, pageSize, page.nextCursor());
        return new OrderHistoryResponse(
            page.records(),
            page.records().size(),
            pageSize,
            page.hasMore(),
            nextPagingState
        );
    }
}

package net.ftgo.orderhistory.api;

import net.ftgo.orderhistory.domain.OrderHistoryRecord;

import java.util.List;

/**
 * Response DTO for paginated order history queries.
 * 
 * Contains:
 * - orders: List of order history records
 * - totalCount: Number of records in current page
 * - pageSize: Requested page size
 * - hasMore: Whether more records are available
 * - nextPagingState: Continuation token for next page (null if no more pages)
 */
public class OrderHistoryResponse {
    
    private List<OrderHistoryRecord> orders;
    private int totalCount;
    private int pageSize;
    private boolean hasMore;
    private String nextPagingState;
    
    public OrderHistoryResponse() {
    }
    
    public OrderHistoryResponse(
        List<OrderHistoryRecord> orders,
        int totalCount,
        int pageSize,
        boolean hasMore,
        String nextPagingState
    ) {
        this.orders = orders;
        this.totalCount = totalCount;
        this.pageSize = pageSize;
        this.hasMore = hasMore;
        this.nextPagingState = nextPagingState;
    }
    
    public List<OrderHistoryRecord> getOrders() {
        return orders;
    }
    
    public void setOrders(List<OrderHistoryRecord> orders) {
        this.orders = orders;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
    
    public boolean isHasMore() {
        return hasMore;
    }
    
    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
    
    public String getNextPagingState() {
        return nextPagingState;
    }
    
    public void setNextPagingState(String nextPagingState) {
        this.nextPagingState = nextPagingState;
    }
}

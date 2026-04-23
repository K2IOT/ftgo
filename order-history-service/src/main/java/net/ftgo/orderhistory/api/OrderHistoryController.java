package net.ftgo.orderhistory.api;

import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.cassandra.core.query.CassandraPageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for querying order history from the CQRS read model.
 * 
 * Provides endpoints for:
 * - Finding a specific order by ID
 * - Finding all orders for a consumer with filtering and pagination
 * 
 * Implements Requirements 9.3, 9.4, 9.5, 9.7
 */
@RestController
@RequestMapping("/api")
public class OrderHistoryController {
    
    @Autowired
    private OrderHistoryRepository orderHistoryRepository;
    
    /**
     * Finds a specific order by order ID.
     * 
     * @param orderId the order ID
     * @return the order history record, or 404 if not found
     * 
     * Requirement 9.3: Query by order_id returns correct record
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderHistoryRecord> findOrder(@PathVariable String orderId) {
        return orderHistoryRepository.findById(orderId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Finds all orders for a consumer with optional filtering and pagination.
     * 
     * Supports filtering by:
     * - status: Order status (APPROVAL_PENDING, APPROVED, REJECTED, CANCELLED)
     * - since: Start date (inclusive) for creation_date filtering
     * - restaurantId: Filter by restaurant
     * - keyword: Search in keywords set
     * 
     * Supports pagination with:
     * - pageSize: Number of records per page (default 20, max 100)
     * - pagingState: Continuation token from previous response (Base64-encoded)
     * 
     * The paging state is an opaque token that represents the position in the result set.
     * Clients should pass the pagingState from the previous response to get the next page.
     * 
     * @param consumerId the consumer ID
     * @param status optional status filter
     * @param since optional start date filter (format: yyyy-MM-dd)
     * @param restaurantId optional restaurant ID filter
     * @param keyword optional keyword search
     * @param pageSize page size (default 20, max 100)
     * @param pagingState continuation token for pagination (Base64-encoded)
     * @return paginated order history response
     * 
     * Requirements 9.4, 9.5, 9.7: Filtering, pagination, and sorting
     */
    @GetMapping("/consumers/{consumerId}/orders")
    public ResponseEntity<OrderHistoryResponse> findOrderHistory(
        @PathVariable Long consumerId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) LocalDate since,
        @RequestParam(required = false) Long restaurantId,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String pagingState
    ) {
        // Validate page size
        if (pageSize < 1 || pageSize > 100) {
            pageSize = 20;
        }
        
        // Create pageable with paging state if provided
        CassandraPageRequest pageable;
        if (pagingState != null && !pagingState.isEmpty()) {
            try {
                // Decode the Base64-encoded paging state
                byte[] pagingStateBytes = Base64.getDecoder().decode(pagingState);
                ByteBuffer pagingStateBuffer = ByteBuffer.wrap(pagingStateBytes);
                
                // Create page request with paging state
                // Use the static factory method that accepts paging state
                pageable = CassandraPageRequest.of(CassandraPageRequest.first(pageSize), pagingStateBuffer);
            } catch (IllegalArgumentException e) {
                // Invalid paging state, return bad request
                return ResponseEntity.badRequest().build();
            }
        } else {
            // First page request
            pageable = CassandraPageRequest.first(pageSize);
        }
        
        // Query with or without date filter
        Slice<OrderHistoryRecord> slice;
        if (since != null) {
            LocalDateTime sinceDateTime = since.atStartOfDay();
            slice = orderHistoryRepository.findByConsumerIdAndCreationDateAfter(
                consumerId, 
                sinceDateTime, 
                pageable
            );
        } else {
            slice = orderHistoryRepository.findByConsumerId(consumerId, pageable);
        }
        
        // Apply client-side filtering (status, restaurant, keyword)
        List<OrderHistoryRecord> filteredRecords = slice.getContent().stream()
            .filter(record -> status == null || status.equals(record.getStatus()))
            .filter(record -> restaurantId == null || restaurantId.equals(record.getRestaurantId()))
            .filter(record -> keyword == null || 
                (record.getKeywords() != null && record.getKeywords().contains(keyword.toLowerCase())))
            .collect(Collectors.toList());
        
        // Extract next paging state if available
        String nextPagingState = null;
        if (slice.hasNext() && pageable instanceof CassandraPageRequest) {
            CassandraPageRequest cassandraPageRequest = (CassandraPageRequest) pageable;
            ByteBuffer currentPagingState = cassandraPageRequest.getPagingState();
            
            // Encode the paging state as Base64 for the client
            if (currentPagingState != null) {
                byte[] pagingStateBytes = new byte[currentPagingState.remaining()];
                currentPagingState.get(pagingStateBytes);
                nextPagingState = Base64.getEncoder().encodeToString(pagingStateBytes);
            }
        }
        
        OrderHistoryResponse response = new OrderHistoryResponse(
            filteredRecords,
            filteredRecords.size(),
            pageSize,
            slice.hasNext(),
            nextPagingState
        );
        
        return ResponseEntity.ok(response);
    }
}

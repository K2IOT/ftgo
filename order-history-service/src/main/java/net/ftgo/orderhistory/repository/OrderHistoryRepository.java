package net.ftgo.orderhistory.repository;

import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Repository for querying order history from ScyllaDB.
 * 
 * Provides methods for:
 * - Finding orders by order ID (primary key query)
 * - Finding orders by consumer ID (materialized view query)
 * - Pagination support for large result sets
 */
@Repository
public interface OrderHistoryRepository extends CassandraRepository<OrderHistoryRecord, String> {
    
    /**
     * Finds all orders for a consumer, sorted by creation date descending.
     * 
     * Uses the order_history_by_consumer materialized view for efficient querying.
     * 
     * @param consumerId the consumer ID
     * @param pageable pagination parameters
     * @return a slice of order history records
     */
    @Query("SELECT * FROM order_history_by_consumer WHERE consumer_id = ?0")
    Slice<OrderHistoryRecord> findByConsumerId(Long consumerId, Pageable pageable);
    
    /**
     * Finds orders for a consumer created after a specific date.
     * 
     * Uses the order_history_by_consumer materialized view with date filtering.
     * 
     * @param consumerId the consumer ID
     * @param since the start date (inclusive)
     * @param pageable pagination parameters
     * @return a slice of order history records
     */
    @Query("SELECT * FROM order_history_by_consumer WHERE consumer_id = ?0 AND creation_date >= ?1")
    Slice<OrderHistoryRecord> findByConsumerIdAndCreationDateAfter(
        Long consumerId, 
        LocalDateTime since, 
        Pageable pageable
    );
}

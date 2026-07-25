package net.ftgo.orderhistory.repository;

import net.ftgo.orderhistory.domain.OrderHistoryByConsumer;
import net.ftgo.orderhistory.domain.OrderHistoryByConsumerKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface OrderHistoryByConsumerRepository
    extends CassandraRepository<OrderHistoryByConsumer, OrderHistoryByConsumerKey> {

    @Query("SELECT * FROM order_history_by_consumer_v2 WHERE consumer_id = ?0 AND creation_month = ?1")
    Slice<OrderHistoryByConsumer> findPage(Long consumerId, String creationMonth, Pageable pageable);

    @Query("SELECT * FROM order_history_by_consumer_v2 WHERE consumer_id = ?0 AND creation_month = ?1 AND creation_date >= ?2")
    Slice<OrderHistoryByConsumer> findPageSince(
        Long consumerId,
        String creationMonth,
        LocalDateTime since,
        Pageable pageable
    );
}

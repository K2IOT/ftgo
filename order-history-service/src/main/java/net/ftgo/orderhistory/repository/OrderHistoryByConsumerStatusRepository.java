package net.ftgo.orderhistory.repository;

import net.ftgo.orderhistory.domain.OrderHistoryByConsumerStatus;
import net.ftgo.orderhistory.domain.OrderHistoryByConsumerStatusKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface OrderHistoryByConsumerStatusRepository
    extends CassandraRepository<OrderHistoryByConsumerStatus, OrderHistoryByConsumerStatusKey> {

    @Query("SELECT * FROM order_history_by_consumer_status_v2 WHERE consumer_id = ?0 AND status = ?1 AND creation_month = ?2")
    Slice<OrderHistoryByConsumerStatus> findPage(
        Long consumerId,
        String status,
        String creationMonth,
        Pageable pageable
    );

    @Query("SELECT * FROM order_history_by_consumer_status_v2 WHERE consumer_id = ?0 AND status = ?1 AND creation_month = ?2 AND creation_date >= ?3")
    Slice<OrderHistoryByConsumerStatus> findPageSince(
        Long consumerId,
        String status,
        String creationMonth,
        LocalDateTime since,
        Pageable pageable
    );
}

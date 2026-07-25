package net.ftgo.orderhistory.repository;

import net.ftgo.orderhistory.domain.PendingOrderEvent;
import net.ftgo.orderhistory.domain.PendingOrderEventKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PendingOrderEventRepository
    extends CassandraRepository<PendingOrderEvent, PendingOrderEventKey> {

    @Query("SELECT * FROM pending_order_events WHERE order_id = ?0")
    List<PendingOrderEvent> findByOrderId(String orderId);
}

package net.ftgo.orderhistory.repository;

import net.ftgo.orderhistory.domain.OrderHistoryByConsumerRestaurant;
import net.ftgo.orderhistory.domain.OrderHistoryByConsumerRestaurantKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface OrderHistoryByConsumerRestaurantRepository
    extends CassandraRepository<OrderHistoryByConsumerRestaurant, OrderHistoryByConsumerRestaurantKey> {

    @Query("SELECT * FROM order_history_by_consumer_restaurant_v2 WHERE consumer_id = ?0 AND restaurant_id = ?1 AND creation_month = ?2")
    Slice<OrderHistoryByConsumerRestaurant> findPage(
        Long consumerId,
        Long restaurantId,
        String creationMonth,
        Pageable pageable
    );

    @Query("SELECT * FROM order_history_by_consumer_restaurant_v2 WHERE consumer_id = ?0 AND restaurant_id = ?1 AND creation_month = ?2 AND creation_date >= ?3")
    Slice<OrderHistoryByConsumerRestaurant> findPageSince(
        Long consumerId,
        Long restaurantId,
        String creationMonth,
        LocalDateTime since,
        Pageable pageable
    );
}

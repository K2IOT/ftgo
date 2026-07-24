package net.ftgo.order.repository;

import jakarta.persistence.LockModeType;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Order aggregate persistence.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Serializes saga-local mutations and restaurant decision claims.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    List<Order> findByConsumerId(Long consumerId);

    List<Order> findByConsumerIdAndState(Long consumerId, OrderState state);

    List<Order> findByRestaurantId(Long restaurantId);

    List<Order> findByState(OrderState state);

    boolean existsById(Long id);
}

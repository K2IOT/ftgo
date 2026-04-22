package net.ftgo.order.repository;

import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Order aggregate persistence.
 * 
 * Implements optimistic locking via JPA @Version field to detect concurrent updates.
 * When a concurrent update is detected, JPA throws OptimisticLockException.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    /**
     * Finds an order by ID with pessimistic write lock.
     * Used during saga execution to prevent concurrent modifications.
     * 
     * @param id the order ID
     * @return an Optional containing the order if found, empty otherwise
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);
    
    /**
     * Finds all orders for a specific consumer.
     * 
     * @param consumerId the consumer ID
     * @return a list of orders
     */
    List<Order> findByConsumerId(Long consumerId);
    
    /**
     * Finds all orders for a specific consumer with a specific state.
     * 
     * @param consumerId the consumer ID
     * @param state the order state
     * @return a list of orders
     */
    List<Order> findByConsumerIdAndState(Long consumerId, OrderState state);
    
    /**
     * Finds all orders for a specific restaurant.
     * 
     * @param restaurantId the restaurant ID
     * @return a list of orders
     */
    List<Order> findByRestaurantId(Long restaurantId);
    
    /**
     * Finds all orders with a specific state.
     * 
     * @param state the order state
     * @return a list of orders
     */
    List<Order> findByState(OrderState state);
    
    /**
     * Checks if an order exists with the given ID.
     * 
     * @param id the order ID
     * @return true if order exists, false otherwise
     */
    boolean existsById(Long id);
}

package net.ftgo.delivery.repository;

import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.domain.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Delivery aggregate.
 */
@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    
    /**
     * Finds a delivery by order ID.
     * 
     * @param orderId the order ID
     * @return the delivery if found
     */
    Optional<Delivery> findByOrderId(Long orderId);
    
    /**
     * Finds all deliveries for a courier.
     * 
     * @param courierId the courier ID
     * @return list of deliveries
     */
    List<Delivery> findByCourierId(Long courierId);
    
    /**
     * Finds all deliveries with a specific status.
     * 
     * @param status the delivery status
     * @return list of deliveries
     */
    List<Delivery> findByStatus(DeliveryStatus status);
    
    /**
     * Finds all deliveries for a courier with a specific status.
     * 
     * @param courierId the courier ID
     * @param status the delivery status
     * @return list of deliveries
     */
    List<Delivery> findByCourierIdAndStatus(Long courierId, DeliveryStatus status);
}

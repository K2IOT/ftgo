package net.ftgo.delivery.repository;

import net.ftgo.delivery.domain.Courier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Courier entity.
 */
@Repository
public interface CourierRepository extends JpaRepository<Courier, Long> {
    
    /**
     * Finds all available couriers.
     * 
     * @return list of available couriers
     */
    List<Courier> findByAvailableTrue();
    
    /**
     * Finds all couriers by availability status.
     * 
     * @param available the availability status
     * @return list of couriers
     */
    List<Courier> findByAvailable(Boolean available);
}

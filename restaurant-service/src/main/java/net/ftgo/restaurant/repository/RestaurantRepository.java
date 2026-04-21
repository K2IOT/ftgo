package net.ftgo.restaurant.repository;

import net.ftgo.restaurant.domain.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Restaurant aggregate persistence.
 */
@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    
    /**
     * Finds a restaurant by name.
     * 
     * @param name the restaurant name to search for
     * @return an Optional containing the restaurant if found, empty otherwise
     */
    Optional<Restaurant> findByName(String name);
    
    /**
     * Checks if a restaurant exists with the given name.
     * 
     * @param name the restaurant name to check
     * @return true if a restaurant exists with the name, false otherwise
     */
    boolean existsByName(String name);
}

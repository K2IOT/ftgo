package net.ftgo.restaurant.repository;

import net.ftgo.restaurant.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MenuItem entity persistence.
 */
@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    
    /**
     * Finds all menu items for a specific restaurant.
     * 
     * @param restaurantId the restaurant ID
     * @return a list of menu items belonging to the restaurant
     */
    List<MenuItem> findByRestaurantId(Long restaurantId);
    
    /**
     * Finds all available menu items for a specific restaurant.
     * 
     * @param restaurantId the restaurant ID
     * @param available the availability status
     * @return a list of available menu items belonging to the restaurant
     */
    List<MenuItem> findByRestaurantIdAndAvailable(Long restaurantId, Boolean available);
    
    /**
     * Finds a menu item by restaurant ID and menu item ID.
     * 
     * @param restaurantId the restaurant ID
     * @param id the menu item ID
     * @return an Optional containing the menu item if found, empty otherwise
     */
    Optional<MenuItem> findByRestaurantIdAndId(Long restaurantId, Long id);
    
    /**
     * Checks if a menu item exists for a specific restaurant.
     * 
     * @param restaurantId the restaurant ID
     * @param id the menu item ID
     * @return true if the menu item exists, false otherwise
     */
    boolean existsByRestaurantIdAndId(Long restaurantId, Long id);
}

package net.ftgo.restaurant.repository;

import net.ftgo.restaurant.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByRestaurantId(Long restaurantId);

    List<MenuItem> findByRestaurantIdAndAvailable(Long restaurantId, Boolean available);

    Optional<MenuItem> findByRestaurantIdAndId(Long restaurantId, Long id);

    List<MenuItem> findByRestaurantIdAndIdIn(Long restaurantId, List<Long> ids);

    boolean existsByRestaurantIdAndId(Long restaurantId, Long id);
}

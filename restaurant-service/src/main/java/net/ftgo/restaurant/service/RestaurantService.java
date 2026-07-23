package net.ftgo.restaurant.service;

import net.ftgo.restaurant.domain.MenuItem;
import net.ftgo.restaurant.domain.Restaurant;
import net.ftgo.restaurant.domain.RestaurantMenuChanged;
import net.ftgo.restaurant.messaging.DomainEventPublisher;
import net.ftgo.restaurant.repository.MenuItemRepository;
import net.ftgo.restaurant.repository.RestaurantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing restaurants and menu items.
 */
@Service
public class RestaurantService {

    private static final Logger logger = LoggerFactory.getLogger(RestaurantService.class);

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final DomainEventPublisher eventPublisher;

    public RestaurantService(RestaurantRepository restaurantRepository,
                             MenuItemRepository menuItemRepository,
                             DomainEventPublisher eventPublisher) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Restaurant createRestaurant(Restaurant restaurant) {
        logger.info("Creating restaurant: {}", restaurant.getName());
        Restaurant saved = restaurantRepository.save(restaurant);
        logger.info("Created restaurant with ID: {}", saved.getId());
        return saved;
    }

    public Restaurant findRestaurant(Long restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
    }

    @Transactional
    public MenuItem createMenuItem(Long restaurantId, MenuItem menuItem) {
        Restaurant restaurant = findRestaurant(restaurantId);

        logger.info("Creating menu item '{}' for restaurant {}", menuItem.getName(), restaurantId);
        MenuItem saved = menuItemRepository.save(menuItem);

        publishMenuChangedEvent(restaurantId, restaurant);

        logger.info("Created menu item with ID: {}", saved.getId());
        return saved;
    }

    @Transactional
    public MenuItem updateMenuItem(Long restaurantId, Long menuItemId,
                                   String name, String description,
                                   net.ftgo.common.Money price, Boolean available) {
        Restaurant restaurant = findRestaurant(restaurantId);

        MenuItem menuItem = menuItemRepository.findByRestaurantIdAndId(restaurantId, menuItemId)
                .orElseThrow(() -> new MenuItemNotFoundException(restaurantId, menuItemId));

        logger.info("Updating menu item {} for restaurant {}", menuItemId, restaurantId);

        if (name != null || description != null || price != null) {
            menuItem.updateDetails(name, description, price);
        }

        if (available != null) {
            menuItem.setAvailable(available);
        }

        MenuItem updated = menuItemRepository.save(menuItem);
        publishMenuChangedEvent(restaurantId, restaurant);

        logger.info("Updated menu item {}", menuItemId);
        return updated;
    }

    @Transactional
    public void deleteMenuItem(Long restaurantId, Long menuItemId) {
        Restaurant restaurant = findRestaurant(restaurantId);

        MenuItem menuItem = menuItemRepository.findByRestaurantIdAndId(restaurantId, menuItemId)
                .orElseThrow(() -> new MenuItemNotFoundException(restaurantId, menuItemId));

        logger.info("Deleting menu item {} for restaurant {}", menuItemId, restaurantId);
        menuItemRepository.delete(menuItem);

        publishMenuChangedEvent(restaurantId, restaurant);

        logger.info("Deleted menu item {}", menuItemId);
    }

    public List<MenuItem> getMenuItems(Long restaurantId) {
        return menuItemRepository.findByRestaurantId(restaurantId);
    }

    public boolean validateMenuItems(Long restaurantId, List<Long> menuItemIds) {
        for (Long menuItemId : menuItemIds) {
            MenuItem menuItem = menuItemRepository.findByRestaurantIdAndId(restaurantId, menuItemId)
                    .orElse(null);

            if (menuItem == null) {
                logger.warn("Menu item {} not found for restaurant {}", menuItemId, restaurantId);
                return false;
            }

            if (!menuItem.isAvailable()) {
                logger.warn("Menu item {} is not available for restaurant {}", menuItemId, restaurantId);
                return false;
            }
        }

        return true;
    }

    /**
     * Publishes a menu snapshot using the authoritative restaurant ID supplied
     * by the service boundary. A newly constructed/mock Restaurant can have a
     * null entity ID before persistence, so the aggregate field must not be used
     * to select or key the event.
     */
    private void publishMenuChangedEvent(Long restaurantId, Restaurant restaurant) {
        List<MenuItem> menuItems = menuItemRepository.findByRestaurantId(restaurantId);

        List<RestaurantMenuChanged.MenuItemInfo> menuItemInfos = menuItems.stream()
                .map(item -> new RestaurantMenuChanged.MenuItemInfo(
                        item.getId(),
                        item.getName(),
                        item.getDescription(),
                        item.getPrice().toString(),
                        item.getAvailable()
                ))
                .collect(Collectors.toList());

        RestaurantMenuChanged event = new RestaurantMenuChanged(
                restaurantId,
                restaurant.getName(),
                menuItemInfos
        );

        eventPublisher.publishRestaurantEvent(restaurantId, event);
    }
}

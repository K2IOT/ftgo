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
    
    /**
     * Creates a new restaurant.
     * 
     * @param restaurant the restaurant to create
     * @return the created restaurant with generated ID
     */
    @Transactional
    public Restaurant createRestaurant(Restaurant restaurant) {
        logger.info("Creating restaurant: {}", restaurant.getName());
        Restaurant saved = restaurantRepository.save(restaurant);
        logger.info("Created restaurant with ID: {}", saved.getId());
        return saved;
    }
    
    /**
     * Finds a restaurant by ID.
     * 
     * @param restaurantId the restaurant ID
     * @return the restaurant
     * @throws RestaurantNotFoundException if restaurant not found
     */
    public Restaurant findRestaurant(Long restaurantId) {
        return restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
    }
    
    /**
     * Creates a new menu item for a restaurant and publishes RestaurantMenuChanged event.
     * 
     * @param restaurantId the restaurant ID
     * @param menuItem the menu item to create
     * @return the created menu item with generated ID
     * @throws RestaurantNotFoundException if restaurant not found
     */
    @Transactional
    public MenuItem createMenuItem(Long restaurantId, MenuItem menuItem) {
        // Verify restaurant exists
        Restaurant restaurant = findRestaurant(restaurantId);
        
        logger.info("Creating menu item '{}' for restaurant {}", menuItem.getName(), restaurantId);
        MenuItem saved = menuItemRepository.save(menuItem);
        
        // Publish RestaurantMenuChanged event
        publishMenuChangedEvent(restaurant);
        
        logger.info("Created menu item with ID: {}", saved.getId());
        return saved;
    }
    
    /**
     * Updates an existing menu item and publishes RestaurantMenuChanged event.
     * 
     * @param restaurantId the restaurant ID
     * @param menuItemId the menu item ID
     * @param name the new name (optional)
     * @param description the new description (optional)
     * @param price the new price (optional)
     * @param available the new availability status (optional)
     * @return the updated menu item
     * @throws RestaurantNotFoundException if restaurant not found
     * @throws MenuItemNotFoundException if menu item not found
     */
    @Transactional
    public MenuItem updateMenuItem(Long restaurantId, Long menuItemId, 
                                  String name, String description, 
                                  net.ftgo.common.Money price, Boolean available) {
        // Verify restaurant exists
        Restaurant restaurant = findRestaurant(restaurantId);
        
        // Find menu item
        MenuItem menuItem = menuItemRepository.findByRestaurantIdAndId(restaurantId, menuItemId)
            .orElseThrow(() -> new MenuItemNotFoundException(restaurantId, menuItemId));
        
        logger.info("Updating menu item {} for restaurant {}", menuItemId, restaurantId);
        
        // Update details if provided
        if (name != null || description != null || price != null) {
            menuItem.updateDetails(name, description, price);
        }
        
        // Update availability if provided
        if (available != null) {
            menuItem.setAvailable(available);
        }
        
        MenuItem updated = menuItemRepository.save(menuItem);
        
        // Publish RestaurantMenuChanged event
        publishMenuChangedEvent(restaurant);
        
        logger.info("Updated menu item {}", menuItemId);
        return updated;
    }
    
    /**
     * Deletes a menu item and publishes RestaurantMenuChanged event.
     * 
     * @param restaurantId the restaurant ID
     * @param menuItemId the menu item ID
     * @throws RestaurantNotFoundException if restaurant not found
     * @throws MenuItemNotFoundException if menu item not found
     */
    @Transactional
    public void deleteMenuItem(Long restaurantId, Long menuItemId) {
        // Verify restaurant exists
        Restaurant restaurant = findRestaurant(restaurantId);
        
        // Find menu item
        MenuItem menuItem = menuItemRepository.findByRestaurantIdAndId(restaurantId, menuItemId)
            .orElseThrow(() -> new MenuItemNotFoundException(restaurantId, menuItemId));
        
        logger.info("Deleting menu item {} for restaurant {}", menuItemId, restaurantId);
        menuItemRepository.delete(menuItem);
        
        // Publish RestaurantMenuChanged event
        publishMenuChangedEvent(restaurant);
        
        logger.info("Deleted menu item {}", menuItemId);
    }
    
    /**
     * Gets all menu items for a restaurant.
     * 
     * @param restaurantId the restaurant ID
     * @return list of menu items
     */
    public List<MenuItem> getMenuItems(Long restaurantId) {
        return menuItemRepository.findByRestaurantId(restaurantId);
    }
    
    /**
     * Validates that menu items exist and are available for order placement.
     * 
     * @param restaurantId the restaurant ID
     * @param menuItemIds the menu item IDs to validate
     * @return true if all items exist and are available
     */
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
     * Publishes a RestaurantMenuChanged event with current menu state.
     * 
     * @param restaurant the restaurant whose menu changed
     */
    private void publishMenuChangedEvent(Restaurant restaurant) {
        List<MenuItem> menuItems = menuItemRepository.findByRestaurantId(restaurant.getId());
        
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
            restaurant.getId(),
            restaurant.getName(),
            menuItemInfos
        );
        
        eventPublisher.publishRestaurantEvent(restaurant.getId(), event);
    }
}

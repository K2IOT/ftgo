package net.ftgo.restaurant.service;

/**
 * Exception thrown when a menu item is not found.
 */
public class MenuItemNotFoundException extends RuntimeException {
    
    private final Long restaurantId;
    private final Long menuItemId;
    
    public MenuItemNotFoundException(Long restaurantId, Long menuItemId) {
        super("Menu item " + menuItemId + " not found for restaurant " + restaurantId);
        this.restaurantId = restaurantId;
        this.menuItemId = menuItemId;
    }
    
    public Long getRestaurantId() {
        return restaurantId;
    }
    
    public Long getMenuItemId() {
        return menuItemId;
    }
}

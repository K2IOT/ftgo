package net.ftgo.restaurant.service;

/**
 * Exception thrown when a restaurant is not found.
 */
public class RestaurantNotFoundException extends RuntimeException {
    
    private final Long restaurantId;
    
    public RestaurantNotFoundException(Long restaurantId) {
        super("Restaurant not found: " + restaurantId);
        this.restaurantId = restaurantId;
    }
    
    public Long getRestaurantId() {
        return restaurantId;
    }
}

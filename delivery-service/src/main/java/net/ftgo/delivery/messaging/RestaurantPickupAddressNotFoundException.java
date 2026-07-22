package net.ftgo.delivery.messaging;

public class RestaurantPickupAddressNotFoundException extends RuntimeException {

    private final Long restaurantId;

    public RestaurantPickupAddressNotFoundException(Long restaurantId) {
        super("Pickup address not found for restaurant " + restaurantId);
        this.restaurantId = restaurantId;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }
}

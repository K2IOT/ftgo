package net.ftgo.delivery.messaging;

public class RestaurantServiceUnavailableException extends RuntimeException {

    public RestaurantServiceUnavailableException(Long restaurantId, Throwable cause) {
        super("Restaurant Service is unavailable while resolving pickup address for restaurant "
                + restaurantId, cause);
    }

    public RestaurantServiceUnavailableException(Long restaurantId, String detail) {
        super("Cannot resolve pickup address for restaurant " + restaurantId + ": " + detail);
    }
}

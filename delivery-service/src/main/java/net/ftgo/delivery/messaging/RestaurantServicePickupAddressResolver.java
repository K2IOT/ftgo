package net.ftgo.delivery.messaging;

import net.ftgo.common.Address;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestaurantServicePickupAddressResolver implements RestaurantPickupAddressResolver {

    private final RestClient restClient;

    public RestaurantServicePickupAddressResolver(
        RestClient.Builder restClientBuilder,
        @Value("${services.restaurant-service.url:http://localhost:8083}") String restaurantServiceUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(restaurantServiceUrl).build();
    }

    @Override
    public Address resolvePickupAddress(Long restaurantId) {
        RestaurantResponse restaurant = restClient.get()
            .uri("/restaurants/{restaurantId}", restaurantId)
            .retrieve()
            .body(RestaurantResponse.class);

        if (restaurant == null || restaurant.getAddress() == null) {
            throw new IllegalArgumentException("Pickup address not found for restaurant: " + restaurantId);
        }

        return restaurant.getAddress();
    }

    public static class RestaurantResponse {
        private Address address;

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }
    }
}

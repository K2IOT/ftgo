package net.ftgo.delivery.messaging;

import net.ftgo.common.Address;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpRestaurantPickupAddressResolver implements RestaurantPickupAddressResolver {

    private final RestClient restClient;
    private final int maxRetries;

    public HttpRestaurantPickupAddressResolver(
            @Qualifier("restaurantRestClient") RestClient restClient,
            @Value("${services.restaurant-service.max-retries:2}") int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries cannot be negative");
        }
        this.restClient = restClient;
        this.maxRetries = maxRetries;
    }

    @Override
    public Address resolvePickupAddress(Long restaurantId) {
        if (restaurantId == null || restaurantId <= 0) {
            throw new IllegalArgumentException("restaurantId must be positive");
        }

        int retries = 0;
        while (true) {
            try {
                RestaurantPickupAddressResponse response = restClient.get()
                        .uri("/internal/restaurants/{restaurantId}/pickup-address", restaurantId)
                        .retrieve()
                        .body(RestaurantPickupAddressResponse.class);

                if (response == null || response.address() == null) {
                    throw new RestaurantServiceUnavailableException(
                            restaurantId,
                            "response did not contain an address");
                }
                if (!restaurantId.equals(response.restaurantId())) {
                    throw new RestaurantServiceUnavailableException(
                            restaurantId,
                            "response restaurantId did not match request");
                }
                return response.address();
            } catch (HttpClientErrorException.NotFound exception) {
                throw new RestaurantPickupAddressNotFoundException(restaurantId);
            } catch (HttpServerErrorException | ResourceAccessException exception) {
                if (retries >= maxRetries) {
                    throw new RestaurantServiceUnavailableException(restaurantId, exception);
                }
                retries++;
            } catch (HttpClientErrorException exception) {
                throw new RestaurantServiceUnavailableException(
                        restaurantId,
                        "Restaurant Service rejected the request with status "
                                + exception.getStatusCode().value());
            } catch (RestClientException exception) {
                throw new RestaurantServiceUnavailableException(restaurantId, exception);
            }
        }
    }

    private record RestaurantPickupAddressResponse(Long restaurantId, Address address) {
    }
}

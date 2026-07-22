package net.ftgo.delivery.messaging;

import net.ftgo.common.Address;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpRestaurantPickupAddressResolverTest {

    @Test
    void resolvesAuthoritativeAddress() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://restaurant-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRestaurantPickupAddressResolver resolver =
                new HttpRestaurantPickupAddressResolver(builder.build(), 2);

        server.expect(once(), requestTo(
                        "http://restaurant-service/internal/restaurants/42/pickup-address"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"restaurantId\":42,\"address\":{\"street\":\"10 Kitchen Road\",\"city\":\"Bangkok\",\"state\":\"Bangkok\",\"zipCode\":\"10110\"}}",
                        MediaType.APPLICATION_JSON));

        Address address = resolver.resolvePickupAddress(42L);

        assertThat(address).isEqualTo(
                new Address("10 Kitchen Road", "Bangkok", "Bangkok", "10110"));
        server.verify();
    }

    @Test
    void doesNotRetryMissingRestaurant() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://restaurant-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRestaurantPickupAddressResolver resolver =
                new HttpRestaurantPickupAddressResolver(builder.build(), 2);

        server.expect(once(), requestTo(
                        "http://restaurant-service/internal/restaurants/999/pickup-address"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> resolver.resolvePickupAddress(999L))
                .isInstanceOf(RestaurantPickupAddressNotFoundException.class);
        server.verify();
    }

    @Test
    void retriesTransientServerFailureAtMostTwice() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://restaurant-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpRestaurantPickupAddressResolver resolver =
                new HttpRestaurantPickupAddressResolver(builder.build(), 2);

        String uri = "http://restaurant-service/internal/restaurants/42/pickup-address";
        server.expect(once(), requestTo(uri)).andRespond(withServerError());
        server.expect(once(), requestTo(uri)).andRespond(withServerError());
        server.expect(once(), requestTo(uri)).andRespond(withServerError());

        assertThatThrownBy(() -> resolver.resolvePickupAddress(42L))
                .isInstanceOf(RestaurantServiceUnavailableException.class);
        server.verify();
    }
}

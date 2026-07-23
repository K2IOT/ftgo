package net.ftgo.delivery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestaurantClientConfiguration {

    @Bean("restaurantRestClient")
    public RestClient restaurantRestClient(
            RestClient.Builder builder,
            @Value("${services.restaurant-service.url}") String restaurantServiceUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));

        return builder
                .baseUrl(restaurantServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }
}

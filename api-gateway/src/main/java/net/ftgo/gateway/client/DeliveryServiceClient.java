package net.ftgo.gateway.client;

import net.ftgo.gateway.dto.DeliveryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Client for calling Delivery Service.
 */
@Component
public class DeliveryServiceClient {
    
    private final WebClient webClient;
    
    public DeliveryServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${services.delivery-service.url:http://delivery-service:8080}") String deliveryServiceUrl
    ) {
        this.webClient = webClientBuilder
                .baseUrl(deliveryServiceUrl)
                .build();
    }
    
    /**
     * Get delivery by order ID.
     * Returns empty Mono if delivery not found (404).
     * 
     * @param orderId the order ID
     * @return Mono of DeliveryResponse, empty if not found
     */
    public Mono<DeliveryResponse> getDeliveryByOrderId(Long orderId) {
        return webClient.get()
                .uri("/deliveries/by-order/{orderId}", orderId)
                .retrieve()
                .bodyToMono(DeliveryResponse.class)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }
}

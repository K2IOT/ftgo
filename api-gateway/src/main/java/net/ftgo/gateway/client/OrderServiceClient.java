package net.ftgo.gateway.client;

import net.ftgo.gateway.dto.OrderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Client for calling Order Service.
 */
@Component
public class OrderServiceClient {
    
    private final WebClient webClient;
    
    public OrderServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${services.order-service.url:http://order-service:8080}") String orderServiceUrl
    ) {
        this.webClient = webClientBuilder
                .baseUrl(orderServiceUrl)
                .build();
    }
    
    /**
     * Get order details by order ID.
     * 
     * @param orderId the order ID
     * @return Mono of OrderResponse
     */
    public Mono<OrderResponse> getOrder(Long orderId) {
        return webClient.get()
                .uri("/orders/{orderId}", orderId)
                .retrieve()
                .bodyToMono(OrderResponse.class);
    }
}

package net.ftgo.gateway.client;

import net.ftgo.gateway.dto.TicketResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Client for calling Kitchen Service.
 */
@Component
public class KitchenServiceClient {
    
    private final WebClient webClient;
    
    public KitchenServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${services.kitchen-service.url:http://kitchen-service:8080}") String kitchenServiceUrl
    ) {
        this.webClient = webClientBuilder
                .baseUrl(kitchenServiceUrl)
                .build();
    }
    
    /**
     * Get ticket by order ID.
     * Returns empty Mono if ticket not found (404).
     * 
     * @param orderId the order ID
     * @return Mono of TicketResponse, empty if not found
     */
    public Mono<TicketResponse> getTicketByOrderId(Long orderId) {
        return webClient.get()
                .uri("/tickets/by-order/{orderId}", orderId)
                .retrieve()
                .bodyToMono(TicketResponse.class)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }
}

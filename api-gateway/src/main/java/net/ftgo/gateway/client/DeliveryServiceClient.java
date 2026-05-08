package net.ftgo.gateway.client;

import net.ftgo.gateway.dto.DeliveryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Client for calling Delivery Service.
 *
 * B6 FIX: Forwards the Authorization header from the current security context
 * to the downstream service so JWT-protected endpoints accept the call.
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
     * Propagates the caller's JWT to the downstream service.
     *
     * @param orderId the order ID
     * @return Mono of DeliveryResponse, empty if not found
     */
    public Mono<DeliveryResponse> getDeliveryByOrderId(Long orderId) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> ((JwtAuthenticationToken) auth).getToken().getTokenValue())
                .flatMap(token -> webClient.get()
                        .uri("/deliveries/by-order/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .bodyToMono(DeliveryResponse.class))
                .switchIfEmpty(webClient.get()
                        .uri("/deliveries/by-order/{orderId}", orderId)
                        .retrieve()
                        .bodyToMono(DeliveryResponse.class))
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }
}

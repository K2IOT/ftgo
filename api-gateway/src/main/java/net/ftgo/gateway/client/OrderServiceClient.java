package net.ftgo.gateway.client;

import net.ftgo.gateway.dto.OrderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Client for calling Order Service.
 *
 * B6 FIX: Forwards the Authorization header from the current security context
 * to the downstream service so JWT-protected endpoints accept the call.
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
     * Propagates the caller's JWT to the downstream service.
     *
     * @param orderId the order ID
     * @return Mono of OrderResponse
     */
    public Mono<OrderResponse> getOrder(Long orderId) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> ((JwtAuthenticationToken) auth).getToken().getTokenValue())
                .flatMap(token -> webClient.get()
                        .uri("/orders/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .bodyToMono(OrderResponse.class))
                .switchIfEmpty(webClient.get()
                        .uri("/orders/{orderId}", orderId)
                        .retrieve()
                        .bodyToMono(OrderResponse.class));
    }
}

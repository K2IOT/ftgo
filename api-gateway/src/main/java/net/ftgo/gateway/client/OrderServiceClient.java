package net.ftgo.gateway.client;

import net.ftgo.gateway.dto.OrderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Client for calling Order Service.
 *
 * Propagates the caller JWT and preserves an Order Service 404 as an empty
 * result so the API-composition endpoint can return 404 instead of 502/503.
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

    public Mono<OrderResponse> getOrder(Long orderId) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(authentication -> authentication instanceof JwtAuthenticationToken)
                .map(authentication -> ((JwtAuthenticationToken) authentication).getToken().getTokenValue())
                .defaultIfEmpty("")
                .flatMap(token -> fetchOrder(orderId, token));
    }

    private Mono<OrderResponse> fetchOrder(Long orderId, String token) {
        return webClient.get()
                .uri("/orders/{orderId}", orderId)
                .headers(headers -> {
                    if (!token.isBlank()) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    }
                })
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(OrderResponse.class);
                    }
                    if (response.statusCode().equals(HttpStatus.NOT_FOUND)) {
                        return Mono.empty();
                    }
                    return response.createException().flatMap(Mono::error);
                });
    }
}

package net.ftgo.gateway.client;

import net.ftgo.common.security.FtgoJwtAuthenticationToken;
import net.ftgo.gateway.dto.OrderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Client for calling Order Service.
 *
 * <p>Propagates the verified caller JWT and preserves an Order Service 404 as
 * an empty result so the API-composition endpoint can return 404.
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

    /** Context-based compatibility entry point for standalone reactive callers. */
    public Mono<OrderResponse> getOrder(Long orderId) {
        return verifiedToken().flatMap(token -> getOrder(orderId, token));
    }

    /** Explicit token entry point used by API composition across AOP boundaries. */
    public Mono<OrderResponse> getOrder(Long orderId, String verifiedToken) {
        return webClient.get()
                .uri("/orders/{orderId}", orderId)
                .header(HttpHeaders.AUTHORIZATION, bearer(verifiedToken))
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

    private Mono<String> verifiedToken() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(FtgoJwtAuthenticationToken.class::isInstance)
                .cast(FtgoJwtAuthenticationToken.class)
                .map(FtgoJwtAuthenticationToken::tokenValue)
                .switchIfEmpty(Mono.error(new IllegalStateException("Verified FTGO JWT is required")));
    }

    private String bearer(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Verified FTGO JWT is required");
        }
        return "Bearer " + token;
    }
}

package net.ftgo.gateway.client;

import net.ftgo.common.security.FtgoJwtAuthenticationToken;
import net.ftgo.gateway.dto.DeliveryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/** Client for calling Delivery Service with the verified caller JWT. */
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

    /** Context-based compatibility entry point for standalone reactive callers. */
    public Mono<DeliveryResponse> getDeliveryByOrderId(Long orderId) {
        return verifiedToken().flatMap(token -> getDeliveryByOrderId(orderId, token));
    }

    /** Explicit token entry point used by API composition across AOP boundaries. */
    public Mono<DeliveryResponse> getDeliveryByOrderId(Long orderId, String verifiedToken) {
        return webClient.get()
                .uri("/deliveries/by-order/{orderId}", orderId)
                .header(HttpHeaders.AUTHORIZATION, bearer(verifiedToken))
                .retrieve()
                .bodyToMono(DeliveryResponse.class)
                .onErrorResume(WebClientResponseException.NotFound.class, error -> Mono.empty());
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

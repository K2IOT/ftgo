package net.ftgo.gateway.client;

import net.ftgo.gateway.dto.TicketResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Client for calling Kitchen Service.
 *
 * B6 FIX: Forwards the Authorization header from the current security context
 * to the downstream service so JWT-protected endpoints accept the call.
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
     * Propagates the caller's JWT to the downstream service.
     *
     * @param orderId the order ID
     * @return Mono of TicketResponse, empty if not found
     */
    public Mono<TicketResponse> getTicketByOrderId(Long orderId) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> ((JwtAuthenticationToken) auth).getToken().getTokenValue())
                .flatMap(token -> webClient.get()
                        .uri("/tickets/by-order/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .bodyToMono(TicketResponse.class))
                .switchIfEmpty(webClient.get()
                        .uri("/tickets/by-order/{orderId}", orderId)
                        .retrieve()
                        .bodyToMono(TicketResponse.class))
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty());
    }
}

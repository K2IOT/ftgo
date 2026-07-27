package net.ftgo.gateway.client;

import net.ftgo.common.security.FtgoJwtAuthenticationToken;
import net.ftgo.gateway.dto.TicketResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/** Client for calling Kitchen Service with the verified caller JWT. */
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

    public Mono<TicketResponse> getTicketByOrderId(Long orderId) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(authentication -> authentication instanceof FtgoJwtAuthenticationToken)
                .map(authentication -> ((FtgoJwtAuthenticationToken) authentication).tokenValue())
                .switchIfEmpty(Mono.error(new IllegalStateException("Verified FTGO JWT is required")))
                .flatMap(token -> webClient.get()
                        .uri("/tickets/by-order/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .bodyToMono(TicketResponse.class))
                .onErrorResume(WebClientResponseException.NotFound.class, error -> Mono.empty());
    }
}

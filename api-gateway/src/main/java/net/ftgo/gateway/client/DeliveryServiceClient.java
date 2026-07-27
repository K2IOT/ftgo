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

    public Mono<DeliveryResponse> getDeliveryByOrderId(Long orderId) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(authentication -> authentication instanceof FtgoJwtAuthenticationToken)
                .map(authentication -> ((FtgoJwtAuthenticationToken) authentication).tokenValue())
                .switchIfEmpty(Mono.error(new IllegalStateException("Verified FTGO JWT is required")))
                .flatMap(token -> webClient.get()
                        .uri("/deliveries/by-order/{orderId}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .bodyToMono(DeliveryResponse.class))
                .onErrorResume(WebClientResponseException.NotFound.class, error -> Mono.empty());
    }
}

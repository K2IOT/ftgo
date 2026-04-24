package net.ftgo.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Gateway configuration for rate limiting and other cross-cutting concerns.
 */
@Configuration
public class GatewayConfiguration {
    
    /**
     * Key resolver for rate limiting based on authenticated user ID.
     * Falls back to IP address for unauthenticated requests.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .filter(principal -> principal instanceof Authentication)
            .map(principal -> ((Authentication) principal).getName())
            .switchIfEmpty(Mono.justOrEmpty(
                exchange.getRequest()
                    .getRemoteAddress()
            ).map(addr -> addr.getAddress().getHostAddress()));
    }
    
    /**
     * WebClient builder for service-to-service communication.
     * Used by service clients for API composition.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}

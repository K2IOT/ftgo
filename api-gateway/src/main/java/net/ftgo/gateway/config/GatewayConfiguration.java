package net.ftgo.gateway.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.core.Authentication;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Gateway configuration for rate limiting, WebClient, and other cross-cutting concerns.
 *
 * B5 FIX: Configures WebClient with connect/read timeouts and connection pool limits.
 * M10 PARTIAL: Sets deny-empty-key=false on routes to allow requests through when
 * Redis is unavailable (key resolver returns empty).
 */
@Configuration
public class GatewayConfiguration {

    @Value("${webclient.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${webclient.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Value("${webclient.max-connections:500}")
    private int maxConnections;

    @Value("${webclient.max-idle-time-ms:20000}")
    private int maxIdleTimeMs;

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
     *
     * B5 FIX: Configured with proper timeouts and connection pooling to prevent
     * cascading failures when downstream services are slow or hung.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        // Connection pool configuration
        ConnectionProvider connectionProvider = ConnectionProvider.builder("gateway-pool")
                .maxConnections(maxConnections)
                .maxIdleTime(Duration.ofMillis(maxIdleTimeMs))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireTimeout(Duration.ofSeconds(5))
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        // HTTP client with timeouts
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}

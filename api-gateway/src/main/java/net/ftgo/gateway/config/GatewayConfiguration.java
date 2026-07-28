package net.ftgo.gateway.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import net.ftgo.gateway.security.ForwardedHeaderPolicy;
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

/** Gateway configuration for rate limiting and bounded downstream clients. */
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
     * Uses the verified subject for authenticated callers and a sanitized
     * direct/forwarded client address for anonymous traffic.
     */
    @Bean
    public KeyResolver userKeyResolver(ForwardedHeaderPolicy forwardedHeaderPolicy) {
        return exchange -> exchange.getPrincipal()
            .filter(Authentication.class::isInstance)
            .cast(Authentication.class)
            .filter(Authentication::isAuthenticated)
            .map(Authentication::getName)
            .filter(name -> name != null && !name.isBlank())
            .switchIfEmpty(Mono.fromSupplier(() ->
                forwardedHeaderPolicy.resolveClientAddress(exchange)));
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("gateway-pool")
                .maxConnections(maxConnections)
                .maxIdleTime(Duration.ofMillis(maxIdleTimeMs))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireTimeout(Duration.ofSeconds(5))
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}

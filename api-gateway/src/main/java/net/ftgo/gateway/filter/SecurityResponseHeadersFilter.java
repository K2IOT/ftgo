package net.ftgo.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Adds response hardening headers and removes spoofable client identity headers.
 * Downstream services derive identity only from the independently validated bearer token.
 */
@Component
public final class SecurityResponseHeadersFilter implements GlobalFilter, Ordered {

    private static final Set<String> CLIENT_IDENTITY_HEADERS = Set.of(
        "X-User-Id",
        "X-User-Roles"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
        responseHeaders.set("X-Content-Type-Options", "nosniff");
        responseHeaders.set("X-Frame-Options", "DENY");
        responseHeaders.set("X-XSS-Protection", "1; mode=block");
        responseHeaders.set("Referrer-Policy", "strict-origin-when-cross-origin");
        responseHeaders.set("Cache-Control", "no-store");

        ServerWebExchange sanitized = exchange.mutate()
            .request(request -> request.headers(headers ->
                CLIENT_IDENTITY_HEADERS.forEach(headers::remove)))
            .build();
        return chain.filter(sanitized);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}

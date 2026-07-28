package net.ftgo.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Exposes the public edge API under /api/v1 without duplicating Gateway routes.
 *
 * <p>The filter runs before Spring Security and route lookup, strips the version
 * prefix for known public resources, and leaves internal, actuator, admin, and
 * unknown namespaces untouched. Legacy routes remain available for one
 * compatibility window and advertise their versioned successor.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class ApiVersioningFilter implements WebFilter {

    static final String VERSION_PREFIX = "/api/v1";
    static final String VERSION_HEADER = "X-API-Version";
    static final String DEPRECATION_HEADER = "Deprecation";

    private static final List<String> PUBLIC_PREFIXES = List.of(
        "/orders",
        "/consumers",
        "/restaurants",
        "/tickets",
        "/deliveries",
        "/order-history",
        "/order-details"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();

        if (path.startsWith(VERSION_PREFIX + "/")) {
            String rewrittenPath = path.substring(VERSION_PREFIX.length());
            if (!isPublicPath(rewrittenPath)) {
                return chain.filter(exchange);
            }

            exchange.getResponse().getHeaders().set(VERSION_HEADER, "1");
            ServerWebExchange versioned = exchange.mutate()
                .request(exchange.getRequest().mutate().path(rewrittenPath).build())
                .build();
            return chain.filter(versioned);
        }

        if (isPublicPath(path)) {
            exchange.getResponse().beforeCommit(() -> {
                HttpHeaders headers = exchange.getResponse().getHeaders();
                headers.set(DEPRECATION_HEADER, "true");
                headers.set(
                    HttpHeaders.LINK,
                    "<" + VERSION_PREFIX + path + ">; rel=\"successor-version\""
                );
                return Mono.empty();
            });
        }

        return chain.filter(exchange);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(prefix ->
            path.equals(prefix) || path.startsWith(prefix + "/")
        );
    }
}

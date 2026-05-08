package net.ftgo.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * M4: Security headers and user-context header injection filter.
 *
 * For every request routed to downstream services, this filter:
 * 1. Adds standard security headers to the response
 * 2. Extracts user identity from the JWT and injects X-User-Id and X-User-Roles
 *    headers into the downstream request
 * 3. Strips any client-supplied X-User-Id/X-User-Roles to prevent spoofing
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(SecurityHeadersFilter.class);

    private static final String X_USER_ID = "X-User-Id";
    private static final String X_USER_ROLES = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Add security response headers
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add("X-Content-Type-Options", "nosniff");
        response.getHeaders().add("X-Frame-Options", "DENY");
        response.getHeaders().add("X-XSS-Protection", "1; mode=block");
        response.getHeaders().add("Referrer-Policy", "strict-origin-when-cross-origin");
        response.getHeaders().add("Cache-Control", "no-store");

        // Inject user context headers from JWT into downstream requests
        return exchange.getPrincipal()
                .filter(principal -> principal instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> {
                    String userId = jwtAuth.getName();
                    String roles = jwtAuth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.joining(","));

                    // Strip any client-supplied user context headers (prevent spoofing)
                    // and inject the real ones from the verified JWT
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .headers(headers -> {
                                headers.remove(X_USER_ID);
                                headers.remove(X_USER_ROLES);
                            })
                            .header(X_USER_ID, userId)
                            .header(X_USER_ROLES, roles)
                            .build();

                    logger.debug("Injected user context: userId={}, roles={}", userId, roles);

                    return exchange.mutate().request(mutatedRequest).build();
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        // Run after authentication but before routing
        return -1;
    }
}

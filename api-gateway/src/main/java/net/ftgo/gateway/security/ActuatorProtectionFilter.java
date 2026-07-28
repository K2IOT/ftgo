package net.ftgo.gateway.security;

import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Enforces the FTGO actuator boundary before management endpoint dispatch.
 *
 * <p>Spring Boot 3.2 may install a management security chain that permits the
 * aggregate health endpoint before the application's WebFlux chain. This
 * filter provides a deterministic outer boundary while reusing the same JWT
 * decoder, principal converter, and ROLE_ADMIN policy as the gateway chain.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class ActuatorProtectionFilter implements WebFilter {

    private static final String ACTUATOR_PREFIX = "/actuator/";
    private static final String LIVENESS = "/actuator/health/liveness";
    private static final String READINESS = "/actuator/health/readiness";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final ReactiveJwtDecoder jwtDecoder;
    private final FtgoJwtAuthenticationConverter authenticationConverter =
        new FtgoJwtAuthenticationConverter("");

    public ActuatorProtectionFilter(ReactiveJwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.startsWith(ACTUATOR_PREFIX) || LIVENESS.equals(path) || READINESS.equals(path)) {
            return chain.filter(exchange);
        }

        String token = bearerToken(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            return unauthorized(exchange);
        }

        Mono<AbstractAuthenticationToken> authentication = jwtDecoder.decode(token)
            .map(authenticationConverter::convert)
            .onErrorResume(error -> Mono.empty());

        return authentication
            .flatMap(authenticated -> {
                boolean admin = authenticated.getAuthorities().stream()
                    .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
                if (!admin) {
                    return forbidden(exchange);
                }
                return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authenticated));
            })
            .switchIfEmpty(unauthorized(exchange));
    }

    private String bearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(
            HttpHeaders.WWW_AUTHENTICATE,
            "Bearer realm=\"ftgo\", error=\"unauthorized\""
        );
        return write(exchange, """
            {"error":"unauthorized","message":"Authentication required","status":401}""");
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return write(exchange, """
            {"error":"forbidden","message":"Access denied","status":403}""");
    }

    private Mono<Void> write(ServerWebExchange exchange, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(
            exchange.getResponse().bufferFactory().wrap(bytes)
        ));
    }
}

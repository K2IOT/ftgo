package net.ftgo.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Web filter for explicit JWT validation.
 * Validates JWT signature and expiration time.
 * Returns 401 Unauthorized for invalid or expired tokens.
 */
@Component
public class JwtValidationFilter implements WebFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtValidationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    
    private final ReactiveJwtDecoder jwtDecoder;
    
    public JwtValidationFilter(ReactiveJwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // Skip validation for public endpoints
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }
        
        String authHeader = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
        
        // If no Authorization header, let Spring Security handle it
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return chain.filter(exchange);
        }
        
        String token = authHeader.substring(BEARER_PREFIX.length());
        
        return jwtDecoder.decode(token)
            .flatMap(jwt -> {
                // Explicit expiration check
                Instant expiresAt = jwt.getExpiresAt();
                Instant now = Instant.now();
                
                if (expiresAt != null && now.isAfter(expiresAt)) {
                    logger.warn("JWT token expired at {}, current time is {}", expiresAt, now);
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    exchange.getResponse().getHeaders().add("WWW-Authenticate", 
                        "Bearer error=\"invalid_token\", error_description=\"The access token expired\"");
                    return exchange.getResponse().setComplete();
                }
                
                // Token is valid, continue with filter chain
                return chain.filter(exchange);
            })
            .onErrorResume(JwtException.class, e -> {
                // Invalid signature or malformed token
                logger.error("JWT validation failed: {}", e.getMessage());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                exchange.getResponse().getHeaders().add("WWW-Authenticate", 
                    "Bearer error=\"invalid_token\", error_description=\"" + e.getMessage() + "\"");
                return exchange.getResponse().setComplete();
            });
    }
    
    /**
     * Check if the endpoint is public and doesn't require authentication.
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/actuator/health") ||
               path.startsWith("/actuator/info") ||
               path.startsWith("/fallback/") ||
               path.equals("/consumers") && !path.contains("/");
    }
}

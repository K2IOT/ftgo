package net.ftgo.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import reactor.core.publisher.Mono;

/**
 * Security configuration for API Gateway.
 * Implements JWT authentication and role-based authorization.
 *
 * Updated to include:
 * - Accounting service endpoint authorization (B2)
 * - JSON error responses for 401/403 instead of Whitelabel
 * - Proper entry point and access denied handlers
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges
                // Public endpoints
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                .pathMatchers("/fallback/**").permitAll()

                // Order endpoints - ROLE_CONSUMER can create, view, cancel, revise orders
                .pathMatchers(HttpMethod.POST, "/orders").hasRole("CONSUMER")
                .pathMatchers(HttpMethod.GET, "/orders/**").hasAnyRole("CONSUMER", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/orders/*/cancel").hasRole("CONSUMER")
                .pathMatchers(HttpMethod.POST, "/orders/*/revise").hasRole("CONSUMER")

                // Consumer endpoints - ROLE_CONSUMER can manage their profile
                .pathMatchers(HttpMethod.POST, "/consumers").permitAll() // Registration
                .pathMatchers(HttpMethod.GET, "/consumers/**").hasAnyRole("CONSUMER", "ADMIN")
                .pathMatchers(HttpMethod.PUT, "/consumers/**").hasRole("CONSUMER")

                // Restaurant endpoints - ROLE_RESTAURANT can manage restaurants and menus
                .pathMatchers(HttpMethod.POST, "/restaurants").hasRole("RESTAURANT")
                .pathMatchers(HttpMethod.GET, "/restaurants/**").permitAll() // Public browsing
                .pathMatchers(HttpMethod.PUT, "/restaurants/**").hasRole("RESTAURANT")
                .pathMatchers(HttpMethod.DELETE, "/restaurants/**").hasRole("RESTAURANT")
                .pathMatchers("/restaurants/*/menu-items/**").hasRole("RESTAURANT")

                // Kitchen/Ticket endpoints - ROLE_RESTAURANT can view and manage tickets
                .pathMatchers(HttpMethod.GET, "/tickets/**").hasAnyRole("RESTAURANT", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/tickets/*/accept").hasRole("RESTAURANT")
                .pathMatchers(HttpMethod.POST, "/tickets/*/preparing").hasRole("RESTAURANT")
                .pathMatchers(HttpMethod.POST, "/tickets/*/ready").hasRole("RESTAURANT")

                // Accounting endpoints - ROLE_ADMIN only (B2)
                .pathMatchers("/accounts/**").hasRole("ADMIN")

                // Delivery endpoints - ROLE_COURIER can manage deliveries
                .pathMatchers(HttpMethod.GET, "/deliveries/**").hasAnyRole("COURIER", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/deliveries/*/assign").hasRole("COURIER")
                .pathMatchers(HttpMethod.POST, "/deliveries/*/pickup").hasRole("COURIER")
                .pathMatchers(HttpMethod.POST, "/deliveries/*/deliver").hasRole("COURIER")

                // Order History endpoints - ROLE_CONSUMER can view their order history
                .pathMatchers(HttpMethod.GET, "/order-history/**").hasAnyRole("CONSUMER", "ADMIN")

                // Order Details (API composition) - authenticated users
                .pathMatchers(HttpMethod.GET, "/order-details/**").hasAnyRole("CONSUMER", "ADMIN")

                // Admin endpoints
                .pathMatchers("/actuator/**").hasRole("ADMIN")

                // All other requests require authentication
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthenticationConverter()))
                .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                .accessDeniedHandler(jsonAccessDeniedHandler())
            )
            .build();
    }

    /**
     * Returns a structured JSON 401 response instead of Whitelabel error page.
     */
    private ServerAuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (exchange, ex) -> {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().add("Content-Type", "application/json");
            exchange.getResponse().getHeaders().add("WWW-Authenticate",
                    "Bearer realm=\"ftgo\", error=\"unauthorized\"");
            String body = """
                    {"error":"unauthorized","message":"Authentication required","status":401}""";
            var buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
            return exchange.getResponse().writeWith(Mono.just(buffer));
        };
    }

    /**
     * Returns a structured JSON 403 response instead of Whitelabel error page.
     */
    private ServerAccessDeniedHandler jsonAccessDeniedHandler() {
        return (exchange, denied) -> {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            exchange.getResponse().getHeaders().add("Content-Type", "application/json");
            String body = """
                    {"error":"forbidden","message":"Access denied","status":403}""";
            var buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
            return exchange.getResponse().writeWith(Mono.just(buffer));
        };
    }
}

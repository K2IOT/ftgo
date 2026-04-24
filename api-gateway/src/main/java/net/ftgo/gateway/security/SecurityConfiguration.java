package net.ftgo.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for API Gateway.
 * Implements JWT authentication and role-based authorization.
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
                
                // Delivery endpoints - ROLE_COURIER can manage deliveries
                .pathMatchers(HttpMethod.GET, "/deliveries/**").hasAnyRole("COURIER", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/deliveries/*/assign").hasRole("COURIER")
                .pathMatchers(HttpMethod.POST, "/deliveries/*/pickup").hasRole("COURIER")
                .pathMatchers(HttpMethod.POST, "/deliveries/*/deliver").hasRole("COURIER")
                
                // Order History endpoints - ROLE_CONSUMER can view their order history
                .pathMatchers(HttpMethod.GET, "/order-history/**").hasAnyRole("CONSUMER", "ADMIN")
                
                // Admin endpoints
                .pathMatchers("/actuator/**").hasRole("ADMIN")
                
                // All other requests require authentication
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthenticationConverter()))
            )
            .build();
    }
}

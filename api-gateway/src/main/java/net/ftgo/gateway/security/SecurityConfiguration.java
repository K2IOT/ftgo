package net.ftgo.gateway.security;

import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.common.security.FtgoReactiveJwtDecoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Bean
    @Order(0)
    public SecurityWebFilterChain actuatorSecurityWebFilterChain(ServerHttpSecurity http) {
        FtgoJwtAuthenticationConverter converter = new FtgoJwtAuthenticationConverter("");
        return configureResourceServer(
            http.securityMatcher(ServerWebExchangeMatchers.pathMatchers("/actuator/**")),
            converter
        )
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(
                    "/actuator/health/liveness",
                    "/actuator/health/readiness"
                ).permitAll()
                .pathMatchers("/actuator/**").hasRole("ADMIN")
                .anyExchange().denyAll()
            )
            .build();
    }

    @Bean
    @Order(1)
    public SecurityWebFilterChain securityWebFilterChain(
        ServerHttpSecurity http,
        @Value("${ftgo.security.public-audience:ftgo-api}") String publicAudience
    ) {
        FtgoJwtAuthenticationConverter converter = new FtgoJwtAuthenticationConverter("");
        return configureResourceServer(http, converter)
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/fallback/**").permitAll()

                .pathMatchers(HttpMethod.POST, "/orders")
                    .access(publicApiAccess(publicAudience, "CONSUMER"))
                .pathMatchers(HttpMethod.GET, "/orders/**")
                    .access(publicApiAccess(publicAudience, "CONSUMER", "ADMIN"))
                .pathMatchers(HttpMethod.POST, "/orders/*/cancel")
                    .access(publicApiAccess(publicAudience, "CONSUMER"))
                .pathMatchers(HttpMethod.POST, "/orders/*/revise")
                    .access(publicApiAccess(publicAudience, "CONSUMER"))

                .pathMatchers("/admin/consumers/**")
                    .access(publicApiAccess(publicAudience, "ADMIN"))
                .pathMatchers(HttpMethod.POST, "/consumers")
                    .access(publicApiAccess(publicAudience, "CONSUMER", "ADMIN"))
                .pathMatchers(HttpMethod.GET, "/consumers/**")
                    .access(publicApiAccess(publicAudience, "CONSUMER", "ADMIN"))
                .pathMatchers(HttpMethod.PUT, "/consumers/**")
                    .access(publicApiAccess(publicAudience, "CONSUMER", "ADMIN"))

                .pathMatchers(HttpMethod.POST, "/restaurants")
                    .access(publicApiAccess(publicAudience, "ADMIN"))
                .pathMatchers(HttpMethod.GET, "/restaurants/**").permitAll()
                .pathMatchers(HttpMethod.PUT, "/restaurants/**")
                    .access(publicApiAccess(publicAudience, "RESTAURANT", "ADMIN"))
                .pathMatchers(HttpMethod.DELETE, "/restaurants/**")
                    .access(publicApiAccess(publicAudience, "RESTAURANT", "ADMIN"))
                .pathMatchers("/restaurants/*/menu-items/**")
                    .access(publicApiAccess(publicAudience, "RESTAURANT", "ADMIN"))

                .pathMatchers(HttpMethod.GET, "/tickets/**")
                    .access(publicApiAccess(publicAudience, "RESTAURANT", "ADMIN"))
                .pathMatchers(HttpMethod.POST, "/tickets/*/accept")
                    .access(publicApiAccess(publicAudience, "RESTAURANT", "ADMIN"))
                .pathMatchers(HttpMethod.POST, "/tickets/*/preparing")
                    .access(publicApiAccess(publicAudience, "RESTAURANT", "ADMIN"))
                .pathMatchers(HttpMethod.POST, "/tickets/*/ready")
                    .access(publicApiAccess(publicAudience, "RESTAURANT", "ADMIN"))

                .pathMatchers("/api/admin/payment-settlement/**", "/accounts/**")
                    .access(publicApiAccess(publicAudience, "ADMIN"))

                .pathMatchers(HttpMethod.GET, "/deliveries/**")
                    .access(publicApiAccess(publicAudience, "COURIER", "ADMIN"))
                .pathMatchers(HttpMethod.POST, "/deliveries/*/assign")
                    .access(publicApiAccess(publicAudience, "COURIER", "ADMIN"))
                .pathMatchers(HttpMethod.POST, "/deliveries/*/pickup")
                    .access(publicApiAccess(publicAudience, "COURIER", "ADMIN"))
                .pathMatchers(HttpMethod.POST, "/deliveries/*/deliver")
                    .access(publicApiAccess(publicAudience, "COURIER", "ADMIN"))

                .pathMatchers(HttpMethod.GET, "/order-history/**")
                    .access(publicApiAccess(publicAudience, "CONSUMER", "ADMIN"))
                .pathMatchers(HttpMethod.GET, "/order-details/**")
                    .access(publicApiAccess(publicAudience, "CONSUMER", "ADMIN"))

                .anyExchange().denyAll()
            )
            .build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(
        @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
        @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
        @Value("${ftgo.security.public-audience:ftgo-api}") String publicAudience,
        @Value("${ftgo.security.internal-audience:ftgo-internal}") String internalAudience
    ) {
        return FtgoReactiveJwtDecoders.create(
            issuerUri,
            jwkSetUri,
            List.of(publicAudience, internalAudience)
        );
    }

    private ReactiveAuthorizationManager<AuthorizationContext> publicApiAccess(
        String publicAudience,
        String... roles
    ) {
        String audienceAuthority = "AUD_" + publicAudience.trim();
        Set<String> roleAuthorities = Arrays.stream(roles)
            .map(role -> "ROLE_" + role.trim().toUpperCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
        return (authentication, context) -> authentication
            .map(actor -> new AuthorizationDecision(
                hasAuthority(actor, audienceAuthority)
                    && roleAuthorities.stream().anyMatch(role -> hasAuthority(actor, role))
            ))
            .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private boolean hasAuthority(Authentication authentication, String authorityName) {
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals(authorityName));
    }

    private ServerHttpSecurity configureResourceServer(
        ServerHttpSecurity http,
        FtgoJwtAuthenticationConverter converter
    ) {
        return http.oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> Mono.just(converter.convert(token))))
            .authenticationEntryPoint(jsonAuthenticationEntryPoint())
            .accessDeniedHandler(jsonAccessDeniedHandler())
        );
    }

    private ServerAuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (exchange, error) -> {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().set("Content-Type", "application/json");
            exchange.getResponse().getHeaders().set(
                "WWW-Authenticate",
                "Bearer realm=\"ftgo\", error=\"unauthorized\""
            );
            byte[] body = """
                {"error":"unauthorized","message":"Authentication required","status":401}"""
                .getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body)
            ));
        };
    }

    private ServerAccessDeniedHandler jsonAccessDeniedHandler() {
        return (exchange, denied) -> {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            exchange.getResponse().getHeaders().set("Content-Type", "application/json");
            byte[] body = """
                {"error":"forbidden","message":"Access denied","status":403}"""
                .getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body)
            ));
        };
    }
}

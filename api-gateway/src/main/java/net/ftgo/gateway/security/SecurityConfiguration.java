package net.ftgo.gateway.security;

import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.common.security.FtgoReactiveJwtDecoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        FtgoJwtAuthenticationConverter converter = new FtgoJwtAuthenticationConverter("");
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(
                    "/actuator/health/liveness",
                    "/actuator/health/readiness"
                ).permitAll()
                .pathMatchers("/fallback/**").permitAll()

                .pathMatchers(HttpMethod.POST, "/orders").hasRole("CONSUMER")
                .pathMatchers(HttpMethod.GET, "/orders/**").hasAnyRole("CONSUMER", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/orders/*/cancel").hasRole("CONSUMER")
                .pathMatchers(HttpMethod.POST, "/orders/*/revise").hasRole("CONSUMER")

                .pathMatchers(HttpMethod.POST, "/consumers").hasAnyRole("CONSUMER", "ADMIN")
                .pathMatchers(HttpMethod.GET, "/consumers/**").hasAnyRole("CONSUMER", "ADMIN")
                .pathMatchers(HttpMethod.PUT, "/consumers/**").hasAnyRole("CONSUMER", "ADMIN")

                .pathMatchers(HttpMethod.POST, "/restaurants").hasRole("ADMIN")
                .pathMatchers(HttpMethod.GET, "/restaurants/**").permitAll()
                .pathMatchers(HttpMethod.PUT, "/restaurants/**").hasAnyRole("RESTAURANT", "ADMIN")
                .pathMatchers(HttpMethod.DELETE, "/restaurants/**").hasAnyRole("RESTAURANT", "ADMIN")
                .pathMatchers("/restaurants/*/menu-items/**").hasAnyRole("RESTAURANT", "ADMIN")

                .pathMatchers(HttpMethod.GET, "/tickets/**").hasAnyRole("RESTAURANT", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/tickets/*/accept").hasAnyRole("RESTAURANT", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/tickets/*/preparing").hasAnyRole("RESTAURANT", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/tickets/*/ready").hasAnyRole("RESTAURANT", "ADMIN")

                .pathMatchers("/api/admin/payment-settlement/**").hasRole("ADMIN")
                .pathMatchers("/accounts/**").hasRole("ADMIN")

                .pathMatchers(HttpMethod.GET, "/deliveries/**").hasAnyRole("COURIER", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/deliveries/*/assign").hasAnyRole("COURIER", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/deliveries/*/pickup").hasAnyRole("COURIER", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/deliveries/*/deliver").hasAnyRole("COURIER", "ADMIN")

                .pathMatchers(HttpMethod.GET, "/order-history/**").hasAnyRole("CONSUMER", "ADMIN")
                .pathMatchers(HttpMethod.GET, "/order-details/**").hasAnyRole("CONSUMER", "ADMIN")

                .pathMatchers("/actuator/**").hasRole("ADMIN")
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> Mono.just(converter.convert(token))))
                .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                .accessDeniedHandler(jsonAccessDeniedHandler())
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

    private ServerAuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (exchange, ex) -> {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().add("Content-Type", "application/json");
            exchange.getResponse().getHeaders().add(
                "WWW-Authenticate",
                "Bearer realm=\"ftgo\", error=\"unauthorized\""
            );
            String body = """
                {"error":"unauthorized","message":"Authentication required","status":401}""";
            var buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
            return exchange.getResponse().writeWith(Mono.just(buffer));
        };
    }

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

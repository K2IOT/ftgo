package net.ftgo.gateway;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import static org.mockito.Mockito.mock;

/**
 * Test-only security infrastructure for gateway composition and resilience tests.
 * Production SecurityConfiguration remains active, but this higher-priority chain
 * permits requests so these tests exercise routing behavior rather than JWT parsing.
 */
@TestConfiguration
public class GatewayTestSecurityConfiguration {

    @Bean
    @Primary
    ReactiveJwtDecoder testReactiveJwtDecoder() {
        return mock(ReactiveJwtDecoder.class);
    }

    @Bean
    @Order(-100)
    SecurityWebFilterChain testPermitAllSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.anyExchange())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }
}

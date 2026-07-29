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
 * Opt-in test security infrastructure for tests that intentionally bypass
 * production authorization. Tests must import this configuration explicitly.
 */
@TestConfiguration(proxyBeanMethods = false)
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

package net.ftgo.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayHardeningIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    @Test
    void registersScopedActuatorAndApplicationSecurityChains() {
        Map<String, SecurityWebFilterChain> chains =
            applicationContext.getBeansOfType(SecurityWebFilterChain.class);

        assertThat(chains).containsOnlyKeys(
            "actuatorSecurityWebFilterChain",
            "securityWebFilterChain"
        );

        SecurityWebFilterChain actuator = chains.get("actuatorSecurityWebFilterChain");
        SecurityWebFilterChain application = chains.get("securityWebFilterChain");
        assertThat(matches(actuator, "/actuator/health/liveness")).isTrue();
        assertThat(matches(actuator, "/orders/security-probe")).isFalse();
        assertThat(matches(application, "/orders/security-probe")).isTrue();
        assertThat(matches(application, "/security/unknown")).isTrue();
    }

    @Test
    void anonymousLivenessIsAvailableWithoutComponentDetails() {
        webTestClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.components").doesNotExist();
    }

    @Test
    void anonymousFullHealthIsProtected() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void anonymousGatewayRouteIntrospectionIsProtected() {
        webTestClient.get()
            .uri("/actuator/gateway/routes")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void internalAudienceOnlyCannotAccessPublicOrderRoute() {
        when(jwtDecoder.decode("internal-consumer-token")).thenReturn(Mono.just(jwt(
            "internal-consumer-token",
            "consumer-user",
            List.of("CONSUMER"),
            List.of("ftgo-internal")
        )));

        webTestClient.get()
            .uri("/orders/security-probe")
            .header(HttpHeaders.AUTHORIZATION, "Bearer internal-consumer-token")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void authenticatedUnknownRouteIsDenied() {
        when(jwtDecoder.decode("admin-token")).thenReturn(Mono.just(jwt(
            "admin-token",
            "admin-user",
            List.of("ADMIN"),
            List.of("ftgo-api")
        )));

        webTestClient.get()
            .uri("/security/unknown")
            .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
            .exchange()
            .expectStatus().isForbidden();
    }

    private boolean matches(SecurityWebFilterChain chain, String path) {
        return chain.matches(MockServerWebExchange.from(MockServerHttpRequest.get(path).build()))
            .blockOptional()
            .orElse(false);
    }

    private Jwt jwt(
        String tokenValue,
        String subject,
        List<String> roles,
        List<String> audiences
    ) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(tokenValue)
            .header("alg", "RS256")
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(audiences)
            .claim("roles", roles)
            .build();
    }
}

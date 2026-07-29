package net.ftgo.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayHardeningIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

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
        webTestClient.mutateWith(mockJwt().authorities(
                new SimpleGrantedAuthority("ROLE_CONSUMER"),
                new SimpleGrantedAuthority("AUD_ftgo-internal")
            ))
            .get()
            .uri("/orders/security-probe")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void authenticatedUnknownRouteIsDenied() {
        webTestClient.mutateWith(mockJwt().authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("AUD_ftgo-api")
            ))
            .get()
            .uri("/security/unknown")
            .exchange()
            .expectStatus().isForbidden();
    }
}

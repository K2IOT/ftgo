package net.ftgo.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

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
}

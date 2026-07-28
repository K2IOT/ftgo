package net.ftgo.gateway.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Performance tests for OrderDetailsController.
 * Verifies that parallel service calls reduce latency compared to sequential calls.
 */
@SpringBootTest
class OrderDetailsPerformanceTest {

    @Autowired
    private ApplicationContext applicationContext;

    private WebTestClient authenticatedClient;
    private static WireMockServer orderServiceMock;
    private static WireMockServer kitchenServiceMock;
    private static WireMockServer deliveryServiceMock;

    @BeforeEach
    void setUp() {
        orderServiceMock = new WireMockServer(8091);
        kitchenServiceMock = new WireMockServer(8092);
        deliveryServiceMock = new WireMockServer(8093);

        orderServiceMock.start();
        kitchenServiceMock.start();
        deliveryServiceMock.start();

        WireMock.configureFor("localhost", 8091);
        authenticatedClient = WebTestClient.bindToApplicationContext(applicationContext)
            .apply(springSecurity())
            .configureClient()
            .responseTimeout(Duration.ofSeconds(15))
            .build()
            .mutateWith(mockAuthentication(consumerAuthentication()));
    }

    @AfterEach
    void tearDown() {
        orderServiceMock.stop();
        kitchenServiceMock.stop();
        deliveryServiceMock.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("services.order-service.url", () -> "http://localhost:8091");
        registry.add("services.kitchen-service.url", () -> "http://localhost:8092");
        registry.add("services.delivery-service.url", () -> "http://localhost:8093");
        registry.add(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> "https://identity.example/realms/ftgo"
        );
        registry.add(
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "https://identity.example/realms/ftgo/protocol/openid-connect/certs"
        );
        registry.add("spring.redis.host", () -> "localhost");
    }

    @Test
    void testParallelCallsReduceLatency() {
        Long orderId = 12345L;
        int serviceLatencyMs = 200;

        orderServiceMock.stubFor(get(urlEqualTo("/orders/" + orderId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withFixedDelay(serviceLatencyMs)
                .withBody("""
                    {
                        "id": 12345,
                        "state": "APPROVED",
                        "consumerId": 100,
                        "restaurantId": 200,
                        "orderTotal": 45.99,
                        "deliveryAddress": "123 Main St",
                        "createdAt": "2025-01-15T10:30:00",
                        "lineItems": []
                    }
                    """)));

        kitchenServiceMock.stubFor(get(urlEqualTo("/tickets/by-order/" + orderId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withFixedDelay(serviceLatencyMs)
                .withBody("""
                    {
                        "id": 5001,
                        "orderId": 12345,
                        "state": "ACCEPTED",
                        "acceptedAt": "2025-01-15T10:35:00",
                        "readyBy": "2025-01-15T11:00:00"
                    }
                    """)));

        deliveryServiceMock.stubFor(get(urlEqualTo("/deliveries/by-order/" + orderId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withFixedDelay(serviceLatencyMs)
                .withBody("""
                    {
                        "id": 7001,
                        "orderId": 12345,
                        "status": "ASSIGNED",
                        "courierId": 300,
                        "scheduledTime": "2025-01-15T11:30:00"
                    }
                    """)));

        long startTime = System.currentTimeMillis();

        authenticatedClient.get()
            .uri("/order-details/{orderId}", orderId)
            .exchange()
            .expectStatus().isOk();

        long totalLatency = System.currentTimeMillis() - startTime;
        int maxExpectedLatency = serviceLatencyMs + 300;

        assertThat(totalLatency)
            .as("Parallel calls should complete in ~%dms, not ~%dms (sequential)",
                serviceLatencyMs, serviceLatencyMs * 3)
            .isLessThan(maxExpectedLatency);

        orderServiceMock.verify(getRequestedFor(urlEqualTo("/orders/" + orderId)));
        kitchenServiceMock.verify(getRequestedFor(urlEqualTo("/tickets/by-order/" + orderId)));
        deliveryServiceMock.verify(getRequestedFor(urlEqualTo("/deliveries/by-order/" + orderId)));
    }

    @Test
    void testResponseTimeUnderLoad() {
        Long orderId = 12346L;
        int concurrentRequests = 10;

        orderServiceMock.stubFor(get(urlEqualTo("/orders/" + orderId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withFixedDelay(50)
                .withBody("""
                    {
                        "id": 12346,
                        "state": "APPROVED",
                        "consumerId": 100,
                        "restaurantId": 200,
                        "orderTotal": 45.99,
                        "deliveryAddress": "123 Main St",
                        "createdAt": "2025-01-15T10:30:00",
                        "lineItems": []
                    }
                    """)));

        kitchenServiceMock.stubFor(get(urlEqualTo("/tickets/by-order/" + orderId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withFixedDelay(50)
                .withBody("""
                    {
                        "id": 5001,
                        "orderId": 12346,
                        "state": "ACCEPTED",
                        "acceptedAt": "2025-01-15T10:35:00",
                        "readyBy": "2025-01-15T11:00:00"
                    }
                    """)));

        deliveryServiceMock.stubFor(get(urlEqualTo("/deliveries/by-order/" + orderId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withFixedDelay(50)
                .withBody("""
                    {
                        "id": 7001,
                        "orderId": 12346,
                        "status": "ASSIGNED",
                        "courierId": 300,
                        "scheduledTime": "2025-01-15T11:30:00"
                    }
                    """)));

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentRequests; i++) {
            authenticatedClient.get()
                .uri("/order-details/{orderId}", orderId)
                .exchange()
                .expectStatus().isOk();
        }

        long totalTime = System.currentTimeMillis() - startTime;
        long avgLatency = totalTime / concurrentRequests;

        assertThat(avgLatency)
            .as("Average latency should be less than 500ms")
            .isLessThan(500);
    }

    private AbstractAuthenticationToken consumerAuthentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("performance-consumer-token")
            .header("alg", "RS256")
            .issuer("https://identity.example/realms/ftgo")
            .subject("consumer-100")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("roles", List.of("CONSUMER"))
            .claim("consumer_id", 100L)
            .build();
        return new FtgoJwtAuthenticationConverter("").convert(jwt);
    }
}

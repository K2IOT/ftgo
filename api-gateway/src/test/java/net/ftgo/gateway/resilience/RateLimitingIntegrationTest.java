package net.ftgo.gateway.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for rate limiting in API Gateway.
 * Tests that rate limiting returns 429 after exceeding request limit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class RateLimitingIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private static WireMockServer wireMockServer;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8081);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8081);

        // Configure WireMock to return success
        stubFor(get(urlPathMatching("/orders/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"orderId\": \"123\", \"status\": \"APPROVED\"}")));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.routes[0].uri", () -> "http://localhost:8081");
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }

    /**
     * Test that rate limiting returns 429 after 100 requests per minute.
     * Requirements: 10.5, 10.7
     */
    @Test
    void testRateLimitingReturns429After100Requests() {
        // Make 100 requests (within rate limit)
        for (int i = 0; i < 100; i++) {
            webTestClient.get()
                .uri("/orders/123")
                .exchange()
                .expectStatus().isOk();
        }

        // Wait a small amount to ensure rate limiter processes all requests
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Next request should be rate limited (exceeds 100 requests per minute)
        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Test that rate limiting is per user (based on authentication).
     * Requirements: 10.5
     */
    @Test
    void testRateLimitingPerUser() {
        // Make 100 requests as user1
        for (int i = 0; i < 100; i++) {
            webTestClient.get()
                .uri("/orders/123")
                .header("X-User-Id", "user1")
                .exchange()
                .expectStatus().isOk();
        }

        // Wait for rate limiter to process
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Next request as user1 should be rate limited
        webTestClient.get()
            .uri("/orders/123")
            .header("X-User-Id", "user1")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Request as user2 should succeed (different user)
        webTestClient.get()
            .uri("/orders/123")
            .header("X-User-Id", "user2")
            .exchange()
            .expectStatus().isOk();
    }

    /**
     * Test that rate limiting resets after time window.
     * Requirements: 10.5
     */
    @Test
    void testRateLimitingResetsAfterTimeWindow() {
        // Make 100 requests (within rate limit)
        for (int i = 0; i < 100; i++) {
            webTestClient.get()
                .uri("/orders/123")
                .exchange()
                .expectStatus().isOk();
        }

        // Wait for rate limiter to process
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Next request should be rate limited
        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Wait for rate limit window to reset (1 minute)
        try {
            Thread.sleep(60000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Request should succeed after window reset
        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isOk();
    }

    /**
     * Test that rate limiting applies to all routes.
     * Requirements: 10.5
     */
    @Test
    void testRateLimitingAppliesToAllRoutes() {
        // Configure WireMock for consumer service
        WireMockServer consumerWireMock = new WireMockServer(8082);
        consumerWireMock.start();
        WireMock.configureFor("localhost", 8082);
        stubFor(get(urlPathMatching("/consumers/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"consumerId\": \"456\", \"name\": \"John Doe\"}")));

        // Make 50 requests to order service
        for (int i = 0; i < 50; i++) {
            webTestClient.get()
                .uri("/orders/123")
                .exchange()
                .expectStatus().isOk();
        }

        // Make 50 requests to consumer service
        for (int i = 0; i < 50; i++) {
            webTestClient.get()
                .uri("/consumers/456")
                .exchange()
                .expectStatus().isOk();
        }

        // Wait for rate limiter to process
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Next request to either service should be rate limited (total 100 requests)
        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        consumerWireMock.stop();
    }

    /**
     * Test burst capacity allows temporary spike above rate limit.
     * Requirements: 10.5
     */
    @Test
    void testBurstCapacityAllowsTemporarySpike() {
        // Make 150 requests rapidly (burst capacity is 200)
        for (int i = 0; i < 150; i++) {
            webTestClient.get()
                .uri("/orders/123")
                .exchange()
                .expectStatus().isOk();
        }

        // Burst capacity should allow these requests
        // But sustained rate should still be limited to 100 per minute
    }
}

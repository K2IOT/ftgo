package net.ftgo.gateway.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;


import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for circuit breaker behavior in API Gateway.
 * Tests circuit breaker opening after consecutive failures and closing after successful requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CircuitBreakerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("orderServiceCircuitBreaker").reset();
        circuitBreakerRegistry.circuitBreaker("consumerServiceCircuitBreaker").reset();

        wireMockServer = new WireMockServer(18081);
        wireMockServer.start();
        WireMock.configureFor("localhost", 18081);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("services.order-service.url", () -> "http://localhost:18081");
        registry.add("services.consumer-service.url", () -> "http://localhost:18082");
        registry.add("spring.redis.host", () -> "localhost");
        registry.add("spring.redis.port", () -> "6379");
        registry.add("resilience4j.circuitbreaker.instances.orderServiceCircuitBreaker.slidingWindowSize", () -> "5");
        registry.add("resilience4j.circuitbreaker.instances.orderServiceCircuitBreaker.minimumNumberOfCalls", () -> "5");
        registry.add("resilience4j.circuitbreaker.instances.orderServiceCircuitBreaker.failureRateThreshold", () -> "100");
        registry.add("resilience4j.circuitbreaker.instances.orderServiceCircuitBreaker.waitDurationInOpenState", () -> "100ms");
        registry.add("resilience4j.circuitbreaker.instances.orderServiceCircuitBreaker.permittedNumberOfCallsInHalfOpenState", () -> "1");
        registry.add("resilience4j.circuitbreaker.instances.orderServiceCircuitBreaker.automaticTransitionFromOpenToHalfOpenEnabled", () -> "true");
    }

    /**
     * Test that circuit breaker opens after 5 consecutive failures.
     * Requirements: 10.7, 18
     */
    @Test
    void testCircuitBreakerOpensAfter5ConsecutiveFailures() {
        // Configure WireMock to return 500 errors
        stubFor(get(urlPathMatching("/orders/.*"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        // Make 5 consecutive requests that should fail
        for (int i = 0; i < 5; i++) {
            webTestClient.get()
                .uri("/orders/123")
                .exchange()
                .expectStatus().is5xxServerError();
        }

        // Wait for circuit breaker to open
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Next request should get fallback response (circuit is open)
        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectBody()
            .jsonPath("$.error").isEqualTo("service_unavailable")
            .jsonPath("$.service").isEqualTo("order-service");

        // Verify that the downstream service was not called (circuit is open)
        verify(exactly(5), getRequestedFor(urlPathMatching("/orders/.*")));
    }

    /**
     * Test that circuit breaker closes after successful test request in half-open state.
     * Requirements: 10.7, 18
     */
    @Test
    void testCircuitBreakerClosesAfterSuccessfulTestRequest() {
        // Configure WireMock to return 500 errors initially
        stubFor(get(urlPathMatching("/orders/.*"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        // Make 5 consecutive requests to open the circuit
        for (int i = 0; i < 5; i++) {
            webTestClient.get()
                .uri("/orders/123")
                .exchange()
                .expectStatus().is5xxServerError();
        }

        // Wait for circuit breaker to open
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify circuit is open (fallback response)
        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // Wait for circuit breaker to transition to half-open (30 seconds in config)
        // For testing, we'll use a shorter wait time and configure the circuit breaker accordingly
        try {
            Thread.sleep(250); // Wait for half-open state
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Configure WireMock to return success
        stubFor(get(urlPathMatching("/orders/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"orderId\": \"123\", \"status\": \"APPROVED\"}")));

        // Make a successful request in half-open state
        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.orderId").isEqualTo("123");

        // Circuit should now be closed, verify subsequent requests succeed
        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isOk();
    }

    /**
     * Test that circuit breaker configuration is correct.
     * Requirements: 18
     */
    @Test
    void testCircuitBreakerConfiguration() {
        // Configure WireMock to return 500 errors
        stubFor(get(urlPathMatching("/orders/.*"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        // Make exactly 4 requests (below threshold)
        for (int i = 0; i < 4; i++) {
            webTestClient.get()
                .uri("/orders/123")
                .exchange()
                .expectStatus().is5xxServerError();
        }

        // Circuit should still be closed (threshold is 5)
        // Configure WireMock to return success
        stubFor(get(urlPathMatching("/orders/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"orderId\": \"123\", \"status\": \"APPROVED\"}")));

        // This request should succeed (circuit is still closed)
        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isOk();
    }

    /**
     * Test fallback response format when circuit breaker is open.
     * Requirements: 10.5
     */
    @Test
    void testFallbackResponseFormat() {
        // Configure WireMock to return 500 errors
        stubFor(get(urlPathMatching("/orders/.*"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        // Open the circuit
        for (int i = 0; i < 5; i++) {
            webTestClient.get()
                .uri("/orders/123")
                .exchange()
                .expectStatus().is5xxServerError();
        }

        // Wait for circuit to open
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify fallback response format
        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.error").isEqualTo("service_unavailable")
            .jsonPath("$.message").exists()
            .jsonPath("$.service").isEqualTo("order-service");
    }

    /**
     * Test circuit breaker for different services.
     * Requirements: 10.7, 18
     */
    @Test
    void testCircuitBreakerPerService() {
        // Configure WireMock for order service to fail
        stubFor(get(urlPathMatching("/orders/.*"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        // Configur WireMock for consumer service to succeed
        WireMockServer consumerWireMock = new WireMockServer(18082);
        consumerWireMock.start();
        WireMock.configureFor("localhost", 18082);
        stubFor(get(urlPathMatching("/consumers/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"consumerId\": \"456\", \"name\": \"John Doe\"}")));

        // Open circuit for order service
        for (int i = 0; i < 5; i++) {
            webTestClient.get()
                .uri("/orders/123")
                .exchange()
                .expectStatus().is5xxServerError();
        }

        // Wait for circuit to open
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Order service circuit should be open
        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // Consumer service circuit should still be closed
        webTestClient.get()
            .uri("/consumers/456")
            .exchange()
            .expectStatus().isOk();

        consumerWireMock.stop();
    }
}

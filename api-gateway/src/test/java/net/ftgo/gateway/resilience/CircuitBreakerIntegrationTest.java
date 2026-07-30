package net.ftgo.gateway.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CircuitBreakerIntegrationTest {

    private static final String ORDER_BREAKER = "orderServiceCircuitBreaker";
    private static final String CONSUMER_BREAKER = "consumerServiceCircuitBreaker";
    private static final WireMockServer orderService = new WireMockServer(18081);

    @LocalServerPort
    private int port;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private WebTestClient webTestClient;

    @BeforeAll
    static void startWireMock() {
        orderService.start();
    }

    @AfterAll
    static void stopWireMock() {
        orderService.stop();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("ftgo.security.permit-all", () -> "true");
        registry.add("ftgo.services.order.url", () -> "http://localhost:18081");
        registry.add("ftgo.services.consumer.url", () -> "http://localhost:18082");
    }

    @BeforeEach
    void setUp() {
        orderService.resetAll();
        circuitBreakerRegistry.circuitBreaker(ORDER_BREAKER).reset();
        circuitBreakerRegistry.circuitBreaker(CONSUMER_BREAKER).reset();
        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Test
    void opensAfterConfiguredFailures() {
        stubOrderFailure();
        triggerOrderFailures(5);
        awaitOrderState(CircuitBreaker.State.OPEN);
        expectOrderFallback();
    }

    @Test
    void closesAfterSuccessfulHalfOpenProbe() {
        stubOrderFailure();
        triggerOrderFailures(5);
        awaitOrderState(CircuitBreaker.State.OPEN);

        orderService.resetAll();
        stubOrderSuccess();
        orderBreaker().transitionToHalfOpenState();
        awaitOrderState(CircuitBreaker.State.HALF_OPEN);

        expectOrderSuccess();
        awaitOrderState(CircuitBreaker.State.CLOSED);
        expectOrderSuccess();
    }

    @Test
    void remainsClosedBelowMinimumNumberOfCalls() {
        stubOrderFailure();
        triggerOrderFailures(4);
        assertEquals(CircuitBreaker.State.CLOSED, orderBreaker().getState());

        orderService.resetAll();
        stubOrderSuccess();
        expectOrderSuccess();
    }

    @Test
    void fallbackResponseHasStableProblemDetailContract() {
        stubOrderFailure();
        triggerOrderFailures(5);
        awaitOrderState(CircuitBreaker.State.OPEN);

        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectHeader().valueMatches("X-Correlation-ID", "[A-Za-z0-9._:-]{8,128}")
            .expectBody()
            .jsonPath("$.type").isEqualTo("https://ftgo.example/problems/service-unavailable")
            .jsonPath("$.title").isEqualTo("Downstream service unavailable")
            .jsonPath("$.status").isEqualTo(503)
            .jsonPath("$.detail").isEqualTo("The order service is temporarily unavailable")
            .jsonPath("$.instance").isEqualTo("/fallback/orders")
            .jsonPath("$.errorCode").isEqualTo("SERVICE_UNAVAILABLE")
            .jsonPath("$.correlationId").value(value ->
                assertTrue(value.toString().matches("[A-Za-z0-9._:-]{8,128}")))
            .jsonPath("$.timestamp").doesNotExist();
    }

    @Test
    void circuitStateIsIsolatedPerService() {
        WireMockServer consumerService = new WireMockServer(18082);
        consumerService.start();
        try {
            consumerService.stubFor(get(urlPathMatching("/consumers/.*"))
                .willReturn(okJson("{\"consumerId\":456,\"name\":\"John Doe\"}")));

            stubOrderFailure();
            triggerOrderFailures(5);
            awaitOrderState(CircuitBreaker.State.OPEN);

            expectOrderFallback();
            webTestClient.get()
                .uri("/consumers/456")
                .exchange()
                .expectStatus().isOk();
            assertEquals(
                CircuitBreaker.State.CLOSED,
                circuitBreakerRegistry.circuitBreaker(CONSUMER_BREAKER).getState()
            );
        } finally {
            consumerService.stop();
        }
    }

    private void stubOrderFailure() {
        orderService.stubFor(get(urlPathMatching("/orders/.*"))
            .willReturn(serverError().withBody("Internal Server Error")));
    }

    private void stubOrderSuccess() {
        orderService.stubFor(get(urlPathMatching("/orders/.*"))
            .willReturn(okJson("{\"orderId\":123,\"state\":\"APPROVED\"}")));
    }

    private void triggerOrderFailures(int calls) {
        for (int index = 0; index < calls; index++) {
            expectOrderFallback();
        }
    }

    private void expectOrderFallback() {
        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private void expectOrderSuccess() {
        webTestClient.get()
            .uri("/orders/123")
            .exchange()
            .expectStatus().isOk();
    }

    private CircuitBreaker orderBreaker() {
        return circuitBreakerRegistry.circuitBreaker(ORDER_BREAKER);
    }

    private void awaitOrderState(CircuitBreaker.State expected) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (orderBreaker().getState() == expected) {
                return;
            }
            Thread.onSpinWait();
        }
        assertEquals(expected, orderBreaker().getState());
    }
}

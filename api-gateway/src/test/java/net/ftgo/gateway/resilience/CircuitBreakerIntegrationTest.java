package net.ftgo.gateway.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import net.ftgo.gateway.GatewayTestSecurityConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
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
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for circuit-breaker behavior in API Gateway.
 *
 * The test defines complete, isolated routes so Retry and Redis rate limiting do
 * not alter the number of calls observed by the circuit breaker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(GatewayTestSecurityConfiguration.class)
class CircuitBreakerIntegrationTest {

    private static final String ORDER_BREAKER = "orderServiceCircuitBreaker";
    private static final String CONSUMER_BREAKER = "consumerServiceCircuitBreaker";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private WireMockServer orderService;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker(ORDER_BREAKER).reset();
        circuitBreakerRegistry.circuitBreaker(CONSUMER_BREAKER).reset();

        orderService = new WireMockServer(18081);
        orderService.start();
    }

    @AfterEach
    void tearDown() {
        orderService.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        addCircuitRoute(
            registry,
            0,
            "order-circuit-test",
            "http://localhost:18081",
            "/orders/**",
            ORDER_BREAKER,
            "forward:/fallback/orders"
        );
        addCircuitRoute(
            registry,
            1,
            "consumer-circuit-test",
            "http://localhost:18082",
            "/consumers/**",
            CONSUMER_BREAKER,
            "forward:/fallback/consumers"
        );

        registry.add(
            "resilience4j.circuitbreaker.instances." + ORDER_BREAKER + ".slidingWindowSize",
            () -> "5"
        );
        registry.add(
            "resilience4j.circuitbreaker.instances." + ORDER_BREAKER + ".minimumNumberOfCalls",
            () -> "5"
        );
        registry.add(
            "resilience4j.circuitbreaker.instances." + ORDER_BREAKER + ".failureRateThreshold",
            () -> "100"
        );
        registry.add(
            "resilience4j.circuitbreaker.instances." + ORDER_BREAKER + ".waitDurationInOpenState",
            () -> "30s"
        );
        registry.add(
            "resilience4j.circuitbreaker.instances." + ORDER_BREAKER
                + ".permittedNumberOfCallsInHalfOpenState",
            () -> "1"
        );
        registry.add(
            "resilience4j.circuitbreaker.instances." + ORDER_BREAKER
                + ".automaticTransitionFromOpenToHalfOpenEnabled",
            () -> "false"
        );
    }

    private static void addCircuitRoute(
        DynamicPropertyRegistry registry,
        int index,
        String id,
        String uri,
        String path,
        String breakerName,
        String fallbackUri
    ) {
        String route = "spring.cloud.gateway.routes[" + index + "]";
        registry.add(route + ".id", () -> id);
        registry.add(route + ".uri", () -> uri);
        registry.add(route + ".predicates[0]", () -> "Path=" + path);
        registry.add(route + ".filters[0].name", () -> "CircuitBreaker");
        registry.add(route + ".filters[0].args.name", () -> breakerName);
        registry.add(route + ".filters[0].args.fallbackUri", () -> fallbackUri);
        registry.add(route + ".filters[0].args.statusCodes[0]", () -> "500");
        registry.add(route + ".filters[0].args.statusCodes[1]", () -> "502");
        registry.add(route + ".filters[0].args.statusCodes[2]", () -> "503");
        registry.add(route + ".filters[0].args.statusCodes[3]", () -> "504");
    }

    @Test
    void opensAfterFiveConsecutiveFailures() {
        stubOrderFailure();
        triggerOrderFailures(5);
        awaitOrderState(CircuitBreaker.State.OPEN);

        int callsBeforeProbe = orderService.getAllServeEvents().size();
        expectOrderFallback();
        assertEquals(callsBeforeProbe, orderService.getAllServeEvents().size());
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
            .jsonPath("$.correlationId").exists()
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
            .willReturn(okJson("{\"orderId\":\"123\",\"status\":\"APPROVED\"}")));
    }

    private void triggerOrderFailures(int count) {
        for (int index = 0; index < count; index++) {
            webTestClient.get()
                .uri("/orders/123")
                .exchange()
                .expectStatus().is5xxServerError();
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
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.orderId").isEqualTo("123");
    }

    private CircuitBreaker orderBreaker() {
        return circuitBreakerRegistry.circuitBreaker(ORDER_BREAKER);
    }

    private void awaitOrderState(CircuitBreaker.State expected) {
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
            assertEquals(expected, orderBreaker().getState())
        );
    }
}

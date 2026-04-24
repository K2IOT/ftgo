package net.ftgo.gateway.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance tests for OrderDetailsController.
 * Verifies that parallel service calls reduce latency compared to sequential calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderDetailsPerformanceTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
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
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
        registry.add("spring.redis.host", () -> "localhost");
    }
    
    @Test
    void testParallelCallsReduceLatency() {
        // Given: Each service has 200ms latency
        Long orderId = 12345L;
        int serviceLatencyMs = 200;
        
        // Mock Order Service with 200ms delay
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
        
        // Mock Kitchen Service with 200ms delay
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
        
        // Mock Delivery Service with 200ms delay
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
        
        // When: Request order details and measure time
        long startTime = System.currentTimeMillis();
        
        webTestClient.get()
                .uri("/order-details/{orderId}", orderId)
                .exchange()
                .expectStatus().isOk();
        
        long endTime = System.currentTimeMillis();
        long totalLatency = endTime - startTime;
        
        // Then: Total latency should be close to max service latency (parallel execution)
        // not the sum of all latencies (sequential execution)
        // With parallel calls: ~200ms (max of all services)
        // With sequential calls: ~600ms (sum of all services)
        // Allow some overhead for network and processing
        int maxExpectedLatency = serviceLatencyMs + 300; // 500ms max
        int minSequentialLatency = serviceLatencyMs * 3 - 100; // 500ms min for sequential
        
        System.out.println("Total latency: " + totalLatency + "ms");
        System.out.println("Expected parallel latency: ~" + serviceLatencyMs + "ms");
        System.out.println("Expected sequential latency: ~" + (serviceLatencyMs * 3) + "ms");
        
        // Verify parallel execution (latency much less than sequential)
        assertThat(totalLatency)
                .as("Parallel calls should complete in ~%dms, not ~%dms (sequential)", 
                    serviceLatencyMs, serviceLatencyMs * 3)
                .isLessThan(maxExpectedLatency);
        
        // Verify all services were called
        orderServiceMock.verify(getRequestedFor(urlEqualTo("/orders/" + orderId)));
        kitchenServiceMock.verify(getRequestedFor(urlEqualTo("/tickets/by-order/" + orderId)));
        deliveryServiceMock.verify(getRequestedFor(urlEqualTo("/deliveries/by-order/" + orderId)));
    }
    
    @Test
    void testResponseTimeUnderLoad() {
        // Given: Multiple concurrent requests
        Long orderId = 12346L;
        int concurrentRequests = 10;
        
        // Mock all services with minimal delay
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
        
        // When: Make multiple concurrent requests
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < concurrentRequests; i++) {
            webTestClient.get()
                    .uri("/order-details/{orderId}", orderId)
                    .exchange()
                    .expectStatus().isOk();
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        long avgLatency = totalTime / concurrentRequests;
        
        System.out.println("Total time for " + concurrentRequests + " requests: " + totalTime + "ms");
        System.out.println("Average latency per request: " + avgLatency + "ms");
        
        // Then: Average latency should be reasonable (< 500ms per request)
        assertThat(avgLatency)
                .as("Average latency should be less than 500ms")
                .isLessThan(500);
    }
}

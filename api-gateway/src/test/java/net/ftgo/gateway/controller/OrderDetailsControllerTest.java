package net.ftgo.gateway.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import net.ftgo.gateway.dto.OrderDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OrderDetailsController.
 * Tests API composition with WireMock for downstream services.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderDetailsControllerTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    private static WireMockServer orderServiceMock;
    private static WireMockServer kitchenServiceMock;
    private static WireMockServer deliveryServiceMock;
    
    @BeforeEach
    void setUp() {
        // Start WireMock servers for each downstream service
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
    void testSuccessfulAggregationFromAllServices() {
        // Given: All three services return successful responses
        Long orderId = 12345L;
        
        // Mock Order Service response
        orderServiceMock.stubFor(get(urlEqualTo("/orders/" + orderId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": 12345,
                                    "state": "APPROVED",
                                    "consumerId": 100,
                                    "restaurantId": 200,
                                    "orderTotal": 45.99,
                                    "deliveryAddress": "123 Main St",
                                    "createdAt": "2025-01-15T10:30:00",
                                    "lineItems": [
                                        {
                                            "menuItemId": 1,
                                            "name": "Burger",
                                            "price": 12.99,
                                            "quantity": 2
                                        },
                                        {
                                            "menuItemId": 2,
                                            "name": "Fries",
                                            "price": 4.99,
                                            "quantity": 1
                                        }
                                    ]
                                }
                                """)));
        
        // Mock Kitchen Service response
        kitchenServiceMock.stubFor(get(urlEqualTo("/tickets/by-order/" + orderId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": 5001,
                                    "orderId": 12345,
                                    "state": "ACCEPTED",
                                    "acceptedAt": "2025-01-15T10:35:00",
                                    "readyBy": "2025-01-15T11:00:00"
                                }
                                """)));
        
        // Mock Delivery Service response
        deliveryServiceMock.stubFor(get(urlEqualTo("/deliveries/by-order/" + orderId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": 7001,
                                    "orderId": 12345,
                                    "status": "ASSIGNED",
                                    "courierId": 300,
                                    "scheduledTime": "2025-01-15T11:30:00"
                                }
                                """)));
        
        // When: Request order details
        webTestClient.get()
                .uri("/order-details/{orderId}", orderId)
                .exchange()
                
                // Then: Should return 200 with aggregated data
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(OrderDetails.class)
                .value(details -> {
                    // Verify Order Service data
                    assertThat(details.getOrderId()).isEqualTo(12345L);
                    assertThat(details.getOrderState()).isEqualTo("APPROVED");
                    assertThat(details.getConsumerId()).isEqualTo(100L);
                    assertThat(details.getRestaurantId()).isEqualTo(200L);
                    assertThat(details.getOrderTotal()).isEqualByComparingTo("45.99");
                    assertThat(details.getDeliveryAddress()).isEqualTo("123 Main St");
                    assertThat(details.getLineItems()).hasSize(2);
                    
                    // Verify Kitchen Service data
                    assertThat(details.getTicketInfo()).isNotNull();
                    assertThat(details.getTicketInfo().getTicketId()).isEqualTo(5001L);
                    assertThat(details.getTicketInfo().getTicketState()).isEqualTo("ACCEPTED");
                    
                    // Verify Delivery Service data
                    assertThat(details.getDeliveryInfo()).isNotNull();
                    assertThat(details.getDeliveryInfo().getDeliveryId()).isEqualTo(7001L);
                    assertThat(details.getDeliveryInfo().getDeliveryStatus()).isEqualTo("ASSIGNED");
                    assertThat(details.getDeliveryInfo().getCourierId()).isEqualTo(300L);
                });
        
        // Verify all services were called
        orderServiceMock.verify(getRequestedFor(urlEqualTo("/orders/" + orderId)));
        kitchenServiceMock.verify(getRequestedFor(urlEqualTo("/tickets/by-order/" + orderId)));
        deliveryServiceMock.verify(getRequestedFor(urlEqualTo("/deliveries/by-order/" + orderId)));
    }
    
    @Test
    void testFallbackWhenKitchenServiceUnavailable() {
        // Given: Order Service succeeds, Kitchen Service fails, Delivery Service succeeds
        Long orderId = 12346L;
        
        // Mock Order Service response
        orderServiceMock.stubFor(get(urlEqualTo("/orders/" + orderId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": 12346,
                                    "state": "APPROVED",
                                    "consumerId": 101,
                                    "restaurantId": 201,
                                    "orderTotal": 25.50,
                                    "deliveryAddress": "456 Oak Ave",
                                    "createdAt": "2025-01-15T11:00:00",
                                    "lineItems": []
                                }
                                """)));
        
        // Mock Kitchen Service failure (503)
        kitchenServiceMock.stubFor(get(urlEqualTo("/tickets/by-order/" + orderId))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));
        
        // Mock Delivery Service response
        deliveryServiceMock.stubFor(get(urlEqualTo("/deliveries/by-order/" + orderId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": 7002,
                                    "orderId": 12346,
                                    "status": "PENDING",
                                    "scheduledTime": "2025-01-15T12:00:00"
                                }
                                """)));
        
        // When: Request order details
        webTestClient.get()
                .uri("/order-details/{orderId}", orderId)
                .exchange()
                
                // Then: Should return 200 with partial data (no ticket info)
                .expectStatus().isOk()
                .expectBody(OrderDetails.class)
                .value(details -> {
                    // Order data should be present
                    assertThat(details.getOrderId()).isEqualTo(12346L);
                    assertThat(details.getOrderState()).isEqualTo("APPROVED");
                    
                    // Ticket info should be null (service unavailable)
                    assertThat(details.getTicketInfo()).isNull();
                    
                    // Delivery info should be present
                    assertThat(details.getDeliveryInfo()).isNotNull();
                    assertThat(details.getDeliveryInfo().getDeliveryId()).isEqualTo(7002L);
                });
    }
    
    @Test
    void testFallbackWhenDeliveryServiceUnavailable() {
        // Given: Order and Kitchen services succeed, Delivery Service fails
        Long orderId = 12347L;
        
        // Mock Order Service response
        orderServiceMock.stubFor(get(urlEqualTo("/orders/" + orderId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": 12347,
                                    "state": "APPROVED",
                                    "consumerId": 102,
                                    "restaurantId": 202,
                                    "orderTotal": 35.75,
                                    "deliveryAddress": "789 Pine Rd",
                                    "createdAt": "2025-01-15T12:00:00",
                                    "lineItems": []
                                }
                                """)));
        
        // Mock Kitchen Service response
        kitchenServiceMock.stubFor(get(urlEqualTo("/tickets/by-order/" + orderId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": 5002,
                                    "orderId": 12347,
                                    "state": "PREPARING",
                                    "acceptedAt": "2025-01-15T12:05:00",
                                    "readyBy": "2025-01-15T12:30:00"
                                }
                                """)));
        
        // Mock Delivery Service failure (timeout)
        deliveryServiceMock.stubFor(get(urlEqualTo("/deliveries/by-order/" + orderId))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));
        
        // When: Request order details
        webTestClient.get()
                .uri("/order-details/{orderId}", orderId)
                .exchange()
                
                // Then: Should return 200 with partial data (no delivery info)
                .expectStatus().isOk()
                .expectBody(OrderDetails.class)
                .value(details -> {
                    // Order data should be present
                    assertThat(details.getOrderId()).isEqualTo(12347L);
                    
                    // Ticket info should be present
                    assertThat(details.getTicketInfo()).isNotNull();
                    assertThat(details.getTicketInfo().getTicketState()).isEqualTo("PREPARING");
                    
                    // Delivery info should be null (service unavailable)
                    assertThat(details.getDeliveryInfo()).isNull();
                });
    }
    
    @Test
    void testNotFoundWhenOrderDoesNotExist() {
        // Given: Order Service returns 404
        Long orderId = 99999L;
        
        orderServiceMock.stubFor(get(urlEqualTo("/orders/" + orderId))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("Order not found")));
        
        // When: Request order details
        webTestClient.get()
                .uri("/order-details/{orderId}", orderId)
                .exchange()
                
                // Then: Should return 404
                .expectStatus().isNotFound();
    }
    
    @Test
    void testPartialResponseWhenTicketNotYetCreated() {
        // Given: Order exists but ticket not yet created (404 from Kitchen Service)
        Long orderId = 12348L;
        
        // Mock Order Service response
        orderServiceMock.stubFor(get(urlEqualTo("/orders/" + orderId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": 12348,
                                    "state": "APPROVAL_PENDING",
                                    "consumerId": 103,
                                    "restaurantId": 203,
                                    "orderTotal": 50.00,
                                    "deliveryAddress": "321 Elm St",
                                    "createdAt": "2025-01-15T13:00:00",
                                    "lineItems": []
                                }
                                """)));
        
        // Mock Kitchen Service 404 (ticket not created yet)
        kitchenServiceMock.stubFor(get(urlEqualTo("/tickets/by-order/" + orderId))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("Ticket not found")));
        
        // Mock Delivery Service 404 (delivery not created yet)
        deliveryServiceMock.stubFor(get(urlEqualTo("/deliveries/by-order/" + orderId))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("Delivery not found")));
        
        // When: Request order details
        webTestClient.get()
                .uri("/order-details/{orderId}", orderId)
                .exchange()
                
                // Then: Should return 200 with only order data
                .expectStatus().isOk()
                .expectBody(OrderDetails.class)
                .value(details -> {
                    // Order data should be present
                    assertThat(details.getOrderId()).isEqualTo(12348L);
                    assertThat(details.getOrderState()).isEqualTo("APPROVAL_PENDING");
                    
                    // Ticket and delivery info should be null (not created yet)
                    assertThat(details.getTicketInfo()).isNull();
                    assertThat(details.getDeliveryInfo()).isNull();
                });
    }
}

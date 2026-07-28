package net.ftgo.gateway.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import net.ftgo.gateway.dto.OrderDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Integration tests for authenticated Order Details API composition. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderDetailsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    private WireMockServer orderServiceMock;
    private WireMockServer kitchenServiceMock;
    private WireMockServer deliveryServiceMock;

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
        registry.add("server.error.include-message", () -> "always");
        registry.add("server.error.include-exception", () -> "true");
        registry.add("server.error.include-stacktrace", () -> "always");
    }

    @BeforeEach
    void setUp() {
        orderServiceMock = new WireMockServer(8091);
        kitchenServiceMock = new WireMockServer(8092);
        deliveryServiceMock = new WireMockServer(8093);
        orderServiceMock.start();
        kitchenServiceMock.start();
        deliveryServiceMock.start();
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.just(consumerJwt()));
    }

    @AfterEach
    void tearDown() {
        stop(orderServiceMock);
        stop(kitchenServiceMock);
        stop(deliveryServiceMock);
    }

    @Test
    void testSuccessfulAggregationFromAllServices() {
        long orderId = 12345L;
        stubOrder(orderId, 100L, "APPROVED", "45.99", "123 Main St");
        stubTicket(orderId, 5001L, "ACCEPTED");
        stubDelivery(orderId, 7001L, "ASSIGNED", 300L);

        authenticatedGet(orderId)
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(OrderDetails.class)
            .value(details -> {
                assertThat(details.getOrderId()).isEqualTo(orderId);
                assertThat(details.getOrderState()).isEqualTo("APPROVED");
                assertThat(details.getConsumerId()).isEqualTo(100L);
                assertThat(details.getRestaurantId()).isEqualTo(200L);
                assertThat(details.getOrderTotal()).isEqualByComparingTo("45.99");
                assertThat(details.getDeliveryAddress()).isEqualTo("123 Main St");
                assertThat(details.getTicketInfo()).isNotNull();
                assertThat(details.getTicketInfo().getTicketId()).isEqualTo(5001L);
                assertThat(details.getDeliveryInfo()).isNotNull();
                assertThat(details.getDeliveryInfo().getDeliveryId()).isEqualTo(7001L);
                assertThat(details.getDeliveryInfo().getCourierId()).isEqualTo(300L);
            });

        orderServiceMock.verify(getRequestedFor(urlEqualTo("/orders/" + orderId))
            .withHeader(HttpHeaders.AUTHORIZATION, com.github.tomakehurst.wiremock.client.WireMock.equalTo("Bearer consumer-token")));
        kitchenServiceMock.verify(getRequestedFor(urlEqualTo("/tickets/by-order/" + orderId))
            .withHeader(HttpHeaders.AUTHORIZATION, com.github.tomakehurst.wiremock.client.WireMock.equalTo("Bearer consumer-token")));
        deliveryServiceMock.verify(getRequestedFor(urlEqualTo("/deliveries/by-order/" + orderId))
            .withHeader(HttpHeaders.AUTHORIZATION, com.github.tomakehurst.wiremock.client.WireMock.equalTo("Bearer consumer-token")));
    }

    @Test
    void testFallbackWhenKitchenServiceUnavailable() {
        long orderId = 12346L;
        stubOrder(orderId, 101L, "APPROVED", "25.50", "456 Oak Ave");
        kitchenServiceMock.stubFor(get(urlEqualTo("/tickets/by-order/" + orderId))
            .willReturn(aResponse().withStatus(503)));
        stubDelivery(orderId, 7002L, "PENDING", null);

        authenticatedGet(orderId)
            .expectStatus().isOk()
            .expectBody(OrderDetails.class)
            .value(details -> {
                assertThat(details.getOrderId()).isEqualTo(orderId);
                assertThat(details.getTicketInfo()).isNull();
                assertThat(details.getDeliveryInfo()).isNotNull();
            });
    }

    @Test
    void testFallbackWhenDeliveryServiceUnavailable() {
        long orderId = 12347L;
        stubOrder(orderId, 102L, "APPROVED", "35.75", "789 Pine Rd");
        stubTicket(orderId, 5002L, "PREPARING");
        deliveryServiceMock.stubFor(get(urlEqualTo("/deliveries/by-order/" + orderId))
            .willReturn(aResponse().withStatus(500)));

        authenticatedGet(orderId)
            .expectStatus().isOk()
            .expectBody(OrderDetails.class)
            .value(details -> {
                assertThat(details.getTicketInfo()).isNotNull();
                assertThat(details.getDeliveryInfo()).isNull();
            });
    }

    @Test
    void testNotFoundWhenOrderDoesNotExist() {
        long orderId = 99999L;
        orderServiceMock.stubFor(get(urlEqualTo("/orders/" + orderId))
            .willReturn(aResponse().withStatus(404)));

        EntityExchangeResult<byte[]> result = webTestClient.get()
            .uri("/order-details/{orderId}", orderId)
            .headers(headers -> headers.setBearerAuth("consumer-token"))
            .exchange()
            .expectBody()
            .returnResult();
        byte[] responseBody = result.getResponseBody();
        String body = responseBody == null ? "" : new String(responseBody, StandardCharsets.UTF_8);
        assertThat(result.getStatus())
            .withFailMessage("Gateway status=%s response=%s", result.getStatus(), body)
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testPartialResponseWhenTicketNotYetCreated() {
        long orderId = 12348L;
        stubOrder(orderId, 103L, "APPROVAL_PENDING", "50.00", "321 Elm St");
        kitchenServiceMock.stubFor(get(urlEqualTo("/tickets/by-order/" + orderId))
            .willReturn(aResponse().withStatus(404)));
        deliveryServiceMock.stubFor(get(urlEqualTo("/deliveries/by-order/" + orderId))
            .willReturn(aResponse().withStatus(404)));

        authenticatedGet(orderId)
            .expectStatus().isOk()
            .expectBody(OrderDetails.class)
            .value(details -> {
                assertThat(details.getOrderState()).isEqualTo("APPROVAL_PENDING");
                assertThat(details.getTicketInfo()).isNull();
                assertThat(details.getDeliveryInfo()).isNull();
            });
    }

    private WebTestClient.ResponseSpec authenticatedGet(long orderId) {
        return webTestClient.get()
            .uri("/order-details/{orderId}", orderId)
            .headers(headers -> headers.setBearerAuth("consumer-token"))
            .exchange();
    }

    private void stubOrder(long orderId, long consumerId, String state, String total, String address) {
        orderServiceMock.stubFor(get(urlEqualTo("/orders/" + orderId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody("""
                    {
                      "id": %d,
                      "state": "%s",
                      "consumerId": %d,
                      "restaurantId": 200,
                      "orderTotal": %s,
                      "deliveryAddress": "%s",
                      "createdAt": "2025-01-15T10:30:00",
                      "lineItems": []
                    }
                    """.formatted(orderId, state, consumerId, total, address))));
    }

    private void stubTicket(long orderId, long ticketId, String state) {
        kitchenServiceMock.stubFor(get(urlEqualTo("/tickets/by-order/" + orderId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody("""
                    {
                      "id": %d,
                      "orderId": %d,
                      "state": "%s",
                      "acceptedAt": "2025-01-15T10:35:00",
                      "readyBy": "2025-01-15T11:00:00"
                    }
                    """.formatted(ticketId, orderId, state))));
    }

    private void stubDelivery(long orderId, long deliveryId, String status, Long courierId) {
        String courierField = courierId == null ? "null" : courierId.toString();
        deliveryServiceMock.stubFor(get(urlEqualTo("/deliveries/by-order/" + orderId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody("""
                    {
                      "id": %d,
                      "orderId": %d,
                      "status": "%s",
                      "courierId": %s,
                      "scheduledTime": "2025-01-15T11:30:00"
                    }
                    """.formatted(deliveryId, orderId, status, courierField))));
    }

    private Jwt consumerJwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("consumer-token")
            .header("alg", "RS256")
            .issuer("https://identity.example/realms/ftgo")
            .subject("consumer-100")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("roles", List.of("CONSUMER"))
            .claim("consumer_id", 100L)
            .build();
    }

    private void stop(WireMockServer server) {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }
}

package net.ftgo.gateway.controller;

import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.gateway.client.DeliveryServiceClient;
import net.ftgo.gateway.client.KitchenServiceClient;
import net.ftgo.gateway.client.OrderServiceClient;
import net.ftgo.gateway.dto.DeliveryResponse;
import net.ftgo.gateway.dto.OrderDetails;
import net.ftgo.gateway.dto.OrderResponse;
import net.ftgo.gateway.dto.TicketResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Unit tests for authenticated Order Details composition. */
@ExtendWith(MockitoExtension.class)
class OrderDetailsControllerUnitTest {

    private static final String TOKEN = "signed-consumer-token";

    @Mock
    private OrderServiceClient orderServiceClient;

    @Mock
    private KitchenServiceClient kitchenServiceClient;

    @Mock
    private DeliveryServiceClient deliveryServiceClient;

    private OrderDetailsController controller;
    private AbstractAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        controller = new OrderDetailsController(
            orderServiceClient,
            kitchenServiceClient,
            deliveryServiceClient
        );
        authentication = consumerAuthentication();
    }

    @Test
    void testSuccessfulAggregationFromAllServices() {
        Long orderId = 12345L;
        OrderResponse order = order(orderId, "APPROVED", 100L, 200L, "45.99", "123 Main St");
        TicketResponse ticket = ticket(orderId, 5001L, "ACCEPTED");
        DeliveryResponse delivery = delivery(orderId, 7001L, "ASSIGNED", 300L);

        when(orderServiceClient.getOrder(orderId, TOKEN)).thenReturn(Mono.just(order));
        when(kitchenServiceClient.getTicketByOrderId(orderId, TOKEN)).thenReturn(Mono.just(ticket));
        when(deliveryServiceClient.getDeliveryByOrderId(orderId, TOKEN)).thenReturn(Mono.just(delivery));

        StepVerifier.create(controller.getOrderDetails(orderId, authentication))
            .assertNext(response -> {
                assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                OrderDetails details = response.getBody();
                assertThat(details).isNotNull();
                assertThat(details.getOrderId()).isEqualTo(orderId);
                assertThat(details.getOrderState()).isEqualTo("APPROVED");
                assertThat(details.getTicketInfo()).isNotNull();
                assertThat(details.getTicketInfo().getTicketId()).isEqualTo(5001L);
                assertThat(details.getDeliveryInfo()).isNotNull();
                assertThat(details.getDeliveryInfo().getDeliveryId()).isEqualTo(7001L);
            })
            .verifyComplete();
    }

    @Test
    void testPartialResponseWhenKitchenServiceFails() {
        Long orderId = 12346L;
        OrderResponse order = order(orderId, "APPROVED", 101L, 201L, "25.50", "456 Oak Ave");
        DeliveryResponse delivery = delivery(orderId, 7002L, "PENDING", null);

        when(orderServiceClient.getOrder(orderId, TOKEN)).thenReturn(Mono.just(order));
        when(kitchenServiceClient.getTicketByOrderId(orderId, TOKEN))
            .thenReturn(Mono.error(new RuntimeException("Service unavailable")));
        when(deliveryServiceClient.getDeliveryByOrderId(orderId, TOKEN)).thenReturn(Mono.just(delivery));

        StepVerifier.create(controller.getOrderDetails(orderId, authentication))
            .assertNext(response -> {
                assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getTicketInfo()).isNull();
                assertThat(response.getBody().getDeliveryInfo()).isNotNull();
            })
            .verifyComplete();
    }

    @Test
    void testPartialResponseWhenDeliveryServiceFails() {
        Long orderId = 12347L;
        OrderResponse order = order(orderId, "APPROVED", 102L, 202L, "35.75", "789 Pine Rd");
        TicketResponse ticket = ticket(orderId, 5002L, "PREPARING");

        when(orderServiceClient.getOrder(orderId, TOKEN)).thenReturn(Mono.just(order));
        when(kitchenServiceClient.getTicketByOrderId(orderId, TOKEN)).thenReturn(Mono.just(ticket));
        when(deliveryServiceClient.getDeliveryByOrderId(orderId, TOKEN))
            .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

        StepVerifier.create(controller.getOrderDetails(orderId, authentication))
            .assertNext(response -> {
                assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getTicketInfo()).isNotNull();
                assertThat(response.getBody().getDeliveryInfo()).isNull();
            })
            .verifyComplete();
    }

    @Test
    void testNotFoundWhenOrderServiceReturnsEmpty() {
        Long orderId = 99999L;
        when(orderServiceClient.getOrder(orderId, TOKEN)).thenReturn(Mono.empty());
        when(kitchenServiceClient.getTicketByOrderId(anyLong(), eq(TOKEN))).thenReturn(Mono.empty());
        when(deliveryServiceClient.getDeliveryByOrderId(anyLong(), eq(TOKEN))).thenReturn(Mono.empty());

        StepVerifier.create(controller.getOrderDetails(orderId, authentication))
            .assertNext(response -> assertThat(response.getStatusCode().is4xxClientError()).isTrue())
            .verifyComplete();
    }

    @Test
    void testPartialResponseWhenTicketNotYetCreated() {
        Long orderId = 12348L;
        OrderResponse order = order(
            orderId,
            "APPROVAL_PENDING",
            103L,
            203L,
            "50.00",
            "321 Elm St"
        );

        when(orderServiceClient.getOrder(orderId, TOKEN)).thenReturn(Mono.just(order));
        when(kitchenServiceClient.getTicketByOrderId(orderId, TOKEN)).thenReturn(Mono.empty());
        when(deliveryServiceClient.getDeliveryByOrderId(orderId, TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(controller.getOrderDetails(orderId, authentication))
            .assertNext(response -> {
                assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getOrderState()).isEqualTo("APPROVAL_PENDING");
                assertThat(response.getBody().getTicketInfo()).isNull();
                assertThat(response.getBody().getDeliveryInfo()).isNull();
            })
            .verifyComplete();
    }

    private OrderResponse order(
        Long orderId,
        String state,
        Long consumerId,
        Long restaurantId,
        String total,
        String address
    ) {
        OrderResponse response = new OrderResponse();
        response.setId(orderId);
        response.setState(state);
        response.setConsumerId(consumerId);
        response.setRestaurantId(restaurantId);
        response.setOrderTotal(new BigDecimal(total));
        response.setDeliveryAddress(address);
        response.setCreatedAt(LocalDateTime.now());
        response.setLineItems(Collections.emptyList());
        return response;
    }

    private TicketResponse ticket(Long orderId, Long ticketId, String state) {
        TicketResponse response = new TicketResponse();
        response.setId(ticketId);
        response.setOrderId(orderId);
        response.setState(state);
        response.setAcceptedAt(LocalDateTime.now());
        return response;
    }

    private DeliveryResponse delivery(Long orderId, Long deliveryId, String status, Long courierId) {
        DeliveryResponse response = new DeliveryResponse();
        response.setId(deliveryId);
        response.setOrderId(orderId);
        response.setStatus(status);
        response.setCourierId(courierId);
        return response;
    }

    private AbstractAuthenticationToken consumerAuthentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue(TOKEN)
            .header("alg", "RS256")
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

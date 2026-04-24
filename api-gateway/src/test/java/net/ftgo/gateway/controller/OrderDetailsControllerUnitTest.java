package net.ftgo.gateway.controller;

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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OrderDetailsController using mocked service clients.
 */
@ExtendWith(MockitoExtension.class)
class OrderDetailsControllerUnitTest {
    
    @Mock
    private OrderServiceClient orderServiceClient;
    
    @Mock
    private KitchenServiceClient kitchenServiceClient;
    
    @Mock
    private DeliveryServiceClient deliveryServiceClient;
    
    private OrderDetailsController controller;
    
    @BeforeEach
    void setUp() {
        controller = new OrderDetailsController(
                orderServiceClient,
                kitchenServiceClient,
                deliveryServiceClient
        );
    }
    
    @Test
    void testSuccessfulAggregationFromAllServices() {
        // Given
        Long orderId = 12345L;
        
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setId(orderId);
        orderResponse.setState("APPROVED");
        orderResponse.setConsumerId(100L);
        orderResponse.setRestaurantId(200L);
        orderResponse.setOrderTotal(new BigDecimal("45.99"));
        orderResponse.setDeliveryAddress("123 Main St");
        orderResponse.setCreatedAt(LocalDateTime.now());
        orderResponse.setLineItems(Collections.emptyList());
        
        TicketResponse ticketResponse = new TicketResponse();
        ticketResponse.setId(5001L);
        ticketResponse.setOrderId(orderId);
        ticketResponse.setState("ACCEPTED");
        ticketResponse.setAcceptedAt(LocalDateTime.now());
        
        DeliveryResponse deliveryResponse = new DeliveryResponse();
        deliveryResponse.setId(7001L);
        deliveryResponse.setOrderId(orderId);
        deliveryResponse.setStatus("ASSIGNED");
        deliveryResponse.setCourierId(300L);
        
        when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(orderResponse));
        when(kitchenServiceClient.getTicketByOrderId(orderId)).thenReturn(Mono.just(ticketResponse));
        when(deliveryServiceClient.getDeliveryByOrderId(orderId)).thenReturn(Mono.just(deliveryResponse));
        
        // When
        Mono<ResponseEntity<OrderDetails>> result = controller.getOrderDetails(orderId);
        
        // Then
        StepVerifier.create(result)
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
        // Given
        Long orderId = 12346L;
        
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setId(orderId);
        orderResponse.setState("APPROVED");
        orderResponse.setConsumerId(101L);
        orderResponse.setRestaurantId(201L);
        orderResponse.setOrderTotal(new BigDecimal("25.50"));
        orderResponse.setDeliveryAddress("456 Oak Ave");
        orderResponse.setCreatedAt(LocalDateTime.now());
        orderResponse.setLineItems(Collections.emptyList());
        
        DeliveryResponse deliveryResponse = new DeliveryResponse();
        deliveryResponse.setId(7002L);
        deliveryResponse.setOrderId(orderId);
        deliveryResponse.setStatus("PENDING");
        
        when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(orderResponse));
        when(kitchenServiceClient.getTicketByOrderId(orderId)).thenReturn(Mono.error(new RuntimeException("Service unavailable")));
        when(deliveryServiceClient.getDeliveryByOrderId(orderId)).thenReturn(Mono.just(deliveryResponse));
        
        // When
        Mono<ResponseEntity<OrderDetails>> result = controller.getOrderDetails(orderId);
        
        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    OrderDetails details = response.getBody();
                    assertThat(details).isNotNull();
                    assertThat(details.getOrderId()).isEqualTo(orderId);
                    assertThat(details.getTicketInfo()).isNull(); // Kitchen service failed
                    assertThat(details.getDeliveryInfo()).isNotNull();
                })
                .verifyComplete();
    }
    
    @Test
    void testPartialResponseWhenDeliveryServiceFails() {
        // Given
        Long orderId = 12347L;
        
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setId(orderId);
        orderResponse.setState("APPROVED");
        orderResponse.setConsumerId(102L);
        orderResponse.setRestaurantId(202L);
        orderResponse.setOrderTotal(new BigDecimal("35.75"));
        orderResponse.setDeliveryAddress("789 Pine Rd");
        orderResponse.setCreatedAt(LocalDateTime.now());
        orderResponse.setLineItems(Collections.emptyList());
        
        TicketResponse ticketResponse = new TicketResponse();
        ticketResponse.setId(5002L);
        ticketResponse.setOrderId(orderId);
        ticketResponse.setState("PREPARING");
        
        when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(orderResponse));
        when(kitchenServiceClient.getTicketByOrderId(orderId)).thenReturn(Mono.just(ticketResponse));
        when(deliveryServiceClient.getDeliveryByOrderId(orderId)).thenReturn(Mono.error(new RuntimeException("Service unavailable")));
        
        // When
        Mono<ResponseEntity<OrderDetails>> result = controller.getOrderDetails(orderId);
        
        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    OrderDetails details = response.getBody();
                    assertThat(details).isNotNull();
                    assertThat(details.getOrderId()).isEqualTo(orderId);
                    assertThat(details.getTicketInfo()).isNotNull();
                    assertThat(details.getDeliveryInfo()).isNull(); // Delivery service failed
                })
                .verifyComplete();
    }
    
    @Test
    void testNotFoundWhenOrderServiceReturnsEmpty() {
        // Given
        Long orderId = 99999L;
        
        when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.empty());
        when(kitchenServiceClient.getTicketByOrderId(anyLong())).thenReturn(Mono.empty());
        when(deliveryServiceClient.getDeliveryByOrderId(anyLong())).thenReturn(Mono.empty());
        
        // When
        Mono<ResponseEntity<OrderDetails>> result = controller.getOrderDetails(orderId);
        
        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is4xxClientError()).isTrue();
                })
                .verifyComplete();
    }
    
    @Test
    void testPartialResponseWhenTicketNotYetCreated() {
        // Given
        Long orderId = 12348L;
        
        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setId(orderId);
        orderResponse.setState("APPROVAL_PENDING");
        orderResponse.setConsumerId(103L);
        orderResponse.setRestaurantId(203L);
        orderResponse.setOrderTotal(new BigDecimal("50.00"));
        orderResponse.setDeliveryAddress("321 Elm St");
        orderResponse.setCreatedAt(LocalDateTime.now());
        orderResponse.setLineItems(Collections.emptyList());
        
        when(orderServiceClient.getOrder(orderId)).thenReturn(Mono.just(orderResponse));
        when(kitchenServiceClient.getTicketByOrderId(orderId)).thenReturn(Mono.empty()); // Not created yet
        when(deliveryServiceClient.getDeliveryByOrderId(orderId)).thenReturn(Mono.empty()); // Not created yet
        
        // When
        Mono<ResponseEntity<OrderDetails>> result = controller.getOrderDetails(orderId);
        
        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    OrderDetails details = response.getBody();
                    assertThat(details).isNotNull();
                    assertThat(details.getOrderId()).isEqualTo(orderId);
                    assertThat(details.getOrderState()).isEqualTo("APPROVAL_PENDING");
                    assertThat(details.getTicketInfo()).isNull();
                    assertThat(details.getDeliveryInfo()).isNull();
                })
                .verifyComplete();
    }
}

package net.ftgo.gateway.controller;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import net.ftgo.gateway.client.DeliveryServiceClient;
import net.ftgo.gateway.client.KitchenServiceClient;
import net.ftgo.gateway.client.OrderServiceClient;
import net.ftgo.gateway.dto.DeliveryResponse;
import net.ftgo.gateway.dto.OrderDetails;
import net.ftgo.gateway.dto.OrderResponse;
import net.ftgo.gateway.dto.TicketResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * API composition endpoint for aggregating order details from multiple services.
 * Implements the API Composition pattern to provide a unified view of order data.
 */
@RestController
@RequestMapping("/order-details")
public class OrderDetailsController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderDetailsController.class);
    
    private final OrderServiceClient orderServiceClient;
    private final KitchenServiceClient kitchenServiceClient;
    private final DeliveryServiceClient deliveryServiceClient;
    
    public OrderDetailsController(
            OrderServiceClient orderServiceClient,
            KitchenServiceClient kitchenServiceClient,
            DeliveryServiceClient deliveryServiceClient
    ) {
        this.orderServiceClient = orderServiceClient;
        this.kitchenServiceClient = kitchenServiceClient;
        this.deliveryServiceClient = deliveryServiceClient;
    }
    
    /**
     * Get aggregated order details from Order Service, Kitchen Service, and Delivery Service.
     * Uses parallel service calls to minimize latency.
     * Implements fallback for service unavailability (returns partial response).
     * 
     * @param orderId the order ID
     * @return ResponseEntity with OrderDetails
     */
    @GetMapping("/{orderId}")
    @CircuitBreaker(name = "orderDetails", fallbackMethod = "getOrderDetailsFallback")
    public Mono<ResponseEntity<OrderDetails>> getOrderDetails(@PathVariable Long orderId) {
        logger.info("Fetching order details for orderId: {}", orderId);
        
        // Fetch data from all three services in parallel
        Mono<OrderResponse> orderMono = orderServiceClient.getOrder(orderId)
                .doOnError(e -> logger.error("Failed to fetch order from Order Service", e));
        
        Mono<TicketResponse> ticketMono = kitchenServiceClient.getTicketByOrderId(orderId)
                .doOnError(e -> logger.warn("Failed to fetch ticket from Kitchen Service", e))
                .onErrorResume(e -> Mono.empty()); // Continue with empty if kitchen service fails
        
        Mono<DeliveryResponse> deliveryMono = deliveryServiceClient.getDeliveryByOrderId(orderId)
                .doOnError(e -> logger.warn("Failed to fetch delivery from Delivery Service", e))
                .onErrorResume(e -> Mono.empty()); // Continue with empty if delivery service fails
        
        // Combine all three responses in parallel, wrapping optional values
        return orderMono
                .flatMap(order -> 
                    Mono.zip(
                        ticketMono.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()),
                        deliveryMono.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty())
                    )
                    .map(tuple -> {
                        TicketResponse ticket = tuple.getT1().orElse(null);
                        DeliveryResponse delivery = tuple.getT2().orElse(null);
                        return ResponseEntity.ok(buildOrderDetails(order, ticket, delivery));
                    })
                )
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }
    
    /**
     * Fallback method when circuit breaker is open.
     * Returns cached data or partial response with 503 status.
     * 
     * @param orderId the order ID
     * @param throwable the exception that triggered the fallback
     * @return ResponseEntity with error message
     */
    public Mono<ResponseEntity<OrderDetails>> getOrderDetailsFallback(Long orderId, Throwable throwable) {
        logger.error("Circuit breaker fallback triggered for orderId: {}", orderId, throwable);
        
        // In a production system, this could return cached data from Redis
        // For now, return a service unavailable response
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(null));
    }
    
    /**
     * Build OrderDetails from individual service responses.
     * Handles null responses gracefully (partial data).
     * 
     * @param order the order response
     * @param ticket the ticket response (may be null)
     * @param delivery the delivery response (may be null)
     * @return OrderDetails
     */
    private OrderDetails buildOrderDetails(OrderResponse order, TicketResponse ticket, DeliveryResponse delivery) {
        OrderDetails details = new OrderDetails();
        
        // Order Service data (required)
        details.setOrderId(order.getId());
        details.setOrderState(order.getState());
        details.setConsumerId(order.getConsumerId());
        details.setRestaurantId(order.getRestaurantId());
        details.setOrderTotal(order.getOrderTotal());
        details.setDeliveryAddress(order.getDeliveryAddress());
        details.setCreatedAt(order.getCreatedAt());
        
        // Convert line items
        if (order.getLineItems() != null) {
            details.setLineItems(order.getLineItems().stream()
                    .map(item -> new OrderDetails.LineItem(
                            item.getMenuItemId(),
                            item.getName(),
                            item.getPrice(),
                            item.getQuantity()
                    ))
                    .collect(Collectors.toList()));
        }
        
        // Kitchen Service data (optional)
        if (ticket != null) {
            details.setTicketInfo(new OrderDetails.TicketInfo(
                    ticket.getId(),
                    ticket.getState(),
                    ticket.getAcceptedAt(),
                    ticket.getPreparedAt(),
                    ticket.getReadyBy()
            ));
        }
        
        // Delivery Service data (optional)
        if (delivery != null) {
            details.setDeliveryInfo(new OrderDetails.DeliveryInfo(
                    delivery.getId(),
                    delivery.getStatus(),
                    delivery.getCourierId(),
                    delivery.getScheduledTime(),
                    delivery.getPickupTime(),
                    delivery.getDeliveryTime()
            ));
        }
        
        return details;
    }
}

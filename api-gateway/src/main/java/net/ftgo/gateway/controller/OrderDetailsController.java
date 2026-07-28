package net.ftgo.gateway.controller;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import net.ftgo.common.security.FtgoJwtAuthenticationToken;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * API composition endpoint for aggregating order details from multiple services.
 * The verified caller JWT is passed explicitly to downstream clients so token
 * propagation remains stable across circuit-breaker and Reactor boundaries.
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

    @GetMapping("/{orderId}")
    @CircuitBreaker(name = "orderDetails", fallbackMethod = "getOrderDetailsFallback")
    public Mono<ResponseEntity<OrderDetails>> getOrderDetails(
            @PathVariable Long orderId,
            Authentication authentication
    ) {
        String verifiedToken = requireVerifiedToken(authentication);
        logger.info("Fetching order details for orderId: {}", orderId);

        Mono<OrderResponse> orderMono = orderServiceClient.getOrder(orderId, verifiedToken)
                .doOnError(error -> logger.error("Failed to fetch order from Order Service", error));

        Mono<TicketResponse> ticketMono = kitchenServiceClient
                .getTicketByOrderId(orderId, verifiedToken)
                .doOnError(error -> logger.warn("Failed to fetch ticket from Kitchen Service", error))
                .onErrorResume(error -> Mono.empty());

        Mono<DeliveryResponse> deliveryMono = deliveryServiceClient
                .getDeliveryByOrderId(orderId, verifiedToken)
                .doOnError(error -> logger.warn("Failed to fetch delivery from Delivery Service", error))
                .onErrorResume(error -> Mono.empty());

        return orderMono
                .flatMap(order -> Mono.zip(
                        ticketMono.map(java.util.Optional::of)
                                .defaultIfEmpty(java.util.Optional.empty()),
                        deliveryMono.map(java.util.Optional::of)
                                .defaultIfEmpty(java.util.Optional.empty())
                    )
                    .map(tuple -> ResponseEntity.ok(buildOrderDetails(
                            order,
                            tuple.getT1().orElse(null),
                            tuple.getT2().orElse(null)
                    )))
                )
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    public Mono<ResponseEntity<OrderDetails>> getOrderDetailsFallback(
            Long orderId,
            Authentication authentication,
            Throwable throwable
    ) {
        logger.error("Circuit breaker fallback triggered for orderId: {}", orderId, throwable);
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(null));
    }

    private String requireVerifiedToken(Authentication authentication) {
        if (!(authentication instanceof FtgoJwtAuthenticationToken token)) {
            throw new AccessDeniedException("Verified FTGO JWT is required");
        }
        return token.tokenValue();
    }

    private OrderDetails buildOrderDetails(
            OrderResponse order,
            TicketResponse ticket,
            DeliveryResponse delivery
    ) {
        OrderDetails details = new OrderDetails();
        details.setOrderId(order.getId());
        details.setOrderState(order.getState());
        details.setConsumerId(order.getConsumerId());
        details.setRestaurantId(order.getRestaurantId());
        details.setOrderTotal(order.getOrderTotal());
        details.setDeliveryAddress(order.getDeliveryAddress());
        details.setCreatedAt(order.getCreatedAt());

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

        if (ticket != null) {
            details.setTicketInfo(new OrderDetails.TicketInfo(
                    ticket.getId(),
                    ticket.getState(),
                    ticket.getAcceptedAt(),
                    ticket.getPreparedAt(),
                    ticket.getReadyBy()
            ));
        }

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

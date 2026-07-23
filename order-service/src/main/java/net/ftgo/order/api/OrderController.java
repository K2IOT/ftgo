package net.ftgo.order.api;

import jakarta.validation.Valid;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.service.OrderNotFoundException;
import net.ftgo.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for Order operations.
 *
 * Endpoints:
 * - POST /orders - Create a new order (initiates CreateOrderSaga)
 * - GET /orders/{orderId} - Get order details
 * - POST /orders/{orderId}/cancel - Cancel an order (initiates CancelOrderSaga)
 * - POST /orders/{orderId}/revise - Revise an order (initiates ReviseOrderSaga)
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(
        @Valid @RequestBody CreateOrderRequest request
    ) {
        logger.info(
            "POST /orders - Creating order: consumerId={}, restaurantId={}, expectedMenuVersion={}",
            request.getConsumerId(),
            request.getRestaurantId(),
            request.getExpectedMenuVersion()
        );

        try {
            List<OrderLineItem> lineItems = request.getLineItems().stream()
                .map(item -> new OrderLineItem(
                    item.getMenuItemId(),
                    item.getName(),
                    item.getPrice(),
                    item.getQuantity()
                ))
                .collect(Collectors.toList());

            DeliveryInfo deliveryInfo = new DeliveryInfo(
                request.getDeliveryAddress(),
                request.getDeliveryTime()
            );
            PaymentInfo paymentInfo = new PaymentInfo(request.getPaymentToken());

            Long orderId = orderService.createOrder(
                request.getConsumerId(),
                request.getRestaurantId(),
                request.getExpectedMenuVersion(),
                lineItems,
                deliveryInfo,
                paymentInfo
            );

            logger.info("Order created successfully: orderId={}", orderId);
            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new CreateOrderResponse(orderId));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid order creation request: {}", e.getMessage());
            throw e;
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long orderId) {
        logger.info("GET /orders/{} - Retrieving order", orderId);
        Order order = orderService.getOrder(orderId);
        OrderResponse response = OrderResponse.fromOrder(order);
        logger.debug("Order retrieved: orderId={}, state={}", orderId, order.getState());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
        logger.info("POST /orders/{}/cancel - Cancelling order", orderId);
        try {
            orderService.cancelOrder(orderId);
            logger.info("Order cancellation initiated: orderId={}", orderId);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            logger.warn("Cannot cancel order: orderId={}, reason={}", orderId, e.getMessage());
            throw e;
        }
    }

    @PostMapping("/{orderId}/revise")
    public ResponseEntity<Void> reviseOrder(
        @PathVariable Long orderId,
        @Valid @RequestBody ReviseOrderRequest request
    ) {
        logger.info(
            "POST /orders/{}/revise - Revising order with {} line items",
            orderId,
            request.getRevisedLineItems().size()
        );

        try {
            List<OrderLineItem> revisedLineItems = request.getRevisedLineItems().stream()
                .map(item -> new OrderLineItem(
                    item.getMenuItemId(),
                    item.getName(),
                    item.getPrice(),
                    item.getQuantity()
                ))
                .collect(Collectors.toList());

            orderService.reviseOrder(orderId, revisedLineItems);
            logger.info("Order revision initiated: orderId={}", orderId);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            logger.warn("Cannot revise order: orderId={}, reason={}", orderId, e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            logger.warn(
                "Invalid order revision request: orderId={}, reason={}",
                orderId,
                e.getMessage()
            );
            throw e;
        }
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException e) {
        logger.warn("Order not found: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("ORDER_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailure(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage() == null
                ? "Request validation failed"
                : error.getDefaultMessage())
            .orElse("Request validation failed");
        logger.warn("Invalid order request: {}", message);
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_REQUEST", message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        logger.warn("Illegal state: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("Invalid argument: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedError(Exception e) {
        logger.error("Unexpected error", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

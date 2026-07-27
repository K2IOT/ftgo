package net.ftgo.order.api;

import jakarta.validation.Valid;
import net.ftgo.common.security.FtgoPrincipal;
import net.ftgo.common.security.PrincipalAccess;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.service.OrderNotFoundException;
import net.ftgo.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
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

    @Value("${ftgo.order.phase2-enabled:true}")
    private boolean phase2Enabled = true;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(
        @Valid @RequestBody CreateOrderRequest request,
        Authentication authentication
    ) {
        if (!phase2Enabled) {
            throw new OrderFlowDisabledException(
                "New Phase 02 order intake is temporarily disabled"
            );
        }

        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        Long consumerId = requireConsumerId(principal);

        logger.info(
            "POST /orders - Creating order: actorSubject={}, consumerId={}, restaurantId={}, expectedMenuVersion={}",
            principal.subject(),
            consumerId,
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
                consumerId,
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
    public ResponseEntity<OrderResponse> getOrder(
        @PathVariable Long orderId,
        Authentication authentication
    ) {
        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        logger.info(
            "GET /orders/{} - Retrieving order for actorSubject={}",
            orderId,
            principal.subject()
        );
        Order order = orderService.getOrder(orderId, principal);
        OrderResponse response = OrderResponse.fromOrder(order);
        logger.debug("Order retrieved: orderId={}, state={}", orderId, order.getState());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(
        @PathVariable Long orderId,
        Authentication authentication
    ) {
        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        logger.info(
            "POST /orders/{}/cancel - Cancelling order for actorSubject={}",
            orderId,
            principal.subject()
        );
        try {
            orderService.cancelOrder(orderId, principal);
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
        @Valid @RequestBody ReviseOrderRequest request,
        Authentication authentication
    ) {
        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        logger.info(
            "POST /orders/{}/revise - Revising order with {} line items for actorSubject={}",
            orderId,
            request.getRevisedLineItems().size(),
            principal.subject()
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

            orderService.reviseOrder(orderId, revisedLineItems, principal);
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

    private Long requireConsumerId(FtgoPrincipal principal) {
        if (principal.consumerId() == null) {
            logger.warn(
                "Create order denied because consumer identity is missing: actorSubject={}",
                principal.subject()
            );
            throw new AccessDeniedException("Authenticated consumer identity is required");
        }
        return principal.consumerId();
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException e) {
        logger.warn("Order not found: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("ORDER_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(OrderFlowDisabledException.class)
    public ResponseEntity<ErrorResponse> handleOrderFlowDisabled(OrderFlowDisabledException e) {
        logger.warn("Order flow disabled: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse("ORDER_FLOW_DISABLED", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        logger.warn("Order access denied: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("ACCESS_DENIED", "Order access denied"));
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

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
import org.springframework.web.bind.annotation.*;

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
 * 
 * Validation:
 * - Request validation using Jakarta Bean Validation
 * - Semantic lock validation (409 Conflict for pending states)
 * - Business rule validation in domain layer
 * 
 * Error Handling:
 * - 400 Bad Request for validation errors
 * - 404 Not Found for non-existent orders
 * - 409 Conflict for concurrent modification attempts
 * - 500 Internal Server Error for unexpected errors
 */
@RestController
@RequestMapping("/orders")
public class OrderController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    
    private final OrderService orderService;
    
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    /**
     * Creates a new order.
     * 
     * Initiates CreateOrderSaga to coordinate order approval across:
     * - Consumer Service (credit verification)
     * - Kitchen Service (ticket creation)
     * - Accounting Service (payment authorization)
     * 
     * @param request the create order request
     * @return 201 Created with order ID
     * @throws IllegalArgumentException if request validation fails (400 Bad Request)
     */
    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        logger.info("POST /orders - Creating order: consumerId={}, restaurantId={}",
            request.getConsumerId(), request.getRestaurantId());
        
        try {
            // Convert request DTOs to domain objects
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
            
            // Create order and initiate saga
            Long orderId = orderService.createOrder(
                request.getConsumerId(),
                request.getRestaurantId(),
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
            throw e; // Will be handled by @ExceptionHandler
        }
    }
    
    /**
     * Retrieves order details by ID.
     * 
     * @param orderId the order ID
     * @return 200 OK with order details
     * @throws OrderNotFoundException if order not found (404 Not Found)
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long orderId) {
        logger.info("GET /orders/{} - Retrieving order", orderId);
        
        Order order = orderService.getOrder(orderId);
        OrderResponse response = OrderResponse.fromOrder(order);
        
        logger.debug("Order retrieved: orderId={}, state={}", orderId, order.getState());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Cancels an order.
     * 
     * Initiates CancelOrderSaga to coordinate order cancellation across:
     * - Kitchen Service (ticket cancellation)
     * - Accounting Service (payment reversal)
     * 
     * Semantic Lock Validation:
     * - Returns 409 Conflict if order is in a pending state
     * - Returns 400 Bad Request if order is not in APPROVED state
     * 
     * @param orderId the order ID to cancel
     * @return 200 OK if cancellation initiated successfully
     * @throws OrderNotFoundException if order not found (404 Not Found)
     * @throws IllegalStateException if order cannot be cancelled (409 Conflict)
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
        logger.info("POST /orders/{}/cancel - Cancelling order", orderId);
        
        try {
            orderService.cancelOrder(orderId);
            
            logger.info("Order cancellation initiated: orderId={}", orderId);
            
            return ResponseEntity.ok().build();
            
        } catch (IllegalStateException e) {
            logger.warn("Cannot cancel order: orderId={}, reason={}", orderId, e.getMessage());
            throw e; // Will be handled by @ExceptionHandler
        }
    }
    
    /**
     * Revises an order.
     * 
     * Initiates ReviseOrderSaga to coordinate order revision across:
     * - Kitchen Service (ticket revision)
     * - Accounting Service (payment adjustment)
     * 
     * Semantic Lock Validation:
     * - Returns 409 Conflict if order is in a pending state
     * - Returns 400 Bad Request if order is not in APPROVED state
     * 
     * @param orderId the order ID to revise
     * @param request the revise order request with new line items
     * @return 200 OK if revision initiated successfully
     * @throws OrderNotFoundException if order not found (404 Not Found)
     * @throws IllegalStateException if order cannot be revised (409 Conflict)
     * @throws IllegalArgumentException if revised line items are invalid (400 Bad Request)
     */
    @PostMapping("/{orderId}/revise")
    public ResponseEntity<Void> reviseOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody ReviseOrderRequest request) {
        logger.info("POST /orders/{}/revise - Revising order with {} line items",
            orderId, request.getRevisedLineItems().size());
        
        try {
            // Convert request DTOs to domain objects
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
            throw e; // Will be handled by @ExceptionHandler
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid order revision request: orderId={}, reason={}",
                orderId, e.getMessage());
            throw e; // Will be handled by @ExceptionHandler
        }
    }
    
    /**
     * Exception handler for OrderNotFoundException.
     * Returns 404 Not Found.
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException e) {
        logger.warn("Order not found: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("ORDER_NOT_FOUND", e.getMessage()));
    }
    
    /**
     * Exception handler for IllegalStateException (semantic lock violations).
     * Returns 409 Conflict.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        logger.warn("Illegal state: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("CONFLICT", e.getMessage()));
    }
    
    /**
     * Exception handler for IllegalArgumentException (validation errors).
     * Returns 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("Invalid argument: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }
    
    /**
     * Exception handler for unexpected errors.
     * Returns 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedError(Exception e) {
        logger.error("Unexpected error", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

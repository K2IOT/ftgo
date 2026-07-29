package net.ftgo.order.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import net.ftgo.common.security.FtgoPrincipal;
import net.ftgo.common.security.PrincipalAccess;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.idempotency.IdempotentResult;
import net.ftgo.order.idempotency.InvalidIdempotencyKeyException;
import net.ftgo.order.idempotency.OrderMutationIdempotencyService;
import net.ftgo.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/** REST controller for Order operations. */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    private final OrderService orderService;
    private final OrderMutationIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Value("${ftgo.order.phase2-enabled:true}")
    private boolean phase2Enabled = true;

    public OrderController(
        OrderService orderService,
        OrderMutationIdempotencyService idempotencyService,
        ObjectMapper objectMapper
    ) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<String> createOrder(
        @Valid @RequestBody CreateOrderRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        Authentication authentication
    ) {
        if (!phase2Enabled) {
            throw new OrderFlowDisabledException(
                "New Phase 02 order intake is temporarily disabled"
            );
        }

        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        Long consumerId = requireConsumerId(principal);
        String key = requireIdempotencyKey(idempotencyKey);

        logger.info(
            "POST /orders - Creating order: actorSubject={}, consumerId={}, restaurantId={}, expectedMenuVersion={}",
            principal.subject(),
            consumerId,
            request.getRestaurantId(),
            request.getExpectedMenuVersion()
        );

        List<OrderLineItem> lineItems = toLineItems(request.getLineItems());
        DeliveryInfo deliveryInfo = new DeliveryInfo(
            request.getDeliveryAddress(),
            request.getDeliveryTime()
        );
        PaymentInfo paymentInfo = new PaymentInfo(request.getPaymentToken());

        IdempotentResult<String> result = idempotencyService.execute(
            consumerId,
            OrderMutationIdempotencyService.CREATE_ORDER,
            key,
            idempotencyService.hashCreate(consumerId, request),
            () -> {
                Long orderId = orderService.createOrder(
                    consumerId,
                    request.getRestaurantId(),
                    request.getExpectedMenuVersion(),
                    lineItems,
                    deliveryInfo,
                    paymentInfo
                );
                logger.info("Order created successfully: orderId={}", orderId);
                return writeJson(new CreateOrderResponse(orderId));
            }
        );

        return ResponseEntity
            .status(result.httpStatus())
            .contentType(MediaType.APPLICATION_JSON)
            .body(result.responseBody());
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
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        Authentication authentication
    ) {
        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        Long consumerId = idempotencyConsumerId(principal);
        String key = requireIdempotencyKey(idempotencyKey);
        String operation = OrderMutationIdempotencyService.cancelOperation(orderId);

        logger.info(
            "POST /orders/{}/cancel - Cancelling order for actorSubject={}",
            orderId,
            principal.subject()
        );
        IdempotentResult<String> result = idempotencyService.execute(
            consumerId,
            operation,
            key,
            idempotencyService.hashCancel(consumerId, orderId),
            () -> {
                orderService.cancelOrder(orderId, principal);
                logger.info("Order cancellation initiated: orderId={}", orderId);
                return null;
            }
        );
        return ResponseEntity.status(result.httpStatus()).build();
    }

    @PostMapping("/{orderId}/revise")
    public ResponseEntity<Void> reviseOrder(
        @PathVariable Long orderId,
        @Valid @RequestBody ReviseOrderRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        Authentication authentication
    ) {
        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        Long consumerId = idempotencyConsumerId(principal);
        String key = requireIdempotencyKey(idempotencyKey);
        String operation = OrderMutationIdempotencyService.reviseOperation(orderId);

        logger.info(
            "POST /orders/{}/revise - Revising order with {} line items for actorSubject={}",
            orderId,
            request.getRevisedLineItems().size(),
            principal.subject()
        );

        List<OrderLineItem> revisedLineItems = toLineItems(request.getRevisedLineItems());
        IdempotentResult<String> result = idempotencyService.execute(
            consumerId,
            operation,
            key,
            idempotencyService.hashRevise(consumerId, orderId, request),
            () -> {
                orderService.reviseOrder(orderId, revisedLineItems, principal);
                logger.info("Order revision initiated: orderId={}", orderId);
                return null;
            }
        );
        return ResponseEntity.status(result.httpStatus()).build();
    }

    private List<OrderLineItem> toLineItems(List<OrderLineItemRequest> requests) {
        return requests.stream()
            .map(item -> new OrderLineItem(
                item.getMenuItemId(),
                item.getName(),
                item.getPrice(),
                item.getQuantity()
            ))
            .collect(Collectors.toList());
    }

    private String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw InvalidIdempotencyKeyException.required();
        }
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH
            || !key.equals(key.trim())
            || key.chars().anyMatch(Character::isISOControl)) {
            throw InvalidIdempotencyKeyException.invalid();
        }
        return key;
    }

    private Long idempotencyConsumerId(FtgoPrincipal principal) {
        if (principal.consumerId() != null) {
            return principal.consumerId();
        }
        if (principal.roles().contains("ADMIN")) {
            return 0L;
        }
        throw new AccessDeniedException("Authenticated consumer identity is required");
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to serialize order response", error);
        }
    }
}

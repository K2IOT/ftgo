package net.ftgo.order.api;

import jakarta.servlet.http.HttpServletRequest;
import net.ftgo.common.web.FtgoProblemDetail;
import net.ftgo.common.web.FtgoProblemResponses;
import net.ftgo.order.service.OrderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OrderController.class)
public final class OrderApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(OrderApiExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<FtgoProblemDetail> orderNotFound(
        OrderNotFoundException error,
        HttpServletRequest request
    ) {
        logger.warn("Order not found: {}", error.getMessage());
        return FtgoProblemResponses.response(
            HttpStatus.NOT_FOUND,
            "order-not-found",
            "Order not found",
            "The requested order does not exist",
            "ORDER_NOT_FOUND",
            request
        );
    }

    @ExceptionHandler(OrderFlowDisabledException.class)
    ResponseEntity<FtgoProblemDetail> orderFlowDisabled(
        OrderFlowDisabledException error,
        HttpServletRequest request
    ) {
        logger.warn("Order flow disabled: {}", error.getMessage());
        return FtgoProblemResponses.response(
            HttpStatus.SERVICE_UNAVAILABLE,
            "order-flow-disabled",
            "Order intake unavailable",
            "New order intake is temporarily unavailable",
            "ORDER_FLOW_DISABLED",
            request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<FtgoProblemDetail> accessDenied(
        AccessDeniedException error,
        HttpServletRequest request
    ) {
        logger.warn("Order access denied: {}", error.getMessage());
        return FtgoProblemResponses.response(
            HttpStatus.FORBIDDEN,
            "forbidden",
            "Access denied",
            "Order access is forbidden",
            "FORBIDDEN",
            request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<FtgoProblemDetail> validationFailure(
        MethodArgumentNotValidException error,
        HttpServletRequest request
    ) {
        return FtgoProblemResponses.response(
            HttpStatus.BAD_REQUEST,
            "validation-error",
            "Validation failed",
            "Request validation failed",
            "VALIDATION_ERROR",
            request
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<FtgoProblemDetail> conflict(
        IllegalStateException error,
        HttpServletRequest request
    ) {
        logger.warn("Order state conflict: {}", error.getMessage());
        return FtgoProblemResponses.response(
            HttpStatus.CONFLICT,
            "conflict",
            "Resource conflict",
            "Request conflicts with the current order state",
            "CONFLICT",
            request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<FtgoProblemDetail> invalidRequest(
        IllegalArgumentException error,
        HttpServletRequest request
    ) {
        logger.warn("Invalid order request: {}", error.getMessage());
        return FtgoProblemResponses.response(
            HttpStatus.BAD_REQUEST,
            "invalid-request",
            "Invalid request",
            "Invalid order request",
            "INVALID_REQUEST",
            request
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<FtgoProblemDetail> unexpected(
        Exception error,
        HttpServletRequest request
    ) {
        logger.error("Unexpected order API error", error);
        return FtgoProblemResponses.response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal-error",
            "Internal server error",
            "An unexpected error occurred",
            "INTERNAL_ERROR",
            request
        );
    }
}

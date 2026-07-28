package net.ftgo.delivery.api;

import jakarta.servlet.http.HttpServletRequest;
import net.ftgo.common.web.FtgoProblemDetail;
import net.ftgo.common.web.FtgoProblemResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DeliveryController.class)
public final class DeliveryApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<FtgoProblemDetail> notFound(
        IllegalArgumentException error,
        HttpServletRequest request
    ) {
        logger.warn("Delivery not found: {}", error.getMessage());
        return FtgoProblemResponses.response(
            HttpStatus.NOT_FOUND,
            "delivery-not-found",
            "Delivery not found",
            "The requested delivery does not exist",
            "DELIVERY_NOT_FOUND",
            request
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<FtgoProblemDetail> conflict(
        IllegalStateException error,
        HttpServletRequest request
    ) {
        logger.warn("Delivery state conflict: {}", error.getMessage());
        return FtgoProblemResponses.response(
            HttpStatus.CONFLICT,
            "conflict",
            "Resource conflict",
            "Request conflicts with the current delivery state",
            "CONFLICT",
            request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<FtgoProblemDetail> forbidden(
        AccessDeniedException error,
        HttpServletRequest request
    ) {
        logger.warn("Delivery access denied: {}", error.getMessage());
        return FtgoProblemResponses.response(
            HttpStatus.FORBIDDEN,
            "forbidden",
            "Access denied",
            "Delivery access is forbidden",
            "FORBIDDEN",
            request
        );
    }
}

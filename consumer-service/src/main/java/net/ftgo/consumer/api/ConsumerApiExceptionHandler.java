package net.ftgo.consumer.api;

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

@RestControllerAdvice(assignableTypes = {ConsumerController.class, ConsumerAdminController.class})
public final class ConsumerApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerApiExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<FtgoProblemDetail> accessDenied(
        AccessDeniedException error,
        HttpServletRequest request
    ) {
        logger.warn("Consumer access denied: {}", error.getMessage());
        return FtgoProblemResponses.response(
            HttpStatus.FORBIDDEN,
            "forbidden",
            "Access denied",
            "Consumer access is forbidden",
            "FORBIDDEN",
            request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<FtgoProblemDetail> invalidRequest(
        IllegalArgumentException error,
        HttpServletRequest request
    ) {
        logger.warn("Invalid consumer request: {}", error.getMessage());
        return FtgoProblemResponses.response(
            HttpStatus.BAD_REQUEST,
            "invalid-request",
            "Invalid request",
            "Invalid consumer request",
            "INVALID_REQUEST",
            request
        );
    }
}

package net.ftgo.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Central MVC exception mapping with stable, non-sensitive RFC 9457 responses. */
@RestControllerAdvice
public final class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://ftgo.example/problems/";

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        MissingServletRequestParameterException.class,
        HttpMessageNotReadableException.class
    })
    ResponseEntity<FtgoProblemDetail> validationFailure(
        Exception error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.BAD_REQUEST,
            "validation-error",
            "Validation failed",
            "Request validation failed",
            "VALIDATION_ERROR",
            request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<FtgoProblemDetail> invalidRequest(
        IllegalArgumentException error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.BAD_REQUEST,
            "invalid-request",
            "Invalid request",
            "Invalid request",
            "INVALID_REQUEST",
            request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<FtgoProblemDetail> accessDenied(
        AccessDeniedException error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.FORBIDDEN,
            "forbidden",
            "Access denied",
            "Access to this resource is forbidden",
            "FORBIDDEN",
            request
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<FtgoProblemDetail> conflict(
        IllegalStateException error,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.CONFLICT,
            "conflict",
            "Resource conflict",
            "Request conflicts with the current resource state",
            "CONFLICT",
            request
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<FtgoProblemDetail> unexpected(
        Exception error,
        HttpServletRequest request
    ) {
        String correlationId = CorrelationIdFilter.current(request);
        logger.error("Unexpected API error; correlationId={}", correlationId, error);
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal-error",
            "Internal server error",
            "An unexpected error occurred",
            "INTERNAL_ERROR",
            request,
            correlationId
        );
    }

    private ResponseEntity<FtgoProblemDetail> response(
        HttpStatus status,
        String type,
        String title,
        String detail,
        String errorCode,
        HttpServletRequest request
    ) {
        return response(
            status,
            type,
            title,
            detail,
            errorCode,
            request,
            CorrelationIdFilter.current(request)
        );
    }

    private ResponseEntity<FtgoProblemDetail> response(
        HttpStatus status,
        String type,
        String title,
        String detail,
        String errorCode,
        HttpServletRequest request,
        String correlationId
    ) {
        FtgoProblemDetail body = new FtgoProblemDetail(
            URI.create(TYPE_BASE + type),
            title,
            status.value(),
            detail,
            URI.create(request.getRequestURI()),
            errorCode,
            correlationId
        );
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .header(CorrelationIdFilter.HEADER_NAME, correlationId)
            .body(body);
    }
}

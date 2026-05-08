package net.ftgo.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * M3: Global error handler for the API Gateway.
 *
 * Catches all unhandled exceptions and returns structured JSON error responses
 * instead of Spring Boot's default Whitelabel error page. Never leaks stack
 * traces to external clients.
 *
 * Ordered at -2 to run before Spring Boot's default error handler (-1).
 */
@Component
@Order(-2)
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalErrorHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // Don't override if response is already committed
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status;
        String error;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            error = status.getReasonPhrase().toLowerCase().replace(" ", "_");
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
        } else if (ex instanceof WebClientResponseException wcre) {
            status = HttpStatus.BAD_GATEWAY;
            error = "bad_gateway";
            message = "Downstream service returned an error: " + wcre.getStatusCode();
            logger.error("Downstream service error: {} {}", wcre.getStatusCode(), wcre.getStatusText(), wcre);
        } else if (ex instanceof WebClientRequestException) {
            status = HttpStatus.BAD_GATEWAY;
            error = "service_unreachable";
            message = "Unable to connect to downstream service";
            logger.error("Cannot connect to downstream service", ex);
        } else if (ex instanceof ConnectException) {
            status = HttpStatus.BAD_GATEWAY;
            error = "connection_refused";
            message = "Downstream service connection refused";
            logger.error("Connection refused to downstream service", ex);
        } else if (ex instanceof TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            error = "gateway_timeout";
            message = "Request to downstream service timed out";
            logger.error("Downstream service timeout", ex);
        } else if (ex instanceof org.springframework.security.access.AccessDeniedException) {
            status = HttpStatus.FORBIDDEN;
            error = "forbidden";
            message = "Access denied";
        } else if (ex instanceof org.springframework.security.core.AuthenticationException) {
            status = HttpStatus.UNAUTHORIZED;
            error = "unauthorized";
            message = "Authentication required";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            error = "internal_error";
            message = "An unexpected error occurred";
            logger.error("Unhandled exception in API Gateway", ex);
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("error", error);
        errorBody.put("message", message);
        errorBody.put("status", status.value());
        errorBody.put("path", exchange.getRequest().getPath().value());
        errorBody.put("timestamp", Instant.now().toString());

        // Include requestId if available
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        if (requestId != null) {
            errorBody.put("requestId", requestId);
        }

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorBody);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize error response", e);
            return response.setComplete();
        }
    }
}

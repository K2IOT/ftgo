package net.ftgo.gateway.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/** Converts unhandled Gateway failures to stable, correlated RFC 9457 responses. */
@Component
@Order(-2)
public final class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalErrorHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }

        ProblemSpec problem = classify(error);
        String correlationId = GatewayProblemResponses.correlationId(exchange);
        if (problem.status().is5xxServerError()) {
            logger.error("Gateway request failed; correlationId={}", correlationId, error);
        }
        return GatewayProblemResponses.write(
            exchange,
            problem.status(),
            problem.type(),
            problem.title(),
            problem.detail(),
            problem.errorCode()
        );
    }

    private ProblemSpec classify(Throwable error) {
        if (error instanceof ResponseStatusException responseStatus) {
            return forStatus(HttpStatus.valueOf(responseStatus.getStatusCode().value()));
        }
        if (error instanceof WebClientResponseException) {
            return new ProblemSpec(
                HttpStatus.BAD_GATEWAY,
                "downstream-error",
                "Downstream service error",
                "The downstream service returned an invalid response",
                "DOWNSTREAM_ERROR"
            );
        }
        if (error instanceof WebClientRequestException || error instanceof ConnectException) {
            return new ProblemSpec(
                HttpStatus.BAD_GATEWAY,
                "service-unavailable",
                "Downstream service unavailable",
                "The downstream service is unavailable",
                "SERVICE_UNAVAILABLE"
            );
        }
        if (error instanceof TimeoutException) {
            return new ProblemSpec(
                HttpStatus.GATEWAY_TIMEOUT,
                "gateway-timeout",
                "Gateway timeout",
                "The downstream service did not respond in time",
                "GATEWAY_TIMEOUT"
            );
        }
        if (error instanceof AccessDeniedException) {
            return forStatus(HttpStatus.FORBIDDEN);
        }
        if (error instanceof AuthenticationException) {
            return forStatus(HttpStatus.UNAUTHORIZED);
        }
        return new ProblemSpec(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal-error",
            "Internal server error",
            "An unexpected error occurred",
            "INTERNAL_ERROR"
        );
    }

    private ProblemSpec forStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> new ProblemSpec(
                status, "invalid-request", "Invalid request", "The request is invalid", "INVALID_REQUEST"
            );
            case UNAUTHORIZED -> new ProblemSpec(
                status,
                "unauthorized",
                "Authentication required",
                "Authentication is required to access this resource",
                "UNAUTHORIZED"
            );
            case FORBIDDEN -> new ProblemSpec(
                status, "forbidden", "Access denied", "Access to this resource is forbidden", "FORBIDDEN"
            );
            case NOT_FOUND -> new ProblemSpec(
                status, "not-found", "Resource not found", "The requested resource was not found", "NOT_FOUND"
            );
            case TOO_MANY_REQUESTS -> new ProblemSpec(
                status,
                "rate-limit-exceeded",
                "Too many requests",
                "The request rate limit was exceeded",
                "RATE_LIMIT_EXCEEDED"
            );
            default -> new ProblemSpec(
                status,
                status.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                status.getReasonPhrase(),
                "The request could not be completed",
                status.name()
            );
        };
    }

    private record ProblemSpec(
        HttpStatus status,
        String type,
        String title,
        String detail,
        String errorCode
    ) {
    }
}

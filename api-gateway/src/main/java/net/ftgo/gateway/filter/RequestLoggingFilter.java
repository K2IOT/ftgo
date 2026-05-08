package net.ftgo.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * M2: Request logging and correlation ID filter.
 *
 * Generates a unique X-Request-Id for each request, injects it into:
 * - The response headers (for client-side correlation)
 * - The MDC (for structured log correlation)
 * - The downstream request headers (for distributed tracing)
 *
 * Logs request method, path, status, and latency in structured format.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startTime = System.currentTimeMillis();

        // Extract or generate request ID
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        final String finalRequestId = requestId;

        // Mutate request to include the request ID header for downstream propagation
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, finalRequestId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Add request ID to response headers
        mutatedExchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, finalRequestId);

        // Log request
        String method = mutatedExchange.getRequest().getMethod().name();
        String path = mutatedExchange.getRequest().getPath().value();
        String clientIp = getClientIp(mutatedExchange.getRequest());

        logger.info(">>> {} {} from {} [requestId={}]", method, path, clientIp, finalRequestId);

        return chain.filter(mutatedExchange)
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - startTime;
                    ServerHttpResponse response = mutatedExchange.getResponse();
                    int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 0;

                    if (statusCode >= 500) {
                        logger.error("<<< {} {} → {} ({}ms) [requestId={}]",
                                method, path, statusCode, duration, finalRequestId);
                    } else if (statusCode >= 400) {
                        logger.warn("<<< {} {} → {} ({}ms) [requestId={}]",
                                method, path, statusCode, duration, finalRequestId);
                    } else {
                        logger.info("<<< {} {} → {} ({}ms) [requestId={}]",
                                method, path, statusCode, duration, finalRequestId);
                    }
                });
    }

    /**
     * Extract client IP from request, respecting X-Forwarded-For if present.
     */
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Take the first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}

package net.ftgo.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.web.CorrelationIdFilter;
import net.ftgo.common.web.FtgoProblemDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/** Reactive RFC 9457 writer shared by Gateway security and fallback handlers. */
public final class GatewayProblemResponses {

    private static final Logger logger = LoggerFactory.getLogger(GatewayProblemResponses.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TYPE_BASE = "https://ftgo.example/problems/";
    private static final String CORRELATION_ATTRIBUTE =
        GatewayProblemResponses.class.getName() + ".correlationId";

    private GatewayProblemResponses() {
    }

    public static String correlationId(ServerWebExchange exchange) {
        Object existing = exchange.getAttribute(CORRELATION_ATTRIBUTE);
        if (existing instanceof String correlationId && CorrelationIdFilter.isSafe(correlationId)) {
            return correlationId;
        }
        String correlationId = CorrelationIdFilter.normalizeOrGenerate(
            exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)
        );
        exchange.getAttributes().put(CORRELATION_ATTRIBUTE, correlationId);
        return correlationId;
    }

    public static Mono<Void> write(
        ServerWebExchange exchange,
        HttpStatus status,
        String type,
        String title,
        String detail,
        String errorCode
    ) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(new IllegalStateException("Response is already committed"));
        }

        String correlationId = correlationId(exchange);
        FtgoProblemDetail body = new FtgoProblemDetail(
            URI.create(TYPE_BASE + type),
            title,
            status.value(),
            detail,
            URI.create(exchange.getRequest().getPath().value()),
            errorCode,
            correlationId
        );

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        exchange.getResponse().getHeaders().set(CorrelationIdFilter.HEADER_NAME, correlationId);
        try {
            byte[] bytes = JSON.writeValueAsBytes(body);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException serializationError) {
            logger.error(
                "Failed to serialize Gateway problem response; correlationId={}",
                correlationId,
                serializationError
            );
            return exchange.getResponse().setComplete();
        }
    }
}

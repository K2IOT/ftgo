package net.ftgo.gateway.observability;

import net.ftgo.common.observability.CorrelationContext;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonicalizes incoming W3C propagation headers and forwards the same stable
 * correlation context to downstream services and the caller.
 *
 * <p>The filter deliberately does not open a ThreadLocal scope because Reactor
 * execution can switch threads. OpenTelemetry's Reactor instrumentation owns
 * the reactive span context.</p>
 */
@Component
public class CorrelationPropagationFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        CorrelationContext.Snapshot snapshot = CorrelationContext.resolve(
            headers(exchange.getRequest().getHeaders())
        );
        ServerHttpRequest request = exchange.getRequest()
            .mutate()
            .headers(values -> CorrelationContext.inject(snapshot, values::set))
            .build();
        CorrelationContext.inject(snapshot, exchange.getResponse().getHeaders()::set);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    private Map<String, String> headers(HttpHeaders headers) {
        Map<String, String> values = new LinkedHashMap<>();
        copyHeader(headers, values, CorrelationContext.TRACEPARENT);
        copyHeader(headers, values, CorrelationContext.TRACESTATE);
        copyHeader(headers, values, CorrelationContext.BAGGAGE);
        copyHeader(headers, values, CorrelationContext.CORRELATION_ID);
        copyHeader(headers, values, CorrelationContext.CAUSATION_ID);
        return values;
    }

    private void copyHeader(HttpHeaders headers, Map<String, String> values, String name) {
        String value = headers.getFirst(name);
        if (value != null) {
            values.put(name, value);
        }
    }
}

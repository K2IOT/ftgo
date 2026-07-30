package net.ftgo.gateway.filter;

import net.ftgo.common.web.CorrelationIdFilter;
import net.ftgo.gateway.security.ForwardedHeaderPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Validates and propagates the canonical FTGO correlation header. */
@Component
public final class GatewayCorrelationFilter implements WebFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(GatewayCorrelationFilter.class);
    private static final String LEGACY_REQUEST_ID = "X-Request-Id";

    private final ForwardedHeaderPolicy forwardedHeaderPolicy;

    public GatewayCorrelationFilter(ForwardedHeaderPolicy forwardedHeaderPolicy) {
        this.forwardedHeaderPolicy = forwardedHeaderPolicy;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = CorrelationIdFilter.normalizeOrGenerate(
            exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME)
        );
        String clientAddress = forwardedHeaderPolicy.resolveClientAddress(exchange);
        long startedAt = System.nanoTime();

        ServerWebExchange correlated = exchange.mutate()
            .request(request -> request.headers(headers -> {
                headers.remove(LEGACY_REQUEST_ID);
                headers.set(CorrelationIdFilter.HEADER_NAME, correlationId);
            }))
            .build();
        correlated.getResponse().getHeaders().set(CorrelationIdFilter.HEADER_NAME, correlationId);

        return Mono.defer(() -> {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
            logger.info(
                "Gateway request started method={} path={} clientAddress={} correlationId={}",
                correlated.getRequest().getMethod(),
                correlated.getRequest().getPath().value(),
                clientAddress,
                correlationId
            );
            return chain.filter(correlated).doFinally(signal -> {
                long durationMillis = (System.nanoTime() - startedAt) / 1_000_000L;
                int status = correlated.getResponse().getStatusCode() == null
                    ? 0
                    : correlated.getResponse().getStatusCode().value();
                logger.info(
                    "Gateway request completed method={} path={} status={} durationMs={} correlationId={}",
                    correlated.getRequest().getMethod(),
                    correlated.getRequest().getPath().value(),
                    status,
                    durationMillis,
                    correlationId
                );
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            });
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}

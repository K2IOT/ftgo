package net.ftgo.gateway.observability;

import net.ftgo.common.observability.CorrelationContext;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationPropagationFilterTest {

    private static final String TRACEPARENT =
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Test
    void canonicalizesAndPropagatesContextDownstreamAndBackToCaller() {
        CorrelationPropagationFilter filter = new CorrelationPropagationFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/orders")
                .header(CorrelationContext.TRACEPARENT, TRACEPARENT)
                .header(CorrelationContext.TRACESTATE, "vendor=value")
                .header(CorrelationContext.BAGGAGE, "tenant=t-101")
                .header(CorrelationContext.CORRELATION_ID, "corr-gateway-101")
                .build()
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = downstream -> {
            forwarded.set(downstream);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders downstreamHeaders = forwarded.get().getRequest().getHeaders();
        assertThat(downstreamHeaders.getFirst(CorrelationContext.TRACEPARENT))
            .isEqualTo(TRACEPARENT);
        assertThat(downstreamHeaders.getFirst(CorrelationContext.TRACESTATE))
            .isEqualTo("vendor=value");
        assertThat(downstreamHeaders.getFirst(CorrelationContext.CORRELATION_ID))
            .isEqualTo("corr-gateway-101");
        assertThat(downstreamHeaders.getFirst(CorrelationContext.BAGGAGE))
            .contains("tenant=t-101")
            .contains("ftgo.correlation_id=corr-gateway-101");

        assertThat(exchange.getResponse().getHeaders()
            .getFirst(CorrelationContext.CORRELATION_ID))
            .isEqualTo("corr-gateway-101");
        assertThat(exchange.getResponse().getHeaders()
            .getFirst(CorrelationContext.TRACEPARENT))
            .isEqualTo(TRACEPARENT);
    }

    @Test
    void generatesContextWhenCallerDoesNotProvideOne() {
        CorrelationPropagationFilter filter = new CorrelationPropagationFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/orders").build()
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, downstream -> {
            forwarded.set(downstream);
            return Mono.empty();
        })).verifyComplete();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(CorrelationContext.CORRELATION_ID))
            .matches("[0-9a-f]{32}");
        assertThat(headers.getFirst(CorrelationContext.TRACEPARENT))
            .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]");
        assertThat(headers.getFirst(CorrelationContext.BAGGAGE))
            .contains("ftgo.correlation_id=");
    }
}

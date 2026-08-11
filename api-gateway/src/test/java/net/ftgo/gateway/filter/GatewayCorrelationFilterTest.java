package net.ftgo.gateway.filter;

import net.ftgo.common.web.CorrelationIds;
import net.ftgo.gateway.security.ForwardedHeaderPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayCorrelationFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void preservesSafeCorrelationIdAndPropagatesItDownstreamAndBackToClient() {
        GatewayCorrelationFilter filter = new GatewayCorrelationFilter(new ForwardedHeaderPolicy(""));
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/orders/1")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 54321))
                .header(CorrelationIds.HEADER_NAME, "corr-12345678")
                .header("X-Request-Id", "legacy-request-id")
        );
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, captured -> {
                downstream.set(captured);
                return Mono.empty();
            }))
            .verifyComplete();

        assertThat(downstream.get().getRequest().getHeaders()
            .getFirst(CorrelationIds.HEADER_NAME)).isEqualTo("corr-12345678");
        assertThat(downstream.get().getRequest().getHeaders().containsHeader("X-Request-Id"))
            .isFalse();
        assertThat(exchange.getResponse().getHeaders()
            .getFirst(CorrelationIds.HEADER_NAME)).isEqualTo("corr-12345678");
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeOrOversizedCorrelationIds() {
        GatewayCorrelationFilter filter = new GatewayCorrelationFilter(new ForwardedHeaderPolicy(""));
        String oversized = "x".repeat(129);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/orders/1")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 54321))
                .header(CorrelationIds.HEADER_NAME, oversized)
        );
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, captured -> {
                downstream.set(captured);
                return Mono.empty();
            }))
            .verifyComplete();

        String generated = downstream.get().getRequest().getHeaders()
            .getFirst(CorrelationIds.HEADER_NAME);
        assertThat(generated).isNotBlank().isNotEqualTo(oversized);
        assertThat(generated).matches("[A-Za-z0-9._:-]{8,128}");
        assertThat(exchange.getResponse().getHeaders()
            .getFirst(CorrelationIds.HEADER_NAME)).isEqualTo(generated);
    }

    @Test
    void usesTrustedForwardedHeaderPolicyForClientAddress() {
        ForwardedHeaderPolicy policy = new ForwardedHeaderPolicy("10.0.0.0/8");
        MockServerWebExchange trustedProxy = MockServerWebExchange.from(
            MockServerHttpRequest.get("/orders/1")
                .remoteAddress(new InetSocketAddress("10.1.2.3", 443))
                .header("X-Forwarded-For", "203.0.113.10, 10.1.2.3")
        );
        MockServerWebExchange untrustedPeer = MockServerWebExchange.from(
            MockServerHttpRequest.get("/orders/1")
                .remoteAddress(new InetSocketAddress("198.51.100.8", 443))
                .header("X-Forwarded-For", "203.0.113.10")
        );

        assertThat(policy.resolveClientAddress(trustedProxy)).isEqualTo("203.0.113.10");
        assertThat(policy.resolveClientAddress(untrustedPeer)).isEqualTo("198.51.100.8");
    }
}

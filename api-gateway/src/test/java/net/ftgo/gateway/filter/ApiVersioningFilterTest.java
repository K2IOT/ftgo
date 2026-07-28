package net.ftgo.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ApiVersioningFilterTest {

    private final ApiVersioningFilter filter = new ApiVersioningFilter();

    @Test
    void rewritesVersionedPublicPathBeforeSecurityAndRouting() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/orders/123?expand=ticket").build()
        );
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, candidate -> {
            captured.set(candidate);
            return candidate.getResponse().setComplete();
        }).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getPath().value()).isEqualTo("/orders/123");
        assertThat(captured.get().getRequest().getQueryParams().getFirst("expand"))
            .isEqualTo("ticket");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-API-Version"))
            .isEqualTo("1");
        assertThat(exchange.getResponse().getHeaders().getFirst("Deprecation")).isNull();
    }

    @Test
    void legacyPublicPathRemainsAvailableWithDeprecationMetadata() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/orders/123").build()
        );

        filter.filter(exchange, candidate -> candidate.getResponse().setComplete()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("Deprecation"))
            .isEqualTo("true");
        assertThat(exchange.getResponse().getHeaders().getFirst("Link"))
            .contains("</api/v1/orders/123>")
            .contains("successor-version");
    }

    @Test
    void actuatorAndAdminNamespacesAreNotRewrittenOrDeprecated() {
        assertUnchanged("/actuator/health/liveness");
        assertUnchanged("/api/admin/payment-settlement/authorizations/1");
        assertUnchanged("/api/v1/unknown-resource");
    }

    private void assertUnchanged(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get(path).build()
        );
        AtomicReference<String> capturedPath = new AtomicReference<>();

        filter.filter(exchange, candidate -> {
            capturedPath.set(candidate.getRequest().getPath().value());
            return candidate.getResponse().setComplete();
        }).block();

        assertThat(capturedPath.get()).isEqualTo(path);
        assertThat(exchange.getResponse().getHeaders().getFirst("Deprecation")).isNull();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-API-Version")).isNull();
    }
}

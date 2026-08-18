package net.ftgo.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityResponseHeadersFilterTest {

    @Test
    void stripsClientIdentityHeadersWithoutReplacingBearerIdentity() {
        SecurityResponseHeadersFilter filter = new SecurityResponseHeadersFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/orders/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer signed-token")
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Roles", "ROLE_ADMIN")
        );
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, captured -> {
                downstream.set(captured);
                return Mono.empty();
            }))
            .verifyComplete();

        HttpHeaders headers = downstream.get().getRequest().getHeaders();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer signed-token");
        assertThat(headers.containsHeader("X-User-Id")).isFalse();
        assertThat(headers.containsHeader("X-User-Roles")).isFalse();
    }

    @Test
    void addsOnlyResponseHardeningHeaders() {
        SecurityResponseHeadersFilter filter = new SecurityResponseHeadersFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/restaurants/1")
        );

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty()))
            .verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"))
            .isEqualTo("nosniff");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Frame-Options"))
            .isEqualTo("DENY");
        assertThat(exchange.getResponse().getHeaders().getFirst("Referrer-Policy"))
            .isEqualTo("strict-origin-when-cross-origin");
        assertThat(exchange.getResponse().getHeaders().getFirst("Cache-Control"))
            .isEqualTo("no-store");
    }
}

package net.ftgo.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ForwardedHeaderPolicyTest {

    @Test
    void untrustedPeerCannotSpoofForwardedClientAddress() {
        ForwardedHeaderPolicy policy = new ForwardedHeaderPolicy("10.0.0.0/8");
        MockServerWebExchange exchange = exchange(
            "203.0.113.9",
            "198.51.100.7, 10.1.2.3"
        );

        assertThat(policy.resolveClientAddress(exchange)).isEqualTo("203.0.113.9");

        AtomicReference<ServerWebExchange> filtered = new AtomicReference<>();
        policy.filter(exchange, candidate -> {
            filtered.set(candidate);
            return Mono.empty();
        }).block();

        assertThat(filtered.get()).isNotNull();
        assertThat(filtered.get().getRequest().getHeaders().getFirst("X-Forwarded-For")).isNull();
        assertThat(filtered.get().getRequest().getHeaders().getFirst(HttpHeaders.FORWARDED)).isNull();
    }

    @Test
    void trustedProxyUsesFirstValidForwardedAddress() {
        ForwardedHeaderPolicy policy = new ForwardedHeaderPolicy("10.0.0.0/8");
        MockServerWebExchange exchange = exchange(
            "10.1.2.3",
            "198.51.100.7, 10.1.2.3"
        );

        assertThat(policy.resolveClientAddress(exchange)).isEqualTo("198.51.100.7");
    }

    @Test
    void malformedForwardedAddressFallsBackToRemotePeer() {
        ForwardedHeaderPolicy policy = new ForwardedHeaderPolicy("10.0.0.0/8");
        MockServerWebExchange exchange = exchange("10.1.2.3", "attacker.example");

        assertThat(policy.resolveClientAddress(exchange)).isEqualTo("10.1.2.3");
    }

    private MockServerWebExchange exchange(String remoteAddress, String forwardedFor) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/restaurants")
            .remoteAddress(new InetSocketAddress(remoteAddress, 12345))
            .header("X-Forwarded-For", forwardedFor)
            .header(HttpHeaders.FORWARDED, "for=\"198.51.100.7\"")
            .build();
        return MockServerWebExchange.from(request);
    }
}

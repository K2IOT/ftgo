package net.ftgo.gateway.security;

import net.ftgo.gateway.config.GatewayConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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

    @Test
    void rateLimitResolverUsesSanitizedAddressForAnonymousRequest() {
        ForwardedHeaderPolicy policy = new ForwardedHeaderPolicy("10.0.0.0/8");
        GatewayConfiguration configuration = new GatewayConfiguration();

        StepVerifier.create(configuration.userKeyResolver(policy).resolve(exchange(
                "203.0.113.9",
                "198.51.100.7"
            )))
            .expectNext("203.0.113.9")
            .verifyComplete();
    }

    @Test
    void rateLimitResolverPrefersAuthenticatedSubject() {
        ForwardedHeaderPolicy policy = new ForwardedHeaderPolicy("");
        GatewayConfiguration configuration = new GatewayConfiguration();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
            "consumer-101",
            "",
            "ROLE_CONSUMER"
        );
        ServerWebExchange authenticated = exchange("203.0.113.9", "198.51.100.7")
            .mutate()
            .principal(Mono.just(authentication))
            .build();

        StepVerifier.create(configuration.userKeyResolver(policy).resolve(authenticated))
            .expectNext("consumer-101")
            .verifyComplete();
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

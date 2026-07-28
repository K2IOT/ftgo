package net.ftgo.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Defines the trust boundary for proxy-provided client address headers.
 *
 * <p>Forwarded headers are ignored and stripped unless the direct peer is in
 * the configured CIDR allowlist. The default empty allowlist trusts no proxy.
 */
@Component
public final class ForwardedHeaderPolicy implements WebFilter, Ordered {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final Set<String> FORWARDED_HEADERS = Set.of(
        HttpHeaders.FORWARDED,
        X_FORWARDED_FOR,
        "X-Forwarded-Host",
        "X-Forwarded-Port",
        "X-Forwarded-Proto",
        "X-Forwarded-Prefix"
    );

    private final List<IpAddressMatcher> trustedProxies;

    public ForwardedHeaderPolicy(
        @Value("${ftgo.gateway.trusted-proxies:}") String trustedProxyCidrs
    ) {
        this.trustedProxies = parseMatchers(trustedProxyCidrs);
    }

    /** Returns a stable rate-limit key address without trusting arbitrary headers. */
    public String resolveClientAddress(ServerWebExchange exchange) {
        String remoteAddress = directPeerAddress(exchange);
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }
        String firstHop = forwardedFor.split(",", 2)[0].trim();
        String normalized = normalizeIpLiteral(firstHop);
        return normalized == null ? remoteAddress : normalized;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String remoteAddress = directPeerAddress(exchange);
        if (isTrustedProxy(remoteAddress)) {
            return chain.filter(exchange);
        }

        ServerWebExchange sanitized = exchange.mutate()
            .request(request -> request.headers(headers ->
                FORWARDED_HEADERS.forEach(headers::remove)))
            .build();
        return chain.filter(sanitized);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private List<IpAddressMatcher> parseMatchers(String configuredCidrs) {
        if (configuredCidrs == null || configuredCidrs.isBlank()) {
            return List.of();
        }
        return Arrays.stream(configuredCidrs.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(IpAddressMatcher::new)
            .toList();
    }

    private boolean isTrustedProxy(String address) {
        if (address == null || address.isBlank() || "unknown".equals(address)) {
            return false;
        }
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(address));
    }

    private String directPeerAddress(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null) {
            return "unknown";
        }
        InetAddress address = remote.getAddress();
        return address == null ? remote.getHostString() : address.getHostAddress();
    }

    private String normalizeIpLiteral(String candidate) {
        String value = candidate;
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isBlank() || !value.matches("[0-9A-Fa-f:.]+")
            || (!value.contains(".") && !value.contains(":"))) {
            return null;
        }
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }
}

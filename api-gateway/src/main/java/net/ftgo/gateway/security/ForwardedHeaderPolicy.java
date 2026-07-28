package net.ftgo.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
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

    private static final String FORWARDED = "Forwarded";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final Set<String> FORWARDED_HEADERS = Set.of(
        FORWARDED,
        X_FORWARDED_FOR,
        "X-Forwarded-Host",
        "X-Forwarded-Port",
        "X-Forwarded-Proto",
        "X-Forwarded-Prefix"
    );

    private final List<CidrRange> trustedProxies;

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

    private List<CidrRange> parseMatchers(String configuredCidrs) {
        if (configuredCidrs == null || configuredCidrs.isBlank()) {
            return List.of();
        }
        return Arrays.stream(configuredCidrs.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(CidrRange::parse)
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
        InetAddress address = parseIpLiteral(candidate);
        return address == null ? null : address.getHostAddress();
    }

    private static InetAddress parseIpLiteral(String candidate) {
        if (candidate == null) {
            return null;
        }
        String value = candidate.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        int zoneIndex = value.indexOf('%');
        if (zoneIndex >= 0) {
            value = value.substring(0, zoneIndex);
        }
        if (value.isBlank() || !value.matches("[0-9A-Fa-f:.]+")
            || (!value.contains(".") && !value.contains(":"))) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class CidrRange {
        private final byte[] network;
        private final int prefixLength;

        private CidrRange(byte[] network, int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
        }

        static CidrRange parse(String cidr) {
            String[] parts = cidr.split("/", 2);
            InetAddress address = parseIpLiteral(parts[0]);
            if (address == null) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr);
            }
            byte[] bytes = address.getAddress();
            int maximumPrefix = bytes.length * Byte.SIZE;
            int prefix = parts.length == 1
                ? maximumPrefix
                : parsePrefix(parts[1], maximumPrefix, cidr);
            return new CidrRange(mask(bytes, prefix), prefix);
        }

        boolean matches(String candidate) {
            InetAddress address = parseIpLiteral(candidate);
            if (address == null) {
                return false;
            }
            byte[] bytes = address.getAddress();
            return bytes.length == network.length
                && Arrays.equals(mask(bytes, prefixLength), network);
        }

        private static int parsePrefix(String raw, int maximum, String cidr) {
            try {
                int value = Integer.parseInt(raw.trim());
                if (value < 0 || value > maximum) {
                    throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr);
                }
                return value;
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr, error);
            }
        }

        private static byte[] mask(byte[] address, int prefixLength) {
            byte[] result = Arrays.copyOf(address, address.length);
            int fullBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            if (remainingBits > 0 && fullBytes < result.length) {
                int mask = 0xFF << (Byte.SIZE - remainingBits);
                result[fullBytes] = (byte) (result[fullBytes] & mask);
                fullBytes++;
            }
            Arrays.fill(result, fullBytes, result.length, (byte) 0);
            return result;
        }
    }
}

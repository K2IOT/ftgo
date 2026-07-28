package net.ftgo.e2e.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight RS256 identity provider for real-process security verification.
 *
 * <p>It exposes only a JWKS endpoint and signs short-lived tokens with explicit
 * issuer, audience, role, and domain ownership claims. It is deliberately
 * test-only and never participates in production runtime code.
 */
public final class TestIdentityProvider implements AutoCloseable {

    private static final String REALM_PATH = "/realms/ftgo";
    private static final String JWKS_PATH = REALM_PATH + "/protocol/openid-connect/certs";

    private final HttpServer server;
    private final ExecutorService executor;
    private final RSAKey signingKey;
    private final String issuer;

    private TestIdentityProvider(HttpServer server, ExecutorService executor, RSAKey signingKey) {
        this.server = server;
        this.executor = executor;
        this.signingKey = signingKey;
        this.issuer = "http://localhost:" + server.getAddress().getPort() + REALM_PATH;
    }

    public static TestIdentityProvider start(int port) throws Exception {
        RSAKey signingKey = new RSAKeyGenerator(2048)
            .keyID("ftgo-e2e-" + UUID.randomUUID())
            .generate();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ftgo-test-identity-provider");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);

        TestIdentityProvider identityProvider = new TestIdentityProvider(server, executor, signingKey);
        server.createContext(JWKS_PATH, identityProvider::serveJwks);
        server.createContext(REALM_PATH + "/.well-known/openid-configuration", identityProvider::serveDiscovery);
        server.start();
        return identityProvider;
    }

    public String issuer() {
        return issuer;
    }

    public String jwkSetUri() {
        return issuer + "/protocol/openid-connect/certs";
    }

    public String keyId() {
        return signingKey.getKeyID();
    }

    public String issueToken(
        String subject,
        List<String> roles,
        List<String> audiences,
        Map<String, ?> domainClaims,
        Duration lifetime
    ) throws Exception {
        Objects.requireNonNull(subject, "subject is required");
        Objects.requireNonNull(roles, "roles are required");
        Objects.requireNonNull(audiences, "audiences are required");
        Objects.requireNonNull(domainClaims, "domainClaims are required");
        Objects.requireNonNull(lifetime, "lifetime is required");

        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject)
            .audience(List.copyOf(audiences))
            .issueTime(Date.from(now))
            .notBeforeTime(Date.from(now.minusSeconds(1)))
            .expirationTime(Date.from(now.plus(lifetime)))
            .jwtID(UUID.randomUUID().toString())
            .claim("roles", List.copyOf(roles));
        domainClaims.forEach(claims::claim);

        SignedJWT token = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signingKey.getKeyID())
                .type(com.nimbusds.jose.JOSEObjectType.JWT)
                .build(),
            claims.build()
        );
        token.sign(new RSASSASigner(signingKey));
        return token.serialize();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void serveJwks(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        JWKSet publicKeys = new JWKSet(signingKey.toPublicJWK());
        writeJson(exchange, 200, publicKeys.toString());
    }

    private void serveDiscovery(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("issuer", issuer);
        metadata.put("jwks_uri", jwkSetUri());
        String body = "{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + jwkSetUri() + "\"}";
        writeJson(exchange, 200, body);
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }
}

package net.ftgo.e2e;

import net.ftgo.e2e.support.TestIdentityProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FTGO_E2E_ENABLED", matches = "true")
class ApiAbuseTest {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private static final String GATEWAY_URL = System.getProperty(
        "ftgo.e2e.gateway-url", "http://localhost:8080");

    private static TestIdentityProvider identityProvider;
    private static String adminToken;
    private static String consumerToken;

    @BeforeAll
    static void startIdentityProvider() throws Exception {
        identityProvider = TestIdentityProvider.start(19000);
        adminToken = identityProvider.issueToken(
            "api-abuse-admin",
            List.of("ADMIN"),
            List.of("ftgo-api"),
            Map.of(),
            Duration.ofMinutes(30)
        );
        consumerToken = identityProvider.issueToken(
            "api-abuse-consumer",
            List.of("CONSUMER"),
            List.of("ftgo-api"),
            Map.of("consumer_id", 900001L),
            Duration.ofMinutes(30)
        );
    }

    @AfterAll
    static void stopIdentityProvider() {
        if (identityProvider != null) {
            identityProvider.close();
        }
    }

    @Test
    void oversizedMutationIsRejectedBeforeReachingOrderService() {
        String oversizedJson = "{\"padding\":\"" + "x".repeat(300 * 1024) + "\"}";
        HttpResponse<String> response = send(
            "POST",
            GATEWAY_URL + "/orders",
            oversizedJson,
            consumerToken,
            null
        );

        assertThat(response.statusCode())
            .withFailMessage("Oversized response status=%s body=%s", response.statusCode(), response.body())
            .isEqualTo(413);
    }

    @Test
    void malformedBearerTokenIsRejected() {
        HttpResponse<String> response = send(
            "GET",
            GATEWAY_URL + "/orders/1",
            null,
            "not-a-jwt",
            null
        );

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void consumerCannotReachPaymentSettlementAdminRoute() {
        HttpResponse<String> response = send(
            "GET",
            GATEWAY_URL + "/api/admin/payment-settlement/non-existent",
            null,
            consumerToken,
            null
        );

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void changingForwardedForCannotBypassSubjectRateLimit() {
        List<CompletableFuture<HttpResponse<String>>> requests = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            HttpRequest request = request(
                "GET",
                GATEWAY_URL + "/api/admin/payment-settlement/non-existent",
                null,
                adminToken,
                "198.51.100." + (index + 1)
            );
            requests.add(HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
        }

        List<Integer> statuses = requests.stream()
            .map(CompletableFuture::join)
            .map(HttpResponse::statusCode)
            .toList();

        assertThat(statuses)
            .withFailMessage("Expected rate limiting, received statuses=%s", statuses)
            .contains(429);
        assertThat(statuses.stream().filter(status -> status == 429).count()).isPositive();
    }

    private static HttpResponse<String> send(
        String method,
        String url,
        String body,
        String token,
        String forwardedFor
    ) {
        try {
            return HTTP.send(
                request(method, url, body, token, forwardedFor),
                HttpResponse.BodyHandlers.ofString()
            );
        } catch (Exception error) {
            throw new RuntimeException("HTTP request failed: " + method + " " + url, error);
        }
    }

    private static HttpRequest request(
        String method,
        String url,
        String body,
        String token,
        String forwardedFor
    ) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
            request.header("Forwarded", "for=\"" + forwardedFor + "\"");
        }
        if ("GET".equals(method)) {
            request.GET();
        } else {
            request.method(
                method,
                HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body)
            );
        }
        return request.build();
    }
}

package net.ftgo.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FTGO_E2E_ENABLED", matches = "true")
class SecurityAuthorizationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private static final String GATEWAY_URL = System.getProperty(
        "ftgo.e2e.gateway-url", "http://localhost:8080");
    private static final String CONSUMER_URL = System.getProperty(
        "ftgo.e2e.consumer-url", "http://localhost:8082");
    private static final String RESTAURANT_URL = System.getProperty(
        "ftgo.e2e.restaurant-url", "http://localhost:8083");
    private static final String ORDER_HISTORY_URL = System.getProperty(
        "ftgo.e2e.order-history-url", "http://localhost:8087");

    private static TestIdentityProvider identityProvider;
    private static String adminToken;
    private static String consumerAToken;
    private static String consumerBToken;
    private static String restaurantAToken;
    private static long consumerAId;
    private static long consumerBId;
    private static long restaurantAId;
    private static long restaurantBId;

    @BeforeAll
    static void setUpSecurityFixtures() throws Exception {
        identityProvider = TestIdentityProvider.start(19000);
        adminToken = token(
            "security-e2e-admin",
            List.of("ADMIN"),
            List.of("ftgo-api"),
            Map.of(),
            Duration.ofMinutes(30)
        );

        consumerAId = createConsumer("A");
        consumerBId = createConsumer("B");
        consumerAToken = token(
            "consumer-" + consumerAId,
            List.of("CONSUMER"),
            List.of("ftgo-api"),
            Map.of("consumer_id", consumerAId),
            Duration.ofMinutes(30)
        );
        consumerBToken = token(
            "consumer-" + consumerBId,
            List.of("CONSUMER"),
            List.of("ftgo-api"),
            Map.of("consumer_id", consumerBId),
            Duration.ofMinutes(30)
        );

        restaurantAId = createRestaurant("A");
        restaurantBId = createRestaurant("B");
        restaurantAToken = token(
            "restaurant-" + restaurantAId,
            List.of("RESTAURANT"),
            List.of("ftgo-api"),
            Map.of("restaurant_ids", List.of(restaurantAId)),
            Duration.ofMinutes(30)
        );
    }

    @AfterAll
    static void tearDownIdentityProvider() {
        if (identityProvider != null) {
            identityProvider.close();
        }
    }

    @Test
    void missingTokenIsRejectedAtGatewayAndDirectService() {
        assertStatus(send("GET", GATEWAY_URL + "/orders/999999", null, null), 401);
        assertStatus(send("GET", CONSUMER_URL + "/consumers/" + consumerAId, null, null), 401);
    }

    @Test
    void expiredAndWrongAudienceTokensAreRejected() throws Exception {
        String expired = token(
            "consumer-" + consumerAId,
            List.of("CONSUMER"),
            List.of("ftgo-api"),
            Map.of("consumer_id", consumerAId),
            Duration.ofSeconds(-30)
        );
        String wrongAudience = token(
            "consumer-" + consumerAId,
            List.of("CONSUMER"),
            List.of("another-api"),
            Map.of("consumer_id", consumerAId),
            Duration.ofMinutes(5)
        );

        assertStatus(send(
            "GET",
            CONSUMER_URL + "/consumers/" + consumerAId,
            null,
            expired
        ), 401);
        assertStatus(send(
            "GET",
            CONSUMER_URL + "/consumers/" + consumerAId,
            null,
            wrongAudience
        ), 401);
    }

    @Test
    void internalAudienceCannotAccessPublicRoutes() throws Exception {
        String internalConsumer = token(
            "internal-consumer-" + consumerAId,
            List.of("CONSUMER"),
            List.of("ftgo-internal"),
            Map.of("consumer_id", consumerAId),
            Duration.ofMinutes(5)
        );

        assertStatus(send(
            "GET",
            CONSUMER_URL + "/consumers/" + consumerAId,
            null,
            internalConsumer
        ), 403);
        assertStatus(send(
            "GET",
            GATEWAY_URL + "/consumers/" + consumerAId,
            null,
            internalConsumer
        ), 403);
    }

    @Test
    void scopeAndLegacyAuthoritiesCannotGrantAdmin() throws Exception {
        ObjectNode body = JSON.createObjectNode().put("creditLimit", "999999.00");
        String scopeAdmin = token(
            "scope-admin",
            List.of(),
            List.of("ftgo-api"),
            Map.of("scope", "openid ADMIN SERVICE"),
            Duration.ofMinutes(5)
        );
        String legacyAuthorityAdmin = token(
            "legacy-authority-admin",
            List.of(),
            List.of("ftgo-api"),
            Map.of("authorities", List.of("ROLE_ADMIN")),
            Duration.ofMinutes(5)
        );

        assertStatus(send(
            "PUT",
            CONSUMER_URL + "/admin/consumers/" + consumerBId + "/credit-limit",
            body,
            scopeAdmin
        ), 403);
        assertStatus(send(
            "PUT",
            GATEWAY_URL + "/admin/consumers/" + consumerBId + "/credit-limit",
            body,
            legacyAuthorityAdmin
        ), 403);
    }

    @Test
    void unknownApplicationRoleIsRejected() throws Exception {
        String unknownRole = token(
            "unknown-role-user",
            List.of("SUPERUSER"),
            List.of("ftgo-api"),
            Map.of(),
            Duration.ofMinutes(5)
        );

        assertStatus(send(
            "GET",
            CONSUMER_URL + "/consumers/" + consumerAId,
            null,
            unknownRole
        ), 401);
    }

    @Test
    void authenticatedUnknownRoutesAreDeniedAtEdgeAndDirectPort() {
        assertStatus(send(
            "GET",
            CONSUMER_URL + "/security/unknown",
            null,
            adminToken
        ), 403);
        assertStatus(send(
            "GET",
            GATEWAY_URL + "/security/unknown",
            null,
            adminToken
        ), 403);
    }

    @Test
    void consumerCannotReadAnotherConsumerProfile() {
        assertStatus(send(
            "GET",
            CONSUMER_URL + "/consumers/" + consumerBId,
            null,
            consumerAToken
        ), 403);
        assertStatus(send(
            "GET",
            CONSUMER_URL + "/consumers/" + consumerAId,
            null,
            consumerAToken
        ), 200);
    }

    @Test
    void consumerCannotReadAnotherConsumersOrderHistoryAtEdgeOrDirectPort() {
        assertStatus(send(
            "GET",
            ORDER_HISTORY_URL + "/api/consumers/" + consumerBId + "/orders",
            null,
            consumerAToken
        ), 403);
        assertStatus(send(
            "GET",
            GATEWAY_URL + "/order-history/consumers/" + consumerBId + "/orders",
            null,
            consumerAToken
        ), 403);
    }

    @Test
    void consumerCannotUsePrivilegedCreditLimitEndpoint() {
        ObjectNode body = JSON.createObjectNode().put("creditLimit", "999999.00");
        assertStatus(send(
            "PUT",
            CONSUMER_URL + "/admin/consumers/" + consumerBId + "/credit-limit",
            body,
            consumerAToken
        ), 403);
        assertStatus(send(
            "PUT",
            CONSUMER_URL + "/admin/consumers/" + consumerBId + "/credit-limit",
            body,
            adminToken
        ), 200);
    }

    @Test
    void restaurantCannotMutateAnotherRestaurantMenu() {
        ObjectNode menuItem = JSON.createObjectNode();
        menuItem.put("name", "Unauthorized item");
        menuItem.put("description", "must not be created");
        menuItem.set("price", JSON.createObjectNode().put("amount", "12.00"));

        assertStatus(send(
            "POST",
            RESTAURANT_URL + "/restaurants/" + restaurantBId + "/menu-items",
            menuItem,
            restaurantAToken
        ), 403);
        assertStatus(send(
            "POST",
            RESTAURANT_URL + "/restaurants/" + restaurantAId + "/menu-items",
            menuItem,
            restaurantAToken
        ), 201);
    }

    @Test
    void publicAudienceTokenCannotAccessInternalNamespace() {
        assertStatus(send(
            "GET",
            CONSUMER_URL + "/internal/consumers/" + consumerAId,
            null,
            consumerAToken
        ), 403);
    }

    @Test
    void livenessIsPublicWhileDetailedHealthIsProtected() {
        assertStatus(send(
            "GET",
            CONSUMER_URL + "/actuator/health/liveness",
            null,
            null
        ), 200);
        assertStatus(send(
            "GET",
            CONSUMER_URL + "/actuator/health",
            null,
            null
        ), 401);
        assertStatus(send(
            "GET",
            GATEWAY_URL + "/actuator/health/liveness",
            null,
            null
        ), 200);
        assertStatus(send(
            "GET",
            GATEWAY_URL + "/actuator/gateway/routes",
            null,
            null
        ), 401);
    }

    @Test
    void adminCanAccessProtectedActuatorEndpoints() {
        assertStatus(send(
            "GET",
            GATEWAY_URL + "/actuator/health",
            null,
            adminToken
        ), 200);
    }

    private static long createConsumer(String label) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        ObjectNode body = JSON.createObjectNode();
        body.put("name", "Security Consumer " + label);
        body.put("email", "security-" + label.toLowerCase() + "-" + suffix + "@example.test");
        HttpResponse<String> response = send("POST", CONSUMER_URL + "/consumers", body, adminToken);
        assertStatus(response, 201);
        return parseId(response.body());
    }

    private static long createRestaurant(String label) {
        ObjectNode address = JSON.createObjectNode();
        address.put("street", "1 Security Street");
        address.put("city", "Bangkok");
        address.put("state", "BK");
        address.put("zipCode", "10110");

        ObjectNode body = JSON.createObjectNode();
        body.put("name", "Security Restaurant " + label + " " + UUID.randomUUID());
        body.set("address", address);
        body.put("openingHours", "{\"daily\":\"00:00-23:59\"}");
        HttpResponse<String> response = send(
            "POST",
            RESTAURANT_URL + "/restaurants",
            body,
            adminToken
        );
        assertStatus(response, 201);
        return parseId(response.body());
    }

    private static String token(
        String subject,
        List<String> roles,
        List<String> audiences,
        Map<String, ?> claims,
        Duration lifetime
    ) throws Exception {
        return identityProvider.issueToken(subject, roles, audiences, claims, lifetime);
    }

    private static HttpResponse<String> send(
        String method,
        String url,
        ObjectNode body,
        String token
    ) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json");
            if (token != null) {
                request.header("Authorization", "Bearer " + token);
            }
            if ("GET".equals(method)) {
                request.GET();
            } else {
                request.method(
                    method,
                    HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body.toString())
                );
            }
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception error) {
            throw new RuntimeException("HTTP request failed: " + method + " " + url, error);
        }
    }

    private static void assertStatus(HttpResponse<String> response, int expectedStatus) {
        assertThat(response.statusCode())
            .withFailMessage(
                "Expected status %s but received %s body=%s",
                expectedStatus,
                response.statusCode(),
                response.body()
            )
            .isEqualTo(expectedStatus);
    }

    private static long parseId(String body) {
        try {
            return JSON.readTree(body).path("id").asLong();
        } catch (Exception error) {
            throw new RuntimeException("Invalid entity response: " + body, error);
        }
    }
}

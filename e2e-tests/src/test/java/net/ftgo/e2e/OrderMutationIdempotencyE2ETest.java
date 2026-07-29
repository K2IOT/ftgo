package net.ftgo.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.ftgo.e2e.support.TestIdentityProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FTGO_E2E_ENABLED", matches = "true")
class OrderMutationIdempotencyE2ETest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private static final String GATEWAY_URL = System.getProperty(
        "ftgo.e2e.gateway-url",
        "http://localhost:8080"
    );
    private static final String ORDER_URL = System.getProperty(
        "ftgo.e2e.order-url",
        "http://localhost:8081"
    );
    private static final String CONSUMER_URL = System.getProperty(
        "ftgo.e2e.consumer-url",
        "http://localhost:8082"
    );
    private static final String RESTAURANT_URL = System.getProperty(
        "ftgo.e2e.restaurant-url",
        "http://localhost:8083"
    );
    private static final String JDBC_BASE = System.getProperty(
        "ftgo.e2e.jdbc-url",
        "jdbc:mysql://localhost:33306"
    );
    private static final String DB_USER = "ftgo_user";
    private static final String DB_PASSWORD = "ftgo_password";

    private static TestIdentityProvider identityProvider;
    private static String adminToken;

    @BeforeAll
    static void startIdentityProvider() throws Exception {
        identityProvider = TestIdentityProvider.start(19000);
        adminToken = identityProvider.issueToken(
            "remediation-04-admin",
            List.of("ADMIN"),
            List.of("ftgo-api"),
            Map.of(),
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
    void createOrderIsIdempotentAtDirectAndGatewayBoundaries() throws Exception {
        Fixture fixture = createFixture();

        ObjectNode missingKeyRequest = orderRequest(fixture, 1, 1);
        assertMissingKeyRejected(ORDER_URL, missingKeyRequest, fixture.consumerToken());
        assertMissingKeyRejected(GATEWAY_URL, missingKeyRequest, fixture.consumerToken());

        ObjectNode directRequest = orderRequest(fixture, 1, 2);
        String directKey = "remediation-04-direct-" + UUID.randomUUID();
        HttpResponse<String> directFirst = postOrder(
            ORDER_URL,
            directRequest,
            fixture.consumerToken(),
            directKey
        );
        HttpResponse<String> directReplay = postOrder(
            ORDER_URL,
            directRequest,
            fixture.consumerToken(),
            directKey
        );
        assertExactReplay(directFirst, directReplay);
        long directOrderId = parse(directFirst.body()).path("orderId").asLong();
        assertSingleMutationArtifacts(directOrderId, directKey);

        ObjectNode changedPayload = directRequest.deepCopy();
        ((ObjectNode) changedPayload.withArray("lineItems").get(0)).put("quantity", 2);
        HttpResponse<String> conflict = postOrder(
            ORDER_URL,
            changedPayload,
            fixture.consumerToken(),
            directKey
        );
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(parse(conflict.body()).path("errorCode").asText())
            .isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertSingleMutationArtifacts(directOrderId, directKey);

        ObjectNode gatewayRequest = orderRequest(fixture, 1, 3);
        String gatewayKey = "remediation-04-gateway-" + UUID.randomUUID();
        HttpResponse<String> gatewayFirst = postOrder(
            GATEWAY_URL,
            gatewayRequest,
            fixture.consumerToken(),
            gatewayKey
        );
        HttpResponse<String> gatewayReplay = postOrder(
            GATEWAY_URL,
            gatewayRequest,
            fixture.consumerToken(),
            gatewayKey
        );
        assertExactReplay(gatewayFirst, gatewayReplay);
        long gatewayOrderId = parse(gatewayFirst.body()).path("orderId").asLong();
        assertSingleMutationArtifacts(gatewayOrderId, gatewayKey);

        verifyTwentyConcurrentCreatesReplayOneCommittedMutation(fixture);
    }

    private void verifyTwentyConcurrentCreatesReplayOneCommittedMutation(Fixture fixture)
        throws Exception {
        int requestCount = 20;
        ObjectNode request = orderRequest(fixture, 1, 4);
        String key = "remediation-04-concurrent-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);

        try {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent create barrier timed out");
                    }
                    return postOrder(ORDER_URL, request, fixture.consumerToken(), key);
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<HttpResponse<String>> responses = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }

            assertThat(responses).allMatch(response -> response.statusCode() == 201);
            Set<String> bodies = responses.stream()
                .map(HttpResponse::body)
                .collect(Collectors.toSet());
            assertThat(bodies).hasSize(1);
            long orderId = parse(responses.get(0).body()).path("orderId").asLong();
            assertSingleMutationArtifacts(orderId, key);
        } finally {
            executor.shutdownNow();
        }
    }

    private Fixture createFixture() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        ObjectNode consumer = JSON.createObjectNode();
        consumer.put("name", "Remediation 04 Consumer " + suffix);
        consumer.put("email", "remediation-04-" + suffix + "@example.test");
        long consumerId = postJson(CONSUMER_URL + "/consumers", consumer, adminToken)
            .path("id")
            .asLong();

        ObjectNode creditLimit = JSON.createObjectNode().put("creditLimit", "1000.00");
        assertThat(send(
            "PUT",
            CONSUMER_URL + "/admin/consumers/" + consumerId + "/credit-limit",
            creditLimit,
            adminToken,
            null
        ).statusCode()).isEqualTo(200);

        String consumerToken = identityProvider.issueToken(
            "consumer-" + consumerId,
            List.of("CONSUMER"),
            List.of("ftgo-api"),
            Map.of("consumer_id", consumerId),
            Duration.ofMinutes(30)
        );

        ObjectNode address = address("1 Remediation Street");
        ObjectNode restaurant = JSON.createObjectNode();
        restaurant.put("name", "Remediation 04 Restaurant " + suffix);
        restaurant.set("address", address);
        restaurant.put("openingHours", "{\"daily\":\"00:00-23:59\"}");
        long restaurantId = postJson(
            RESTAURANT_URL + "/restaurants",
            restaurant,
            adminToken
        ).path("id").asLong();

        String restaurantToken = identityProvider.issueToken(
            "restaurant-" + restaurantId,
            List.of("RESTAURANT"),
            List.of("ftgo-api"),
            Map.of("restaurant_ids", List.of(restaurantId)),
            Duration.ofMinutes(30)
        );

        String itemName = "Idempotent Burger " + suffix;
        ObjectNode menuItem = JSON.createObjectNode();
        menuItem.put("name", itemName);
        menuItem.put("description", "Remediation 04 E2E item");
        menuItem.set("price", money(new BigDecimal("25.00")));
        long menuItemId = postJson(
            RESTAURANT_URL + "/restaurants/" + restaurantId + "/menu-items",
            menuItem,
            restaurantToken
        ).path("id").asLong();

        long menuVersion = Long.parseLong(queryString(
            "ftgo_restaurant",
            "SELECT menu_version FROM restaurants WHERE id = ?",
            restaurantId
        ));
        return new Fixture(
            consumerId,
            restaurantId,
            menuItemId,
            menuVersion,
            itemName,
            consumerToken
        );
    }

    private ObjectNode orderRequest(Fixture fixture, int quantity, int hourOffset) {
        ObjectNode lineItem = JSON.createObjectNode();
        lineItem.put("menuItemId", fixture.menuItemId());
        lineItem.put("name", fixture.itemName());
        lineItem.set("price", money(new BigDecimal("25.00")));
        lineItem.put("quantity", quantity);
        ArrayNode lineItems = JSON.createArrayNode().add(lineItem);

        ObjectNode request = JSON.createObjectNode();
        request.put("consumerId", fixture.consumerId());
        request.put("restaurantId", fixture.restaurantId());
        request.put("expectedMenuVersion", fixture.menuVersion());
        request.set("lineItems", lineItems);
        request.set("deliveryAddress", address("2 Remediation Delivery Street"));
        request.put("deliveryTime", LocalDateTime.now().plusHours(hourOffset).toString());
        request.put("paymentToken", "tok_remediation_04");
        return request;
    }

    private void assertMissingKeyRejected(
        String baseUrl,
        JsonNode request,
        String consumerToken
    ) {
        HttpResponse<String> response = send(
            "POST",
            baseUrl + "/orders",
            request,
            consumerToken,
            null
        );
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(parse(response.body()).path("errorCode").asText())
            .isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    }

    private void assertExactReplay(
        HttpResponse<String> first,
        HttpResponse<String> replay
    ) {
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(replay.statusCode()).isEqualTo(first.statusCode());
        assertThat(replay.body()).isEqualTo(first.body());
    }

    private void assertSingleMutationArtifacts(long orderId, String key) {
        assertThat(count(
            "ftgo_order",
            "SELECT COUNT(*) FROM orders WHERE id = ?",
            orderId
        )).isEqualTo(1L);
        assertThat(count(
            "ftgo_order",
            "SELECT COUNT(*) FROM api_idempotency_records WHERE idempotency_key = ?",
            key
        )).isEqualTo(1L);
        assertThat(count(
            "ftgo_order",
            "SELECT COUNT(*) FROM outbox WHERE aggregate_id = ? AND event_type = 'OrderCreated'",
            Long.toString(orderId)
        )).isEqualTo(1L);
        assertThat(count(
            "eventuate",
            "SELECT COUNT(*) FROM saga_instance WHERE saga_type LIKE '%CreateOrderSaga%' "
                + "AND saga_data_json LIKE ?",
            "%\"orderId\":" + orderId + "%"
        )).isEqualTo(1L);
    }

    private HttpResponse<String> postOrder(
        String baseUrl,
        JsonNode request,
        String token,
        String key
    ) {
        return send("POST", baseUrl + "/orders", request, token, key);
    }

    private JsonNode postJson(String url, JsonNode body, String token) {
        HttpResponse<String> response = send("POST", url, body, token, null);
        assertThat(response.statusCode())
            .withFailMessage(
                "POST %s failed: status=%s body=%s",
                url,
                response.statusCode(),
                response.body()
            )
            .isBetween(200, 299);
        return parse(response.body());
    }

    private HttpResponse<String> send(
        String method,
        String url,
        JsonNode body,
        String token,
        String idempotencyKey
    ) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token);
            if (idempotencyKey != null) {
                builder.header("Idempotency-Key", idempotencyKey);
            }
            String payload = body == null ? "{}" : JSON.writeValueAsString(body);
            builder.method(method, HttpRequest.BodyPublishers.ofString(payload));
            return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception error) {
            throw new IllegalStateException("HTTP request failed: " + method + " " + url, error);
        }
    }

    private ObjectNode address(String street) {
        ObjectNode address = JSON.createObjectNode();
        address.put("street", street);
        address.put("city", "Bangkok");
        address.put("state", "BK");
        address.put("zipCode", "10110");
        return address;
    }

    private ObjectNode money(BigDecimal amount) {
        return JSON.createObjectNode().put("amount", amount);
    }

    private JsonNode parse(String body) {
        try {
            return JSON.readTree(body);
        } catch (Exception error) {
            throw new IllegalStateException("Invalid JSON response: " + body, error);
        }
    }

    private long count(String schema, String sql, Object parameter) {
        return Long.parseLong(queryString(schema, sql, parameter));
    }

    private String queryString(String schema, String sql, Object parameter) {
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Query returned no rows: " + sql);
                }
                return resultSet.getString(1);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Query failed for " + schema + ": " + sql, error);
        }
    }

    private Connection connection(String schema) throws Exception {
        return DriverManager.getConnection(
            JDBC_BASE + "/" + schema
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            DB_USER,
            DB_PASSWORD
        );
    }

    private record Fixture(
        long consumerId,
        long restaurantId,
        long menuItemId,
        long menuVersion,
        String itemName,
        String consumerToken
    ) {
    }
}

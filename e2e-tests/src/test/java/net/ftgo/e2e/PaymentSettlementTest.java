package net.ftgo.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.ftgo.e2e.support.TestIdentityProvider;
import org.awaitility.Awaitility;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FTGO_PHASE02B_E2E_ENABLED", matches = "true")
class PaymentSettlementTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private static final String ORDER_URL = "http://localhost:8081";
    private static final String CONSUMER_URL = "http://localhost:8082";
    private static final String RESTAURANT_URL = "http://localhost:8083";
    private static final String KITCHEN_URL = "http://localhost:8084";
    private static final String ACCOUNTING_URL = "http://localhost:8085";
    private static final String JDBC_BASE = environment(
        "FTGO_E2E_JDBC_URL",
        "jdbc:mysql://localhost:33306"
    );
    private static final String DB_USER = "ftgo_user";
    private static final String DB_PASSWORD = "ftgo_password";

    private static TestIdentityProvider identityProvider;
    private static String adminToken;
    private static String consumerToken;

    @BeforeAll
    static void startIdentityProvider() throws Exception {
        identityProvider = TestIdentityProvider.start(19000);
        adminToken = identityProvider.issueToken(
            "phase02b-admin",
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
    void capturePartialRefundDuplicateOverRefundReconcileAndRepair() throws Exception {
        Fixture fixture = createFixture(new BigDecimal("1000.00"), new BigDecimal("25.00"));
        long orderId = createOrder(fixture, "tok_phase02b");
        long ticketId = awaitTicket(orderId, fixture.restaurantId());
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE");

        post(KITCHEN_URL + "/tickets/" + ticketId + "/accept", null);
        awaitOrderState(orderId, "APPROVED");

        long authorizationId = Long.parseLong(awaitSqlValue(
            "ftgo_accounting",
            "select id from authorizations where order_id = ?",
            orderId,
            value -> value != null
        ));
        awaitSqlValue(
            "ftgo_accounting",
            "select status from authorizations where id = ?",
            authorizationId,
            "CAPTURED"::equals
        );
        awaitSqlValue(
            "ftgo_accounting",
            "select status from simulated_provider_payments where authorization_id = ?",
            authorizationId,
            "CAPTURED"::equals
        );
        assertThat(count(
            "ftgo_accounting",
            "select count(*) from payment_ledger_entries where authorization_id = ? and operation_type = 'AUTHORIZE'",
            authorizationId
        )).isEqualTo(1L);
        assertThat(count(
            "ftgo_accounting",
            "select count(*) from payment_ledger_entries where authorization_id = ? and operation_type = 'CAPTURE'",
            authorizationId
        )).isEqualTo(1L);

        String refundRequestId = "phase02b-refund-" + authorizationId;
        ObjectNode refund = JSON.createObjectNode();
        refund.set("amount", money(new BigDecimal("10.00")));
        refund.put("reason", "partial item adjustment");
        refund.put("idempotencyKey", refundRequestId);

        JsonNode firstRefund = post(
            ACCOUNTING_URL + "/api/admin/payment-settlement/authorizations/"
                + authorizationId + "/refunds",
            refund
        );
        JsonNode replayedRefund = post(
            ACCOUNTING_URL + "/api/admin/payment-settlement/authorizations/"
                + authorizationId + "/refunds",
            refund
        );

        assertThat(firstRefund.path("status").asText()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(replayedRefund.path("requestId").asText()).isEqualTo(refundRequestId);

        ObjectNode conflictingReplay = refund.deepCopy();
        conflictingReplay.set("amount", money(new BigDecimal("5.00")));
        conflictingReplay.put("reason", "changed payload must not replay");
        HttpResponse<String> conflict = send(
            "POST",
            ACCOUNTING_URL + "/api/admin/payment-settlement/authorizations/"
                + authorizationId + "/refunds",
            conflictingReplay,
            adminToken
        );
        assertThat(conflict.statusCode())
            .withFailMessage(
                "Conflicting idempotency replay status=%s body=%s",
                conflict.statusCode(),
                conflict.body()
            )
            .isEqualTo(409);

        awaitSqlValue(
            "ftgo_accounting",
            "select refunded_amount from authorizations where id = ?",
            authorizationId,
            value -> new BigDecimal(value).compareTo(new BigDecimal("10.00")) == 0
        );
        awaitSqlValue(
            "ftgo_accounting",
            "select refunded_amount from simulated_provider_payments where authorization_id = ?",
            authorizationId,
            value -> new BigDecimal(value).compareTo(new BigDecimal("10.00")) == 0
        );
        assertThat(count(
            "ftgo_accounting",
            "select count(*) from payment_refunds where authorization_id = ?",
            authorizationId
        )).isEqualTo(1L);
        assertThat(count(
            "ftgo_accounting",
            "select count(*) from payment_ledger_entries where authorization_id = ? and operation_type = 'REFUND'",
            authorizationId
        )).isEqualTo(1L);

        ObjectNode overRefund = JSON.createObjectNode();
        overRefund.set("amount", money(new BigDecimal("20.00")));
        overRefund.put("reason", "must reject over refund");
        overRefund.put("idempotencyKey", "phase02b-over-refund-" + authorizationId);
        HttpResponse<String> rejected = send(
            "POST",
            ACCOUNTING_URL + "/api/admin/payment-settlement/authorizations/"
                + authorizationId + "/refunds",
            overRefund,
            adminToken
        );
        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(parse(rejected.body()).path("message").asText())
            .contains("exceeds captured amount");

        execute(
            "ftgo_accounting",
            "update simulated_provider_payments set refunded_amount = 0.00, status = 'CAPTURED' where authorization_id = ?",
            authorizationId
        );
        post(ACCOUNTING_URL + "/api/admin/payment-settlement/reconcile", null);

        JsonNode discrepancies = get(
            ACCOUNTING_URL + "/api/admin/payment-settlement/authorizations/"
                + authorizationId + "/discrepancies"
        );
        long repairDiscrepancyId = findOpenDiscrepancy(
            discrepancies,
            "REFUND_AMOUNT_MISMATCH"
        );
        assertThat(repairDiscrepancyId).isPositive();

        ObjectNode repair = JSON.createObjectNode();
        repair.put("action", "SYNC_PROVIDER_FROM_LOCAL");
        repair.put("idempotencyKey", "phase02b-repair-" + authorizationId);
        repair.put("reason", "restore provider state from immutable local settlement");
        JsonNode repaired = post(
            ACCOUNTING_URL + "/api/admin/payment-settlement/discrepancies/"
                + repairDiscrepancyId + "/actions",
            repair
        );
        JsonNode replayedRepair = post(
            ACCOUNTING_URL + "/api/admin/payment-settlement/discrepancies/"
                + repairDiscrepancyId + "/actions",
            repair
        );
        assertThat(repaired.path("status").asText()).isEqualTo("RESOLVED");
        assertThat(replayedRepair.path("repairRequestId").asText())
            .isEqualTo("phase02b-repair-" + authorizationId);

        post(ACCOUNTING_URL + "/api/admin/payment-settlement/reconcile", null);
        awaitSqlValue(
            "ftgo_accounting",
            "select status from simulated_provider_payments where authorization_id = ?",
            authorizationId,
            "PARTIALLY_REFUNDED"::equals
        );
        awaitSqlValue(
            "ftgo_accounting",
            "select refunded_amount from simulated_provider_payments where authorization_id = ?",
            authorizationId,
            value -> new BigDecimal(value).compareTo(new BigDecimal("10.00")) == 0
        );
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> assertThat(count(
                "ftgo_accounting",
                "select count(*) from settlement_discrepancies where authorization_id = ? and status <> 'RESOLVED'",
                authorizationId
            )).isZero());
    }

    private Fixture createFixture(BigDecimal creditLimit, BigDecimal price) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        ObjectNode consumer = JSON.createObjectNode();
        consumer.put("name", "Phase02B Consumer " + suffix);
        consumer.put("email", "phase02b-" + suffix + "@example.test");
        long consumerId = post(CONSUMER_URL + "/consumers", consumer).path("id").asLong();

        ObjectNode creditLimitRequest = JSON.createObjectNode();
        creditLimitRequest.put("creditLimit", creditLimit);
        HttpResponse<String> creditLimitResponse = send(
            "PUT",
            CONSUMER_URL + "/admin/consumers/" + consumerId + "/credit-limit",
            creditLimitRequest,
            adminToken
        );
        assertThat(creditLimitResponse.statusCode())
            .withFailMessage(
                "Credit limit update failed: status=%s body=%s",
                creditLimitResponse.statusCode(),
                creditLimitResponse.body()
            )
            .isEqualTo(200);

        consumerToken = identityProvider.issueToken(
            "consumer-" + consumerId,
            List.of("CONSUMER"),
            List.of("ftgo-api"),
            Map.of("consumer_id", consumerId),
            Duration.ofMinutes(30)
        );

        ObjectNode address = JSON.createObjectNode();
        address.put("street", "1 Settlement Street");
        address.put("city", "Bangkok");
        address.put("state", "BK");
        address.put("zipCode", "10110");

        ObjectNode restaurant = JSON.createObjectNode();
        restaurant.put("name", "Phase02B Restaurant " + suffix);
        restaurant.set("address", address);
        restaurant.put("openingHours", "{\"daily\":\"00:00-23:59\"}");
        long restaurantId = post(RESTAURANT_URL + "/restaurants", restaurant)
            .path("id").asLong();

        String menuName = "Settlement item " + suffix;
        ObjectNode menuItem = JSON.createObjectNode();
        menuItem.put("name", menuName);
        menuItem.put("description", "Phase 02B settlement E2E item");
        menuItem.set("price", money(price));
        long menuItemId = post(
            RESTAURANT_URL + "/restaurants/" + restaurantId + "/menu-items",
            menuItem
        ).path("id").asLong();

        long menuVersion = Long.parseLong(queryString(
            "ftgo_restaurant",
            "select menu_version from restaurants where id = ?",
            restaurantId
        ));
        return new Fixture(consumerId, restaurantId, menuItemId, menuVersion, price, menuName);
    }

    private long createOrder(Fixture fixture, String paymentToken) {
        ObjectNode lineItem = JSON.createObjectNode();
        lineItem.put("menuItemId", fixture.menuItemId());
        lineItem.put("name", fixture.menuName());
        lineItem.set("price", money(fixture.price()));
        lineItem.put("quantity", 1);
        ArrayNode lineItems = JSON.createArrayNode().add(lineItem);

        ObjectNode deliveryAddress = JSON.createObjectNode();
        deliveryAddress.put("street", "1 Settlement Street");
        deliveryAddress.put("city", "Bangkok");
        deliveryAddress.put("state", "BK");
        deliveryAddress.put("zipCode", "10110");

        ObjectNode request = JSON.createObjectNode();
        request.put("consumerId", fixture.consumerId());
        request.put("restaurantId", fixture.restaurantId());
        request.put("expectedMenuVersion", fixture.menuVersion());
        request.set("lineItems", lineItems);
        request.set("deliveryAddress", deliveryAddress);
        request.put("deliveryTime", LocalDateTime.now().plusHours(1).toString());
        request.put("paymentToken", paymentToken);
        return post(ORDER_URL + "/orders", request).path("orderId").asLong();
    }

    private long awaitTicket(long orderId, long restaurantId) {
        final long[] ticketId = {-1L};
        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> {
                JsonNode tickets = get(KITCHEN_URL + "/tickets?restaurantId=" + restaurantId);
                for (JsonNode ticket : tickets) {
                    if (ticket.path("orderId").asLong() == orderId) {
                        ticketId[0] = ticket.path("id").asLong();
                    }
                }
                assertThat(ticketId[0]).isPositive();
            });
        return ticketId[0];
    }

    private void awaitOrderState(long orderId, String expectedState) {
        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> assertThat(
                get(ORDER_URL + "/orders/" + orderId).path("state").asText()
            ).isEqualTo(expectedState));
    }

    private long findOpenDiscrepancy(JsonNode discrepancies, String type) {
        for (JsonNode discrepancy : discrepancies) {
            if (type.equals(discrepancy.path("type").asText())
                && !"RESOLVED".equals(discrepancy.path("status").asText())) {
                return discrepancy.path("id").asLong();
            }
        }
        return -1L;
    }

    private ObjectNode money(BigDecimal amount) {
        return JSON.createObjectNode().put("amount", amount);
    }

    private JsonNode get(String url) {
        HttpResponse<String> response = send("GET", url, null, tokenFor(url));
        assertThat(response.statusCode())
            .withFailMessage("GET %s failed: status=%s body=%s", url, response.statusCode(), response.body())
            .isBetween(200, 299);
        return parse(response.body());
    }

    private JsonNode post(String url, JsonNode body) {
        HttpResponse<String> response = send("POST", url, body, tokenFor(url));
        assertThat(response.statusCode())
            .withFailMessage("POST %s failed: status=%s body=%s", url, response.statusCode(), response.body())
            .isBetween(200, 299);
        return response.body() == null || response.body().isBlank()
            ? JSON.createObjectNode()
            : parse(response.body());
    }

    private String tokenFor(String url) {
        if (url.startsWith(ORDER_URL)) {
            if (consumerToken == null) {
                throw new IllegalStateException("Consumer token is not initialized");
            }
            return consumerToken;
        }
        return adminToken;
    }

    private HttpResponse<String> send(String method, String url, JsonNode body, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json");
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            if ("POST".equals(method) && (ORDER_URL + "/orders").equals(url)) {
                builder.header("Idempotency-Key", "payment-settlement-" + UUID.randomUUID());
            }
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                String payload = body == null ? "{}" : JSON.writeValueAsString(body);
                builder.method(method, HttpRequest.BodyPublishers.ofString(payload));
            }
            return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception error) {
            throw new RuntimeException("HTTP request failed: " + method + " " + url, error);
        }
    }

    private JsonNode parse(String body) {
        try {
            return JSON.readTree(body);
        } catch (Exception error) {
            throw new RuntimeException("Invalid JSON response: " + body, error);
        }
    }

    private String awaitSqlValue(
        String schema,
        String sql,
        Object parameter,
        java.util.function.Predicate<String> predicate
    ) {
        final String[] value = {null};
        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> {
                value[0] = queryString(schema, sql, parameter);
                assertThat(predicate.test(value[0])).isTrue();
            });
        return value[0];
    }

    private long count(String schema, String sql, Object parameter) {
        String value = queryString(schema, sql, parameter);
        return value == null ? 0L : Long.parseLong(value);
    }

    private String queryString(String schema, String sql, Object parameter) {
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        } catch (Exception error) {
            throw new RuntimeException("Query failed for " + schema + ": " + sql, error);
        }
    }

    private void execute(String schema, String sql, Object parameter) {
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            statement.executeUpdate();
        } catch (Exception error) {
            throw new RuntimeException("Update failed for " + schema + ": " + sql, error);
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

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Fixture(
        long consumerId,
        long restaurantId,
        long menuItemId,
        long menuVersion,
        BigDecimal price,
        String menuName
    ) {
    }
}

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FTGO_E2E_ENABLED", matches = "true")
class CoreOrderFlowTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private static final String ORDER_URL = System.getProperty(
        "ftgo.e2e.order-url", "http://localhost:8081");
    private static final String CONSUMER_URL = System.getProperty(
        "ftgo.e2e.consumer-url", "http://localhost:8082");
    private static final String RESTAURANT_URL = System.getProperty(
        "ftgo.e2e.restaurant-url", "http://localhost:8083");
    private static final String KITCHEN_URL = System.getProperty(
        "ftgo.e2e.kitchen-url", "http://localhost:8084");
    private static final String JDBC_BASE = System.getProperty(
        "ftgo.e2e.jdbc-url", "jdbc:mysql://localhost:33306");
    private static final String DB_USER = "ftgo_user";
    private static final String DB_PASSWORD = "ftgo_password";
    private static final int IDENTITY_PORT = 19000;

    private static TestIdentityProvider identityProvider;
    private static String adminToken;

    @BeforeAll
    static void startIdentityProvider() throws Exception {
        identityProvider = TestIdentityProvider.start(IDENTITY_PORT);
        adminToken = identityProvider.issueToken(
            "phase02-e2e-admin",
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
    void happyAcceptanceCommitsCreditAndCapturesPayment() {
        Fixture fixture = createFixture(new BigDecimal("1000.00"), new BigDecimal("25.00"));
        long orderId = createOrder(fixture, fixture.menuVersion(), "tok_happy");
        long ticketId = awaitTicket(orderId, fixture.restaurantId(), fixture.restaurantToken());

        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE", fixture.consumerToken());
        post(KITCHEN_URL + "/tickets/" + ticketId + "/accept", null, fixture.restaurantToken());
        awaitOrderState(orderId, "APPROVED", fixture.consumerToken());

        awaitSqlValue("ftgo_consumer",
            "select status from credit_reservations where order_id = ?",
            orderId, "COMMITTED");
        awaitSqlValue("ftgo_accounting",
            "select status from authorizations where order_id = ?",
            orderId, "CAPTURED");
        awaitSqlValue("ftgo_kitchen",
            "select state from tickets where id = ?",
            ticketId, "ACCEPTED");
    }

    @Test
    void staleMenuVersionRejectsBeforeRemoteResourcesAreCreated() {
        Fixture fixture = createFixture(new BigDecimal("1000.00"), new BigDecimal("25.00"));
        long orderId = createOrder(fixture, fixture.menuVersion() + 1, "tok_stale_menu");

        awaitOrderState(orderId, "REJECTED", fixture.consumerToken());
        assertThat(count("ftgo_consumer",
            "select count(*) from credit_reservations where order_id = ?", orderId)).isZero();
        assertThat(count("ftgo_kitchen",
            "select count(*) from tickets where order_id = ?", orderId)).isZero();
        assertThat(count("ftgo_accounting",
            "select count(*) from authorizations where order_id = ?", orderId)).isZero();
    }

    @Test
    void insufficientCreditRejectsWithoutLeakingResources() {
        Fixture fixture = createFixture(new BigDecimal("5.00"), new BigDecimal("25.00"));
        long orderId = createOrder(fixture, fixture.menuVersion(), "tok_low_credit");

        awaitOrderState(orderId, "REJECTED", fixture.consumerToken());
        assertThat(count("ftgo_consumer",
            "select count(*) from credit_reservations where order_id = ?", orderId)).isZero();
        assertThat(count("ftgo_kitchen",
            "select count(*) from tickets where order_id = ?", orderId)).isZero();
        assertThat(count("ftgo_accounting",
            "select count(*) from authorizations where order_id = ?", orderId)).isZero();
    }

    @Test
    void paymentProviderDenialCancelsTicketAndReleasesCredit() {
        Fixture fixture = createFixture(new BigDecimal("1000.00"), new BigDecimal("25.00"));
        long orderId = createOrder(fixture, fixture.menuVersion(), "tok_e2e_decline");

        awaitOrderState(orderId, "REJECTED", fixture.consumerToken());
        awaitSqlValue("ftgo_consumer",
            "select status from credit_reservations where order_id = ?",
            orderId, "RELEASED");
        awaitSqlValue("ftgo_kitchen",
            "select state from tickets where order_id = ?",
            orderId, "CANCELLED");
        assertThat(count("ftgo_accounting",
            "select count(*) from authorizations where order_id = ?", orderId)).isZero();
    }

    @Test
    void explicitRestaurantRejectionVoidsAuthorizationAndReleasesCredit() {
        Fixture fixture = createFixture(new BigDecimal("1000.00"), new BigDecimal("25.00"));
        long orderId = createOrder(fixture, fixture.menuVersion(), "tok_reject");
        long ticketId = awaitTicket(orderId, fixture.restaurantId(), fixture.restaurantToken());
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE", fixture.consumerToken());

        ObjectNode reason = JSON.createObjectNode().put("reason", "RESTAURANT_CAPACITY");
        post(
            KITCHEN_URL + "/tickets/" + ticketId + "/reject",
            reason,
            fixture.restaurantToken()
        );
        awaitOrderState(orderId, "REJECTED", fixture.consumerToken());

        awaitSqlValue("ftgo_consumer",
            "select status from credit_reservations where order_id = ?",
            orderId, "RELEASED");
        awaitSqlValue("ftgo_accounting",
            "select status from authorizations where order_id = ?",
            orderId, "VOIDED");
        awaitSqlValue("ftgo_kitchen",
            "select state from tickets where id = ?",
            ticketId, "REJECTED_BY_RESTAURANT");
    }

    @Test
    void acceptanceTimeoutRejectsOrderAndReleasesResources() {
        Fixture fixture = createFixture(new BigDecimal("1000.00"), new BigDecimal("25.00"));
        long orderId = createOrder(fixture, fixture.menuVersion(), "tok_timeout");
        long ticketId = awaitTicket(orderId, fixture.restaurantId(), fixture.restaurantToken());
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE", fixture.consumerToken());

        expireTicket(ticketId);
        awaitSqlValue("ftgo_kitchen",
            "select state from tickets where id = ?",
            ticketId, "REJECTED_TIMEOUT");
        awaitOrderState(orderId, "REJECTED", fixture.consumerToken());
        awaitSqlValue("ftgo_consumer",
            "select status from credit_reservations where order_id = ?",
            orderId, "RELEASED");
        awaitSqlValue("ftgo_accounting",
            "select status from authorizations where order_id = ?",
            orderId, "VOIDED");
    }

    @Test
    void acceptAndTimeoutRaceProducesExactlyOneDecision() {
        Fixture fixture = createFixture(new BigDecimal("1000.00"), new BigDecimal("25.00"));
        long orderId = createOrder(fixture, fixture.menuVersion(), "tok_race");
        long ticketId = awaitTicket(orderId, fixture.restaurantId(), fixture.restaurantToken());
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE", fixture.consumerToken());

        expireTicket(ticketId);
        CompletableFuture<HttpResponse<String>> accept = CompletableFuture.supplyAsync(() ->
            send(
                "POST",
                KITCHEN_URL + "/tickets/" + ticketId + "/accept",
                null,
                fixture.restaurantToken()
            ));

        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(200))
            .untilAsserted(() -> assertThat(ticketState(ticketId))
                .isIn("ACCEPTED", "REJECTED_TIMEOUT"));

        accept.join();
        String finalTicketState = ticketState(ticketId);
        if ("ACCEPTED".equals(finalTicketState)) {
            awaitOrderState(orderId, "APPROVED", fixture.consumerToken());
        } else {
            awaitOrderState(orderId, "REJECTED", fixture.consumerToken());
        }

        long decisionRows = count("ftgo_kitchen",
            "select count(*) from outbox where aggregate_id = ? " +
                "and event_type in ('TicketAcceptedEvent', 'TicketAcceptanceTimedOutEvent')",
            Long.toString(ticketId));
        assertThat(decisionRows).isEqualTo(1L);
        assertThat(queryString("ftgo_kitchen",
            "select decision_event_id from tickets where id = ?", ticketId)).isNotBlank();
    }

    @Test
    void duplicateAcceptanceDeliveryIsIdempotent() {
        Fixture fixture = createFixture(new BigDecimal("1000.00"), new BigDecimal("25.00"));
        long orderId = createOrder(fixture, fixture.menuVersion(), "tok_duplicate");
        long ticketId = awaitTicket(orderId, fixture.restaurantId(), fixture.restaurantToken());
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE", fixture.consumerToken());

        post(KITCHEN_URL + "/tickets/" + ticketId + "/accept", null, fixture.restaurantToken());
        post(KITCHEN_URL + "/tickets/" + ticketId + "/accept", null, fixture.restaurantToken());
        awaitOrderState(orderId, "APPROVED", fixture.consumerToken());

        assertThat(count("ftgo_kitchen",
            "select count(*) from outbox where aggregate_id = ? and event_type = 'TicketAcceptedEvent'",
            Long.toString(ticketId))).isEqualTo(1L);
        assertThat(count("ftgo_consumer",
            "select count(*) from credit_reservations where order_id = ? and status = 'COMMITTED'",
            orderId)).isEqualTo(1L);
        assertThat(count("ftgo_accounting",
            "select count(*) from authorizations where order_id = ? and status = 'CAPTURED'",
            orderId)).isEqualTo(1L);
    }

    private Fixture createFixture(BigDecimal creditLimit, BigDecimal price) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        ObjectNode consumer = JSON.createObjectNode();
        consumer.put("name", "E2E Consumer " + suffix);
        consumer.put("email", "e2e-" + suffix + "@example.test");
        long consumerId = post(CONSUMER_URL + "/consumers", consumer, adminToken)
            .path("id").asLong();

        ObjectNode creditPolicy = JSON.createObjectNode().put("creditLimit", creditLimit);
        put(
            CONSUMER_URL + "/admin/consumers/" + consumerId + "/credit-limit",
            creditPolicy,
            adminToken
        );
        String consumerToken = issueToken(
            "consumer-" + consumerId,
            List.of("CONSUMER"),
            Map.of("consumer_id", consumerId)
        );

        ObjectNode address = JSON.createObjectNode();
        address.put("street", "1 E2E Street");
        address.put("city", "Bangkok");
        address.put("state", "BK");
        address.put("zipCode", "10110");

        ObjectNode restaurant = JSON.createObjectNode();
        restaurant.put("name", "E2E Restaurant " + suffix);
        restaurant.set("address", address);
        restaurant.put("openingHours", "{\"daily\":\"00:00-23:59\"}");
        long restaurantId = post(RESTAURANT_URL + "/restaurants", restaurant, adminToken)
            .path("id").asLong();
        String restaurantToken = issueToken(
            "restaurant-" + restaurantId,
            List.of("RESTAURANT"),
            Map.of("restaurant_ids", List.of(restaurantId))
        );

        String menuName = "Burger " + suffix;
        ObjectNode menuItem = JSON.createObjectNode();
        menuItem.put("name", menuName);
        menuItem.put("description", "Phase 02 E2E item");
        menuItem.set("price", money(price));
        long menuItemId = post(
            RESTAURANT_URL + "/restaurants/" + restaurantId + "/menu-items",
            menuItem,
            restaurantToken
        ).path("id").asLong();

        long menuVersion = Long.parseLong(queryString(
            "ftgo_restaurant",
            "select menu_version from restaurants where id = ?",
            restaurantId
        ));
        return new Fixture(
            consumerId,
            restaurantId,
            menuItemId,
            menuVersion,
            price,
            menuName,
            consumerToken,
            restaurantToken
        );
    }

    private long createOrder(Fixture fixture, long expectedMenuVersion, String paymentToken) {
        ObjectNode lineItem = JSON.createObjectNode();
        lineItem.put("menuItemId", fixture.menuItemId());
        lineItem.put("name", fixture.menuName());
        lineItem.set("price", money(fixture.price()));
        lineItem.put("quantity", 1);
        ArrayNode lineItems = JSON.createArrayNode().add(lineItem);

        ObjectNode deliveryAddress = JSON.createObjectNode();
        deliveryAddress.put("street", "1 E2E Street");
        deliveryAddress.put("city", "Bangkok");
        deliveryAddress.put("state", "BK");
        deliveryAddress.put("zipCode", "10110");

        ObjectNode request = JSON.createObjectNode();
        request.put("consumerId", fixture.consumerId());
        request.put("restaurantId", fixture.restaurantId());
        request.put("expectedMenuVersion", expectedMenuVersion);
        request.set("lineItems", lineItems);
        request.set("deliveryAddress", deliveryAddress);
        request.put("deliveryTime", LocalDateTime.now().plusHours(1).toString());
        request.put("paymentToken", paymentToken);
        return post(ORDER_URL + "/orders", request, fixture.consumerToken())
            .path("orderId").asLong();
    }

    private ObjectNode money(BigDecimal amount) {
        return JSON.createObjectNode().put("amount", amount);
    }

    private long awaitTicket(long orderId, long restaurantId, String restaurantToken) {
        final long[] ticketId = {-1L};
        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> {
                JsonNode tickets = get(
                    KITCHEN_URL + "/tickets?restaurantId=" + restaurantId,
                    restaurantToken
                );
                for (JsonNode ticket : tickets) {
                    if (ticket.path("orderId").asLong() == orderId) {
                        ticketId[0] = ticket.path("id").asLong();
                    }
                }
                assertThat(ticketId[0]).isPositive();
            });
        return ticketId[0];
    }

    private void awaitOrderState(long orderId, String expectedState, String consumerToken) {
        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> assertThat(
                get(ORDER_URL + "/orders/" + orderId, consumerToken).path("state").asText()
            ).isEqualTo(expectedState));
    }

    private void awaitSqlValue(String schema, String sql, Object parameter, String expected) {
        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> assertThat(queryString(schema, sql, parameter))
                .isEqualTo(expected));
    }

    private String ticketState(long ticketId) {
        return queryString("ftgo_kitchen", "select state from tickets where id = ?", ticketId);
    }

    private void expireTicket(long ticketId) {
        execute("ftgo_kitchen",
            "update tickets set acceptance_deadline = current_timestamp(6) - interval 1 second where id = ?",
            ticketId);
    }

    private JsonNode get(String url, String token) {
        HttpResponse<String> response = send("GET", url, null, token);
        assertThat(response.statusCode())
            .withFailMessage("GET %s failed: status=%s body=%s", url, response.statusCode(), response.body())
            .isBetween(200, 299);
        return parse(response.body());
    }

    private JsonNode post(String url, JsonNode body, String token) {
        return write("POST", url, body, token);
    }

    private JsonNode put(String url, JsonNode body, String token) {
        return write("PUT", url, body, token);
    }

    private JsonNode write(String method, String url, JsonNode body, String token) {
        HttpResponse<String> response = send(method, url, body, token);
        assertThat(response.statusCode())
            .withFailMessage(
                "%s %s failed: status=%s body=%s",
                method,
                url,
                response.statusCode(),
                response.body()
            )
            .isBetween(200, 299);
        return response.body() == null || response.body().isBlank()
            ? JSON.createObjectNode()
            : parse(response.body());
    }

    private HttpResponse<String> send(String method, String url, JsonNode body, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token);
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

    private String issueToken(String subject, List<String> roles, Map<String, ?> claims) {
        try {
            return identityProvider.issueToken(
                subject,
                roles,
                List.of("ftgo-api"),
                claims,
                Duration.ofMinutes(30)
            );
        } catch (Exception error) {
            throw new RuntimeException("Unable to issue E2E JWT for " + subject, error);
        }
    }

    private JsonNode parse(String body) {
        try {
            return JSON.readTree(body);
        } catch (Exception error) {
            throw new RuntimeException("Invalid JSON response: " + body, error);
        }
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

    private record Fixture(
        long consumerId,
        long restaurantId,
        long menuItemId,
        long menuVersion,
        BigDecimal price,
        String menuName,
        String consumerToken,
        String restaurantToken
    ) {
    }
}

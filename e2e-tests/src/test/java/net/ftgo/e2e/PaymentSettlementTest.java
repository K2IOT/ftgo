package net.ftgo.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FTGO_E2E_ENABLED", matches = "true")
class PaymentSettlementTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();
    private static final String ORDER_URL = "http://localhost:8081";
    private static final String CONSUMER_URL = "http://localhost:8082";
    private static final String RESTAURANT_URL = "http://localhost:8083";
    private static final String KITCHEN_URL = "http://localhost:8084";
    private static final String ACCOUNTING_URL = "http://localhost:8085";
    private static final String JDBC_BASE = System.getProperty(
        "ftgo.e2e.jdbc-url", "jdbc:mysql://localhost:33306");
    private static final String WEBHOOK_SECRET = "phase02b-e2e-secret";

    @BeforeEach
    void resetProviderCounters() {
        send("DELETE", ACCOUNTING_URL + "/admin/payments/provider-sandbox", null);
    }

    @Test
    void authorizationSucceedsWithoutPrematureCapture() {
        Fixture fixture = createFixture();
        long orderId = createOrder(fixture, "tok_auth_only");
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE");
        awaitSqlValue("ftgo_accounting",
            "select status from authorizations where order_id = ?", orderId, "AUTHORIZED");
        assertThat(count("ftgo_accounting",
            "select count(*) from payment_captures pc join authorizations a on a.id=pc.authorization_id where a.order_id=?",
            orderId)).isZero();
    }

    @Test
    void acceptanceCapturesExactlyOnceAndBlocksPreparingBeforeCapture() {
        Fixture fixture = createFixture();
        long orderId = createOrder(fixture, "tok_capture_once");
        long ticketId = awaitTicket(orderId, fixture.restaurantId());
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE");

        assertThat(send("POST", KITCHEN_URL + "/tickets/" + ticketId + "/preparing", null).statusCode())
            .isEqualTo(409);
        assertThat(send("POST", KITCHEN_URL + "/tickets/" + ticketId + "/accept", null).statusCode())
            .isEqualTo(202);
        awaitOrderState(orderId, "APPROVED");
        awaitSqlValue("ftgo_kitchen", "select state from tickets where id=?", ticketId, "ACCEPTED");
        assertThat(operationCount("capture")).isEqualTo(1L);
    }

    @Test
    void captureDeclineCompensatesAcceptanceCreditAndAuthorization() {
        Fixture fixture = createFixture();
        long orderId = createOrder(fixture, "tok_capture_decline_e2e");
        long ticketId = awaitTicket(orderId, fixture.restaurantId());
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE");
        execute("ftgo_accounting",
            "update authorizations set provider_authorization_id='pa_capture_decline_e2e' where order_id=?",
            orderId);

        post(KITCHEN_URL + "/tickets/" + ticketId + "/accept", null);
        awaitOrderState(orderId, "REJECTED");
        awaitSqlValue("ftgo_consumer",
            "select status from credit_reservations where order_id=?", orderId, "RELEASED");
        awaitSqlValue("ftgo_accounting",
            "select status from authorizations where order_id=?", orderId, "VOIDED");
        assertThat(operationCount("capture")).isEqualTo(1L);
    }

    @Test
    void transientCaptureFailureRetriesAndRecoversWithoutDoubleCapture() {
        Fixture fixture = createFixture();
        long orderId = createOrder(fixture, "tok_capture_retry_e2e");
        long ticketId = awaitTicket(orderId, fixture.restaurantId());
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE");
        String originalProviderId = queryString("ftgo_accounting",
            "select provider_authorization_id from authorizations where order_id=?", orderId);
        execute("ftgo_accounting",
            "update authorizations set provider_authorization_id='pa_capture_error_e2e' where order_id=?",
            orderId);

        post(KITCHEN_URL + "/tickets/" + ticketId + "/accept", null);
        awaitOrderState(orderId, "CONFIRMATION_PENDING");
        execute("ftgo_accounting",
            "update authorizations set provider_authorization_id=? where order_id=" + orderId,
            originalProviderId);
        awaitOrderState(orderId, "APPROVED");
        assertThat(operationCount("capture")).isEqualTo(1L);
    }

    @Test
    void duplicateAcceptanceAndReplyReplayDoNotDoubleCharge() {
        Fixture fixture = createFixture();
        long orderId = createOrder(fixture, "tok_duplicate_capture");
        long ticketId = awaitTicket(orderId, fixture.restaurantId());
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE");

        post(KITCHEN_URL + "/tickets/" + ticketId + "/accept", null);
        post(KITCHEN_URL + "/tickets/" + ticketId + "/accept", null);
        awaitOrderState(orderId, "APPROVED");
        assertThat(operationCount("capture")).isEqualTo(1L);
        assertThat(count("ftgo_accounting",
            "select count(*) from payment_captures pc join authorizations a on a.id=pc.authorization_id where a.order_id=? and pc.status='SUCCEEDED'",
            orderId)).isEqualTo(1L);
    }

    @Test
    void cancelBeforeCaptureVoidsExactlyOnce() {
        Fixture fixture = createFixture();
        long orderId = createOrder(fixture, "tok_cancel_void");
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE");

        post(ORDER_URL + "/orders/" + orderId + "/cancel", null);
        awaitOrderState(orderId, "CANCELLED");
        awaitSqlValue("ftgo_accounting",
            "select status from authorizations where order_id=?", orderId, "VOIDED");
        assertThat(operationCount("void")).isEqualTo(1L);
    }

    @Test
    void cancelAfterCaptureRefundsExactlyOnce() {
        Fixture fixture = createFixture();
        long orderId = createOrder(fixture, "tok_cancel_refund");
        long ticketId = awaitTicket(orderId, fixture.restaurantId());
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE");
        post(KITCHEN_URL + "/tickets/" + ticketId + "/accept", null);
        awaitOrderState(orderId, "APPROVED");

        post(ORDER_URL + "/orders/" + orderId + "/cancel", null);
        awaitOrderState(orderId, "CANCELLED");
        awaitSqlValue("ftgo_accounting",
            "select status from authorizations where order_id=?", orderId, "REFUNDED");
        assertThat(operationCount("refund")).isEqualTo(1L);
    }

    @Test
    void duplicateSignedWebhookIsAppliedOnce() throws Exception {
        Fixture fixture = createFixture();
        long orderId = createOrder(fixture, "tok_webhook");
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE");
        String providerAuthorizationId = queryString("ftgo_accounting",
            "select provider_authorization_id from authorizations where order_id=?", orderId);
        String eventId = "evt-" + UUID.randomUUID();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("eventId", eventId);
        payload.put("type", "PAYMENT_CAPTURED");
        payload.put("providerAuthorizationId", providerAuthorizationId);
        payload.put("providerCaptureId", "pc-webhook-" + orderId);
        payload.set("amount", money(fixture.price()));
        payload.put("occurredAt", Instant.now().toString());
        byte[] raw = JSON.writeValueAsBytes(payload);
        long timestamp = Instant.now().getEpochSecond();
        String signature = signature(timestamp, raw);

        webhook(timestamp, signature, raw);
        webhook(timestamp, signature, raw);
        assertThat(count("ftgo_accounting",
            "select count(*) from payment_webhook_events where provider_event_id=?", eventId))
            .isEqualTo(1L);
    }

    @Test
    void reconciliationSafeUpdatesAndCreatesDeterministicManualReview() {
        Fixture safeFixture = createFixture();
        long safeOrderId = createOrder(safeFixture, "tok_reconcile_safe");
        awaitOrderState(safeOrderId, "AWAITING_RESTAURANT_ACCEPTANCE");
        String safeProviderId = queryString("ftgo_accounting",
            "select provider_authorization_id from authorizations where order_id=?", safeOrderId);
        ObjectNode capture = JSON.createObjectNode();
        capture.put("providerAuthorizationId", safeProviderId);
        capture.set("amount", money(safeFixture.price()));
        capture.put("requestId", "reconcile-safe-" + safeOrderId);
        post(ACCOUNTING_URL + "/admin/payments/provider-sandbox/capture", capture);
        awaitSqlValue("ftgo_accounting",
            "select status from authorizations where order_id=?", safeOrderId, "CAPTURED");

        Fixture mismatchFixture = createFixture();
        long mismatchOrderId = createOrder(mismatchFixture, "tok_reconcile_mismatch");
        awaitOrderState(mismatchOrderId, "AWAITING_RESTAURANT_ACCEPTANCE");
        String mismatchProviderId = queryString("ftgo_accounting",
            "select provider_authorization_id from authorizations where order_id=?", mismatchOrderId);
        ObjectNode mismatch = JSON.createObjectNode();
        mismatch.put("providerAuthorizationId", mismatchProviderId);
        mismatch.set("amount", money(mismatchFixture.price().add(BigDecimal.ONE)));
        mismatch.put("requestId", "reconcile-mismatch-" + mismatchOrderId);
        post(ACCOUNTING_URL + "/admin/payments/provider-sandbox/capture", mismatch);
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
            assertThat(count("ftgo_accounting",
                "select count(*) from payment_reconciliation_cases where authorization_id=(select id from authorizations where order_id=?) and case_type='AMOUNT_MISMATCH'",
                mismatchOrderId)).isEqualTo(1L));
    }

    private Fixture createFixture() {
        BigDecimal price = new BigDecimal("25.00");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ObjectNode consumer = JSON.createObjectNode()
            .put("name", "Payment E2E " + suffix)
            .put("email", "payment-" + suffix + "@example.test")
            .put("creditLimit", new BigDecimal("1000.00"));
        long consumerId = post(CONSUMER_URL + "/consumers", consumer).path("id").asLong();
        ObjectNode address = JSON.createObjectNode()
            .put("street", "1 Settlement Street").put("city", "Hanoi")
            .put("state", "HN").put("zipCode", "10000");
        ObjectNode restaurant = JSON.createObjectNode()
            .put("name", "Settlement Restaurant " + suffix)
            .put("openingHours", "{\"daily\":\"00:00-23:59\"}");
        restaurant.set("address", address);
        long restaurantId = post(RESTAURANT_URL + "/restaurants", restaurant).path("id").asLong();
        String menuName = "Settlement Item " + suffix;
        ObjectNode item = JSON.createObjectNode().put("name", menuName)
            .put("description", "Phase 02B settlement item");
        item.set("price", money(price));
        long menuItemId = post(RESTAURANT_URL + "/restaurants/" + restaurantId + "/menu-items", item)
            .path("id").asLong();
        long menuVersion = Long.parseLong(queryString("ftgo_restaurant",
            "select menu_version from restaurants where id=?", restaurantId));
        return new Fixture(consumerId, restaurantId, menuItemId, menuVersion, price, menuName);
    }

    private long createOrder(Fixture f, String token) {
        ObjectNode line = JSON.createObjectNode().put("menuItemId", f.menuItemId())
            .put("name", f.menuName()).put("quantity", 1);
        line.set("price", money(f.price()));
        ArrayNode lines = JSON.createArrayNode().add(line);
        ObjectNode address = JSON.createObjectNode().put("street", "1 Settlement Street")
            .put("city", "Hanoi").put("state", "HN").put("zipCode", "10000");
        ObjectNode request = JSON.createObjectNode().put("consumerId", f.consumerId())
            .put("restaurantId", f.restaurantId()).put("expectedMenuVersion", f.menuVersion())
            .put("deliveryTime", LocalDateTime.now().plusHours(1).toString())
            .put("paymentToken", token);
        request.set("lineItems", lines);
        request.set("deliveryAddress", address);
        return post(ORDER_URL + "/orders", request).path("orderId").asLong();
    }

    private long awaitTicket(long orderId, long restaurantId) {
        long[] result = {-1};
        Awaitility.await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            for (JsonNode ticket : get(KITCHEN_URL + "/tickets?restaurantId=" + restaurantId)) {
                if (ticket.path("orderId").asLong() == orderId) result[0] = ticket.path("id").asLong();
            }
            assertThat(result[0]).isPositive();
        });
        return result[0];
    }

    private void awaitOrderState(long orderId, String state) {
        Awaitility.await().atMost(90, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> assertThat(get(ORDER_URL + "/orders/" + orderId)
                .path("state").asText()).isEqualTo(state));
    }

    private long operationCount(String name) {
        return get(ACCOUNTING_URL + "/admin/payments/provider-sandbox/operations")
            .path(name).asLong(0L);
    }

    private void webhook(long timestamp, String signature, byte[] raw) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ACCOUNTING_URL + "/webhooks/payments/sandbox"))
            .header("Content-Type", "application/json")
            .header("X-Payment-Timestamp", Long.toString(timestamp))
            .header("X-Payment-Signature", signature)
            .POST(HttpRequest.BodyPublishers.ofByteArray(raw)).build();
        assertThat(HTTP.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
    }

    private String signature(long timestamp, byte[] raw) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        return HexFormat.of().formatHex(mac.doFinal(raw));
    }

    private JsonNode get(String url) {
        HttpResponse<String> response = send("GET", url, null);
        assertThat(response.statusCode()).isBetween(200, 299);
        return parse(response.body());
    }

    private JsonNode post(String url, JsonNode body) {
        HttpResponse<String> response = send("POST", url, body);
        assertThat(response.statusCode()).withFailMessage("POST %s: %s %s", url,
            response.statusCode(), response.body()).isBetween(200, 299);
        return response.body().isBlank() ? JSON.createObjectNode() : parse(response.body());
    }

    private HttpResponse<String> send(String method, String url, JsonNode body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
            String payload = body == null ? "{}" : JSON.writeValueAsString(body);
            if ("GET".equals(method)) builder.GET();
            else if ("DELETE".equals(method)) builder.DELETE();
            else builder.method(method, HttpRequest.BodyPublishers.ofString(payload));
            return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("HTTP request failed: " + method + " " + url, e);
        }
    }

    private ObjectNode money(BigDecimal amount) { return JSON.createObjectNode().put("amount", amount); }
    private JsonNode parse(String body) {
        try { return JSON.readTree(body); }
        catch (Exception e) { throw new RuntimeException("Invalid JSON: " + body, e); }
    }

    private void awaitSqlValue(String schema, String sql, Object parameter, String expected) {
        Awaitility.await().atMost(90, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> assertThat(queryString(schema, sql, parameter)).isEqualTo(expected));
    }

    private long count(String schema, String sql, Object parameter) {
        String value = queryString(schema, sql, parameter);
        return value == null ? 0 : Long.parseLong(value);
    }

    private String queryString(String schema, String sql, Object parameter) {
        try (Connection c = connection(schema); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, parameter);
            try (ResultSet r = s.executeQuery()) { return r.next() ? r.getString(1) : null; }
        } catch (Exception e) { throw new RuntimeException("Query failed: " + sql, e); }
    }

    private void execute(String schema, String sql, Object parameter) {
        try (Connection c = connection(schema); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, parameter); s.executeUpdate();
        } catch (Exception e) { throw new RuntimeException("Update failed: " + sql, e); }
    }

    private Connection connection(String schema) throws Exception {
        return DriverManager.getConnection(JDBC_BASE + "/" + schema
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            "ftgo_user", "ftgo_password");
    }

    private record Fixture(long consumerId, long restaurantId, long menuItemId,
                           long menuVersion, BigDecimal price, String menuName) { }
}

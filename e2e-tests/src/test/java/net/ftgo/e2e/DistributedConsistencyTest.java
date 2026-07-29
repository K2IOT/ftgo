package net.ftgo.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.ftgo.e2e.support.FailureInjector;
import net.ftgo.e2e.support.KafkaProbe;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "FTGO_PHASE03_E2E_ENABLED", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DistributedConsistencyTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private static final AtomicLong SYNTHETIC_IDS = new AtomicLong(8_000_000L);

    private static final String ORDER_URL = "http://localhost:8081";
    private static final String CONSUMER_URL = "http://localhost:8082";
    private static final String RESTAURANT_URL = "http://localhost:8083";
    private static final String KITCHEN_URL = "http://localhost:8084";
    private static final String DELIVERY_URL = "http://localhost:8086";
    private static final String HISTORY_URL = "http://localhost:8087";
    private static final String JDBC_BASE = environment(
        "FTGO_E2E_JDBC_URL",
        "jdbc:mysql://localhost:33306"
    );
    private static final String DB_USER = "ftgo_user";
    private static final String DB_PASSWORD = "ftgo_password";
    private static final String ORDER_TOPIC = "net.ftgo.orderservice.domain.Order";
    private static final String ORDER_DLT = ORDER_TOPIC + ".DLT";

    private static KafkaProbe kafka;
    private static FailureInjector failures;

    @BeforeAll
    static void beforeAll() {
        kafka = new KafkaProbe(environment(
            "FTGO_E2E_KAFKA_BOOTSTRAP_SERVERS",
            "localhost:29092"
        ));
        failures = new FailureInjector(
            environment("FTGO_E2E_CONNECT_URL", "http://localhost:18083"),
            Path.of(requiredEnvironment("FTGO_E2E_RUN_DIR"))
        );
        failures.awaitHealth(DELIVERY_URL + "/actuator/health", Duration.ofSeconds(30));
        failures.awaitHealth(HISTORY_URL + "/actuator/health", Duration.ofSeconds(30));
    }

    @AfterAll
    static void afterAll() {
        kafka.close();
    }

    @Test
    @Order(1)
    void participantCommitThenDroppedReplyReplaysOriginalResult() {
        ApprovedOrder approved = createApprovedOrder("lost-reply");
        ProcessedCommand command = awaitReservationCommand(approved.orderId());
        ConsumerRecord<String, String> wireCommand = awaitCommandRecord(
            command.commandId(),
            approved.orderId()
        );
        List<ConsumerRecord<String, String>> beforeReplies = awaitReplyRecords(
            command.commandId(),
            1
        );
        long reservationId = reservationId(approved.orderId());

        kafka.duplicate(wireCommand);

        List<ConsumerRecord<String, String>> replies = awaitReplyRecords(
            command.commandId(),
            beforeReplies.size() + 1
        );
        ProcessedCommand replayed = processedCommand(command.commandId());
        assertThat(replayed.replyPayload()).isEqualTo(command.replyPayload());
        assertThat(reservationId(approved.orderId())).isEqualTo(reservationId);
        assertThat(count(
            "ftgo_consumer",
            "select count(*) from credit_reservations where order_id = ?",
            approved.orderId()
        )).isEqualTo(1L);
        assertThat(kafka.eventuatePayload(replies.get(replies.size() - 1).value()))
            .isEqualTo(kafka.eventuatePayload(replies.get(replies.size() - 2).value()));
    }

    @Test
    @Order(2)
    void serviceRestartAfterCommitStillReplaysSameReplyAndReservationId() {
        ApprovedOrder approved = createApprovedOrder("restart-replay");
        ProcessedCommand command = awaitReservationCommand(approved.orderId());
        ConsumerRecord<String, String> wireCommand = awaitCommandRecord(
            command.commandId(),
            approved.orderId()
        );
        int replyCount = awaitReplyRecords(command.commandId(), 1).size();
        long reservationId = reservationId(approved.orderId());

        failures.restartService(
            "consumer-service",
            CONSUMER_URL + "/actuator/health"
        );
        kafka.duplicate(wireCommand);

        awaitReplyRecords(command.commandId(), replyCount + 1);
        assertThat(processedCommand(command.commandId()).replyPayload())
            .isEqualTo(command.replyPayload());
        assertThat(reservationId(approved.orderId())).isEqualTo(reservationId);
        assertThat(count(
            "ftgo_consumer",
            "select count(*) from processed_commands where command_id = ?",
            command.commandId()
        )).isEqualTo(1L);
    }

    @Test
    @Order(3)
    void exactSameDomainEventPublishedTwiceMutatesDeliveryOnce() {
        ApprovedOrder approved = createApprovedOrder("duplicate-event");
        awaitDelivery(approved.orderId());
        ConsumerRecord<String, String> approvedEvent = kafka.awaitRecord(
            ORDER_TOPIC,
            record -> "OrderApproved".equals(kafka.header(record.headers(), "eventType"))
                && kafka.containsText(record, String.valueOf(approved.orderId())),
            Duration.ofSeconds(60)
        );
        String eventId = kafka.header(approvedEvent.headers(), "id");

        kafka.duplicate(approvedEvent);

        Awaitility.await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> {
                assertThat(count(
                    "ftgo_delivery",
                    "select count(*) from deliveries where order_id = ?",
                    approved.orderId()
                )).isEqualTo(1L);
                assertThat(count(
                    "ftgo_delivery",
                    "select count(*) from processed_messages where message_id = ?",
                    eventId
                )).isEqualTo(1L);
            });
    }

    @Test
    @Order(4)
    void twoSameTypeEventsForOneAggregateBothApplyAndTrueDuplicateIsSkipped() {
        long orderId = nextSyntheticId();
        publishOrderCreated(orderId, "APPROVAL_PENDING", new BigDecimal("40.00"));
        awaitHistoryStatus(orderId, "APPROVAL_PENDING");

        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        String first = orderRevisedEnvelope(orderId, firstId, 2L, new BigDecimal("41.00"));
        String second = orderRevisedEnvelope(orderId, secondId, 3L, new BigDecimal("42.00"));
        sendEnvelope(orderId, "OrderRevised", firstId, first);
        sendEnvelope(orderId, "OrderRevised", secondId, second);
        sendEnvelope(orderId, "OrderRevised", firstId, first);

        awaitHistoryTotal(orderId, new BigDecimal("42.00"));
        Awaitility.await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8))
            .untilAsserted(() -> assertThat(history(orderId).path("orderTotal").decimalValue())
                .isEqualByComparingTo("42.00"));
    }

    @Test
    @Order(5)
    void outOfOrderOrderApprovedIsPendingUntilOrderCreatedArrives() {
        long orderId = nextSyntheticId();
        UUID approvedId = UUID.randomUUID();
        sendEnvelope(
            orderId,
            "OrderApproved",
            approvedId,
            orderApprovedEnvelope(orderId, approvedId, 2L, new BigDecimal("50.00"))
        );
        assertThat(getStatus(HISTORY_URL + "/api/orders/" + orderId)).isEqualTo(404);

        publishOrderCreated(orderId, "APPROVAL_PENDING", new BigDecimal("50.00"));

        awaitHistoryStatus(orderId, "APPROVED");
    }

    @Test
    @Order(6)
    void poisonEventReachesDltAndDoesNotBlockLaterValidEventOnSamePartition() {
        long poisonOrderId = nextSyntheticId();
        UUID poisonId = UUID.randomUUID();
        kafka.send(
            ORDER_TOPIC,
            0,
            String.valueOf(poisonOrderId),
            "{malformed-json",
            headers(poisonId, "OrderCreated")
        );

        kafka.awaitRecord(
            ORDER_DLT,
            record -> poisonId.toString().equals(
                kafka.header(record.headers(), "ftgo_dlt_event_id")
            ),
            Duration.ofSeconds(60)
        );

        long validOrderId = nextSyntheticId();
        publishOrderCreatedOnPartition(
            validOrderId,
            "APPROVAL_PENDING",
            new BigDecimal("60.00"),
            0
        );
        awaitHistoryStatus(validOrderId, "APPROVAL_PENDING");
    }

    @Test
    @Order(7)
    void pausedDebeziumResumesAndDeliversOutboxEventOnce() {
        failures.pauseConnector("phase03-order-outbox");
        ApprovedOrder approved;
        try {
            approved = createApprovedOrderWithoutDeliveryWait("debezium-pause");
            String eventId = awaitOrderOutboxEventId(approved.orderId(), "OrderApproved");
            assertThat(count(
                "ftgo_delivery",
                "select count(*) from deliveries where order_id = ?",
                approved.orderId()
            )).isZero();
            failures.resumeConnector("phase03-order-outbox");
            awaitDelivery(approved.orderId());
            assertThat(count(
                "ftgo_delivery",
                "select count(*) from processed_messages where message_id = ?",
                eventId
            )).isEqualTo(1L);
        } finally {
            try {
                failures.resumeConnector("phase03-order-outbox");
            } catch (RuntimeException ignored) {
                // The connector may already be running after the successful path.
            }
        }
    }

    private ApprovedOrder createApprovedOrder(String scenario) {
        ApprovedOrder approved = createApprovedOrderWithoutDeliveryWait(scenario);
        awaitDelivery(approved.orderId());
        awaitHistoryStatus(approved.orderId(), "APPROVED");
        return approved;
    }

    private ApprovedOrder createApprovedOrderWithoutDeliveryWait(String scenario) {
        Fixture fixture = createFixture(new BigDecimal("1000.00"), new BigDecimal("25.00"), scenario);
        long orderId = createOrder(fixture, "tok_phase03_" + scenario);
        long ticketId = awaitTicket(orderId, fixture.restaurantId());
        awaitOrderState(orderId, "AWAITING_RESTAURANT_ACCEPTANCE");
        post(KITCHEN_URL + "/tickets/" + ticketId + "/accept", null);
        awaitOrderState(orderId, "APPROVED");
        return new ApprovedOrder(orderId, ticketId);
    }

    private ProcessedCommand awaitReservationCommand(long orderId) {
        final ProcessedCommand[] result = new ProcessedCommand[1];
        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> {
                result[0] = findReservationCommand(orderId);
                assertThat(result[0]).isNotNull();
            });
        return result[0];
    }

    private ProcessedCommand findReservationCommand(long orderId) {
        String sql = "select command_id, cast(reply_payload as char), reply_type "
            + "from processed_commands "
            + "where reply_type = 'net.ftgo.common.orderflow.replies.ConsumerCreditReserved' "
            + "and cast(json_unquote(json_extract(reply_payload, '$.orderId')) as unsigned) = ? "
            + "order by processed_at asc limit 1";
        try (Connection connection = connection("ftgo_consumer");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, orderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                return new ProcessedCommand(
                    resultSet.getString(1),
                    resultSet.getString(2),
                    resultSet.getString(3)
                );
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to query processed command", e);
        }
    }

    private ProcessedCommand processedCommand(String commandId) {
        String sql = "select command_id, cast(reply_payload as char), reply_type "
            + "from processed_commands where command_id = ?";
        try (Connection connection = connection("ftgo_consumer");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, commandId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("Processed command not found: " + commandId);
                }
                return new ProcessedCommand(
                    resultSet.getString(1),
                    resultSet.getString(2),
                    resultSet.getString(3)
                );
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to reload processed command", e);
        }
    }

    private ConsumerRecord<String, String> awaitCommandRecord(String commandId, long orderId) {
        return kafka.awaitRecord(
            "consumerService",
            record -> kafka.containsText(record, commandId)
                || (kafka.containsText(record, "ReserveConsumerCreditCommand")
                    && kafka.containsText(record, String.valueOf(orderId))),
            Duration.ofSeconds(60)
        );
    }

    private List<ConsumerRecord<String, String>> awaitReplyRecords(
        String commandId,
        int minimum
    ) {
        List<ConsumerRecord<String, String>> records = kafka.awaitRecords(
            "createOrderSagaReply",
            record -> kafka.containsText(record, commandId),
            minimum,
            Duration.ofSeconds(60)
        );
        assertThat(records).hasSizeGreaterThanOrEqualTo(minimum);
        return records;
    }

    private long reservationId(long orderId) {
        return Long.parseLong(queryString(
            "ftgo_consumer",
            "select id from credit_reservations where order_id = ?",
            orderId
        ));
    }

    private void publishOrderCreated(long orderId, String status, BigDecimal total) {
        publishOrderCreatedOnPartition(orderId, status, total, 0);
    }

    private void publishOrderCreatedOnPartition(
        long orderId,
        String status,
        BigDecimal total,
        int partition
    ) {
        UUID eventId = UUID.randomUUID();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("orderId", orderId);
        payload.put("consumerId", 9001L);
        payload.put("restaurantId", 9002L);
        payload.put("status", status);
        payload.set("orderTotal", money(total));
        ObjectNode item = JSON.createObjectNode();
        item.put("menuItemId", 1L);
        item.put("name", "Synthetic Burger");
        item.set("price", money(total));
        item.put("quantity", 1);
        payload.set("lineItems", JSON.createArrayNode().add(item));
        payload.put("deliveryAddress", "1 Synthetic Street");
        payload.put("deliveryTime", LocalDateTime.now().plusHours(1).toString());
        payload.put("createdAt", LocalDateTime.now().toString());
        String envelope = envelope(eventId, "OrderCreated", orderId, 1L, payload).toString();
        kafka.send(
            ORDER_TOPIC,
            partition,
            String.valueOf(orderId),
            envelope,
            headers(eventId, "OrderCreated")
        );
    }

    private String orderRevisedEnvelope(
        long orderId,
        UUID eventId,
        long version,
        BigDecimal total
    ) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("orderId", orderId);
        payload.put("consumerId", 9001L);
        payload.put("restaurantId", 9002L);
        ObjectNode item = JSON.createObjectNode();
        item.put("menuItemId", 1L);
        item.put("name", "Revised Burger");
        item.set("price", money(total));
        item.put("quantity", 1);
        payload.set("lineItems", JSON.createArrayNode().add(item));
        payload.set("orderTotal", money(total));
        return envelope(eventId, "OrderRevised", orderId, version, payload).toString();
    }

    private String orderApprovedEnvelope(
        long orderId,
        UUID eventId,
        long version,
        BigDecimal total
    ) {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("orderId", orderId);
        payload.put("consumerId", 9001L);
        payload.put("restaurantId", 9002L);
        payload.set("orderTotal", money(total));
        payload.put("ticketId", 9003L);
        payload.put("authorizationId", 9004L);
        payload.set("pickupAddress", address("1 Synthetic Pickup"));
        payload.set("deliveryAddress", address("2 Synthetic Delivery"));
        payload.put("deliveryTime", LocalDateTime.now().plusHours(1).toString());
        return envelope(eventId, "OrderApproved", orderId, version, payload).toString();
    }

    private void sendEnvelope(long orderId, String eventType, UUID eventId, String envelope) {
        kafka.send(
            ORDER_TOPIC,
            0,
            String.valueOf(orderId),
            envelope,
            headers(eventId, eventType)
        );
    }

    private ObjectNode envelope(
        UUID eventId,
        String eventType,
        long aggregateId,
        long version,
        JsonNode payload
    ) {
        ObjectNode envelope = JSON.createObjectNode();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("schemaVersion", 1);
        envelope.put("aggregateType", "Order");
        envelope.put("aggregateId", String.valueOf(aggregateId));
        envelope.put("aggregateVersion", version);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("correlationId", "phase03-e2e-" + aggregateId);
        envelope.put("causationId", "phase03-e2e-cause-" + aggregateId);
        envelope.set("payload", payload);
        return envelope;
    }

    private Map<String, String> headers(UUID eventId, String eventType) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("id", eventId.toString());
        headers.put("eventType", eventType);
        headers.put("correlationId", "phase03-e2e-" + eventId);
        return headers;
    }

    private Fixture createFixture(BigDecimal creditLimit, BigDecimal price, String scenario) {
        String suffix = scenario + "-" + UUID.randomUUID().toString().substring(0, 8);
        ObjectNode consumer = JSON.createObjectNode();
        consumer.put("name", "Phase03 Consumer " + suffix);
        consumer.put("email", suffix + "@example.test");
        consumer.put("creditLimit", creditLimit);
        long consumerId = post(CONSUMER_URL + "/consumers", consumer).path("id").asLong();

        ObjectNode restaurant = JSON.createObjectNode();
        restaurant.put("name", "Phase03 Restaurant " + suffix);
        restaurant.set("address", address("1 Phase03 Restaurant Street"));
        restaurant.put("openingHours", "{\"daily\":\"00:00-23:59\"}");
        long restaurantId = post(RESTAURANT_URL + "/restaurants", restaurant)
            .path("id").asLong();

        String menuName = "Phase03 Burger " + suffix;
        ObjectNode menuItem = JSON.createObjectNode();
        menuItem.put("name", menuName);
        menuItem.put("description", "Distributed consistency item");
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
        ObjectNode item = JSON.createObjectNode();
        item.put("menuItemId", fixture.menuItemId());
        item.put("name", fixture.menuName());
        item.set("price", money(fixture.price()));
        item.put("quantity", 1);

        ObjectNode request = JSON.createObjectNode();
        request.put("consumerId", fixture.consumerId());
        request.put("restaurantId", fixture.restaurantId());
        request.put("expectedMenuVersion", fixture.menuVersion());
        request.set("lineItems", JSON.createArrayNode().add(item));
        request.set("deliveryAddress", address("2 Phase03 Delivery Street"));
        request.put("deliveryTime", LocalDateTime.now().plusHours(1).toString());
        request.put("paymentToken", paymentToken);
        return post(ORDER_URL + "/orders", request).path("orderId").asLong();
    }

    private ObjectNode address(String street) {
        ObjectNode address = JSON.createObjectNode();
        address.put("street", street);
        address.put("city", "Hanoi");
        address.put("state", "HN");
        address.put("zipCode", "10000");
        return address;
    }

    private ObjectNode money(BigDecimal amount) {
        return JSON.createObjectNode().put("amount", amount);
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
        Awaitility.await().atMost(90, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> assertThat(
                get(ORDER_URL + "/orders/" + orderId).path("state").asText()
            ).isEqualTo(expectedState));
    }

    private void awaitDelivery(long orderId) {
        Awaitility.await().atMost(90, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> assertThat(count(
                "ftgo_delivery",
                "select count(*) from deliveries where order_id = ?",
                orderId
            )).isEqualTo(1L));
    }

    private void awaitHistoryStatus(long orderId, String status) {
        Awaitility.await().atMost(90, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> assertThat(history(orderId).path("status").asText())
                .isEqualTo(status));
    }

    private void awaitHistoryTotal(long orderId, BigDecimal total) {
        Awaitility.await().atMost(60, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> assertThat(history(orderId).path("orderTotal").decimalValue())
                .isEqualByComparingTo(total));
    }

    private JsonNode history(long orderId) {
        return get(HISTORY_URL + "/api/orders/" + orderId);
    }

    private String awaitOrderOutboxEventId(long orderId, String eventType) {
        final String[] eventId = {null};
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250))
            .untilAsserted(() -> {
                eventId[0] = queryString(
                    "ftgo_order",
                    "select event_id from outbox where aggregate_id = ? and event_type = ? order by id desc limit 1",
                    String.valueOf(orderId),
                    eventType
                );
                assertThat(eventId[0]).isNotBlank();
            });
        return eventId[0];
    }

    private JsonNode post(String url, JsonNode body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30));
        if ((ORDER_URL + "/orders").equals(url)) {
    builder.header("Idempotency-Key", "distributed-consistency-" + UUID.randomUUID());
}
        if (body == null) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        }
        HttpResponse<String> response = send(builder.build());
        assertThat(response.statusCode()).isBetween(200, 299);
        if (response.body() == null || response.body().isBlank()) {
            return JSON.createObjectNode();
        }
        try {
            return JSON.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JSON response from " + url, e);
        }
    }

    private JsonNode get(String url) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build());
        assertThat(response.statusCode()).isEqualTo(200);
        try {
            return JSON.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JSON response from " + url, e);
        }
    }

    private int getStatus(String url) {
        return send(HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()).statusCode();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("HTTP request failed: " + request.uri(), e);
        }
    }

    private long count(String schema, String sql, Object... parameters) {
        return Long.parseLong(queryString(schema, sql, parameters));
    }

    private String queryString(String schema, String sql, Object... parameters) {
        try (Connection connection = connection(schema);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return resultSet.getString(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                "SQL query failed for schema " + schema + ": " + sql,
                e
            );
        }
    }

    private Connection connection(String schema) throws Exception {
        return DriverManager.getConnection(
            JDBC_BASE + "/" + schema + "?useSSL=false&allowPublicKeyRetrieval=true",
            DB_USER,
            DB_PASSWORD
        );
    }

    private long nextSyntheticId() {
        return SYNTHETIC_IDS.incrementAndGet();
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable is missing: " + name);
        }
        return value;
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

    private record ApprovedOrder(long orderId, long ticketId) {
    }

    private record ProcessedCommand(String commandId, String replyPayload, String replyType) {
    }
}

# FTGO Phase 01 Runtime Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Làm toàn bộ service khởi động được từ môi trường sạch và chứng minh Eventuate plus Debezium truyền message đúng contract.

**Architecture:** Chuẩn hóa schema Eventuate theo dependency version, đồng bộ JPA entity với Flyway, loại duplicate bean, cung cấp Delivery pickup snapshot source và sửa Debezium Outbox Router. Phase này giữ nguyên public API và business semantics.

**Tech Stack:** Java 21, Spring Boot, Eventuate Tram, MySQL 8, Flyway, Kafka KRaft, Debezium 2.4, Docker Compose, Testcontainers, Gradle.

## Global Constraints

- Không thay đổi business API trong phase này.
- Không sửa migration `V1` đã tồn tại; tạo migration version mới.
- Mọi database test phải chạy trên MySQL Testcontainer, không dùng H2 để xác nhận schema.
- Debezium test phải xác nhận topic, key, headers và JSON value thực tế.

---

### Task 1: Add Fresh-Database Migration Verification

**Files:**
- Create: `common/src/testFixtures/java/net/ftgo/testsupport/MySqlMigrationVerifier.java`
- Modify: `build.gradle`
- Create: `order-service/src/test/java/net/ftgo/order/migration/OrderMigrationTest.java`
- Create: `kitchen-service/src/test/java/net/ftgo/kitchen/migration/KitchenMigrationTest.java`
- Create: `accounting-service/src/test/java/net/ftgo/accounting/migration/AccountingMigrationTest.java`
- Create: `delivery-service/src/test/java/net/ftgo/delivery/migration/DeliveryMigrationTest.java`

**Interfaces:**
- Produces: `MySqlMigrationVerifier.migrateAndValidate(String moduleName, String migrationLocation, Consumer<JdbcTemplate> assertions)`.
- Consumes: Flyway migrations from each service classpath.

- [ ] **Step 1: Enable shared test fixtures**

Add Gradle `java-test-fixtures` to `common` and test fixture dependency to MySQL services:

```groovy
project(':common') {
    apply plugin: 'java-test-fixtures'
    testFixturesImplementation 'org.testcontainers:mysql:1.19.3'
    testFixturesImplementation 'org.springframework:spring-jdbc'
    testFixturesImplementation 'org.flywaydb:flyway-core'
    testFixturesImplementation 'org.flywaydb:flyway-mysql'
}

configure([
    project(':order-service'),
    project(':consumer-service'),
    project(':restaurant-service'),
    project(':kitchen-service'),
    project(':accounting-service'),
    project(':delivery-service')
]) {
    dependencies {
        testImplementation testFixtures(project(':common'))
    }
}
```

- [ ] **Step 2: Write failing migration tests**

Each test must start MySQL, run all Flyway migrations and boot a JPA context with `ddl-auto=validate`. Example assertion for Kitchen:

```java
@Test
void freshSchemaMatchesTicketMapping() {
    verifier.migrateAndValidate(
        "kitchen-service",
        "classpath:db/migration",
        jdbc -> {
            assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns " +
                "where table_schema = database() and table_name='tickets' and column_name='previous_state'",
                Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables " +
                "where table_schema = database() and table_name='pending_ticket_line_items'",
                Integer.class)).isEqualTo(1);
        });
}
```

- [ ] **Step 3: Run tests and capture baseline failure**

Run:

```bash
./gradlew :order-service:test --tests '*MigrationTest' \
  :kitchen-service:test --tests '*MigrationTest' \
  :accounting-service:test --tests '*MigrationTest' \
  :delivery-service:test --tests '*MigrationTest'
```

Expected: FAIL for missing Eventuate tables/columns and Kitchen pending-revision schema.

- [ ] **Step 4: Commit test harness**

```bash
git add build.gradle common/src/testFixtures */src/test/java/*/migration
git commit -m "test: verify service migrations on mysql"
```

---

### Task 2: Install the Correct Eventuate Production Schema

**Files:**
- Create: `order-service/src/main/resources/db/migration/V2__create_eventuate_messaging_schema.sql`
- Create: `order-service/src/main/resources/db/migration/V3__align_eventuate_saga_schema.sql`
- Delete after test migration: `order-service/src/test/resources/eventuate-schema.sql`
- Modify: `order-service/src/test/java/net/ftgo/order/saga/OrderServiceIntegrationTestBase.java`
- Test: `order-service/src/test/java/net/ftgo/order/migration/OrderMigrationTest.java`

**Interfaces:**
- Produces tables required by Eventuate Tram `0.34.0.RELEASE` and Eventuate Sagas `0.23.0.RELEASE`.
- Produces schema used identically by production and integration tests.

- [ ] **Step 1: Extend failing assertions**

Assert the fresh schema contains:

```text
eventuate.message
eventuate.received_messages
eventuate.saga_instance
eventuate.saga_instance_participants
```

And columns:

```text
end_state
compensating
failed
```

- [ ] **Step 2: Add official-compatible messaging tables**

Create `V2__create_eventuate_messaging_schema.sql` with the exact columns required by the pinned Eventuate versions:

```sql
CREATE SCHEMA IF NOT EXISTS eventuate;

CREATE TABLE IF NOT EXISTS eventuate.message (
  id VARCHAR(255) PRIMARY KEY,
  destination TEXT NOT NULL,
  headers TEXT NOT NULL,
  payload TEXT NOT NULL,
  published SMALLINT DEFAULT 0,
  message_partition SMALLINT,
  creation_time BIGINT
);

CREATE TABLE IF NOT EXISTS eventuate.received_messages (
  consumer_id VARCHAR(255) NOT NULL,
  message_id VARCHAR(255) NOT NULL,
  creation_time BIGINT,
  PRIMARY KEY (consumer_id, message_id)
);
```

- [ ] **Step 3: Align saga tables without destructive edits**

`V3__align_eventuate_saga_schema.sql` must add missing columns and indexes using guarded migration logic compatible with MySQL 8. The final table contract must match integration tests and library SQL.

- [ ] **Step 4: Make tests use production migrations**

Remove loading of `src/test/resources/eventuate-schema.sql`; integration tests must only use Flyway migrations.

- [ ] **Step 5: Run migration and saga integration tests**

```bash
./gradlew :order-service:test \
  --tests 'net.ftgo.order.migration.OrderMigrationTest' \
  --tests 'net.ftgo.order.saga.*IntegrationTest'
```

Expected: PASS with no manually injected Eventuate schema.

- [ ] **Step 6: Commit**

```bash
git add order-service/src/main/resources/db/migration \
  order-service/src/test/java order-service/src/test/resources
git commit -m "fix: align production eventuate schema"
```

---

### Task 3: Align Kitchen Ticket Mapping and Migration

**Files:**
- Create: `kitchen-service/src/main/resources/db/migration/V2__add_ticket_pending_operation_state.sql`
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/domain/PendingTicketLineItem.java`
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/domain/Ticket.java`
- Test: `kitchen-service/src/test/java/net/ftgo/kitchen/migration/KitchenMigrationTest.java`
- Test: `kitchen-service/src/test/java/net/ftgo/kitchen/domain/TicketTest.java`

**Interfaces:**
- Produces persistent `previous_state` and `pending_ticket_line_items` representation.
- Preserves `Ticket.beginRevise`, `confirmPendingRevise` and `undoRevise` behavior.

- [ ] **Step 1: Add failing persistence round-trip test**

Persist a ticket, call `beginRevise`, clear EntityManager, reload, then assert:

```java
assertThat(reloaded.getState()).isEqualTo(TicketState.REVISION_PENDING);
assertThat(reloaded.getPreviousState()).isEqualTo(TicketState.AWAITING_ACCEPTANCE);
assertThat(reloaded.getPendingRevisionLineItems()).hasSize(2);
```

- [ ] **Step 2: Add forward migration**

```sql
ALTER TABLE tickets ADD COLUMN previous_state VARCHAR(50) NULL;

CREATE TABLE pending_ticket_line_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_id BIGINT NOT NULL,
  menu_item_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  quantity INT NOT NULL,
  CONSTRAINT fk_pending_ticket_line_items_ticket
    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE,
  INDEX idx_pending_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 3: Make mapping explicit**

Map `PendingTicketLineItem` to `pending_ticket_line_items` and expose package-private/read-only getters needed by tests. Do not expose mutable collections publicly.

- [ ] **Step 4: Run tests**

```bash
./gradlew :kitchen-service:test \
  --tests 'net.ftgo.kitchen.migration.KitchenMigrationTest' \
  --tests 'net.ftgo.kitchen.domain.TicketTest'
```

Expected: PASS after EntityManager clear/reload.

- [ ] **Step 5: Commit**

```bash
git add kitchen-service/src/main kitchen-service/src/test
git commit -m "fix: persist kitchen pending operations"
```

---

### Task 4: Remove Duplicate Saga Beans

**Files:**
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSagaLocalSteps.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CancelOrderSagaLocalSteps.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/ReviseOrderSagaLocalSteps.java`
- Modify: `order-service/src/main/java/net/ftgo/order/config/CreateOrderSagaConfiguration.java`
- Modify: `order-service/src/main/java/net/ftgo/order/config/CancelOrderSagaConfiguration.java`
- Modify: `order-service/src/main/java/net/ftgo/order/config/ReviseOrderSagaConfiguration.java`
- Create: `order-service/src/test/java/net/ftgo/order/config/SagaBeanUniquenessTest.java`

**Interfaces:**
- Produces exactly one bean for each local-step type and exactly one command dispatcher per saga.

- [ ] **Step 1: Write failing context test**

```java
assertThat(context.getBeansOfType(CreateOrderSagaLocalSteps.class)).hasSize(1);
assertThat(context.getBeansOfType(CancelOrderSagaLocalSteps.class)).hasSize(1);
assertThat(context.getBeansOfType(ReviseOrderSagaLocalSteps.class)).hasSize(1);
```

- [ ] **Step 2: Run baseline test**

```bash
./gradlew :order-service:test --tests '*SagaBeanUniquenessTest'
```

Expected: FAIL because component scanning and `@Bean` factory both register local-step objects.

- [ ] **Step 3: Keep configuration-owned beans**

Remove `@Component` from all three local-step classes. Keep constructors public/package-visible and retain the existing explicit `@Bean` methods.

- [ ] **Step 4: Run context and saga tests**

```bash
./gradlew :order-service:test --tests '*SagaBeanUniquenessTest' --tests '*Saga*Test'
```

Expected: PASS and no `NoUniqueBeanDefinitionException`.

- [ ] **Step 5: Commit**

```bash
git add order-service/src/main/java/net/ftgo/order/{saga,config} order-service/src/test
git commit -m "fix: register saga local steps once"
```

---

### Task 5: Provide a Delivery Pickup Address Snapshot

**Files:**
- Modify: `common/src/main/java/net/ftgo/common/orderflow/events/OrderApproved.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSagaLocalSteps.java`
- Modify: `delivery-service/src/main/java/net/ftgo/delivery/messaging/OrderEventConsumer.java`
- Delete: `delivery-service/src/main/java/net/ftgo/delivery/messaging/RestaurantPickupAddressResolver.java`
- Modify: `delivery-service/src/test/java/net/ftgo/delivery/DeliveryServiceIntegrationTest.java`

**Interfaces:**
- `OrderApproved` produces `Address pickupAddress` and `Address deliveryAddress` snapshots.
- Delivery consumer creates `Delivery` without synchronous service lookup.

- [ ] **Step 1: Write failing event contract test**

Serialize and deserialize `OrderApproved` and assert pickup address survives round trip.

- [ ] **Step 2: Add pickup address to event contract**

Add constructor/getter/JSON support while keeping a compatibility constructor for existing tests during this phase.

- [ ] **Step 3: Populate the snapshot**

Order approval must obtain pickup address from order saga data or the order snapshot available after Restaurant resolution. Until Phase 02 replaces menu/address resolution, use the existing restaurant test fixture/configured address source and make absence a hard failure rather than a null fallback.

- [ ] **Step 4: Remove missing bean dependency**

Change Delivery construction to:

```java
Delivery delivery = new Delivery(
    event.getOrderId(),
    event.getPickupAddress(),
    event.getDeliveryAddress(),
    event.getDeliveryTime()
);
```

- [ ] **Step 5: Run delivery context and integration tests**

```bash
./gradlew :delivery-service:test --tests '*DeliveryServiceIntegrationTest' --tests '*OrderEventConsumer*'
```

Expected: Spring context starts with no `RestaurantPickupAddressResolver` bean.

- [ ] **Step 6: Commit**

```bash
git add common order-service delivery-service
git commit -m "fix: carry pickup address in order event"
```

---

### Task 6: Correct Debezium Outbox Routing

**Files:**
- Modify: `infrastructure/debezium/register-connectors.sh`
- Create: `infrastructure/debezium/connectors/order-outbox.json`
- Create: `infrastructure/debezium/connectors/kitchen-outbox.json`
- Create: `infrastructure/debezium/connectors/accounting-outbox.json`
- Create: `infrastructure/debezium/connectors/delivery-outbox.json`
- Create: `infrastructure/debezium/verify-connectors.sh`
- Create: `infrastructure/src/test/java/net/ftgo/infrastructure/OutboxRoutingIntegrationTest.java`
- Modify: `settings.gradle`
- Modify: `build.gradle`

**Interfaces:**
- Kafka key: `aggregate_id`.
- Kafka header `id`: outbox row/event ID.
- Kafka header `eventType`: `event_type`.
- Kafka topic: value in outbox `destination`.
- Kafka value: raw `payload`, not an additional envelope generated by SMT.

- [ ] **Step 1: Add infrastructure integration-test module**

Include `infrastructure` as a test-only Gradle module with Testcontainers Kafka, MySQL and Kafka Connect/Debezium image pinned to `2.4.x`.

- [ ] **Step 2: Write failing routing test**

Insert one outbox row:

```sql
INSERT INTO outbox
(aggregate_type, aggregate_id, event_type, payload, destination, published)
VALUES
('Order', '42', 'OrderCreated', JSON_OBJECT('orderId', 42),
 'net.ftgo.orderservice.domain.Order', FALSE);
```

Assert consumed record:

```java
assertThat(record.topic()).isEqualTo("net.ftgo.orderservice.domain.Order");
assertThat(record.key()).isEqualTo("42");
assertThat(header(record, "eventType")).isEqualTo("OrderCreated");
assertThat(header(record, "id")).isNotBlank();
assertThat(objectMapper.readTree(record.value()).get("orderId").asLong()).isEqualTo(42L);
```

- [ ] **Step 3: Replace obsolete connector properties**

Each connector JSON must use:

```json
{
  "connector.class": "io.debezium.connector.mysql.MySqlConnector",
  "topic.prefix": "ftgo-order",
  "schema.history.internal.kafka.bootstrap.servers": "kafka-1:9092,kafka-2:9092,kafka-3:9092",
  "schema.history.internal.kafka.topic": "schema-history.ftgo-order",
  "transforms": "outbox",
  "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
  "transforms.outbox.table.field.event.id": "id",
  "transforms.outbox.table.field.event.key": "aggregate_id",
  "transforms.outbox.table.field.event.type": "event_type",
  "transforms.outbox.table.field.event.payload": "payload",
  "transforms.outbox.route.by.field": "destination",
  "transforms.outbox.route.topic.replacement": "${routedByValue}",
  "transforms.outbox.table.fields.additional.placement": "event_type:header:eventType"
}
```

Do not place `destination` in the Kafka value envelope.

- [ ] **Step 4: Make registration idempotent**

`register-connectors.sh` must use PUT `/connectors/{name}/config`, fail on non-2xx responses and call `verify-connectors.sh` until every connector state is `RUNNING`.

- [ ] **Step 5: Run routing integration test**

```bash
./gradlew :infrastructure:test --tests '*OutboxRoutingIntegrationTest'
```

Expected: PASS with exact topic/key/header/value assertions.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle build.gradle infrastructure
git commit -m "fix: route outbox events with debezium"
```

---

### Task 7: Add a Fresh-Stack Smoke Test

**Files:**
- Modify: `docker-compose.yml`
- Create: `scripts/smoke/fresh-stack.sh`
- Create: `scripts/smoke/assert-service-health.sh`
- Create: `scripts/smoke/assert-order-event.sh`
- Create: `docs/runbooks/fresh-stack-smoke.md`

**Interfaces:**
- Produces one command that removes volumes, starts infrastructure/services, waits for readiness and validates one domain event.

- [ ] **Step 1: Pin images and add health checks required by smoke startup**

Pin Kafka, MySQL, Redis, Scylla and Debezium versions; add health checks used by Compose `depends_on: condition: service_healthy` where supported.

- [ ] **Step 2: Implement deterministic smoke script**

```bash
#!/usr/bin/env bash
set -euo pipefail

docker compose down -v --remove-orphans
docker compose up -d --build
./scripts/smoke/assert-service-health.sh
./infrastructure/debezium/register-connectors.sh
./scripts/smoke/assert-order-event.sh
```

The event assertion must create a valid outbox row through an application transaction or test endpoint enabled only under `smoke` profile, then consume the expected Kafka record.

- [ ] **Step 3: Run from clean state**

```bash
./scripts/smoke/fresh-stack.sh
```

Expected: exit code `0`; all service health endpoints return `UP`; expected order event is observed once.

- [ ] **Step 4: Run full phase verification**

```bash
./gradlew clean test
./scripts/smoke/fresh-stack.sh
```

Expected: both commands exit `0`.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml scripts docs/runbooks
git commit -m "test: add fresh stack smoke verification"
```

## Phase Completion Checklist

- [ ] Fresh MySQL migrations validated for every relational service.
- [ ] Eventuate uses production Flyway schema in tests and runtime.
- [ ] Kitchen pending operation state persists across restart.
- [ ] Exactly one local-step bean exists per saga.
- [ ] Delivery Service starts without an unresolved resolver interface.
- [ ] Debezium routes exact topic/key/headers/value contract.
- [ ] Clean-volume smoke test passes twice consecutively.

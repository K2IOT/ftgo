# FTGO Phase 03 Distributed Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bảo đảm saga commands, domain events và CQRS projections an toàn trước duplicate, lost reply, out-of-order, poison message và service restart.

**Architecture:** Chuẩn hóa event envelope; participant lưu command result; consumer dùng event ID riêng; Kafka retry/DLT có bounded policy; Order History có pending-event recovery; saga/outbox có reconciliation và operator visibility.

**Tech Stack:** Eventuate Tram, Kafka, Spring Kafka, Debezium, MySQL, ScyllaDB, JPA, Testcontainers, Awaitility, Micrometer.

## Global Constraints

- Aggregate ID chỉ dùng làm Kafka partition key, không dùng làm event/message identity.
- Event contract phải backward compatible trong một release window.
- Duplicate command phải trả cùng reply payload và ID.
- Poison event không được chặn partition vô thời hạn.
- Không đánh dấu event processed trước khi projection mutation commit thành công.

---

### Task 1: Introduce Versioned Domain Event Envelope

**Files:**
- Create: `common/src/main/java/net/ftgo/common/messaging/DomainEventEnvelope.java`
- Create: `common/src/main/java/net/ftgo/common/messaging/DomainEventMetadata.java`
- Create: `common/src/main/java/net/ftgo/common/messaging/TraceContext.java`
- Modify: `order-service/src/main/java/net/ftgo/order/messaging/DomainEventPublisher.java`
- Modify: `restaurant-service/src/main/java/net/ftgo/restaurant/messaging/DomainEventPublisher.java`
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/messaging/DomainEventPublisher.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/messaging/DomainEventPublisher.java`
- Modify: `delivery-service/src/main/java/net/ftgo/delivery/messaging/DomainEventPublisher.java`
- Create: `common/src/test/java/net/ftgo/common/messaging/DomainEventEnvelopeTest.java`

**Interfaces:**

```java
public record DomainEventEnvelope<T>(
    UUID eventId,
    String eventType,
    int schemaVersion,
    String aggregateType,
    String aggregateId,
    long aggregateVersion,
    Instant occurredAt,
    String correlationId,
    String causationId,
    TraceContext trace,
    T payload
) {}
```

- [ ] **Step 1: Write serialization compatibility tests**

Assert field names, ISO-8601 timestamps, UUID format and ability to deserialize an envelope with unknown additive fields.

- [ ] **Step 2: Add aggregate version**

Add/standardize `@Version` on mutable published aggregates. Event publisher receives aggregate version after state mutation and before outbox insert.

- [ ] **Step 3: Publish envelope as outbox payload**

Every publisher creates one UUID event ID and persists it. Add outbox columns if needed:

```sql
ALTER TABLE outbox
  ADD COLUMN event_id CHAR(36) NULL,
  ADD COLUMN schema_version INT NOT NULL DEFAULT 1,
  ADD UNIQUE KEY uq_outbox_event_id (event_id);
```

Backfill existing null rows with deterministic UUIDs during migration before making `event_id` non-null in a subsequent migration.

- [ ] **Step 4: Configure Debezium ID field**

Change Event Router ID mapping from numeric row ID to `event_id` so Kafka header `id` matches envelope `eventId`.

- [ ] **Step 5: Run tests**

```bash
./gradlew :common:test --tests '*DomainEventEnvelopeTest'
./gradlew :infrastructure:test --tests '*OutboxRoutingIntegrationTest'
```

Expected: header ID equals envelope event ID; aggregate key remains unchanged.

- [ ] **Step 6: Commit**

```bash
git add common */src/main/java/*/messaging */src/main/resources/db/migration infrastructure
git commit -m "feat: standardize versioned domain events"
```

---

### Task 2: Cache Saga Command Results for Idempotent Replies

**Files:**
- Create: `common/src/main/java/net/ftgo/common/messaging/ProcessedCommand.java`
- Create: `common/src/main/java/net/ftgo/common/messaging/ProcessedCommandResult.java`
- Create: `common/src/main/java/net/ftgo/common/messaging/IdempotentCommandExecutor.java`
- Create: `common/src/testFixtures/java/net/ftgo/testsupport/IdempotentCommandContract.java`
- Create migrations: `consumer-service/src/main/resources/db/migration/V3__create_processed_commands.sql`
- Create migrations: `kitchen-service/src/main/resources/db/migration/V3__create_processed_commands.sql`
- Create migrations: `accounting-service/src/main/resources/db/migration/V3__create_processed_commands.sql`
- Modify: participant command handlers in Consumer, Kitchen and Accounting services.
- Create: `kitchen-service/src/test/java/net/ftgo/kitchen/messaging/LostReplyIdempotencyTest.java`
- Create: `accounting-service/src/test/java/net/ftgo/accounting/messaging/LostReplyIdempotencyTest.java`

**Interfaces:**
- Key: `(consumerName, commandId)`.
- Stored result: success/failure type, reply class, reply JSON, processed timestamp.
- `execute(commandId, Supplier<Message> handler) -> Message`.

- [ ] **Step 1: Write lost-reply tests**

Call the same command handler twice with identical Eventuate message ID. Assert:

- domain row count unchanged on second call
- returned reply payload byte-equivalent
- returned ticket/authorization/reservation ID identical

- [ ] **Step 2: Add processed-command schema**

```sql
CREATE TABLE processed_commands (
  consumer_name VARCHAR(100) NOT NULL,
  command_id VARCHAR(255) NOT NULL,
  outcome VARCHAR(20) NOT NULL,
  reply_type VARCHAR(500) NULL,
  reply_payload JSON NULL,
  processed_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (consumer_name, command_id)
);
```

- [ ] **Step 3: Implement executor transaction boundary**

The domain mutation and processed result insert must commit in the same local transaction. On duplicate primary key race, reload stored result and return it.

- [ ] **Step 4: Wrap all saga participant handlers**

Extract command ID from Eventuate message headers. Apply to create/approve/cancel/revise ticket, reserve/release credit, authorize/reverse/revise payment and future participant commands.

- [ ] **Step 5: Run tests**

```bash
./gradlew :consumer-service:test --tests '*Idempotent*' --tests '*LostReply*'
./gradlew :kitchen-service:test --tests '*Idempotent*' --tests '*LostReply*'
./gradlew :accounting-service:test --tests '*Idempotent*' --tests '*LostReply*'
```

Expected: every duplicate returns the original reply.

- [ ] **Step 6: Commit**

```bash
git add common consumer-service kitchen-service accounting-service
git commit -m "fix: replay saga command results idempotently"
```

---

### Task 3: Correct Event Consumer Identity and Transaction Semantics

**Files:**
- Create: `common/src/main/java/net/ftgo/common/messaging/KafkaEventHeaders.java`
- Create: `common/src/main/java/net/ftgo/common/messaging/EventIdentityExtractor.java`
- Modify: `delivery-service/src/main/java/net/ftgo/delivery/messaging/OrderEventConsumer.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/messaging/OrderHistoryEventHandlers.java`
- Modify: all `ProcessedMessage` entities/repositories.
- Create: `delivery-service/src/test/java/net/ftgo/delivery/messaging/EventIdentityTest.java`
- Create: `order-history-service/src/test/java/net/ftgo/orderhistory/messaging/EventIdentityTest.java`

**Interfaces:**
- `EventIdentityExtractor.eventId(headers, envelope) -> UUID`.
- `record.key()` remains aggregate ID.

- [ ] **Step 1: Write regression tests**

Publish two `OrderRevised` events for the same order with different event IDs. Assert both are processed. Publish the first event again and assert only the duplicate is skipped.

- [ ] **Step 2: Replace aggregate-derived IDs**

Remove:

```java
String messageId = record.key();
String messageId = key + "-" + eventType;
```

Use `id` header and verify it equals envelope `eventId`. A mismatch is a malformed event and goes through retry/DLT.

- [ ] **Step 3: Make processed-event write atomic**

For MySQL consumers, use one DB transaction for projection/domain mutation plus processed row. For Scylla Order History, use a dedicated idempotency table with logged batch only when rows share the required partition key; otherwise use conditional insert before mutation plus a recoverable processing state (`PROCESSING`, `APPLIED`) and reconciliation.

- [ ] **Step 4: Run tests**

```bash
./gradlew :delivery-service:test --tests '*EventIdentityTest'
./gradlew :order-history-service:test --tests '*EventIdentityTest'
```

Expected: two same-type events for one aggregate both apply once.

- [ ] **Step 5: Commit**

```bash
git add common delivery-service order-history-service
git commit -m "fix: deduplicate events by event id"
```

---

### Task 4: Add Bounded Retry and Dead-Letter Topics

**Files:**
- Create: `common/src/main/java/net/ftgo/common/messaging/RetryableEventException.java`
- Create: `common/src/main/java/net/ftgo/common/messaging/NonRetryableEventException.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/config/KafkaConfiguration.java`
- Create: `delivery-service/src/main/java/net/ftgo/delivery/config/KafkaConsumerConfiguration.java`
- Modify: `docker-compose.yml` Kafka topic initialization.
- Create: `order-history-service/src/test/java/net/ftgo/orderhistory/messaging/DeadLetterPublishingTest.java`
- Create: `delivery-service/src/test/java/net/ftgo/delivery/messaging/DeadLetterPublishingTest.java`

**Interfaces:**
- Retry intervals: 1s, 5s, 30s; max 3 retries.
- DLT suffix: `.DLT`.
- DLT headers include original topic, partition, offset, event ID, exception class and correlation ID.

- [ ] **Step 1: Write poison-event test**

Publish malformed JSON and assert it reaches `<topic>.DLT` after configured attempts while a valid later message on the same partition is eventually processed.

- [ ] **Step 2: Configure `DefaultErrorHandler`**

Use `DeadLetterPublishingRecoverer` and classify:

- JSON/schema/validation error: non-retryable or one immediate retry
- transient DB/network error: bounded exponential retry
- missing prerequisite projection: retryable and then pending-event recovery

- [ ] **Step 3: Provision DLT topics explicitly**

Auto-create remains disabled. Add every domain topic DLT with matching partition count and suitable retention.

- [ ] **Step 4: Add DLT metrics**

Counters by source topic and exception class; do not put raw payload or PII in metric labels/logs.

- [ ] **Step 5: Run tests**

```bash
./gradlew :delivery-service:test --tests '*DeadLetterPublishingTest'
./gradlew :order-history-service:test --tests '*DeadLetterPublishingTest'
```

Expected: poison message appears once in DLT and subsequent valid event applies.

- [ ] **Step 6: Commit**

```bash
git add common delivery-service order-history-service docker-compose.yml
git commit -m "feat: add bounded kafka retry and dead letters"
```

---

### Task 5: Recover Out-of-Order Order History Events

**Files:**
- Create: `order-history-service/src/main/java/net/ftgo/orderhistory/domain/PendingOrderEvent.java`
- Create: `order-history-service/src/main/java/net/ftgo/orderhistory/repository/PendingOrderEventRepository.java`
- Create: `order-history-service/src/main/java/net/ftgo/orderhistory/service/OrderHistoryProjectionService.java`
- Create: `order-history-service/src/main/java/net/ftgo/orderhistory/service/PendingEventReconciler.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/messaging/OrderHistoryEventHandlers.java`
- Create: `order-history-service/src/test/java/net/ftgo/orderhistory/messaging/OutOfOrderEventIntegrationTest.java`

**Interfaces:**
- Pending event partition key: `orderId`.
- Clustering order: `occurredAt`, `eventId`.
- Reconciler applies pending events in aggregate version order.

- [ ] **Step 1: Write out-of-order tests**

Scenarios:

1. `OrderApproved` before `OrderCreated`.
2. `TicketReady` before `TicketAccepted`.
3. `DeliveryDelivered` before history record exists.
4. Duplicate pending event.

Expected final projection matches ordered event stream.

- [ ] **Step 2: Create pending table through Scylla migration/bootstrap**

```sql
CREATE TABLE pending_order_events (
  order_id text,
  aggregate_version bigint,
  occurred_at timestamp,
  event_id uuid,
  event_type text,
  envelope text,
  retry_count int,
  next_attempt_at timestamp,
  PRIMARY KEY ((order_id), aggregate_version, occurred_at, event_id)
) WITH CLUSTERING ORDER BY (aggregate_version ASC, occurred_at ASC);
```

- [ ] **Step 3: Separate projection logic from Kafka listener**

Listener parses/validates envelope, then calls `projectionService.apply(envelope)`. Missing prerequisite throws `ProjectionPrerequisiteMissingException` and stores pending event without marking it fully applied.

- [ ] **Step 4: Implement reconciliation**

On successful `OrderCreated`, drain pending events for that order synchronously with a bounded count. Scheduled reconciler handles remaining rows and increments retry count with capped backoff.

- [ ] **Step 5: Add terminal failure policy**

After 20 attempts or 24 hours, move event to projection DLT and emit alert metric. Preserve original envelope and failure metadata.

- [ ] **Step 6: Run tests**

```bash
./gradlew :order-history-service:test --tests '*OutOfOrderEventIntegrationTest'
```

Expected: final projection correct and pending table empty for successful scenarios.

- [ ] **Step 7: Commit**

```bash
git add order-history-service
git commit -m "fix: reconcile out of order history events"
```

---

### Task 6: Redesign Scylla Query Tables and Paging

**Files:**
- Create: `order-history-service/src/main/resources/scylla/V2__query_tables.cql`
- Create: `order-history-service/src/main/java/net/ftgo/orderhistory/repository/OrderHistoryByConsumerRepository.java`
- Create: `order-history-service/src/main/java/net/ftgo/orderhistory/repository/OrderHistoryByConsumerStatusRepository.java`
- Create: `order-history-service/src/main/java/net/ftgo/orderhistory/repository/OrderHistoryByConsumerRestaurantRepository.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/service/OrderHistoryProjectionService.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/api/OrderHistoryController.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/api/OrderHistoryResponse.java`
- Create: `order-history-service/src/test/java/net/ftgo/orderhistory/api/OrderHistoryPagingIntegrationTest.java`

**Interfaces:**
- Queries supported:
  - consumer ordered by creation date
  - consumer + status
  - consumer + restaurant
  - optional `since` within partition/query model
- Keyword filter is removed from baseline API or returns explicit unsupported validation until an indexed search backend exists.

- [ ] **Step 1: Write multi-page tests**

Insert 55 records and request page size 20. Assert pages contain 20/20/15 unique rows, descending creation order, stable next tokens and no gaps/duplicates.

- [ ] **Step 2: Create query tables**

Use partitioning/bucketing that prevents unbounded consumer partitions, for example monthly bucket:

```text
partition key: (consumer_id, creation_month)
clustering: creation_date DESC, order_id
```

Create dedicated status and restaurant tables rather than in-memory filtering.

- [ ] **Step 3: Update projection writes**

Projection service writes canonical record and required denormalized rows. Use idempotent upserts keyed by order ID/version.

- [ ] **Step 4: Return result paging state**

Use Spring Data Cassandra `Slice.getPageable()`/driver execution info appropriate to the pinned version to extract the next paging state from the result, not the incoming request.

- [ ] **Step 5: Remove client-side filtering**

Controller selects the repository matching requested filters. Reject unsupported combinations with `400` and stable error code.

- [ ] **Step 6: Run tests**

```bash
./gradlew :order-history-service:test --tests '*OrderHistoryPagingIntegrationTest'
```

Expected: exact page sizes, stable ordering and valid continuation tokens.

- [ ] **Step 7: Commit**

```bash
git add order-history-service
git commit -m "fix: query order history with scylla access patterns"
```

---

### Task 7: Add Saga and Outbox Reconciliation

**Files:**
- Create: `order-service/src/main/java/net/ftgo/order/operations/StuckSagaMonitor.java`
- Create: `order-service/src/main/java/net/ftgo/order/operations/OrderOperationReconciler.java`
- Create: `common/src/main/java/net/ftgo/common/messaging/OutboxMetrics.java`
- Modify: every service `DomainEventPublisher` configuration.
- Create: `order-service/src/main/java/net/ftgo/order/api/admin/SagaOperationsController.java`
- Create: `order-service/src/test/java/net/ftgo/order/operations/OrderOperationReconcilerTest.java`

**Interfaces:**
- Stuck threshold defaults: Create 5 minutes, Cancel/Revise 10 minutes; configurable.
- Admin repair actions are idempotent and audit logged.

- [ ] **Step 1: Write stuck-operation tests**

Create a pending operation older than threshold and assert monitor emits gauge/counter and reconciler classifies it as resumable, compensatable or manual-review.

- [ ] **Step 2: Add outbox backlog metrics**

Expose per service:

```text
ftgo_outbox_unpublished_count
ftgo_outbox_oldest_age_seconds
ftgo_outbox_publish_error_total
```

The custom outbox `published` column is not updated by Debezium; either remove it in a migration or redefine backlog using connector offsets/row age plus cleanup status. Do not report a permanently false metric.

- [ ] **Step 3: Implement operation reconciliation**

Compare OrderOperation, Order state, saga instance and participant IDs. Safe actions:

- mark success when all durable outcomes already exist
- restart a missing saga start request
- trigger compensation for a failed pre-completion operation
- flag manual review for ambiguous financial state

- [ ] **Step 4: Add protected admin endpoints**

Read-only inspect endpoint and explicit action endpoint requiring reason plus idempotency key. Phase 04 enforces admin authorization.

- [ ] **Step 5: Run tests**

```bash
./gradlew :order-service:test --tests '*OrderOperationReconcilerTest'
```

Expected: no action is executed twice and ambiguous payment state is never auto-forced.

- [ ] **Step 6: Commit**

```bash
git add common order-service */src/main/java/*/messaging
git commit -m "feat: reconcile stuck sagas and outbox health"
```

---

### Task 8: Distributed Failure E2E Suite

**Files:**
- Create: `e2e-tests/src/test/java/net/ftgo/e2e/DistributedConsistencyTest.java`
- Create: `e2e-tests/src/test/java/net/ftgo/e2e/support/FailureInjector.java`
- Create: `e2e-tests/src/test/java/net/ftgo/e2e/support/KafkaProbe.java`

**Interfaces:**
- Test harness can stop/restart service containers, pause Kafka Connect and inject duplicate records.

- [ ] **Step 1: Implement scenarios**

1. Participant commits then reply is dropped; retry returns same result.
2. Service restarts after domain commit before processed result response.
3. Same event published twice.
4. Two same-type events for same aggregate.
5. Order History receives events out of order.
6. Poison event reaches DLT without blocking valid event.
7. Debezium paused then resumed; outbox event is eventually delivered once to idempotent consumer.

- [ ] **Step 2: Run suite twice**

```bash
./gradlew :e2e-tests:test --tests '*DistributedConsistencyTest'
./gradlew :e2e-tests:test --tests '*DistributedConsistencyTest'
```

Expected: both executions pass; row/event counts remain deterministic.

- [ ] **Step 3: Run full verification**

```bash
./gradlew clean test
```

Expected: exit code `0`.

- [ ] **Step 4: Commit**

```bash
git add e2e-tests
git commit -m "test: verify distributed failure recovery"
```

## Phase Completion Checklist

- [ ] Event identity and aggregate partition key are distinct.
- [ ] All participant commands replay original replies.
- [ ] Retry/DLT handling is bounded and observable.
- [ ] Order History recovers out-of-order events.
- [ ] Scylla queries do not filter pages in memory.
- [ ] Stuck saga and outbox health are inspectable and reconcilable.
- [ ] Distributed failure E2E suite passes twice consecutively.

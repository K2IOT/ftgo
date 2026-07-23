# Phase 02 Core Order Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand checkout into an authoritative, restart-safe order flow that reserves credit, authorizes then captures payment after restaurant acceptance, and compensates rejection or timeout exactly once.

**Architecture:** Keep Eventuate Tram command/reply for synchronous participants and use versioned domain events for the restaurant-decision boundary. Split the workflow into `CreateOrderSaga`, `ConfirmOrderSaga`, and `RejectOrderSaga`; every participant operation uses an order-scoped idempotency key and persists its result before replying.

**Tech Stack:** Java 21, Spring Boot 3.2, Gradle 8.5, Eventuate Tram Sagas, JPA, Flyway, MySQL 8, Kafka, Debezium outbox, JUnit 5, Testcontainers.

## Global Constraints

- Work only on `agent/phase-02-core-order-flow`, based directly on `dev`.
- Follow red-green-refactor: commit tests before production behavior when practical.
- Shared wire contracts live under `common/src/main/java/net/ftgo/common/orderflow`.
- Database migrations precede code that emits or consumes new enum values or columns.
- No raw card number/CVV or sensitive provider token is persisted or logged.
- Every command/event handler is idempotent by business key, not only message ID.
- Aggregate mutation and outbox insertion occur in the same local transaction.
- Each task must leave affected module tests green; final verification is `./gradlew clean test` plus real fresh-stack checks.

---

### Task 0: Add Phase 02 CI Gate

**Files:**
- Create: `.github/workflows/phase-02-core-order-flow.yml`

**Interfaces:**
- Trigger: pushes to `agent/phase-02-core-order-flow` and pull requests targeting `dev`.
- Produces: wrapper verification, `clean test`, test reports and logs.

- [ ] **Step 1: Add branch-specific workflow**

Use Java 21 and the checked-in Gradle wrapper:

```yaml
name: Phase 02 Core Order Flow
on:
  push:
    branches: [agent/phase-02-core-order-flow]
  pull_request:
    branches: [dev]
jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803
      - uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95
        with:
          distribution: temurin
          java-version: '21'
      - run: bash scripts/ci/verify-gradle-wrapper.sh
      - run: chmod +x gradlew && ./gradlew --no-daemon clean test --stacktrace
```

- [ ] **Step 2: Verify workflow starts on the branch**

Expected: a workflow run is associated with the workflow commit.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/phase-02-core-order-flow.yml
git commit -m "ci: verify phase 02 core order flow"
```

---

### Task 1: Define Shared Order-Flow Contracts

**Files:**
- Create: `common/src/main/java/net/ftgo/common/orderflow/menu/OrderMenuLineItem.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/commands/ValidateOrderMenuCommand.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/commands/ReserveConsumerCreditCommand.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/commands/CommitConsumerCreditCommand.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/commands/ReleaseConsumerCreditCommand.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/commands/CaptureAuthorizationCommand.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/commands/VoidAuthorizationCommand.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/commands/RefundPaymentCommand.java`
- Modify: `common/src/main/java/net/ftgo/common/orderflow/commands/AuthorizeCardCommand.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/replies/OrderMenuValidated.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/replies/OrderMenuValidationRejected.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/replies/ConsumerCreditReserved.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/replies/ConsumerCreditCommitted.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/replies/ConsumerCreditReleased.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/replies/ConsumerCreditReservationRejected.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/replies/PaymentCaptured.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/replies/AuthorizationVoided.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/replies/PaymentRefunded.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/events/TicketAcceptedEvent.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/events/TicketRejectedEvent.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/events/TicketAcceptanceTimedOutEvent.java`
- Create: `common/src/test/java/net/ftgo/common/orderflow/Phase02OrderFlowContractsSerializationTest.java`
- Modify: `common/src/test/java/net/ftgo/common/orderflow/SharedContractGuardrailsTest.java`

**Interfaces:**
- Menu validation key: `orderId`; request includes `restaurantId`, `expectedMenuVersion`, requested line items.
- Credit commands use `consumerId` and `orderId`; reserve also carries `Money amount`.
- Payment commands use `orderId`, authorization/capture ID, and deterministic `requestId`.
- Ticket decision events carry `eventId`, `ticketId`, `orderId`, and `occurredAt`.

- [ ] **Step 1: Write failing serialization tests**

Round-trip every new contract with `ObjectMapper.findAndRegisterModules()` and assert all business-key fields survive serialization.

- [ ] **Step 2: Verify red**

```bash
./gradlew :common:test --tests '*Phase02OrderFlowContractsSerializationTest'
```

Expected: compilation failure because the new contract classes do not exist.

- [ ] **Step 3: Add minimal contract classes**

Use JavaBeans-compatible no-arg constructors, full constructors, getters and setters because Eventuate/Jackson deserialize by class name.

Update `AuthorizeCardCommand` to:

```java
AuthorizeCardCommand(Long consumerId, Long orderId, Money amount, String requestId)
```

- [ ] **Step 4: Add guardrails**

Assert participant modules no longer define duplicate classes with the same simple names and assert every command implements `io.eventuate.tram.commands.common.Command`.

- [ ] **Step 5: Verify green**

```bash
./gradlew :common:test --tests '*Phase02OrderFlowContractsSerializationTest' --tests '*SharedContractGuardrailsTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add common
git commit -m "feat: define phase 02 order flow contracts"
```

---

### Task 2: Implement Authoritative Restaurant Menu Validation

**Files:**
- Modify: `restaurant-service/src/main/java/net/ftgo/restaurant/domain/Restaurant.java`
- Modify: `restaurant-service/src/main/java/net/ftgo/restaurant/domain/MenuItem.java`
- Modify: `restaurant-service/src/main/java/net/ftgo/restaurant/repository/MenuItemRepository.java`
- Create: `restaurant-service/src/main/java/net/ftgo/restaurant/messaging/RestaurantOrderCommandHandlers.java`
- Create: `restaurant-service/src/main/resources/db/migration/V2__add_menu_version_and_status.sql`
- Create: `restaurant-service/src/test/java/net/ftgo/restaurant/messaging/RestaurantOrderCommandHandlersTest.java`
- Create: `restaurant-service/src/test/java/net/ftgo/restaurant/migration/RestaurantPhase02MigrationTest.java`

**Interfaces:**
- Consumes: `ValidateOrderMenuCommand`.
- Produces: `OrderMenuValidated` or `OrderMenuValidationRejected` with one stable reason code.

- [ ] **Step 1: Write failing handler tests** for exact match and each failure code.
- [ ] **Step 2: Add backward-compatible migrations** for `menu_version`, restaurant enabled/open state and indexes.
- [ ] **Step 3: Implement one-round-trip menu lookup** preserving request item order.
- [ ] **Step 4: Register the Eventuate command dispatcher** on the restaurant command channel.
- [ ] **Step 5: Run** `./gradlew :restaurant-service:test` and expect PASS.
- [ ] **Step 6: Commit** `feat: validate authoritative restaurant menu`.

---

### Task 3: Implement Durable Consumer Credit Reservations

**Files:**
- Create: `consumer-service/src/main/java/net/ftgo/consumer/domain/CreditReservation.java`
- Create: `consumer-service/src/main/java/net/ftgo/consumer/domain/CreditReservationStatus.java`
- Create: `consumer-service/src/main/java/net/ftgo/consumer/repository/CreditReservationRepository.java`
- Create: `consumer-service/src/main/java/net/ftgo/consumer/service/CreditReservationService.java`
- Modify: `consumer-service/src/main/java/net/ftgo/consumer/domain/Consumer.java`
- Modify: `consumer-service/src/main/java/net/ftgo/consumer/messaging/ConsumerCommandHandlers.java`
- Create: `consumer-service/src/main/resources/db/migration/V3__create_credit_reservations.sql`
- Create: `consumer-service/src/test/java/net/ftgo/consumer/service/CreditReservationServiceTest.java`
- Create: `consumer-service/src/test/java/net/ftgo/consumer/integration/CreditReservationConcurrencyTest.java`

**Interfaces:**
- Unique reservation key: `orderId`.
- State machine: `RESERVED -> COMMITTED -> RELEASED`, and `RESERVED -> RELEASED`.
- Duplicate reserve/commit/release returns the established outcome without changing balances twice.

- [ ] **Step 1: Write failing state/idempotency/concurrency tests**.
- [ ] **Step 2: Add schema and optimistic version fields**.
- [ ] **Step 3: Implement transactional reserve/commit/release**.
- [ ] **Step 4: Route shared commands in ConsumerCommandHandlers**.
- [ ] **Step 5: Run** `./gradlew :consumer-service:test` and expect PASS.
- [ ] **Step 6: Commit** `feat: reserve consumer credit per order`.

---

### Task 4: Implement Accounting Authorization Lifecycle

**Files:**
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/domain/Authorization.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/domain/AuthorizationStatus.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/domain/Account.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/messaging/AccountingServiceCommandHandlers.java`
- Create: `accounting-service/src/main/resources/db/migration/V4__add_capture_void_refund_lifecycle.sql`
- Create: `accounting-service/src/test/java/net/ftgo/accounting/domain/AuthorizationLifecycleTest.java`
- Create: `accounting-service/src/test/java/net/ftgo/accounting/messaging/AccountingPhase02CommandHandlersTest.java`

**Interfaces:**
- Authorization state: `AUTHORIZED`, `DENIED`, `CAPTURED`, `VOIDED`, `REFUNDED`.
- Unique operation identity: `(operationType, requestId)`.
- Capture is the pivot; duplicate authorize/capture/void/refund returns the original result.

- [ ] **Step 1: Write failing lifecycle and duplicate-request tests**.
- [ ] **Step 2: Add schema columns and unique operation index**.
- [ ] **Step 3: Implement idempotent authorize/capture/void/refund transitions**.
- [ ] **Step 4: Register shared commands and typed replies**.
- [ ] **Step 5: Run** `./gradlew :accounting-service:test` and expect PASS.
- [ ] **Step 6: Commit** `feat: add payment authorization lifecycle`.

---

### Task 5: Implement Kitchen Accept, Reject and Timeout Decisions

**Files:**
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/domain/Ticket.java`
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/domain/TicketState.java`
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/repository/TicketRepository.java`
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/service/KitchenService.java`
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/api/KitchenController.java`
- Create: `kitchen-service/src/main/java/net/ftgo/kitchen/service/TicketAcceptanceTimeoutService.java`
- Create: `kitchen-service/src/main/resources/db/migration/V7__add_ticket_acceptance_decision.sql`
- Create: `kitchen-service/src/test/java/net/ftgo/kitchen/domain/TicketAcceptanceDecisionTest.java`
- Create: `kitchen-service/src/test/java/net/ftgo/kitchen/service/TicketAcceptanceTimeoutServiceTest.java`

**Interfaces:**
- `POST /tickets/{ticketId}/reject` accepts a stable reason code.
- Only one transition from `AWAITING_ACCEPTANCE` to `ACCEPTED`, `REJECTED_BY_RESTAURANT`, or `REJECTED_TIMEOUT` succeeds.
- Each winner publishes one versioned outbox event.

- [ ] **Step 1: Write failing transition/race tests**.
- [ ] **Step 2: Add migration and configuration properties**.
- [ ] **Step 3: Implement state-guarded accept/reject/timeout methods**.
- [ ] **Step 4: Implement atomic timeout claiming** suitable for multiple instances.
- [ ] **Step 5: Run** `./gradlew :kitchen-service:test` and expect PASS.
- [ ] **Step 6: Commit** `feat: add restaurant ticket decisions`.

---

### Task 6: Split Order Workflow into Create, Confirm and Reject Sagas

**Files:**
- Modify: `order-service/src/main/java/net/ftgo/order/domain/Order.java`
- Modify: `order-service/src/main/java/net/ftgo/order/domain/OrderState.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSaga.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSagaData.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSagaLocalSteps.java`
- Create: `order-service/src/main/java/net/ftgo/order/saga/ConfirmOrderSaga.java`
- Create: `order-service/src/main/java/net/ftgo/order/saga/ConfirmOrderSagaData.java`
- Create: `order-service/src/main/java/net/ftgo/order/saga/ConfirmOrderSagaLocalSteps.java`
- Create: `order-service/src/main/java/net/ftgo/order/saga/RejectOrderSaga.java`
- Create: `order-service/src/main/java/net/ftgo/order/saga/RejectOrderSagaData.java`
- Create: `order-service/src/main/java/net/ftgo/order/saga/RejectOrderSagaLocalSteps.java`
- Create: `order-service/src/main/java/net/ftgo/order/messaging/TicketDecisionEventHandler.java`
- Create: `order-service/src/main/resources/db/migration/V5__add_restaurant_decision_states.sql`
- Create: `order-service/src/test/java/net/ftgo/order/saga/CreateOrderSagaPhase02Test.java`
- Create: `order-service/src/test/java/net/ftgo/order/saga/ConfirmOrderSagaTest.java`
- Create: `order-service/src/test/java/net/ftgo/order/saga/RejectOrderSagaTest.java`
- Create: `order-service/src/test/java/net/ftgo/order/messaging/TicketDecisionEventHandlerTest.java`

**Interfaces:**
- Create saga final state: `AWAITING_RESTAURANT_ACCEPTANCE`.
- Confirm decision CAS: `AWAITING_RESTAURANT_ACCEPTANCE -> CONFIRMATION_PENDING`.
- Reject/timeout decision CAS: `AWAITING_RESTAURANT_ACCEPTANCE -> REJECTION_PENDING`.
- Only the CAS winner starts a decision saga.

- [ ] **Step 1: Write failing saga command/compensation tests**.
- [ ] **Step 2: Add order states and migration**.
- [ ] **Step 3: Rebuild CreateOrderSaga with validate/reserve/create/authorize/wait and reverse-order compensations**.
- [ ] **Step 4: Implement ConfirmOrderSaga with capture pivot, credit commit and approval**.
- [ ] **Step 5: Implement RejectOrderSaga with void, credit release and rejection**.
- [ ] **Step 6: Implement stale/duplicate decision handling without poison-message retries**.
- [ ] **Step 7: Run** `./gradlew :order-service:test` and expect PASS.
- [ ] **Step 8: Commit** `feat: orchestrate restaurant-confirmed orders`.

---

### Task 7: Add Real Cross-Service Verification

**Files:**
- Create: `e2e-tests/build.gradle`
- Modify: `settings.gradle`
- Create: `e2e-tests/src/test/java/net/ftgo/e2e/CoreOrderFlowTest.java`
- Create: `e2e-tests/src/test/resources/application.yml`
- Modify: `deployment/tests/docker-compose.fresh-stack.yml`
- Create: `scripts/smoke/verify-core-order-flow.sh`

**Interfaces:**
- Uses real service processes, MySQL, Kafka/Eventuate and outbox relay.

- [ ] **Step 1: Add failing scenarios** for happy accept, menu mismatch, insufficient credit, authorization denied, explicit reject, timeout, accept-timeout race and duplicate delivery.
- [ ] **Step 2: Implement reusable fresh-stack fixtures**.
- [ ] **Step 3: Run** `./gradlew :e2e-tests:test --tests '*CoreOrderFlowTest'`.
- [ ] **Step 4: Run** `./gradlew clean test`.
- [ ] **Step 5: Run** `bash scripts/smoke/verify-core-order-flow.sh` twice from clean state.
- [ ] **Step 6: Commit** `test: verify core order flow end to end`.

---

### Task 8: Complete Documentation and Review Gates

**Files:**
- Modify: `docs/superpowers/specs/2026-07-23-phase-02-core-order-flow-design.md`
- Modify: `docs/superpowers/plans/2026-07-23-phase-02-core-order-flow.md`
- Modify: `README.md`

- [ ] **Step 1: Mark completed checklist items only after evidence exists**.
- [ ] **Step 2: Document migrations, feature flag, rollout and rollback order**.
- [ ] **Step 3: Record exact verification commands and workflow run URLs**.
- [ ] **Step 4: Review for placeholders, contradictory state names and contract drift**.
- [ ] **Step 5: Commit** `docs: complete phase 02 core order flow`.

## Completion Criteria

- [ ] Restaurant Service rejects stale/unavailable/changed menu snapshots with typed codes.
- [ ] Consumer reservations are durable, unique per order, concurrency-safe and idempotent.
- [ ] Payment authorization is voidable; capture occurs only after restaurant acceptance.
- [ ] Restaurant accept, reject and timeout produce exactly one decision.
- [ ] Create, Confirm and Reject sagas converge after retries/restarts without duplicate effects.
- [ ] Shared contract serialization and guardrail tests pass.
- [ ] Module, full-build and fresh-stack E2E verification pass.

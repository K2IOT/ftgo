# FTGO Phase 02 Business Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Loại bỏ dữ liệu business do client giả mạo, bổ sung credit/payment lifecycle đúng và làm Create/Cancel/Revise idempotent với semantic lock atomically.

**Architecture:** Order Service lấy authoritative menu snapshot từ Restaurant Service, lưu operation resource và idempotency record trước khi start saga. Consumer reserve/release credit theo order; Accounting dùng provider abstraction và compensation payment rõ ràng.

**Tech Stack:** Java 21, Spring MVC/WebClient, Resilience4j, JPA, MySQL, Eventuate Tram Sagas, Bean Validation, Testcontainers, WireMock.

## Global Constraints

- Public order API không nhận `name` hoặc `price`.
- `consumerId` chưa bị xóa khỏi body cho tới Phase 04, nhưng Phase 02 phải tách service signature để Phase 04 có thể truyền authenticated principal ID.
- Không persist raw payment token; chỉ persist opaque `paymentMethodId` hoặc provider reference.
- Mọi Create/Cancel/Revise request có `Idempotency-Key` và trả `202 Accepted` với operation resource.
- Mọi participant handler phải có deterministic command/request identity.

---

### Task 1: Define Authoritative Menu Snapshot Contract

**Files:**
- Create: `common/src/main/java/net/ftgo/common/menu/MenuItemSnapshot.java`
- Create: `common/src/main/java/net/ftgo/common/menu/MenuSnapshotRequest.java`
- Create: `common/src/main/java/net/ftgo/common/menu/MenuSnapshotResponse.java`
- Create: `restaurant-service/src/main/java/net/ftgo/restaurant/api/MenuSnapshotController.java`
- Create: `restaurant-service/src/main/java/net/ftgo/restaurant/service/MenuSnapshotService.java`
- Create: `restaurant-service/src/test/java/net/ftgo/restaurant/api/MenuSnapshotControllerTest.java`
- Create: `restaurant-service/src/test/java/net/ftgo/restaurant/service/MenuSnapshotServiceTest.java`

**Interfaces:**
- Consumes: `MenuSnapshotRequest(Long restaurantId, List<MenuItemQuantity> items)`.
- Produces: `MenuSnapshotResponse(Long restaurantId, long menuVersion, List<MenuItemSnapshot> items)`.
- `MenuItemSnapshot`: `menuItemId`, `name`, `Money unitPrice`, `String currency`, `int quantity`, `boolean available`.

- [ ] **Step 1: Write failing service tests**

Test exact rejection behavior:

```java
assertThatThrownBy(() -> service.snapshot(10L, List.of(item(999L, 1))))
    .isInstanceOf(MenuItemUnavailableException.class)
    .hasMessageContaining("999");

assertThatThrownBy(() -> service.snapshot(10L, List.of(item(100L, 0))))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessage("Quantity must be positive");
```

Test response price/name come from database, not request.

- [ ] **Step 2: Add restaurant menu version**

Create migration:

`restaurant-service/src/main/resources/db/migration/V2__add_restaurant_menu_version.sql`

```sql
ALTER TABLE restaurants ADD COLUMN menu_version BIGINT NOT NULL DEFAULT 0;
```

Increment version in the same transaction as create/update/delete menu item.

- [ ] **Step 3: Implement batch snapshot query**

Add repository query that fetches all requested IDs for one restaurant in one round trip. Reject when count differs, any item unavailable, or duplicate menu item ID exists in request.

- [ ] **Step 4: Expose internal endpoint**

```http
POST /internal/restaurants/{restaurantId}/menu-snapshot
Content-Type: application/json
```

Response must be deterministic and ordered by request item order.

- [ ] **Step 5: Run tests**

```bash
./gradlew :restaurant-service:test \
  --tests '*MenuSnapshotServiceTest' \
  --tests '*MenuSnapshotControllerTest'
```

Expected: PASS with unavailable/missing/wrong-restaurant cases.

- [ ] **Step 6: Commit**

```bash
git add common restaurant-service
git commit -m "feat: expose authoritative menu snapshots"
```

---

### Task 2: Replace Client-Supplied Price and Name

**Files:**
- Modify: `order-service/src/main/java/net/ftgo/order/api/OrderLineItemRequest.java`
- Modify: `order-service/src/main/java/net/ftgo/order/api/CreateOrderRequest.java`
- Modify: `order-service/src/main/java/net/ftgo/order/api/ReviseOrderRequest.java`
- Create: `order-service/src/main/java/net/ftgo/order/menu/MenuSnapshotClient.java`
- Create: `order-service/src/main/java/net/ftgo/order/menu/RestaurantMenuSnapshotClient.java`
- Create: `order-service/src/main/java/net/ftgo/order/config/RestaurantClientConfiguration.java`
- Modify: `order-service/src/main/java/net/ftgo/order/api/OrderController.java`
- Modify: `order-service/src/main/java/net/ftgo/order/service/OrderService.java`
- Create: `order-service/src/test/java/net/ftgo/order/menu/RestaurantMenuSnapshotClientTest.java`
- Modify: `order-service/src/test/java/net/ftgo/order/api/OrderControllerTest.java`

**Interfaces:**
- `OrderLineItemRequest(Long menuItemId, int quantity)`.
- `MenuSnapshotClient.resolve(Long restaurantId, List<OrderLineItemRequest>) -> MenuSnapshotResponse`.
- `OrderService.createOrder` consumes resolved `List<OrderLineItem>` only.

- [ ] **Step 1: Write API rejection tests**

Send a request containing legacy `name` and `price`; configure Jackson to reject unknown properties for public DTOs and expect `400 INVALID_REQUEST`.

- [ ] **Step 2: Change public DTO**

```java
public record OrderLineItemRequest(
    @NotNull @Positive Long menuItemId,
    @Positive @Max(100) int quantity
) {}
```

Set `@Size(max = 50)` on item lists.

- [ ] **Step 3: Implement Restaurant client with bounded failure policy**

Use WebClient/RestClient with:

- connect timeout: 1 second
- response timeout: 2 seconds
- no retry on 4xx
- at most 2 retries with jitter on connection/5xx
- circuit breaker fallback that returns `MenuServiceUnavailableException`; never use stale/fabricated price

- [ ] **Step 4: Resolve snapshot before creating saga**

Controller/service sequence:

```java
MenuSnapshotResponse snapshot = menuSnapshotClient.resolve(restaurantId, request.lineItems());
List<OrderLineItem> items = snapshot.items().stream()
    .map(item -> new OrderLineItem(
        item.menuItemId(), item.name(), item.unitPrice(), item.quantity(), snapshot.menuVersion()))
    .toList();
```

Persist `menuVersion` in order line items using a forward Flyway migration.

- [ ] **Step 5: Add WireMock contract tests**

Assert requested item IDs/quantities, authoritative response mapping, timeout behavior and unavailable-item propagation.

- [ ] **Step 6: Run tests**

```bash
./gradlew :order-service:test \
  --tests '*OrderControllerTest' \
  --tests '*RestaurantMenuSnapshotClientTest'
```

Expected: PASS; no public DTO references `Money price` or item `name`.

- [ ] **Step 7: Commit**

```bash
git add order-service
git commit -m "fix: price orders from restaurant snapshots"
```

---

### Task 3: Introduce Order Operation and HTTP Idempotency

**Files:**
- Create: `order-service/src/main/java/net/ftgo/order/operation/OrderOperation.java`
- Create: `order-service/src/main/java/net/ftgo/order/operation/OrderOperationType.java`
- Create: `order-service/src/main/java/net/ftgo/order/operation/OrderOperationStatus.java`
- Create: `order-service/src/main/java/net/ftgo/order/operation/OrderOperationRepository.java`
- Create: `order-service/src/main/java/net/ftgo/order/operation/OrderOperationService.java`
- Create: `order-service/src/main/java/net/ftgo/order/api/OrderOperationController.java`
- Create: `order-service/src/main/java/net/ftgo/order/api/OrderOperationResponse.java`
- Create: `order-service/src/main/resources/db/migration/V4__create_order_operations.sql`
- Modify: `order-service/src/main/java/net/ftgo/order/api/OrderController.java`
- Create: `order-service/src/test/java/net/ftgo/order/operation/OrderOperationServiceTest.java`
- Create: `order-service/src/test/java/net/ftgo/order/api/OrderIdempotencyIntegrationTest.java`

**Interfaces:**
- `begin(principalId, IdempotencyKey, type, requestHash) -> BeginOperationResult`.
- Same key + same request hash returns existing operation.
- Same key + different hash throws `IdempotencyConflictException`.

- [ ] **Step 1: Write failing idempotency tests**

```java
var first = service.begin(7L, "key-1", CREATE, "hash-a");
var duplicate = service.begin(7L, "key-1", CREATE, "hash-a");
assertThat(duplicate.operationId()).isEqualTo(first.operationId());

assertThatThrownBy(() -> service.begin(7L, "key-1", CREATE, "hash-b"))
    .isInstanceOf(IdempotencyConflictException.class);
```

Add concurrent test with two threads and one unique row.

- [ ] **Step 2: Add schema**

```sql
CREATE TABLE order_operations (
  id CHAR(36) PRIMARY KEY,
  order_id BIGINT NULL,
  principal_id BIGINT NOT NULL,
  idempotency_key VARCHAR(255) NOT NULL,
  operation_type VARCHAR(32) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_code VARCHAR(100) NULL,
  error_detail VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uq_order_operation_idempotency
    (principal_id, operation_type, idempotency_key),
  INDEX idx_order_operation_order (order_id),
  CONSTRAINT fk_order_operation_order FOREIGN KEY (order_id) REFERENCES orders(id)
);
```

- [ ] **Step 3: Compute canonical request hash**

Hash canonical JSON with sorted object properties and stable item order using SHA-256. Do not include headers unrelated to business request.

- [ ] **Step 4: Return operation resource**

Create/Cancel/Revise responses:

```http
HTTP/1.1 202 Accepted
Location: /operations/{operationId}
```

```json
{
  "operationId": "uuid",
  "orderId": 123,
  "type": "CREATE",
  "status": "PENDING"
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :order-service:test \
  --tests '*OrderOperationServiceTest' \
  --tests '*OrderIdempotencyIntegrationTest'
```

Expected: duplicate same request returns same operation; changed request returns `409`.

- [ ] **Step 6: Commit**

```bash
git add order-service
git commit -m "feat: track idempotent order operations"
```

---

### Task 4: Implement Idempotent Consumer Credit Reservation

**Files:**
- Create: `consumer-service/src/main/java/net/ftgo/consumer/domain/CreditReservation.java`
- Create: `consumer-service/src/main/java/net/ftgo/consumer/domain/CreditReservationStatus.java`
- Create: `consumer-service/src/main/java/net/ftgo/consumer/repository/CreditReservationRepository.java`
- Create: `consumer-service/src/main/resources/db/migration/V2__create_credit_reservations.sql`
- Modify: `consumer-service/src/main/java/net/ftgo/consumer/domain/Consumer.java`
- Modify: `consumer-service/src/main/java/net/ftgo/consumer/messaging/ConsumerServiceCommandHandlers.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/commands/ReserveConsumerCreditCommand.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/commands/ReleaseConsumerCreditCommand.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/replies/ConsumerCreditReserved.java`
- Create: `consumer-service/src/test/java/net/ftgo/consumer/messaging/CreditReservationCommandHandlerTest.java`

**Interfaces:**
- Reservation business key: `orderId`.
- Reserve retry returns the same reservation ID.
- Release retry succeeds without increasing available credit twice.

- [ ] **Step 1: Add `@Version` and failing concurrency test**

Two concurrent reservations that exceed available credit together must result in exactly one success.

- [ ] **Step 2: Add reservation schema**

```sql
CREATE TABLE credit_reservations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  consumer_id BIGINT NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  UNIQUE KEY uq_credit_reservation_order (order_id),
  INDEX idx_credit_reservation_consumer (consumer_id)
);
ALTER TABLE consumers ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

- [ ] **Step 3: Implement reserve/release transaction**

Reserve locks via optimistic version, decrements `availableCredit`, persists reservation. Existing reservation returns previous result. Release only transitions `RESERVED -> RELEASED` once.

- [ ] **Step 4: Replace VerifyConsumer in Create Saga**

Create Saga sends `ReserveConsumerCreditCommand(orderId, consumerId, total)` and registers `ReleaseConsumerCreditCommand(orderId)` compensation.

- [ ] **Step 5: Fix credit-limit reduction**

Replace signed difference arithmetic with explicit branch:

```java
if (newLimit.isGreaterThan(oldLimit)) {
    availableCredit = availableCredit.add(newLimit.subtract(oldLimit));
} else {
    Money decrease = oldLimit.subtract(newLimit);
    if (availableCredit.isLessThan(decrease)) {
        throw new CreditLimitBelowReservedAmountException();
    }
    availableCredit = availableCredit.subtract(decrease);
}
```

- [ ] **Step 6: Run tests**

```bash
./gradlew :consumer-service:test --tests '*CreditReservation*' --tests '*ConsumerTest'
```

Expected: concurrent oversubscription prevented; duplicate release leaves exact balance.

- [ ] **Step 7: Commit**

```bash
git add common consumer-service order-service
git commit -m "feat: reserve consumer credit per order"
```

---

### Task 5: Introduce Payment Provider Boundary and Remove Raw Token Persistence

**Files:**
- Create: `accounting-service/src/main/java/net/ftgo/accounting/payment/PaymentProvider.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/payment/PaymentAuthorizationRequest.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/payment/PaymentAuthorizationResult.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/payment/SandboxPaymentProvider.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/domain/Authorization.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/domain/Account.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/messaging/AccountingServiceCommandHandlers.java`
- Modify: `common/src/main/java/net/ftgo/common/orderflow/commands/AuthorizeCardCommand.java`
- Modify: `order-service/src/main/java/net/ftgo/order/domain/PaymentInfo.java`
- Create: `order-service/src/main/resources/db/migration/V5__replace_payment_token_with_method_reference.sql`
- Create: `accounting-service/src/test/java/net/ftgo/accounting/payment/SandboxPaymentProviderTest.java`
- Create: `accounting-service/src/test/java/net/ftgo/accounting/messaging/AuthorizationIdempotencyIntegrationTest.java`

**Interfaces:**
- `PaymentProvider.authorize(request)`, `voidAuthorization(providerAuthorizationId)`, `capture(providerAuthorizationId)`, `refund(providerPaymentId, amount)`.
- `AuthorizeCardCommand` contains `orderId`, `consumerId`, `Money amount`, `String paymentMethodId`, `String requestId`.

- [ ] **Step 1: Write failing provider and retry tests**

Same `requestId` must produce same internal authorization and provider authorization reference. Provider invocation count must remain one.

- [ ] **Step 2: Add provider-neutral columns**

Add `provider`, `provider_authorization_id`, `provider_payment_id`, `request_id`, and payment status timestamps. Ensure `request_id` remains unique.

- [ ] **Step 3: Remove `payment_token` from order persistence**

Create a forward migration that adds `payment_method_id`, copies only development-compatible opaque values when safe, then drops `payment_token`. Production migration must document that real raw tokens are not copied and require pre-deployment cleanup if present.

- [ ] **Step 4: Implement deterministic sandbox provider**

Rules:

- method ID prefixed `pm_decline_` returns declined.
- method ID prefixed `pm_error_` throws retryable provider error.
- all other `pm_` IDs authorize.
- request ID maps deterministically to one provider reference.

- [ ] **Step 5: Fix revision idempotency order**

In `reviseAuthorization`, check `newRequestId` before validating/reversing old authorization. If existing new authorization exists, return it unchanged.

- [ ] **Step 6: Run tests**

```bash
./gradlew :accounting-service:test --tests '*PaymentProvider*' --tests '*AuthorizationIdempotency*'
./gradlew :order-service:test --tests '*PaymentInfo*'
```

Expected: no schema/entity field named `paymentToken`; duplicate provider call count is one.

- [ ] **Step 7: Commit**

```bash
git add common accounting-service order-service
git commit -m "feat: isolate payment provider lifecycle"
```

---

### Task 6: Correct Create Order Saga Compensation

**Files:**
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSaga.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSagaData.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSagaLocalSteps.java`
- Modify: `common/src/main/java/net/ftgo/common/orderflow/commands/ReverseAuthorizationCommand.java`
- Modify: `order-service/src/test/java/net/ftgo/order/saga/CreateOrderSagaIntegrationTest.java`
- Create: `order-service/src/test/java/net/ftgo/order/saga/CreateOrderSagaCompensationTest.java`

**Interfaces:**
- Compensation order: void authorization, cancel ticket, release credit, reject order/operation.
- Saga success marks operation `SUCCEEDED`; failure marks `FAILED` or `COMPENSATED`.

- [ ] **Step 1: Write failure matrix tests**

Cover failure at each participant step:

```text
reserve credit failure -> reject only
create ticket failure -> release credit, reject
payment failure -> cancel ticket, release credit, reject
approve ticket failure -> void payment, cancel ticket, release credit, reject
```

- [ ] **Step 2: Move pivot after compensatable authorization**

Treat payment authorization as compensatable with `ReverseAuthorizationCommand`. The first non-compensatable step is only introduced when capture is performed; this phase does not capture during Create Saga.

- [ ] **Step 3: Update operation state locally**

Approve/reject local handlers update both Order and matching OrderOperation in one transaction and emit the matching domain event once.

- [ ] **Step 4: Run saga tests**

```bash
./gradlew :order-service:test --tests '*CreateOrderSaga*'
```

Expected: every failure matrix test observes reverse-order compensation exactly once.

- [ ] **Step 5: Commit**

```bash
git add order-service common
git commit -m "fix: compensate create order financial steps"
```

---

### Task 7: Set Cancel and Revise Locks Atomically

**Files:**
- Modify: `order-service/src/main/java/net/ftgo/order/service/OrderService.java`
- Modify: `order-service/src/main/java/net/ftgo/order/domain/Order.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CancelOrderSaga.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/ReviseOrderSaga.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CancelOrderSagaLocalSteps.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/ReviseOrderSagaLocalSteps.java`
- Create: `order-service/src/main/java/net/ftgo/order/domain/PendingOrderRevision.java`
- Create: `order-service/src/main/resources/db/migration/V6__persist_pending_order_revision.sql`
- Create: `order-service/src/test/java/net/ftgo/order/service/ConcurrentOrderMutationIntegrationTest.java`

**Interfaces:**
- `beginCancel(orderId, operationId)` performs `APPROVED -> CANCEL_PENDING` before saga creation.
- `beginRevision(orderId, snapshot, operationId)` persists old/new snapshot and delta before saga creation.

- [ ] **Step 1: Write concurrent request tests**

Use two transactions/barriers:

- two cancel requests -> one operation/saga
- cancel versus revise -> one succeeds, one `409`
- two distinct revise idempotency keys -> one succeeds, one `409`

- [ ] **Step 2: Move lock from saga local step to HTTP transaction**

Order Service transaction sequence:

```java
OrderOperation op = operationService.begin(...);
Order order = repository.findByIdForUpdateOrVersion(orderId);
order.beginCancel(op.getId());
repository.save(order);
sagaInstanceFactory.create(cancelSaga, data);
```

If saga creation cannot participate in the same transaction, persist a `START_REQUESTED` operation/outbox command and use a reliable starter worker; do not leave a committed pending lock without a recoverable start request.

- [ ] **Step 3: Persist revision snapshot**

Store old items, new authoritative items, old/new total and payment/credit delta so compensation does not depend on mutable current state.

- [ ] **Step 4: Make saga first step a validation/no-op**

Saga local step verifies operation ID and pending state idempotently rather than performing the first state transition.

- [ ] **Step 5: Run tests**

```bash
./gradlew :order-service:test \
  --tests '*ConcurrentOrderMutationIntegrationTest' \
  --tests '*CancelOrderSaga*' \
  --tests '*ReviseOrderSaga*'
```

Expected: one durable operation per accepted mutation; no duplicate saga side effects.

- [ ] **Step 6: Commit**

```bash
git add order-service
git commit -m "fix: lock order mutations before saga start"
```

---

### Task 8: Full Business Flow Verification

**Files:**
- Create: `e2e-tests/src/test/java/net/ftgo/e2e/OrderBusinessCorrectnessTest.java`
- Modify: `settings.gradle`
- Create: `e2e-tests/build.gradle`
- Create: `e2e-tests/src/test/resources/application.yml`

**Interfaces:**
- Runs real Order, Consumer, Restaurant, Kitchen and Accounting services with MySQL/Kafka/Eventuate.

- [ ] **Step 1: Add failing E2E scenarios**

Scenarios:

1. Client price/name fields rejected.
2. Restaurant price is stored in order.
3. Duplicate create key returns same operation/order.
4. Declined payment compensates ticket and credit.
5. Concurrent create orders cannot overspend credit.
6. Cancel duplicate returns same operation.
7. Revision retry returns same authorization result.

- [ ] **Step 2: Run targeted E2E suite**

```bash
./gradlew :e2e-tests:test --tests '*OrderBusinessCorrectnessTest'
```

Expected: PASS against real service processes/containers, not mocked participants.

- [ ] **Step 3: Run all phase tests**

```bash
./gradlew clean test
```

Expected: exit code `0`.

- [ ] **Step 4: Commit**

```bash
git add e2e-tests settings.gradle
git commit -m "test: verify order business correctness end to end"
```

## Phase Completion Checklist

- [ ] Public order item DTO contains only menu item ID and quantity.
- [ ] Order snapshots authoritative menu data and version.
- [ ] Operation/idempotency records protect all order mutations.
- [ ] Credit reserve/release is concurrency-safe and idempotent.
- [ ] Payment provider boundary exists and raw token persistence is removed.
- [ ] Create Saga compensates authorization, ticket and credit correctly.
- [ ] Cancel/Revise lock state before saga execution.
- [ ] Real-service E2E suite passes.

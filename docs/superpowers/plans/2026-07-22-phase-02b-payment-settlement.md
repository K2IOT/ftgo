# FTGO Phase 02B Payment Settlement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoàn thiện payment lifecycle từ authorization tới capture, void, refund, webhook và reconciliation với financial invariants được kiểm chứng.

**Architecture:** Ticket acceptance tạo một process bất đồng bộ. Order Service orchestrates CapturePaymentSaga; Kitchen giữ ticket ở `ACCEPTANCE_PENDING_PAYMENT` cho tới khi capture thành công. Cancel Saga void hoặc refund dựa trên durable payment state. Accounting xử lý webhook và reconciliation idempotently.

**Tech Stack:** Java 21, Eventuate Tram Sagas, Spring Kafka, JPA, MySQL, Flyway, Spring MVC, provider adapter, Testcontainers, WireMock.

## Global Constraints

- Plan chạy sau Phase 02 Task 5-7 và Phase 02A, trước Phase 02 E2E gate.
- Capture chỉ xảy ra sau restaurant acceptance request và trước `PREPARING`.
- Một provider authorization chỉ được capture thành công tối đa một lần.
- Tổng refund không vượt captured amount.
- Unknown provider state không được tự động force sang success.

---

### Task 1: Expand Accounting Financial State Model

**Files:**
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/domain/Authorization.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/domain/PaymentCapture.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/domain/PaymentRefund.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/domain/FinancialOperationStatus.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/repository/PaymentCaptureRepository.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/repository/PaymentRefundRepository.java`
- Create: `accounting-service/src/main/resources/db/migration/V4__add_capture_and_refund_lifecycle.sql`
- Create: `accounting-service/src/test/java/net/ftgo/accounting/domain/FinancialLifecycleTest.java`

**Interfaces:**
- Authorization states: `APPROVED`, `DENIED`, `VOIDED`, `CAPTURED`.
- Capture business key: provider authorization ID/request ID.
- Refund business key: idempotency request ID.

- [ ] **Step 1: Write failing financial invariant tests**

Assert:

```java
assertThatThrownBy(() -> authorization.captureTwice())
    .isInstanceOf(IllegalStateException.class);

assertThatThrownBy(() -> payment.refund(capturedAmount.add(new Money("0.01"))))
    .isInstanceOf(RefundExceedsCapturedAmountException.class);
```

Also test duplicate capture/refund request returns original result.

- [ ] **Step 2: Add schema**

Create `payment_captures` and `payment_refunds` with unique request IDs, provider IDs, amount, status, timestamps and optimistic version. Add constraints for positive amount and one successful capture per authorization.

- [ ] **Step 3: Implement monotonic transitions**

Allowed transitions are explicit; reverse transitions are rejected. Duplicate request IDs return existing operation before any provider call.

- [ ] **Step 4: Run tests**

```bash
./gradlew :accounting-service:test --tests '*FinancialLifecycleTest'
```

Expected: capture/refund invariants pass and duplicate operations are stable.

- [ ] **Step 5: Commit**

```bash
git add accounting-service
git commit -m "feat: model payment capture and refunds"
```

---

### Task 2: Add Capture, Void and Refund Provider Commands

**Files:**
- Create: `common/src/main/java/net/ftgo/common/orderflow/commands/CaptureAuthorizationCommand.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/commands/RefundPaymentCommand.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/replies/PaymentCaptured.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/replies/PaymentRefunded.java`
- Modify: `common/src/main/java/net/ftgo/common/orderflow/commands/ReverseAuthorizationCommand.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/payment/PaymentProvider.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/payment/SandboxPaymentProvider.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/messaging/AccountingServiceCommandHandlers.java`
- Create: `accounting-service/src/test/java/net/ftgo/accounting/messaging/FinancialCommandIdempotencyTest.java`

**Interfaces:**
- `CaptureAuthorizationCommand(orderId, authorizationId, requestId)`.
- `RefundPaymentCommand(orderId, authorizationId, amount, requestId, reason)`.
- Reverse command voids only `APPROVED` authorization; captured payment requires refund.

- [ ] **Step 1: Write lost-reply tests**

Execute capture/refund command twice with same command ID and request ID. Assert provider call count one and reply IDs identical.

- [ ] **Step 2: Implement provider methods**

Sandbox behavior:

- `providerAuthorizationId` prefixed `pa_capture_decline_` returns capture decline.
- `pa_capture_error_` throws retryable error.
- valid authorization captures exactly once.
- refund request IDs map deterministically to provider refund IDs.

- [ ] **Step 3: Wrap with processed-command result cache**

Use Phase 03 idempotent command executor when available; until then implement the same database result contract locally and migrate to shared executor without schema change.

- [ ] **Step 4: Run tests**

```bash
./gradlew :accounting-service:test --tests '*FinancialCommandIdempotencyTest'
```

Expected: duplicate/lost-reply scenarios produce one provider side effect.

- [ ] **Step 5: Commit**

```bash
git add common accounting-service
git commit -m "feat: handle capture void and refund commands"
```

---

### Task 3: Gate Kitchen Acceptance on Payment Capture

**Files:**
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/domain/TicketState.java`
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/domain/Ticket.java`
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/api/KitchenController.java`
- Create: `kitchen-service/src/main/java/net/ftgo/kitchen/messaging/TicketAcceptanceRequested.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/commands/ConfirmTicketAcceptanceCommand.java`
- Create: `common/src/main/java/net/ftgo/common/orderflow/commands/UndoTicketAcceptanceCommand.java`
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/messaging/KitchenServiceCommandHandlers.java`
- Create: `kitchen-service/src/main/resources/db/migration/V4__add_acceptance_pending_payment_state.sql`
- Create: `kitchen-service/src/test/java/net/ftgo/kitchen/domain/TicketPaymentGateTest.java`

**Interfaces:**
- `requestAcceptance()` transitions `AWAITING_ACCEPTANCE -> ACCEPTANCE_PENDING_PAYMENT`.
- `confirmAcceptance()` transitions pending -> `ACCEPTED`.
- `undoAcceptance()` transitions pending -> `AWAITING_ACCEPTANCE` or cancel policy state.
- `preparing()` rejects while payment pending.

- [ ] **Step 1: Write failing state-machine tests**

Assert `preparing()` fails from `ACCEPTANCE_PENDING_PAYMENT`, confirmation enables preparation and duplicate confirmation is idempotent through command cache.

- [ ] **Step 2: Change acceptance endpoint semantics**

`POST /tickets/{id}/accept` calls `requestAcceptance`, persists ticket and outbox-publishes `TicketAcceptanceRequested(ticketId, orderId, restaurantId)`. Return `202 Accepted` with operation/event reference.

- [ ] **Step 3: Implement confirm/undo commands**

Command handlers perform strict transitions with idempotent reply behavior. Confirmation emits the existing `TicketAcceptedEvent` only after payment capture.

- [ ] **Step 4: Run tests**

```bash
./gradlew :kitchen-service:test --tests '*TicketPaymentGateTest' --tests '*KitchenServiceCommandHandlersTest'
```

Expected: no path reaches `PREPARING` before capture confirmation.

- [ ] **Step 5: Commit**

```bash
git add common kitchen-service
git commit -m "fix: gate ticket acceptance on payment capture"
```

---

### Task 4: Implement Capture Payment Saga

**Files:**
- Create: `order-service/src/main/java/net/ftgo/order/saga/CapturePaymentSaga.java`
- Create: `order-service/src/main/java/net/ftgo/order/saga/CapturePaymentSagaData.java`
- Create: `order-service/src/main/java/net/ftgo/order/saga/CapturePaymentSagaLocalSteps.java`
- Create: `order-service/src/main/java/net/ftgo/order/config/CapturePaymentSagaConfiguration.java`
- Create: `order-service/src/main/java/net/ftgo/order/messaging/TicketAcceptanceEventConsumer.java`
- Modify: `order-service/src/main/java/net/ftgo/order/domain/Order.java`
- Create: `order-service/src/main/java/net/ftgo/order/domain/OrderPaymentState.java`
- Create: `order-service/src/main/resources/db/migration/V8__add_order_payment_state.sql`
- Create: `order-service/src/test/java/net/ftgo/order/saga/CapturePaymentSagaIntegrationTest.java`

**Interfaces:**
- Saga input: order ID, ticket ID, authorization ID, capture request ID.
- Capture request ID: `capture-order-{orderId}-authorization-{authorizationId}`.

- [ ] **Step 1: Write saga success/failure matrix**

Success:

```text
local PAYMENT_CAPTURE_PENDING
-> Accounting capture
-> Kitchen confirm acceptance
-> local PAYMENT_CAPTURED
```

Capture decline:

```text
Kitchen undo acceptance
-> Accounting void if still authorized
-> release credit
-> local reject order and mark operation failed/compensated
```

Lost reply after capture must retry forward without second charge.

- [ ] **Step 2: Consume acceptance request idempotently**

Order consumer deduplicates event ID and starts one CapturePaymentSaga per order/ticket. Unique DB constraint protects duplicate starts.

- [ ] **Step 3: Implement payment state transitions**

Add `AUTHORIZED`, `CAPTURE_PENDING`, `CAPTURED`, `VOIDED`, `REFUND_PENDING`, `REFUNDED`, `FAILED`, `MANUAL_REVIEW`. Transitions use optimistic locking and operation correlation.

- [ ] **Step 4: Treat capture as pivot**

After `PaymentCaptured`, Kitchen confirm and local completion are retriable. Before capture, compensation may void authorization and undo acceptance.

- [ ] **Step 5: Run tests**

```bash
./gradlew :order-service:test --tests '*CapturePaymentSagaIntegrationTest'
```

Expected: success, decline, transient retry and lost-reply cases pass with one capture.

- [ ] **Step 6: Commit**

```bash
git add order-service
git commit -m "feat: orchestrate payment capture on acceptance"
```

---

### Task 5: Update Cancel Order Saga for Void or Refund

**Files:**
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CancelOrderSaga.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CancelOrderSagaData.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CancelOrderSagaLocalSteps.java`
- Create: `order-service/src/test/java/net/ftgo/order/saga/CancelOrderFinancialStateTest.java`

**Interfaces:**
- `AUTHORIZED` -> `ReverseAuthorizationCommand`.
- `CAPTURED` -> `RefundPaymentCommand`.
- `VOIDED`/`REFUNDED` -> no-op success.
- `MANUAL_REVIEW`/unknown -> cancellation operation pauses for manual review.

- [ ] **Step 1: Write financial-state routing tests**

Assert exact command type and request ID for every payment state. Duplicate cancel sends no additional provider action.

- [ ] **Step 2: Add refund request identity**

Use `cancel-order-{orderId}-refund` and persist returned refund ID on Order/operation.

- [ ] **Step 3: Update cancellation completion rule**

Order becomes `CANCELLED` only after kitchen cancellation and financial void/refund durable success. Ambiguous provider state leaves order `CANCEL_PENDING` plus operation `MANUAL_REVIEW`.

- [ ] **Step 4: Run tests**

```bash
./gradlew :order-service:test --tests '*CancelOrderFinancialStateTest' --tests '*CancelOrderSaga*'
```

Expected: correct void/refund/no-op/manual-review routing.

- [ ] **Step 5: Commit**

```bash
git add order-service
git commit -m "fix: refund captured payments on cancellation"
```

---

### Task 6: Add Verified Provider Webhook Processing

**Files:**
- Create: `accounting-service/src/main/java/net/ftgo/accounting/webhook/PaymentWebhookController.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/webhook/PaymentWebhookService.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/webhook/ProviderWebhookVerifier.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/domain/ProcessedProviderEvent.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/repository/ProcessedProviderEventRepository.java`
- Create: `accounting-service/src/main/resources/db/migration/V5__create_processed_provider_events.sql`
- Create: `accounting-service/src/test/java/net/ftgo/accounting/webhook/PaymentWebhookIntegrationTest.java`

**Interfaces:**
- Endpoint: `POST /webhooks/payments/{provider}`.
- Signature and timestamp verification occurs over raw request body.
- Provider event ID unique.

- [ ] **Step 1: Write webhook security/idempotency tests**

Cover valid signature, invalid signature, stale timestamp, duplicate event ID, unknown payment reference and out-of-order provider state.

- [ ] **Step 2: Persist event before applying transition**

Use one transaction for processed-event row and local state update. Duplicate unique-key returns prior success response without reapplying.

- [ ] **Step 3: Enforce allowed transitions**

Provider webhook can confirm/refine local pending state but cannot regress captured/refunded state or increase financial amount.

- [ ] **Step 4: Emit accounting domain event**

Publish versioned event with provider event ID, local authorization/payment/refund reference and resulting state. Never include raw provider payload or secret.

- [ ] **Step 5: Run tests**

```bash
./gradlew :accounting-service:test --tests '*PaymentWebhookIntegrationTest'
```

Expected: invalid/replayed webhooks cause no state mutation; duplicate valid webhook applies once.

- [ ] **Step 6: Commit**

```bash
git add accounting-service
git commit -m "feat: verify and apply payment webhooks"
```

---

### Task 7: Add Payment Reconciliation and Manual Review Cases

**Files:**
- Create: `accounting-service/src/main/java/net/ftgo/accounting/reconciliation/PaymentReconciler.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/reconciliation/ReconciliationCase.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/reconciliation/ReconciliationCaseRepository.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/reconciliation/ReconciliationStatus.java`
- Create: `accounting-service/src/main/resources/db/migration/V6__create_reconciliation_cases.sql`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/api/admin/ReconciliationController.java`
- Create: `accounting-service/src/test/java/net/ftgo/accounting/reconciliation/PaymentReconcilerTest.java`

**Interfaces:**
- Provider interface adds read-only lookup by provider authorization/payment/refund ID.
- Reconciliation case unique by provider reference + mismatch type + active status.

- [ ] **Step 1: Write reconciliation tests**

Scenarios:

- local pending, provider captured -> safely mark captured
- local authorized, provider voided -> safely mark voided
- local captured amount differs -> create manual-review case
- provider has unknown extra charge -> create critical case; no automatic refund
- repeated reconciliation -> one case/one transition

- [ ] **Step 2: Implement scheduled bounded scan**

Scan records pending beyond configurable threshold using indexed query and page size. Use distributed scheduler lock so one active worker owns a page/work item.

- [ ] **Step 3: Add metrics and admin read API**

Metrics:

```text
ftgo_payment_reconciliation_mismatch_total
ftgo_payment_reconciliation_open_cases
ftgo_payment_reconciliation_age_seconds
```

Admin API supports list/detail and explicit resolution with reason/audit actor; no automatic financial mutation endpoint.

- [ ] **Step 4: Run tests**

```bash
./gradlew :accounting-service:test --tests '*PaymentReconcilerTest'
```

Expected: monotonic safe updates and one manual case per mismatch.

- [ ] **Step 5: Commit**

```bash
git add accounting-service
git commit -m "feat: reconcile payment provider state"
```

---

### Task 8: Payment Settlement End-to-End Verification

**Files:**
- Create: `e2e-tests/src/test/java/net/ftgo/e2e/PaymentSettlementTest.java`
- Create: `e2e-tests/src/test/java/net/ftgo/e2e/PaymentWebhookAndReconciliationTest.java`

**Interfaces:**
- Uses real Order, Kitchen and Accounting services plus deterministic sandbox provider.

- [ ] **Step 1: Implement settlement scenarios**

1. Order authorization succeeds; ticket remains awaiting acceptance.
2. Restaurant accepts; exactly one capture; ticket becomes accepted.
3. Kitchen cannot prepare before capture.
4. Capture decline compensates ticket, credit and order.
5. Lost capture reply does not double charge.
6. Cancel after authorization voids.
7. Cancel after capture refunds once.
8. Duplicate webhook applies once.
9. Reconciliation resolves safe pending state and opens case for amount mismatch.

- [ ] **Step 2: Run suite twice**

```bash
./gradlew :e2e-tests:test --tests '*PaymentSettlementTest' --tests '*PaymentWebhookAndReconciliationTest'
./gradlew :e2e-tests:test --tests '*PaymentSettlementTest' --tests '*PaymentWebhookAndReconciliationTest'
```

Expected: both runs pass with deterministic financial row/provider operation counts.

- [ ] **Step 3: Commit**

```bash
git add e2e-tests
git commit -m "test: verify payment settlement lifecycle"
```

## Completion Checklist

- [ ] Payment authorization, capture, void and refund states are durable and monotonic.
- [ ] Ticket cannot prepare before successful capture.
- [ ] Capture Payment Saga handles success, decline, transient error and lost reply.
- [ ] Cancel Saga chooses void or refund by durable payment state.
- [ ] Webhooks are signed, replay-safe and idempotent.
- [ ] Reconciliation safely updates monotonic state and creates manual cases for ambiguity.
- [ ] E2E settlement suite passes twice with no duplicate financial side effect.

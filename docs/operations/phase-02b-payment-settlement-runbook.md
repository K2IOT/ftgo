# Phase 02B Payment Settlement Runbook

## Purpose

Phase 02B turns the Accounting service into the durable settlement authority for an order. It preserves the existing order-flow policy:

1. The Create Order saga authorizes payment before restaurant acceptance.
2. Restaurant acceptance starts the Confirm Order saga.
3. Payment capture is the pivot of Confirm Order.
4. Consumer credit is committed after capture.
5. The Order is then moved to `APPROVED`.

The implementation uses a deterministic simulated provider so failures can be reproduced without a real payment processor.

## Settlement records

### Local authorization aggregate

`authorizations` stores the local state machine:

- `AUTHORIZED`
- `CAPTURED`
- `PARTIALLY_REFUNDED`
- `REFUNDED`
- `VOIDED`

Legacy `APPROVED` and `REVERSED` values remain readable during rolling upgrades.

`payment_refunds` stores each partial refund. The `request_id` column is unique, so retrying the same refund is idempotent. A request ID reused with another amount is rejected.

### Immutable ledger

`payment_ledger_entries` is append-only. Supported operations are:

- `AUTHORIZE`
- `CAPTURE`
- `VOID`
- `REFUND`

Each entry has a unique request ID, amount, currency and provider reference. Existing entries cannot be updated through the application repository.

### Simulated provider

`simulated_provider_payments` stores the provider-side payment snapshot.

`simulated_provider_operations` stores every provider request, outcome and attempt count. Replaying a completed request returns the original provider reference. A timeout outcome remains retryable.

### Reconciliation

`settlement_discrepancies` stores durable differences between:

- local authorization state;
- provider state;
- immutable ledger totals.

Discrepancy types:

- `PROVIDER_MISSING`
- `STATUS_MISMATCH`
- `CAPTURE_AMOUNT_MISMATCH`
- `REFUND_AMOUNT_MISMATCH`
- `LEDGER_MISMATCH`

Lifecycle:

```text
OPEN
  ├─ ACKNOWLEDGED
  ├─ REPAIRING ── RESOLVED
  └─ FAILED ───── REPAIRING
```

A fingerprint composed from authorization ID and discrepancy type prevents duplicate rows during repeated scans.

## Failure injection

The simulated provider accepts comma-separated request IDs through environment variables:

```bash
FTGO_ACCOUNTING_SETTLEMENT_DENY_REQUEST_IDS=order-101-capture
FTGO_ACCOUNTING_SETTLEMENT_TIMEOUT_ONCE_REQUEST_IDS=order-102-capture
FTGO_ACCOUNTING_SETTLEMENT_TIMEOUT_ALWAYS_REQUEST_IDS=refund-103-1
```

Behavior:

- Denied requests return a deterministic business failure and do not mutate local state.
- Timeout-once requests persist the timeout attempt, throw a retryable exception and succeed on the next identical request.
- Timeout-always requests persist every attempt and never mutate provider or local state.

The provider adapter runs in `REQUIRES_NEW`. This models an external system whose successful state may survive a later local transaction failure. Reconciliation is responsible for identifying and repairing that condition.

## Scheduled reconciliation

Configuration:

```bash
FTGO_ACCOUNTING_SETTLEMENT_RECONCILIATION_ENABLED=true
FTGO_ACCOUNTING_SETTLEMENT_RECONCILIATION_INTERVAL_MS=60000
FTGO_ACCOUNTING_SETTLEMENT_RECONCILIATION_INITIAL_DELAY_MS=30000
```

Disable only during controlled migration or incident investigation:

```bash
FTGO_ACCOUNTING_SETTLEMENT_RECONCILIATION_ENABLED=false
```

A manual scan remains available while scheduling is disabled.

## Operations API

Base path:

```text
/api/admin/payment-settlement
```

### Run reconciliation

```bash
curl --fail-with-body \
  --request POST \
  http://localhost:8085/api/admin/payment-settlement/reconcile
```

Example response:

```json
{
  "inspected": 25,
  "detected": 1,
  "resolved": 0
}
```

### List discrepancies

```bash
curl --fail-with-body \
  http://localhost:8085/api/admin/payment-settlement/discrepancies
```

For one authorization:

```bash
curl --fail-with-body \
  http://localhost:8085/api/admin/payment-settlement/authorizations/701/discrepancies
```

### Issue a partial refund

```bash
curl --fail-with-body \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{
    "amount": {"amount": 10.00},
    "reason": "item unavailable",
    "idempotencyKey": "refund-order-101-item-1"
  }' \
  http://localhost:8085/api/admin/payment-settlement/authorizations/701/refunds
```

Retry with the exact same body and idempotency key. Do not generate a new key because an HTTP response was lost.

Expected errors:

- `400`: invalid amount, request conflict or over-refund.
- `409`: state does not allow the requested operation.
- `503`: simulated provider timeout; retry with the same idempotency key.

### Acknowledge a discrepancy

```bash
curl --fail-with-body \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{
    "action": "ACKNOWLEDGE",
    "idempotencyKey": "ack-discrepancy-9001",
    "reason": "assigned to payment operations"
  }' \
  http://localhost:8085/api/admin/payment-settlement/discrepancies/9001/actions
```

### Synchronize provider from local state

Use this only after verifying that the local authorization and immutable ledger represent the intended financial result.

```bash
curl --fail-with-body \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{
    "action": "SYNC_PROVIDER_FROM_LOCAL",
    "idempotencyKey": "repair-discrepancy-9001",
    "reason": "restore missing provider settlement"
  }' \
  http://localhost:8085/api/admin/payment-settlement/discrepancies/9001/actions
```

Repeat with the same idempotency key if the HTTP response is lost.

### Verify resolution

```bash
curl --fail-with-body \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{
    "action": "VERIFY_RESOLVED",
    "idempotencyKey": "verify-discrepancy-9001",
    "reason": "post-repair verification"
  }' \
  http://localhost:8085/api/admin/payment-settlement/discrepancies/9001/actions
```

## Metrics

Prometheus endpoint:

```text
http://localhost:8085/actuator/prometheus
```

Phase 02B metrics:

```text
ftgo_accounting_settlement_discrepancies_detected_total{type="..."}
ftgo_accounting_settlement_discrepancies_resolved_total{type="..."}
ftgo_accounting_settlement_discrepancies_current
ftgo_accounting_settlement_repairs_total{action="...",result="..."}
```

Existing Phase 03 outbox metrics must also remain healthy. A settlement mutation is not complete until aggregate, ledger and outbox writes commit together.

## Alert response

### Open discrepancy alert

1. Run manual reconciliation.
2. Read all discrepancy rows for the authorization.
3. Compare `authorizations`, `payment_refunds`, `payment_ledger_entries`, and `simulated_provider_payments`.
4. If local and ledger agree, use `SYNC_PROVIDER_FROM_LOCAL`.
5. If local and ledger disagree, acknowledge the discrepancy and investigate before repair.

### Repeated repair failure

1. Inspect `failureDetails` on the discrepancy.
2. Verify Accounting MySQL connectivity and provider tables.
3. Confirm the repair idempotency key was not reused for a different action.
4. Correct the dependency issue.
5. Retry with the original key when the previous provider operation might have succeeded; otherwise create a new operator-approved repair request.

### Capture timeout

1. Verify `simulated_provider_operations.attempt_count` increases for the same request ID.
2. Do not manually create a second capture request.
3. Let Eventuate retry the original participant command.
4. If attempts continue indefinitely, remove the timeout-always rule and retry the original request.

## Verification

Focused tests:

```bash
./gradlew :accounting-service:clean :accounting-service:test --no-daemon --stacktrace
```

Shared saga contracts:

```bash
./gradlew :common:test :order-service:test --no-daemon --stacktrace
```

Two clean-state live cycles:

```bash
bash scripts/smoke/verify-payment-settlement.sh --runs 2
```

Each live cycle verifies:

- authorization and capture through the real saga;
- one simulated capture timeout followed by retry success;
- immutable authorize and capture ledger entries;
- partial refund and duplicate replay;
- over-refund rejection;
- provider corruption detection;
- idempotent provider repair;
- final discrepancy resolution.

## Rollout

1. Deploy database migrations V8 through V11 before the new Accounting application.
2. Start Accounting with reconciliation disabled during the first instance replacement.
3. Verify application health and Flyway status.
4. Enable reconciliation on one instance.
5. Run a manual reconciliation and inspect detected discrepancies.
6. Enable reconciliation on the remaining instances only after confirming scans are stable.
7. Run one low-value partial refund with an explicit idempotency key.
8. Confirm local aggregate, provider state, ledger and outbox event.

## Rollback

Application rollback is supported because migrations are additive. Do not drop Phase 02B tables during an incident.

1. Disable scheduled reconciliation.
2. Stop manual settlement operations.
3. Roll the Accounting application back to the previous image.
4. Preserve all Phase 02B tables for investigation and forward recovery.
5. Do not issue refund, void or capture commands with new request IDs while the system state is uncertain.
6. Roll forward after correcting the cause and run manual reconciliation.

Because a real provider call cannot generally be rolled back with a database transaction, operational recovery is always forward reconciliation, not destructive schema rollback.
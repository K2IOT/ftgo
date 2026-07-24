# Phase 02 Core Order Flow Implementation Record

> **Status:** Implementation complete. PR #4 is the authoritative evidence register for the final branch SHA and workflow run URLs.

## Goal

Expand checkout into an authoritative, restart-safe order flow that:

- validates the restaurant menu before reserving resources;
- durably reserves consumer credit;
- authorizes payment before restaurant review;
- captures payment only after restaurant acceptance;
- compensates rejection, timeout, and pre-pivot failures exactly once;
- survives duplicate delivery, retries, service restarts, and clean-stack recreation.

## Final Architecture

Eventuate Tram command/reply is used for synchronous saga participants. The restaurant decision boundary uses Kitchen outbox events published through Debezium.

The workflow is split into three orchestrations:

- `CreateOrderSaga`: validate menu, reserve credit, create ticket, authorize payment, approve ticket, and wait for the restaurant decision.
- `ConfirmOrderSaga`: capture payment, commit credit, and approve the order after acceptance.
- `RejectOrderSaga`: void authorization, release credit, and reject the order after explicit rejection or timeout.

The first valid Kitchen decision wins an order-row compare-and-set transition. Duplicate, stale, and losing race events are successful no-ops.

## Completed Tasks

### Task 0: Phase 02 CI Gates

- [x] Add branch and pull-request workflow for Phase 02 contracts and the full test suite.
- [x] Add a dedicated real-stack E2E workflow.
- [x] Preserve diagnostics for module, migration, context, smoke, and E2E failures.
- [x] Verify the checked-in Gradle wrapper before every build.

### Task 1: Shared Order-Flow Contracts

- [x] Add menu-validation commands and typed replies.
- [x] Add durable credit reserve, commit, and release contracts.
- [x] Add payment authorize, capture, void, and refund contracts.
- [x] Add accepted, rejected, and timeout ticket-decision events.
- [x] Add serialization and duplicate-contract guardrails.
- [x] Propagate the payment token from REST through saga data to Accounting.

### Task 2: Authoritative Restaurant Menu Validation

- [x] Add restaurant availability and menu-version migrations.
- [x] Validate restaurant state, menu version, item identity, name, price, availability, quantity, and duplicates.
- [x] Return stable typed business rejection codes.
- [x] Prevent expected business rejections from marking the Eventuate message transaction rollback-only.
- [x] Add transaction-level regression coverage for failure replies.

### Task 3: Durable Consumer Credit Reservations

- [x] Persist reservations under a unique order business key.
- [x] Implement idempotent reserve, commit, and release transitions.
- [x] Preserve concurrency and credit-limit invariants.
- [x] Prevent typed credit rejections from poisoning the surrounding message transaction.

### Task 4: Accounting Authorization Lifecycle

- [x] Implement configurable payment-provider authorization decisions and a deny list.
- [x] Persist authorization lifecycle and deterministic operation request IDs.
- [x] Implement idempotent authorize, capture, void, and refund operations.
- [x] Align the native MySQL authorization-status enum with the complete Java lifecycle.
- [x] Add migration regression coverage for enum drift.

### Task 5: Kitchen Accept, Reject, and Timeout Decisions

- [x] Add state-guarded accept and reject APIs.
- [x] Add atomic timeout claiming for multiple service instances.
- [x] Publish exactly one typed outbox decision event.
- [x] Align both Kitchen ticket state columns with the complete `TicketState` lifecycle.
- [x] Add race, duplicate, timeout, and migration regression tests.

### Task 6: Create, Confirm, and Reject Order Sagas

- [x] Split the order workflow into Create, Confirm, and Reject sagas.
- [x] Add order decision states and row-lock claim transitions.
- [x] Consolidate all local Order saga command handlers into one complete dispatcher on `orderService`.
- [x] Align the native MySQL order-state enum with the complete `OrderState` lifecycle.
- [x] Decode both direct JSON and Kafka Connect schema-wrapped decision events.
- [x] Project explicit rejection and timeout outcomes into Order History.
- [x] Add stale, duplicate, and accept-timeout race handling.

### Task 7: Real Cross-Service Verification

- [x] Start real Order, Consumer, Restaurant, Kitchen, and Accounting processes.
- [x] Start MySQL, Kafka, ZooKeeper, Eventuate CDC, Debezium Connect, and the Kitchen outbox connector.
- [x] Execute restaurant acceptance.
- [x] Execute stale-menu rejection.
- [x] Execute insufficient-credit rejection.
- [x] Execute payment-provider denial.
- [x] Execute explicit restaurant rejection.
- [x] Execute restaurant acceptance timeout.
- [x] Execute the acceptance-versus-timeout race.
- [x] Execute duplicate decision delivery.
- [x] Repeat the complete suite through two independent clean-state cycles.

### Task 8: Documentation and Review Gates

- [x] Document the Phase 02 architecture and verification commands in the repository README.
- [x] Document migration ordering, rollout, kill-switch operation, rollback, and recovery.
- [x] Keep exact final workflow run URLs and the final SHA in PR #4.
- [x] Require all gates to pass on one final SHA before marking the PR ready.

## Defects Found by Real E2E and Resolved

The real workflow exposed issues that unit tests alone did not detect:

1. **Consumer and Restaurant rollback-only failure replies**
   - Expected typed business exceptions were caught by handlers, but Spring had already marked the joined Eventuate transaction rollback-only.
   - The reply was lost and the Kafka consumer terminated with `UnexpectedRollbackException`.
   - Expected business exceptions are now explicitly excluded from rollback.

2. **Partial Order command dispatchers sharing one channel**
   - Independent dispatcher groups subscribed to `orderService` with incomplete handler sets.
   - Commands were delivered to consumers that had no matching method.
   - One consolidated dispatcher now owns the complete local saga handler set.

3. **Native MySQL enum drift**
   - Accounting, Kitchen, and Order Java state machines had evolved beyond their native MySQL enum definitions.
   - Runtime writes failed with `Data truncated for column` and aborted message processing.
   - Forward migrations and `information_schema` regression tests now keep schema and domain lifecycles aligned.

4. **Kafka Connect schema envelope mismatch**
   - Debezium delivered events as a top-level schema envelope with the event under `payload`.
   - Direct DTO deserialization silently produced decision objects with null business IDs.
   - A shared payload reader now accepts direct JSON, object envelopes, and textual JSON payloads.

5. **Property-test input drift**
   - A generic non-blank generator produced control-only strings that violated the domain's request-ID rules.
   - Accounting properties now use an explicit domain-valid request-ID provider.

## Verification Gates

The following commands and workflows must pass on the same final branch SHA. Exact run URLs are recorded in PR #4.

| Gate | Command or workflow | Required result |
|------|---------------------|-----------------|
| Module matrix | Phase 01 Module Diagnostics | 7/7 modules green |
| Full build | `./gradlew --no-daemon clean test --stacktrace` | Green |
| Phase 02 contracts | Phase 02 Core Order Flow / `phase02-contracts` | Green |
| Phase 02 full suite | Phase 02 Core Order Flow / `clean-test` | Green |
| Migration and context | Phase 01 Verification | Green |
| Fresh stack | Phase 01 Fresh Stack Smoke | Two clean-volume cycles green |
| Core order-flow E2E | Phase 02 Core Order Flow E2E | Eight scenarios in two clean-state cycles green |

### Local E2E Command

```bash
bash scripts/smoke/verify-core-order-flow.sh --runs 2
```

### Dedicated Scenario Set

1. Accept and confirm.
2. Menu drift.
3. Insufficient credit.
4. Payment denial.
5. Explicit reject.
6. Timeout.
7. Accept-timeout race.
8. Duplicate delivery.

## Rollout and Rollback

The operational source of truth is:

- `docs/operations/phase-02-core-order-flow-rollout.md`

Key controls:

- Apply backward-compatible migrations before application rollout.
- Deploy participants before enabling new order creation.
- Use canary rollout and verify saga/consumer health before increasing traffic.
- Disable new Phase 02 orders with `FTGO_ORDER_PHASE2_ENABLED=false` before service rollback.
- Do not contract enum schemas while persisted rows or in-flight messages still use Phase 02 values.
- Preserve outbox, command, reply, and saga data during rollback and recovery.

## Completion Criteria

- [x] Restaurant Service rejects stale, unavailable, or changed menu snapshots with typed codes.
- [x] Consumer reservations are durable, unique per order, concurrency-safe, and idempotent.
- [x] Payment authorization is voidable; capture occurs only after restaurant acceptance.
- [x] Restaurant accept, reject, and timeout produce exactly one winning decision.
- [x] Create, Confirm, and Reject sagas converge without duplicate participant effects.
- [x] Shared contract serialization and guardrail tests pass.
- [x] Module, full-build, migration, context, fresh-stack, and dedicated E2E verification pass.
- [x] Two clean-state E2E cycles pass all eight scenarios.
- [x] Rollout, kill-switch, rollback, and recovery procedures are documented.

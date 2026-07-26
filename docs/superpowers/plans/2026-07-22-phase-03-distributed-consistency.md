# FTGO Phase 03 Distributed Consistency Implementation Plan

> **Status:** Completed and merged through PR #15. Re-verified without regression on Phase 02B head `842f66e4b50ae3197c0dd674a470c71b153966f8`.

**Goal:** Bảo đảm saga commands, domain events và CQRS projections an toàn trước duplicate, lost reply, out-of-order, poison message và service restart.

**Architecture:** Chuẩn hóa event envelope; participant lưu command result; consumer dùng event ID riêng; Kafka retry/DLT có bounded policy; Order History có pending-event recovery; saga/outbox có reconciliation và operator visibility.

**Tech Stack:** Eventuate Tram, Kafka, Spring Kafka, Debezium, MySQL, ScyllaDB, JPA, Testcontainers, Awaitility, Micrometer.

## Completion Summary

- [x] Task 1 — Versioned domain-event envelope and Debezium `event_id` routing.
- [x] Task 2 — Idempotent saga command-result cache for Consumer, Kitchen and Accounting.
- [x] Task 3 — Event dedupe by event ID instead of aggregate key.
- [x] Task 4 — Bounded retry `1s → 5s → 30s → DLT`.
- [x] Task 5 — Order History pending-event recovery with terminal DLT after 20 attempts or 24 hours.
- [x] Task 6 — Scylla access-pattern tables, monthly buckets and opaque paging tokens.
- [x] Task 7 — Stuck-saga monitor, persistent repair operations, idempotent admin actions and outbox metrics.
- [x] Task 8 — Seven distributed-failure scenarios and two clean-state cycles.

## Global Constraints

- [x] Aggregate ID is used only as Kafka partition key, not event/message identity.
- [x] Event contracts remain backward compatible within the release window.
- [x] Duplicate commands return the original reply payload and identifiers.
- [x] Poison events cannot block a partition indefinitely.
- [x] Events are not marked processed before projection mutation commits.

## Implementation Evidence

### Task 1 — Versioned Domain Event Envelope

- `DomainEventEnvelope`, metadata and trace context are implemented.
- Stable event UUID, schema version, aggregate identity/version, correlation and causation metadata are persisted.
- Debezium event-router ID mapping uses `event_id`; aggregate ID remains the Kafka partition key.
- Serialization and outbox-routing contracts are covered by tests.

### Task 2 — Idempotent Saga Command Results

- Shared `IdempotentCommandExecutor` and processed-command persistence are implemented.
- Consumer, Kitchen and Accounting participant handlers replay original business replies.
- Domain mutation and cached result commit in one local transaction.
- Infrastructure and outbox failures propagate instead of being cached as business outcomes.

### Task 3 — Event Identity and Transaction Semantics

- Event identity comes from the envelope/header rather than aggregate key.
- Same-type events for the same aggregate are processed independently.
- Duplicate event IDs are skipped idempotently.
- Projection processing states support recoverable application semantics.

### Task 4 — Bounded Retry and Dead Letters

- Finite retry schedule is `1s → 5s → 30s`.
- Terminal records route to `.DLT` with original event and failure metadata.
- Poison messages cannot permanently block later valid records.
- Retry/DLT metrics do not expose raw payload or PII labels.

### Task 5 — Out-of-Order Order History Recovery

- Pending events are partitioned by order and reconciled in aggregate-version order.
- Successful prerequisite creation performs a bounded synchronous drain.
- Scheduled reconciliation handles remaining rows.
- Events move to projection DLT after 20 attempts or 24 hours.

### Task 6 — Scylla Access Patterns and Paging

- Dedicated consumer, consumer+status and consumer+restaurant query tables exist.
- Tables use monthly partition buckets.
- Filtering is server-side through dedicated access patterns.
- Continuation tokens come from actual driver paging state.
- Cassandra mappings use explicit snake_case columns without duplicate composite-key mappings.

### Task 7 — Saga and Outbox Reconciliation

- Stuck-saga monitoring uses configurable thresholds.
- Repair operations are persistent and action keys are idempotent.
- Repair execution is split into explicit transaction stages.
- Failed repair audit rows survive rollback using `REQUIRES_NEW`.
- Ambiguous financial states route to manual review.
- Outbox backlog, age and publish-error metrics are exposed.

### Task 8 — Distributed Failure E2E

Covered scenarios:

1. Participant commits and reply is dropped; retry returns the original result.
2. Service restarts after durable mutation.
3. Duplicate event delivery.
4. Two same-type events for one aggregate.
5. Out-of-order Order History events.
6. Poison event reaches DLT without blocking a valid event.
7. Debezium pause/resume eventually delivers to an idempotent consumer.

The workflow runs two clean-state cycles.

## Final Verification

Original Phase 3 feature head `8daa395d0db292a2f1c3bafe773a0c0245b308bd` passed the complete required matrix before merge through PR #15.

Phase 3 was re-verified on later Payment Settlement head `842f66e4b50ae3197c0dd674a470c71b153966f8` after Accounting, Kitchen and Scylla changes:

- [x] Phase 01 Verification — run #804.
- [x] Phase 01 Module Diagnostics — run #646.
- [x] Phase 01 Full Gradle Verification — run #654.
- [x] Phase 01 Fresh Stack Smoke — run #560.
- [x] Phase 02 Core Order Flow — run #662.
- [x] Phase 02 Core Order Flow E2E — run #482.
- [x] Phase 02B Payment Settlement E2E — run #21.
- [x] Phase 03 Distributed Consistency — run #348.
- [x] Phase 03 Order History Consistency — run #239.
- [x] Phase 03 Operations Reconciliation — run #196.
- [x] Phase 03 Distributed Failure E2E — run #161, two clean-state cycles.

Documentation-only completion commit `c75b8fa3fd70481bea07fccc64979bc0ce726593` started a fresh verification matrix. The previously verified implementation SHA remains the evidence for runtime correctness until that documentation-only matrix finishes.

## Phase Completion Checklist

- [x] Event identity and aggregate partition key are distinct.
- [x] All participant commands replay original replies.
- [x] Retry/DLT handling is bounded and observable.
- [x] Order History recovers out-of-order events.
- [x] Scylla queries do not filter pages in memory.
- [x] Stuck saga and outbox health are inspectable and reconcilable.
- [x] Distributed failure E2E suite passes twice consecutively.

Phase 03 is complete, merged into `dev`, and verified against subsequent Payment Settlement changes.
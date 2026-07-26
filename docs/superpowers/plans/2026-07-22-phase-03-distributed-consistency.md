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

## Task 1: Versioned Domain Event Envelope

Implemented:

- `DomainEventEnvelope`, metadata and trace context.
- Stable event UUID, schema version, aggregate identity/version, correlation and causation metadata.
- Debezium event-router ID mapping uses `event_id` while aggregate ID remains the Kafka key.
- Serialization and outbox-routing contract coverage.

Verification gate: `Phase 03 Distributed Consistency`.

## Task 2: Idempotent Saga Command Results

Implemented:

- Shared `IdempotentCommandExecutor` and processed-command result persistence.
- Consumer, Kitchen and Accounting participant handlers replay original business replies.
- Domain mutation and cached result share one local transaction.
- Infrastructure/outbox failures propagate and are not cached as business replies.

Verification gates: module diagnostics, full Gradle verification and distributed-failure E2E.

## Task 3: Event Identity and Transaction Semantics

Implemented:

- Event ID extracted from envelope/header rather than aggregate key.
- Same-type events for the same aggregate are processed independently.
- Duplicate event IDs are skipped idempotently.
- Projection processing state supports recoverable application semantics.

Verification gates: Delivery and Order History consistency tests.

## Task 4: Bounded Retry and Dead Letters

Implemented:

- Finite retry schedule `1s → 5s → 30s`.
- Terminal `.DLT` routing with original event/failure metadata.
- Poison messages do not permanently block later valid records.
- Retry/DLT metrics avoid raw payload and PII labels.

Verification gate: distributed-failure E2E.

## Task 5: Out-of-Order Order History Recovery

Implemented:

- Pending-event storage partitioned by order.
- Aggregate-version ordered reconciliation.
- Synchronous bounded drain after prerequisite creation plus scheduled recovery.
- Terminal projection DLT after 20 attempts or 24 hours.

Verification gate: `Phase 03 Order History Consistency`.

## Task 6: Scylla Access Patterns and Paging

Implemented:

- Consumer, consumer+status and consumer+restaurant access-pattern tables.
- Monthly partition buckets.
- Server-side filtering through dedicated tables.
- Opaque continuation tokens derived from actual driver paging state.
- Explicit snake_case Cassandra mappings without duplicate composite-key fields.

Verification gate: `Phase 03 Order History Consistency`.

## Task 7: Saga and Outbox Reconciliation

Implemented:

- Stuck-saga monitoring with configurable thresholds.
- Persistent repair operations and idempotent action keys.
- Repair execution split into transaction stages.
- Failed repair audit rows persist with `REQUIRES_NEW`.
- Ambiguous financial states route to manual review.
- Outbox backlog/age/error metrics.

Verification gate: `Phase 03 Operations Reconciliation`.

## Task 8: Distributed Failure E2E

Implemented scenarios:

1. Participant commits and reply is dropped; retry returns the original result.
2. Service restarts after durable mutation.
3. Duplicate event delivery.
4. Two same-type events for one aggregate.
5. Out-of-order Order History events.
6. Poison event reaches DLT without blocking a valid event.
7. Debezium pause/resume eventually delivers to an idempotent consumer.

The workflow executes two clean-state cycles.

## Final Verification

Original Phase 3 feature head `8daa395d0db292a2f1c3bafe773a0c0245b308bd` passed the complete required matrix before merge through PR #15.

Phase 3 was re-verified on Phase 02B head `842f66e4b50ae3197c0dd674a470c71b153966f8` after later Accounting, Kitchen and Scylla changes:

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

## Phase Completion Checklist

- [x] Event identity and aggregate partition key are distinct.
- [x] All participant commands replay original replies.
- [x] Retry/DLT handling is bounded and observable.
- [x] Order History recovers out-of-order events.
- [x] Scylla queries do not filter pages in memory.
- [x] Stuck saga and outbox health are inspectable and reconcilable.
- [x] Distributed failure E2E suite passes twice consecutively.

Phase 03 is complete, merged into `dev`, and verified against subsequent Payment Settlement changes.
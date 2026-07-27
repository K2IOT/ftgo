# Phase 02B Payment Settlement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver production-grade simulated payment settlement with capture on order approval, immutable accounting ledger, partial refunds, reconciliation, repair operations, and failure-oriented verification.

**Architecture:** Extend the existing Accounting aggregate and ConfirmOrderSaga rather than introducing a second payment service. Every authorize, capture, void, and refund operation remains command-idempotent and additionally appends an immutable ledger entry keyed by operation request ID. A deterministic simulated gateway stores provider-side settlement state; a scheduled reconciler compares provider and local totals and persists repairable discrepancies.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, Eventuate Tram Saga, MySQL, JUnit 5, AssertJ, GitHub Actions.

## Global Constraints

- Capture occurs when the restaurant acceptance flow starts `ConfirmOrderSaga` and before the order becomes `APPROVED`.
- The payment gateway is simulated and deterministic; no external provider dependency is introduced.
- All monetary mutations are idempotent by request ID.
- Ledger rows are append-only and must never be updated or deleted by application code.
- Partial refunds may be repeated until the captured amount is fully refunded; total refunds may never exceed capture.
- Existing Phase 02 and Phase 03 contracts remain backward compatible.

---

### Task 1: Immutable payment ledger

**Files:**
- Create: `accounting-service/src/main/java/net/ftgo/accounting/settlement/PaymentLedgerEntry.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/settlement/PaymentLedgerEntryRepository.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/settlement/PaymentLedgerService.java`
- Create: `accounting-service/src/main/resources/db/migration/V8__create_payment_settlement_ledger.sql`
- Test: `accounting-service/src/test/java/net/ftgo/accounting/settlement/PaymentLedgerServiceTest.java`

- [x] Write tests proving request-ID idempotency and balanced operation metadata.
- [x] Run the focused test and verify it fails before implementation.
- [x] Implement append-only ledger persistence.
- [ ] Run focused tests and commit.

### Task 2: Partial-refund state machine

**Files:**
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/domain/Authorization.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/domain/Account.java`
- Create: `accounting-service/src/main/resources/db/migration/V9__support_partial_refunds.sql`
- Test: `accounting-service/src/test/java/net/ftgo/accounting/domain/AuthorizationPartialRefundTest.java`

- [x] Write tests for first partial refund, multiple partial refunds, exact full refund, duplicate request, and over-refund rejection.
- [x] Run tests and confirm failure.
- [x] Add cumulative refunded amount and idempotent refund operation tracking.
- [ ] Run tests and commit.

### Task 3: Deterministic settlement gateway

**Files:**
- Create: `accounting-service/src/main/java/net/ftgo/accounting/settlement/SettlementGateway.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/settlement/SimulatedSettlementGateway.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/settlement/SettlementDecision.java`
- Create: `accounting-service/src/main/resources/db/migration/V10__create_simulated_provider_settlement.sql`
- Test: `accounting-service/src/test/java/net/ftgo/accounting/settlement/SimulatedSettlementGatewayTest.java`

- [x] Test deterministic approve, deny, timeout, duplicate capture, and partial refund behavior.
- [x] Implement durable provider-side state keyed by authorization and request ID.
- [ ] Verify tests and commit.

### Task 4: Command-handler settlement integration

**Files:**
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/messaging/AccountingServiceCommandHandlers.java`
- Test: `accounting-service/src/test/java/net/ftgo/accounting/messaging/AccountingSettlementCommandHandlersTest.java`

- [x] Test that successful commands mutate provider, aggregate, outbox, and ledger once.
- [x] Test provider denial and retry behavior.
- [x] Wire authorize, capture, void, and refund through gateway and ledger.
- [ ] Verify tests and commit.

### Task 5: Reconciliation and repair API

**Files:**
- Create: `accounting-service/src/main/java/net/ftgo/accounting/settlement/SettlementDiscrepancy.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/settlement/SettlementDiscrepancyRepository.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/settlement/SettlementReconciler.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/api/admin/PaymentSettlementOperationsController.java`
- Create: `accounting-service/src/main/resources/db/migration/V11__create_settlement_discrepancies.sql`
- Test: `accounting-service/src/test/java/net/ftgo/accounting/settlement/SettlementReconcilerTest.java`

- [x] Test detection, deduplication, acknowledgement, retry, and resolved transitions.
- [x] Implement persistent discrepancy lifecycle and idempotent repair commands.
- [ ] Verify tests and commit.

### Task 6: Failure E2E, CI, and runbook

**Files:**
- Create: `e2e-tests/src/test/java/net/ftgo/e2e/PaymentSettlementTest.java`
- Create: `.github/workflows/phase-02b-payment-settlement.yml`
- Create: `docs/operations/phase-02b-payment-settlement-runbook.md`
- Create: `scripts/smoke/verify-payment-settlement.sh`
- Create: `deployment/monitoring/phase-02b-payment-settlement-alerts.yml`

- [x] Cover capture success, duplicate delivery, capture timeout/retry, partial refunds, over-refund rejection, reconciliation, and repair.
- [ ] Run two clean-state cycles.
- [x] Document rollout, rollback, metrics, alerts, and operator repair steps.
- [ ] Run full verification and commit.
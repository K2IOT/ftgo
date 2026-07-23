# Phase 02 Core Order Flow — Rollout and Rollback

This runbook covers the Phase 02 order flow across Restaurant, Consumer,
Kitchen, Accounting and Order services.

## Safety model

Phase 02 uses backward-compatible commands and expand-only database migrations.
During rollout, new participants can accept legacy commands, and the Order
Service must be the last service allowed to create new Phase 02 sagas.

The operational kill switch is:

```bash
FTGO_ORDER_PHASE2_ENABLED=false
```

Disabling the switch blocks new order creation with HTTP `503` while existing
sagas and decision handlers remain running so that reservations,
authorizations and tickets can converge.

## Database migrations

Apply migrations before deploying application code. Do not remove old columns,
states or constructors in the same release.

| Service | Migration | Purpose |
|---|---|---|
| Restaurant | `V2__add_menu_version_and_status.sql` | Authoritative menu version and restaurant acceptance status |
| Restaurant | `V3__create_eventuate_messaging_schema.sql` | Eventuate command/reply messaging tables |
| Consumer | `V3__create_credit_reservations.sql` | Durable, unique per-order credit reservations |
| Accounting | `V4__add_authorization_lifecycle.sql` | Authorization, capture, void and refund lifecycle |
| Kitchen | `V7__add_ticket_acceptance_decision.sql` | Acceptance deadline and exactly-one decision metadata |
| Order | `V5__add_restaurant_decision_states.sql` | Awaiting, confirmation-pending and rejection-pending states |

Before application rollout, verify Flyway succeeds for every schema and that
Eventuate `message`/`received_messages` tables exist for each command
participant.

## Deployment order

1. Keep `FTGO_ORDER_PHASE2_ENABLED=false` on all Order Service instances.
2. Apply all migrations.
3. Deploy Restaurant Service and verify menu-validation command handling.
4. Deploy Consumer Service and verify reservation create/release idempotency.
5. Deploy Accounting Service and verify authorize/void/capture commands.
6. Deploy Kitchen Service and verify accept/reject/timeout decision publishing.
7. Verify Eventuate CDC is relaying each service's `message` table and the
   Kitchen decision outbox is publishing to
   `net.ftgo.kitchenservice.domain.Ticket`.
8. Deploy Order Service with the kill switch still disabled.
9. Run contract tests, module tests and one clean-state E2E cycle.
10. Enable Phase 02 on one canary Order Service instance.
11. Observe the canary until at least one accept, one rejection and one timeout
    have converged without leaked resources.
12. Enable the remaining Order Service instances gradually.
13. Run the full two-cycle clean-state E2E gate before declaring rollout
    complete.

## Canary invariants

For each order, inspect the Order state together with participant resources:

| Final order state | Credit reservation | Authorization | Ticket |
|---|---|---|---|
| `APPROVED` | `COMMITTED` | `CAPTURED` | `ACCEPTED` |
| `REJECTED` before authorization | absent or `RELEASED` | absent | absent or `CANCELLED` |
| `REJECTED` after authorization | `RELEASED` | `VOIDED` | `REJECTED_BY_RESTAURANT` or `REJECTED_TIMEOUT` |

Also verify that each ticket has at most one decision event and each order has
at most one credit reservation and one authorization request ID.

## Monitoring

Track at minimum:

- `order_service_placed_orders_total`
- `order_service_approved_orders_total`
- `order_service_rejected_orders_total`
- `order_service_saga_failures_total{saga="CreateOrderSaga"}`
- Kafka consumer lag for Eventuate command/reply and ticket decision consumers
- count and age of `APPROVAL_PENDING`, `AWAITING_RESTAURANT_ACCEPTANCE`,
  `CONFIRMATION_PENDING` and `REJECTION_PENDING` orders
- count and age of `RESERVED` credit reservations
- count and age of `AUTHORIZED` authorizations
- Eventuate CDC and Kitchen outbox connector status

Alert when a pending state exceeds the configured acceptance deadline plus the
normal command/reply retry window.

## Rollback procedure

1. Set `FTGO_ORDER_PHASE2_ENABLED=false` on every Order Service instance.
2. Confirm new order creation returns HTTP `503`.
3. Keep all Phase 02 participants, Kafka consumers and CDC relays running.
4. Drain in-flight sagas until pending-order, reserved-credit and authorized-
   payment counts stop decreasing.
5. Investigate or replay poison/dead-letter messages before stopping any
   participant.
6. Roll back Order Service application code only after in-flight Phase 02 sagas
   are drained or explicitly compensated.
7. Roll back Kitchen, Accounting, Consumer and Restaurant code in that order,
   while retaining versions that understand both old and new commands.
8. Leave all Phase 02 migrations in place. They are expand-only and are also
   required to inspect or compensate historical in-flight work.
9. Re-enable order intake only after the legacy flow and its verification gates
   are green.

## Database rollback policy

Do not automatically drop Phase 02 tables, columns or enum/check-constraint
states during an application rollback. Destructive cleanup requires a separate
migration after:

- no Phase 02 saga remains in progress,
- all reservations and authorizations have reached terminal states,
- the event-retention and audit window has expired,
- backups and restore procedures have been tested.

## Verification commands

```bash
./gradlew --no-daemon clean test
bash scripts/smoke/run-fresh-stack-smoke.sh --runs 2
bash scripts/smoke/verify-core-order-flow.sh --runs 2
```

The Phase 02 pull request must record the exact source commit and successful
GitHub Actions run URLs for all three gates before it is marked ready for
review.

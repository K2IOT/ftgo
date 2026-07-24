# Phase 02 Core Order Flow — Business Expansion Design

**Date:** 2026-07-23  
**Status:** Proposed design approved in conversation; awaiting written-spec review  
**Branch:** `agent/phase-02-core-order-flow` (created directly from `dev`)

## 1. Purpose

Phase 02 expands the existing FTGO order flow from a basic create-order saga into a production-oriented checkout and restaurant-confirmation workflow spanning:

- Order Service
- Restaurant Service
- Consumer Service
- Kitchen Service
- Accounting Service

The design preserves Eventuate Tram command/reply and transactional outbox patterns already used by the codebase. It adds authoritative menu validation, durable consumer-credit reservations, reversible payment authorization, restaurant acceptance, capture after acceptance, and automatic timeout rejection.

## 2. Goals

1. Reject orders whose menu version, price, availability, or restaurant status is stale.
2. Reserve consumer credit atomically per order and prevent concurrent overspending.
3. Authorize payment during checkout without treating authorization as an irreversible pivot.
4. Keep the order pending until kitchen staff accepts the ticket.
5. Capture payment only after restaurant acceptance.
6. Automatically reject an order when the restaurant does not respond before a configurable deadline.
7. Make all commands, event handlers, state transitions, and compensations idempotent.
8. Remain restart-safe and horizontally scalable.
9. Verify the real MySQL, Kafka/Eventuate, outbox, saga, and compensation paths with integration tests.

## 3. Non-goals

This phase does not implement:

- delivery assignment or courier workflows;
- capture on delivery completion;
- partial capture or split tender;
- partial refund;
- promotion or coupon calculation;
- dynamic taxes, tips, or delivery fees;
- restaurant inventory decrement beyond menu availability validation;
- cross-restaurant orders;
- replacement of Eventuate with a custom process manager.

## 4. Chosen Architecture

Use Eventuate Tram command/reply for synchronous saga participants and domain events for the asynchronous restaurant-decision boundary.

The workflow is split into three bounded processes:

1. `CreateOrderSaga`
   - validates the authoritative restaurant/menu snapshot;
   - reserves consumer credit;
   - creates the kitchen ticket;
   - authorizes payment;
   - places the ticket and order into their waiting-for-restaurant states;
   - then completes.

2. `ConfirmOrderSaga`
   - starts after `TicketAcceptedEvent` wins the order decision lock;
   - captures the authorization;
   - commits the consumer credit reservation;
   - approves the order.

3. `RejectOrderSaga`
   - starts after `TicketRejectedEvent` or `TicketAcceptanceTimedOutEvent` wins the order decision lock;
   - voids the authorization;
   - releases the consumer credit reservation;
   - rejects the order.

This avoids a long-running saga waiting for a human action while keeping compensation, persistence, retry, and recovery inside the existing saga framework.

## 5. End-to-end Flow

### 5.1 Checkout flow

```text
Client
  -> Order Service: create order with restaurantId, menuVersion and priced line-item snapshot
  -> CreateOrderSaga
       1. Validate menu with Restaurant Service
       2. Reserve credit with Consumer Service
       3. Create ticket with Kitchen Service
       4. Authorize payment with Accounting Service
       5. Approve ticket into AWAITING_ACCEPTANCE
       6. Mark order AWAITING_RESTAURANT_ACCEPTANCE
  <- order accepted for restaurant review
```

The client-provided price is never authoritative. Restaurant Service returns the current authoritative menu data. Any difference causes `MENU_PRICE_CHANGED` or another explicit validation failure and the order is rejected.

### 5.2 Restaurant accepts

```text
Kitchen staff
  -> Kitchen Service: accept ticket
  -> TicketAcceptedEvent
  -> Order Service decision handler atomically claims ACCEPT outcome
  -> ConfirmOrderSaga
       1. Capture payment
       2. Commit credit reservation
       3. Approve order
```

### 5.3 Restaurant rejects

```text
Kitchen staff
  -> Kitchen Service: reject ticket
  -> TicketRejectedEvent
  -> Order Service decision handler atomically claims REJECT outcome
  -> RejectOrderSaga
       1. Void payment authorization
       2. Release credit reservation
       3. Reject order
```

### 5.4 Restaurant times out

```text
Kitchen timeout scheduler
  -> atomically mark expired ticket REJECTED_TIMEOUT
  -> TicketAcceptanceTimedOutEvent
  -> Order Service decision handler atomically claims TIMEOUT outcome
  -> RejectOrderSaga
       1. Void payment authorization
       2. Release credit reservation
       3. Reject order
```

## 6. State Machines

### 6.1 Order

```text
APPROVAL_PENDING
  ├─ checkout failure ─────────────────────────────> REJECTED
  └─ checkout prepared ─> AWAITING_RESTAURANT_ACCEPTANCE
                              ├─ accept claimed ─> CONFIRMATION_PENDING ─> APPROVED
                              ├─ reject claimed ─> REJECTION_PENDING ────> REJECTED
                              └─ timeout claimed -> REJECTION_PENDING ────> REJECTED
```

New states:

- `AWAITING_RESTAURANT_ACCEPTANCE`
- `CONFIRMATION_PENDING`
- `REJECTION_PENDING`

`CONFIRMATION_PENDING` and `REJECTION_PENDING` are semantic locks. A compare-and-set state transition determines whether acceptance, explicit rejection, or timeout owns the decision. Losing handlers acknowledge the duplicate/stale event and perform no external side effect.

### 6.2 Kitchen Ticket

```text
CREATE_PENDING
  -> AWAITING_ACCEPTANCE
       ├─ ACCEPTED
       ├─ REJECTED_BY_RESTAURANT
       └─ REJECTED_TIMEOUT
```

Existing preparation states continue from `ACCEPTED`:

```text
ACCEPTED -> PREPARING -> READY_FOR_PICKUP -> PICKED_UP
```

New terminal pre-preparation states:

- `REJECTED_BY_RESTAURANT`
- `REJECTED_TIMEOUT`

### 6.3 Payment Authorization

```text
AUTHORIZED
  ├─ CAPTURED
  └─ VOIDED

CAPTURED
  └─ REFUNDED
```

Required statuses:

- `AUTHORIZED`
- `DENIED`
- `CAPTURED`
- `VOIDED`
- `REFUNDED`

Authorization is compensatable. Capture is the business pivot for this flow.

### 6.4 Credit Reservation

```text
RESERVED -> COMMITTED -> RELEASED
     └──────────────────> RELEASED
```

- `RESERVED`: amount is unavailable to other orders.
- `COMMITTED`: restaurant accepted and the order is financially committed.
- `RELEASED`: reservation no longer consumes available credit.

`releaseCredit` is allowed from `RESERVED` and `COMMITTED` so future cancellation/refund flows can return credit without introducing another reservation type.

## 7. Service Responsibilities

### 7.1 Restaurant Service

Restaurant Service is authoritative for:

- restaurant existence and enabled/open status;
- menu version;
- menu item existence;
- item availability;
- current item price;
- optional quantity constraints.

Add a command:

```text
ValidateOrderMenuCommand
- orderId
- restaurantId
- expectedMenuVersion
- lineItems[]
  - menuItemId
  - expectedName
  - expectedUnitPrice
  - quantity
```

Success reply:

```text
OrderMenuValidated
- restaurantId
- currentMenuVersion
- authoritativeLineItems[]
- authoritativeTotal
```

Failure replies/errors must distinguish:

- `RESTAURANT_NOT_FOUND`
- `RESTAURANT_CLOSED`
- `MENU_VERSION_CHANGED`
- `MENU_ITEM_NOT_FOUND`
- `MENU_ITEM_UNAVAILABLE`
- `MENU_PRICE_CHANGED`
- `INVALID_QUANTITY`

The saga rejects the order when the authoritative snapshot differs. It does not silently rewrite the order to a higher or lower price.

### 7.2 Consumer Service

Add a durable `CreditReservation` aggregate or entity with a unique reservation per `orderId`.

Commands:

```text
ReserveConsumerCreditCommand(consumerId, orderId, amount)
CommitConsumerCreditCommand(consumerId, orderId)
ReleaseConsumerCreditCommand(consumerId, orderId, reason)
```

Replies:

```text
ConsumerCreditReserved(reservationId, orderId, amount)
ConsumerCreditCommitted(reservationId, orderId)
ConsumerCreditReleased(reservationId, orderId)
ConsumerCreditReservationRejected(orderId, reason)
```

Concurrency rule:

```text
availableCredit = creditLimit - sum(active RESERVED or COMMITTED reservations)
```

The reserve operation must lock the consumer row or use optimistic concurrency and retry. The unique `order_id` constraint makes redelivery idempotent.

Proposed table:

```text
credit_reservations
- id
- consumer_id
- order_id                  UNIQUE
- amount
- status
- version
- created_at
- updated_at
```

### 7.3 Kitchen Service

Add:

```http
POST /tickets/{ticketId}/reject
```

Acceptance and rejection must use state-guarded updates from `AWAITING_ACCEPTANCE` only.

Ticket fields:

```text
acceptance_deadline
accepted_at
rejected_at
rejection_reason
version
```

Events:

```text
TicketAcceptedEvent(ticketId, orderId, occurredAt)
TicketRejectedEvent(ticketId, orderId, reason, occurredAt)
TicketAcceptanceTimedOutEvent(ticketId, orderId, deadline, occurredAt)
```

Default configuration:

```yaml
ftgo:
  kitchen:
    acceptance-timeout: PT5M
    timeout-scan-interval: PT10S
    timeout-batch-size: 100
```

The scheduler must claim expired rows atomically. Suitable implementations include pessimistic locking with `SKIP LOCKED` or an atomic conditional update followed by event publication through the transactional outbox. Multiple Kitchen Service instances must not emit multiple effective timeout decisions.

### 7.4 Accounting Service

Commands:

```text
AuthorizeCardCommand(consumerId, orderId, amount, requestId)
CaptureAuthorizationCommand(orderId, authorizationId, requestId)
VoidAuthorizationCommand(orderId, authorizationId, reason, requestId)
RefundPaymentCommand(orderId, captureId, amount, reason, requestId)
```

Replies:

```text
CardAuthorized(authorizationId, orderId)
CardAuthorizationDenied(orderId, reason)
PaymentCaptured(captureId, authorizationId, orderId)
AuthorizationVoided(authorizationId, orderId)
PaymentRefunded(refundId, captureId, orderId)
```

Idempotency keys:

- authorize: `order-{orderId}-authorize`
- capture: `order-{orderId}-capture`
- void: `order-{orderId}-void`
- refund: `order-{orderId}-refund-{refundSequence}`

A unique constraint on operation type plus request ID prevents duplicate financial effects.

### 7.5 Order Service

Order Service owns workflow coordination and the decision race.

`CreateOrderSagaData` must include:

```text
orderId
consumerId
restaurantId
expectedMenuVersion
requestedLineItems
validatedLineItems
authoritativeTotal
creditReservationId
ticketId
authorizationId
acceptanceDeadline
failureCode
```

Order Service consumes kitchen decision events. Each handler first performs a local atomic transition:

```text
AWAITING_RESTAURANT_ACCEPTANCE -> CONFIRMATION_PENDING
AWAITING_RESTAURANT_ACCEPTANCE -> REJECTION_PENDING
```

Only the transaction that succeeds starts its saga. This is the central protection against accept/timeout races.

## 8. Saga Definitions

### 8.1 CreateOrderSaga

Recommended step order:

```text
1. createOrder locally in APPROVAL_PENDING
2. validateMenu
3. reserveCredit
   compensation: releaseCredit
4. createTicket
   compensation: cancelTicket
5. authorizeCard
   compensation: voidAuthorization
6. approveTicketToAwaitingAcceptance
7. markOrderAwaitingRestaurantAcceptance locally
```

No irreversible pivot exists in this saga. Every remote resource created before completion has a compensation.

The final state is not `APPROVED`; it is `AWAITING_RESTAURANT_ACCEPTANCE`.

### 8.2 ConfirmOrderSaga

```text
1. verify/retain CONFIRMATION_PENDING locally
2. captureAuthorization                    PIVOT
3. commitCreditReservation                 retriable
4. markOrderApproved locally               retriable
```

After capture succeeds, all remaining steps must be idempotent and retried until completion.

### 8.3 RejectOrderSaga

```text
1. verify/retain REJECTION_PENDING locally
2. voidAuthorization                       idempotent
3. releaseCreditReservation                idempotent
4. markOrderRejected locally               idempotent
```

`RejectOrderSaga` has no irreversible side effect and can be retried safely.

## 9. Race Conditions and Idempotency

### 9.1 Accept versus timeout

Possible race:

1. Kitchen staff accepts at the deadline.
2. Scheduler also finds the ticket expired.
3. Both events reach Order Service.

Protection exists at two levels:

- Kitchen state transition allows only one of `AWAITING_ACCEPTANCE -> ACCEPTED/REJECTED_TIMEOUT`.
- Order state transition allows only one of `AWAITING_RESTAURANT_ACCEPTANCE -> CONFIRMATION_PENDING/REJECTION_PENDING`.

No payment action occurs before Order Service claims the decision.

### 9.2 Duplicate commands and events

All participant operations use business keys, not message IDs alone:

- credit reservation: `orderId`;
- ticket: `orderId` or saga-specific creation key;
- authorization: `orderId` and operation type;
- order decision: expected current state;
- saga start: unique process key composed from order and decision type.

A duplicate must return the previously established outcome or perform a no-op. It must never create a second reservation, ticket, authorization, capture, void, or saga.

### 9.3 Out-of-order events

Handlers must tolerate:

- timeout after acceptance;
- acceptance after timeout;
- duplicate acceptance;
- explicit rejection after timeout;
- delayed payment replies after saga recovery.

State guards classify these as stale events and acknowledge them without throwing a poison-message loop.

## 10. Transaction and Messaging Rules

1. Aggregate mutation and outbox message insertion occur in the same local database transaction.
2. No service updates another service's database.
3. Eventuate command consumers return typed success/failure replies.
4. Domain events are versioned contracts in a shared module or a dedicated API contract module.
5. Consumers must remain backward compatible for at least one event version during rolling deployment.
6. Database migrations must precede code that emits or consumes new states.

## 11. API Behaviour

The create-order API should return an order whose state reflects the asynchronous workflow.

Example accepted response:

```json
{
  "orderId": 123,
  "state": "APPROVAL_PENDING"
}
```

The state later becomes `AWAITING_RESTAURANT_ACCEPTANCE`, `APPROVED`, or `REJECTED`.

Rejection details should expose a stable code and a user-safe message:

```json
{
  "orderId": 123,
  "state": "REJECTED",
  "rejectionCode": "MENU_PRICE_CHANGED",
  "message": "The restaurant menu changed. Review the latest price and try again."
}
```

Internal payment or infrastructure errors must not leak sensitive provider details.

## 12. Observability

Structured logs and metrics must include:

- `orderId`
- `sagaId`
- `consumerId`
- `restaurantId`
- `ticketId`
- `authorizationId`
- command/event type
- idempotency key
- previous and next state
- failure code

Required metrics:

- checkout started/completed/rejected;
- menu validation failure by reason;
- credit reservation conflict or insufficient credit;
- payment authorization/capture/void outcome;
- restaurant acceptance latency;
- timeout count;
- stale/duplicate decision event count;
- saga compensation count;
- saga age and retry count.

## 13. Security and Data Handling

- Do not store raw card numbers or CVV in FTGO databases or events.
- Payment commands carry only provider tokens or references.
- Logs must exclude sensitive payment tokens and personal data.
- Kitchen endpoints require restaurant-scoped authorization before accept/reject.
- A restaurant user may act only on tickets owned by that restaurant.

## 14. Testing Strategy

### 14.1 Unit tests

Consumer:

- reserve succeeds when credit is available;
- reserve fails when insufficient;
- duplicate reserve is idempotent;
- concurrent reserves cannot exceed limit;
- commit and release transitions;
- duplicate commit/release;
- invalid state transitions.

Restaurant:

- exact menu snapshot succeeds;
- changed version, price, availability, quantity, restaurant state fail with typed codes.

Kitchen:

- accept/reject state transitions;
- timeout transition;
- accept versus timeout race;
- duplicate endpoint calls;
- event contents.

Accounting:

- authorize, capture, void and refund transitions;
- duplicate operation request IDs;
- capture after void rejected;
- void after capture rejected or handled by explicit refund policy.

Order:

- all state transitions and semantic locks;
- stale event handling;
- saga command construction and compensation ordering.

### 14.2 Integration tests

Use Testcontainers with production-like MySQL and Kafka/Eventuate infrastructure. Do not mock the message producer/consumer for acceptance tests.

Required scenarios:

1. happy checkout then restaurant accept:
   - menu validated;
   - credit `RESERVED -> COMMITTED`;
   - payment `AUTHORIZED -> CAPTURED`;
   - ticket `AWAITING_ACCEPTANCE -> ACCEPTED`;
   - order `AWAITING_RESTAURANT_ACCEPTANCE -> APPROVED`.

2. menu price changed:
   - order rejected;
   - no credit reservation, ticket, or authorization remains.

3. insufficient consumer credit:
   - order rejected;
   - no ticket or authorization remains.

4. payment authorization denied:
   - ticket compensated/cancelled;
   - credit released;
   - order rejected.

5. restaurant explicit rejection:
   - authorization voided;
   - credit released;
   - order rejected.

6. restaurant timeout:
   - timeout event emitted once effectively;
   - authorization voided;
   - credit released;
   - order rejected.

7. accept and timeout race:
   - exactly one terminal business outcome;
   - never both capture and void;
   - no duplicate saga side effects.

8. service restart during every saga boundary:
   - saga resumes;
   - final state converges;
   - no duplicate financial or credit effect.

9. duplicate Kafka delivery:
   - all handlers remain idempotent.

10. fresh stack twice:
    - database migrations and startup are repeatable;
    - CDC/outbox relay publishes and consumes real messages.

## 15. Migration and Compatibility Plan

1. Add new enum values and tables with backward-compatible migrations.
2. Deploy consumers capable of reading new event versions before producers emit them.
3. Deploy Restaurant, Consumer, Kitchen, and Accounting participant capabilities.
4. Deploy Order Service saga changes last.
5. Guard the new workflow with configuration:

```yaml
ftgo:
  order-flow:
    restaurant-confirmation-enabled: true
```

6. Existing orders in old states continue using their original lifecycle.
7. Rollback disables new saga starts but keeps consumers available to complete in-flight workflows.

## 16. Delivery Structure

Implementation should be performed on `agent/phase-02-core-order-flow`, created directly from `dev`.

Recommended commit sequence:

1. shared command/reply/event contracts;
2. Restaurant authoritative menu validation;
3. Consumer credit reservation ledger;
4. Accounting payment lifecycle and idempotency;
5. Kitchen reject/timeout lifecycle;
6. Order state machine and saga split;
7. cross-service integration tests and runtime configuration;
8. documentation and task checklist updates.

Each commit must compile and keep its affected module tests green where practical. The final branch must pass the full Gradle build and real fresh-stack acceptance gates before a pull request is marked ready.

## 17. Acceptance Criteria

The design is implemented only when all statements below are true:

- An order cannot be approved using stale or client-trusted menu pricing.
- Concurrent orders cannot reserve more than a consumer's credit limit.
- A credit reservation is unique per order and survives restart.
- Payment authorization can be voided before capture.
- Payment is captured only after restaurant acceptance.
- Restaurant rejection or a five-minute default timeout releases all temporary resources.
- Accept/timeout races produce exactly one outcome.
- Duplicate commands and events produce no duplicate business effects.
- All saga and outbox paths work through real MySQL and Kafka/Eventuate infrastructure.
- The full repository build and fresh-stack tests pass from a clean environment.

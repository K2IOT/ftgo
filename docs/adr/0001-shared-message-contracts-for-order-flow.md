# Shared message contracts for order flow

## Context

Order flow spans Order, Consumer, Kitchen, Accounting, Delivery, and Order History. Eventuate command dispatch and downstream event projection depend on exact message type and payload compatibility, so package-local duplicates create runtime drift even when the class names look the same.

The domain ownership lines are:

- Order Service owns order facts such as order identity, consumer, restaurant, totals, delivery request details, ticket ID, and authorization ID.
- Kitchen Service owns whether food preparation has begun.
- Accounting owns Payment Authorization facts.
- Delivery owns delivery execution, but pickup-location ownership remains outside Order Service.
- Order History consumes cross-service events to build a read model; it does not own the source facts.

## Decision

Saga commands, saga replies, and integration events that cross service boundaries must use canonical shared contract classes rather than package-local duplicates. This includes messages consumed by Delivery or Order History. It excludes local REST request/response DTOs and in-process commands used only inside one service.

Any command serialized and sent through an Eventuate channel is a shared message contract, even when the destination is Order Service itself. Only saga steps executed in-process through `invokeLocal(...)` may use package-local command/data shapes.

The `common` module contains all cross-service order-flow wire contracts, grouped by domain concept and message role. Order-owned commands, replies, and events may live under `common.orderflow.*`; Ticket, Delivery, and Payment Authorization events consumed outside their owning service may use their own shared subpackages such as `common.ticket.events`, `common.delivery.events`, and `common.accounting.events`. Package placement does not transfer business ownership; it only makes the serialized contract explicit and reusable.

## Order And Delivery

Delivery creation is triggered by **Order Approved**. Order Approved may carry the delivery address and delivery time because those are delivery request details owned by Order Service.

Pickup-location ownership remains outside Order Service. Delivery should resolve or maintain pickup information using the restaurant reference instead of requiring Order Service to publish data it does not own.

## Cancel And Revise

Order Service may start a cancel or revise saga by placing the Order in a pending state, but Kitchen rejects the Ticket command if Preparation has already begun; the saga then compensates Order back to its approved state.

Kitchen refusal must be represented by canonical business refusal replies in `common`, such as Ticket cancellation rejected or Ticket revision rejected with a stable reason code like `PREPARATION_ALREADY_STARTED`, rather than relying on generic failure strings.

If Kitchen refuses cancel or revise because Preparation has begun, Order Service restores the Order to its approved state without publishing a new Order integration event. The refusal is saga outcome/control flow, not an Order state change for Order History, unless the product later introduces rejected-attempt history as domain language.

Cancel order flow keeps the payment reversal after Kitchen has accepted the cancellation request. This prevents releasing funds for an order that Kitchen can no longer cancel because Preparation has already begun.

Revision flow treats payment adjustment as replacing the previous authorization with a new authorization. Kitchen stores the proposed Ticket line-item changes when it accepts the begin-revision command; the confirm-revision command identifies the Ticket and does not repeat the revised payload. Order Service must store the new authorization reference when the revision is confirmed so later cancel or revise flows act on the current authorization.

## Payment Authorization

Order Service owns the relationship between an Order and its current authorization reference. Order History should derive authorization-approved status for an Order from Order Approved rather than requiring Accounting events to carry an Order identifier.

Payment Authorization commands should keep Accounting language in their interface. Authorize-card commands use a generic idempotency request ID rather than an Order ID; Order Service may derive that request ID from the Order, but Accounting should not need an Order-specific field to authorize funds.

Revision commands must also carry a caller-provided idempotency request ID. Accounting must not generate revision request IDs from wall-clock time because saga retries must resolve to the same Payment Authorization result.

## Order History

Order History should consume canonical shared integration events only. Temporary migration tests may cover old package-local event shapes while code is being moved, but runtime compatibility branches for old local DTOs should not remain indefinitely because they widen the message interface the ADR is trying to narrow.

## Event Names

When moving package-local event classes into `common`, preserve existing emitted event type names. Event type names are part of the wire contract, even when suffix usage is inconsistent. Renaming event types requires a separate explicit event-version migration.

## Consequences

This decision accepts tighter compile-time coupling in exchange for a runnable, explicit cross-service contract. It should reduce message drift, remove package-local duplicate command and event classes, and make downstream projection behavior easier to test.

Contract tests must enforce this decision. Tests should verify that Eventuate channel commands and consumed integration events use canonical classes from `common`, and should include focused serialization tests for shared contracts. This prevents package-local duplicates from reappearing silently as the Order flow evolves.

This repository treats shared-contract migrations as coordinated monorepo changes. When moving package-local contracts into `common`, update all senders and receivers in the same change and rely on contract tests. Do not add long-lived compatibility wrappers unless the services later need to support independent rolling deployments.

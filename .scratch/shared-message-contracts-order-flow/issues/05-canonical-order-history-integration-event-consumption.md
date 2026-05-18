Status: done

# Canonical Order History integration event consumption

## Parent

.scratch/shared-message-contracts-order-flow/PRD.md

## What to build

Make Order History consume canonical shared integration events only for the Order flow. The completed slice should remove runtime compatibility branches for old package-local DTOs, preserve emitted event type names, and derive authorization-approved status from **Order Approved** rather than from Accounting events carrying an **Order** identifier.

Order History should continue to reflect durable **Order**, **Ticket**, **Payment Authorization**, and **Delivery** facts, while refused cancel/revise attempts remain saga control flow unless separate domain language is introduced later.

## Acceptance criteria

- [x] Order History consumes canonical shared integration event contracts for the Order flow.
- [x] Runtime compatibility branches for old package-local event DTOs are removed.
- [x] Current emitted event type names are preserved.
- [x] Authorization-approved status for an **Order** is derived from **Order Approved**.
- [x] Accounting events are not required to carry an **Order** identifier for Order History correlation.
- [x] Projection tests cover **Order Approved**, Ticket events, Delivery events, and **Order** revision/cancellation behavior through shared contracts.

## Blocked by

- .scratch/shared-message-contracts-order-flow/issues/01-canonical-order-approved-to-delivery-contract.md
- .scratch/shared-message-contracts-order-flow/issues/02-shared-ticket-cancellation-contracts-and-refusals.md
- .scratch/shared-message-contracts-order-flow/issues/03-shared-ticket-revision-contracts-with-kitchen-owned-pending-changes.md
- .scratch/shared-message-contracts-order-flow/issues/04-shared-payment-authorization-reverse-and-revise-contracts.md

## Completed notes

- Updated Order History projection consumption to rely on canonical shared Order-flow contracts (`OrderCreated`, `OrderApproved`, `OrderRejected`, `OrderCancelled`, `OrderRevised`) and removed runtime compatibility branches for package-local `*Event` order DTOs.
- Preserved emitted event type name expectations by continuing to consume current producer event-type headers (`OrderCreated`, `OrderApproved`, `OrderCancelled`, `OrderRevised`, `TicketAcceptedEvent`, `TicketReadyEvent`, `DeliveryPickedUpEvent`, `DeliveryDeliveredEvent`).
- Changed projection behavior so authorization-approved state is set from `OrderApproved` processing (`authorizationStatus=APPROVED`) instead of correlating `CardAuthorizedEvent` by `orderId`.
- Kept account-topic consumption idempotent while ignoring authorization-state mutation from `CardAuthorizedEvent`.
- Updated projection unit tests in `order-history-service` to assert shared-order contract handling and the new authorization-state source.

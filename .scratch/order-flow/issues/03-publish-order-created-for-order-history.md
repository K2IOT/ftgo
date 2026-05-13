# Publish Order Created for Order History

Status: ready-for-agent

## What to build

Publish an Order Created event when a new Order is durably accepted into the order workflow, and project it into Order History as the base read-model record. Order Created means the order entered the workflow in an approval-pending state, not that fulfillment is ready.

## Acceptance criteria

- [ ] Order Service writes the Order and the Order Created outbox entry in the same transaction before starting the Create Order saga.
- [ ] Order Created is part of the shared event contract and contains the order facts needed to create the history record.
- [ ] Order History creates an initial record from Order Created and can later update it from Order Approved, Rejected, Cancelled, or Revised events.
- [ ] Duplicate Order Created delivery is idempotent in Order History.
- [ ] Tests cover the initial projection and a later approval update against the same record.

## Blocked by

- `.scratch/order-flow/issues/02-use-shared-create-order-contracts.md`

## Comments

Created from resolved domain language in `CONTEXT.md`: Order Created is accepted into workflow; Order Approved is cross-service approval complete.

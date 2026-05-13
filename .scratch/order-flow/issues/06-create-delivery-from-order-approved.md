# Create Delivery from Order Approved

Status: done

## What to build

Make Delivery creation consume the canonical Order Approved event without requiring Order Service to publish restaurant pickup data. Order Service publishes the order facts it owns; Delivery resolves or maintains pickup information using the restaurant reference before creating the Delivery.

## Acceptance criteria

- [x] Order Approved is a shared event contract used by Order Service, Delivery Service, and Order History.
- [x] The Order Approved event contains order-owned facts needed downstream, including restaurant reference and requested delivery details, but does not make Order Service own pickup-location data.
- [x] Delivery Service ignores non-Order Approved events on the Order event topic.
- [x] Delivery Service creates one Delivery per approved Order idempotently.
- [x] Tests cover Delivery creation from Order Approved and no-op behavior for unrelated Order events.

## Blocked by

- `.scratch/order-flow/issues/02-use-shared-create-order-contracts.md`
- `.scratch/order-flow/issues/03-publish-order-created-for-order-history.md`

## Comments

Created from ADR 0001. Delivery begins after Order Approved; pickup-location ownership remains outside Order Service.

Implemented via shared `net.ftgo.common.orderflow.events.OrderApproved`, Delivery Service pickup-address resolution by restaurant reference, and idempotent delivery creation tests.

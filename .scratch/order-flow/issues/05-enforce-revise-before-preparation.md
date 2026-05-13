# Enforce revision before Preparation begins

Status: done

## What to build

Make Revise Order saga enforce the rule that an Order may be revised only before kitchen Preparation begins, and make payment revision replace the old payment authorization with a new current authorization reference on the Order.

## Acceptance criteria

- [x] Revise Order saga has registered local step handlers and performs a real Order state transition to revision pending before participant work.
- [x] Kitchen accepts revision only while the Ticket has not begun Preparation.
- [x] If Kitchen rejects revision, payment authorization is not changed and Order returns to approved with its original line items and authorization reference.
- [x] If Accounting revises payment, the saga captures the new authorization reference and stores it on Order when revision is confirmed.
- [x] Tests cover successful revision, rejection after Preparation has begun, and subsequent cancel using the current authorization reference.

## Blocked by

- `.scratch/order-flow/issues/02-use-shared-create-order-contracts.md`

## Comments

Created from ADR 0001. Revision replaces the previous payment authorization, so stale authorization IDs must not survive on the Order.

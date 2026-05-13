# Enforce cancellation before Preparation begins

Status: done

## What to build

Make Cancel Order saga enforce the rule that an Order may be cancelled only before kitchen Preparation begins. Order Service should place the Order in a cancellation-pending state before participant work, Kitchen should reject cancellation if Preparation has begun, and the saga should compensate Order back to approved when Kitchen rejects.

## Acceptance criteria

- [x] Cancel Order saga performs a real Order state transition to cancellation pending before sending Kitchen or Accounting commands.
- [x] Kitchen accepts cancellation only while the Ticket has not begun Preparation.
- [x] If Kitchen rejects cancellation, payment authorization is not reversed and Order returns to approved.
- [x] If Kitchen accepts cancellation, payment authorization is reversed, Kitchen confirms ticket cancellation, and Order becomes cancelled.
- [x] Tests cover both allowed cancellation and rejection after Preparation has begun.

## Blocked by

- `.scratch/order-flow/issues/02-use-shared-create-order-contracts.md`

## Comments

Created from `CONTEXT.md` and ADR 0001. Kitchen owns whether Preparation has begun; Order uses saga compensation instead of keeping a trusted copy of Ticket state.

Implemented with TDD on 2026-05-13. Cancel Order now locks the Order with a real local saga transition before Kitchen work, Kitchen rejects `BeginCancelTicketCommand` once Preparation has begun, saga rejection compensates Order before Accounting reversal, and focused Kitchen/Order saga tests cover accepted and rejected cancellation paths.

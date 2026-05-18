Status: done

# Shared Ticket revision contracts with Kitchen-owned pending changes

## Parent

.scratch/shared-message-contracts-order-flow/PRD.md

## What to build

Make Ticket revision use canonical shared contracts across Order Service and Kitchen Service. The completed slice should prove that begin-revision carries proposed Ticket line-item changes, Kitchen stores those pending changes, and confirm-revision identifies the **Ticket** without repeating the revised payload.

Kitchen remains the owner of whether **Preparation** has begun and must refuse revision through a typed shared business reply when revision is no longer allowed.

## Acceptance criteria

- [x] Order Service and Kitchen Service use canonical shared Ticket revision command and reply contracts.
- [x] Begin-revision carries proposed Ticket line-item changes and Kitchen stores them as pending changes.
- [x] Confirm-revision identifies only the **Ticket** and does not repeat revised line-item payload details.
- [x] Kitchen refusal uses a typed shared business reply with a stable reason code such as `PREPARATION_ALREADY_STARTED`.
- [x] When **Preparation** has begun, the revise saga restores the **Order** to approved state without publishing a new **Order** integration event.
- [x] Tests cover accepted revision, refusal after **Preparation**, and confirmation using the Kitchen-owned pending changes.

## Blocked by

None - can start immediately

## Completed notes

- Added canonical shared revise command contracts in `common`:
  - `common/src/main/java/net/ftgo/common/orderflow/commands/BeginReviseTicketCommand.java`
  - `common/src/main/java/net/ftgo/common/orderflow/commands/ConfirmReviseTicketCommand.java`
  - `common/src/main/java/net/ftgo/common/orderflow/commands/UndoReviseTicketCommand.java`
- Added typed shared business refusal reply:
  - `common/src/main/java/net/ftgo/common/orderflow/replies/TicketRevisionRefused.java`
  - Kitchen `handleBeginReviseTicket()` now returns typed failure with reason code `PREPARATION_ALREADY_STARTED` when preparation has begun.
- Removed duplicate service-local revise command classes from `order-service` and `kitchen-service`.
- Implemented Kitchen-owned pending revision storage:
  - `Ticket.beginRevise()` stores pending revised line items on the Ticket aggregate.
  - `confirmPendingRevise()` applies pending changes so confirm command only needs `ticketId`.
  - Added `PendingTicketLineItem` entity and Flyway migration:
    - `kitchen-service/src/main/java/net/ftgo/kitchen/domain/PendingTicketLineItem.java`
    - `kitchen-service/src/main/resources/db/migration/V2__add_ticket_pending_revision_line_items.sql`
- Added/updated tests:
  - `order-service/src/test/java/net/ftgo/order/saga/ReviseOrderSagaSharedContractTest.java`
  - `order-service/src/test/java/net/ftgo/order/saga/ReviseOrderSagaLocalStepsContractTest.java`
  - `order-service/src/test/java/net/ftgo/order/saga/ReviseOrderSagaTest.java`
  - `kitchen-service/src/test/java/net/ftgo/kitchen/messaging/KitchenServiceCommandHandlersTest.java`
  - `kitchen-service/src/test/java/net/ftgo/kitchen/KitchenServiceIntegrationTest.java`

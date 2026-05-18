Status: done

# Shared Ticket cancellation contracts and refusal replies

## Parent

.scratch/shared-message-contracts-order-flow/PRD.md

## What to build

Make Ticket cancellation use canonical shared contracts across Order Service and Kitchen Service. The completed slice should prove that cancel-related commands and replies have one shared wire shape, that Kitchen refuses cancellation with a typed business refusal when **Preparation** has begun, and that Order Service compensates the **Order** back to approved state without publishing a new **Order** integration event for the refused attempt.

The cancel flow must preserve the ordering where **Payment Authorization** reversal happens only after Kitchen has accepted cancellation.

## Acceptance criteria

- [x] Order Service and Kitchen Service use canonical shared Ticket cancellation command and reply contracts.
- [x] Kitchen refusal uses a typed shared business reply with a stable reason code such as `PREPARATION_ALREADY_STARTED`.
- [x] When **Preparation** has begun, the cancel saga restores the **Order** to approved state.
- [x] A refused cancellation does not publish a new **Order** integration event for Order History.
- [x] Tests prove **Payment Authorization** reversal remains after Kitchen accepts cancellation.
- [x] Command-handler and saga tests cover accepted cancellation and refusal after **Preparation**.

## Blocked by

None - can start immediately

## Completed notes

- Added shared canonical cancel command contracts in `common`:
  - `common/src/main/java/net/ftgo/common/orderflow/commands/BeginCancelTicketCommand.java`
  - `common/src/main/java/net/ftgo/common/orderflow/commands/ConfirmCancelTicketCommand.java`
  - `common/src/main/java/net/ftgo/common/orderflow/commands/UndoCancelTicketCommand.java`
- Added typed shared business refusal reply:
  - `common/src/main/java/net/ftgo/common/orderflow/replies/TicketCancellationRefused.java`
  - Kitchen `handleBeginCancelTicket()` now returns a typed failure reply with reason code `PREPARATION_ALREADY_STARTED` when preparation has begun.
- Removed duplicate service-local cancel command classes from `order-service` and `kitchen-service` so Order and Kitchen use one shared wire shape.
- Added/updated tests:
  - `order-service/src/test/java/net/ftgo/order/saga/CancelOrderSagaSharedContractTest.java`
  - `order-service/src/test/java/net/ftgo/order/saga/CancelOrderSagaLocalStepsContractTest.java`
  - `kitchen-service/src/test/java/net/ftgo/kitchen/messaging/KitchenServiceCommandHandlersTest.java`
  - `order-service/src/test/java/net/ftgo/order/saga/CancelOrderSagaTest.java`

Status: done

# Shared Payment Authorization reverse and revise contracts

## Parent

.scratch/shared-message-contracts-order-flow/PRD.md

## What to build

Make **Payment Authorization** reverse and revise messages use canonical shared contracts across Order Service and Accounting. The completed slice should preserve Accounting language in the shared interface, keep authorize-card behavior based on a generic idempotency request ID, and make revision retries deterministic by requiring the caller to provide a stable idempotency request ID.

This slice does not fix unrelated Accounting persistence mapping issues.

## Acceptance criteria

- [x] Order Service and Accounting use canonical shared reverse and revise **Payment Authorization** command contracts.
- [x] The shared contracts use Accounting language and do not require Accounting to accept an Order-specific field to authorize funds.
- [x] Revision commands carry a caller-provided idempotency request ID.
- [x] Accounting no longer generates revision request IDs from wall-clock time.
- [x] Tests prove repeated revision retries with the same request ID resolve to the same **Payment Authorization** result.
- [x] Existing authorize-card semantics continue to use a generic idempotency request ID.

## Blocked by

None - can start immediately

## Completed notes

- Added canonical shared payment command contracts in `common`:
  - `common/src/main/java/net/ftgo/common/orderflow/commands/ReverseAuthorizationCommand.java`
  - `common/src/main/java/net/ftgo/common/orderflow/commands/ReviseAuthorizationCommand.java`
- Removed duplicate reverse/revise command classes from `order-service` and `accounting-service`.
- Updated `CancelOrderSaga` and `ReviseOrderSaga` to use shared payment commands.
- Added caller-provided idempotency request ID for payment revision:
  - `ReviseOrderSagaData` now carries `paymentRevisionRequestId`.
  - `OrderService.reviseOrder()` now computes a stable deterministic request ID and sets it in saga data.
  - `ReviseOrderSaga` passes that request ID to `ReviseAuthorizationCommand`.
- Updated Accounting command handling:
  - `AccountingServiceCommandHandlers.handleReviseAuthorization()` now uses command `requestId` directly.
  - Removed wall-clock generated request IDs (`System.currentTimeMillis`) from revise flow.
- Added/updated tests:
  - `order-service/src/test/java/net/ftgo/order/saga/PaymentAuthorizationSharedContractTest.java`
  - `accounting-service/src/test/java/net/ftgo/accounting/messaging/AccountingServiceCommandHandlersContractTest.java`
  - `order-service/src/test/java/net/ftgo/order/saga/ReviseOrderSagaTest.java`
  - `order-service/src/test/java/net/ftgo/order/saga/CancelOrderSagaTest.java`
  - `order-service/src/test/java/net/ftgo/order/saga/TestParticipantConfiguration.java`

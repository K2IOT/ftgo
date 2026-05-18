Status: done

# Repo-wide shared contract guardrails

## Parent

.scratch/shared-message-contracts-order-flow/PRD.md

## What to build

Add repo-wide guardrail tests that enforce ADR-0001 after the shared-contract migration is in place. The completed slice should fail when cross-service Eventuate channel commands or consumed integration events use package-local contract classes instead of canonical common contracts, while allowing local REST DTOs and true in-process saga data to remain local.

This is intentionally a final slice so it can scan the completed migration without false positives from work still in progress.

## Acceptance criteria

- [x] Tests fail when an Eventuate channel command uses a package-local cross-service contract class.
- [x] Tests fail when a consumed integration event uses a package-local DTO instead of a canonical shared contract.
- [x] Tests allow local REST request/response DTOs to remain outside common.
- [x] Tests allow saga data and command shapes to remain local only when they are executed in-process and are not serialized through Eventuate.
- [x] Focused serialization tests cover the canonical shared contracts introduced by the migration.
- [x] Current emitted event type names are verified and protected against accidental renaming.

## Blocked by

- .scratch/shared-message-contracts-order-flow/issues/01-canonical-order-approved-to-delivery-contract.md
- .scratch/shared-message-contracts-order-flow/issues/02-shared-ticket-cancellation-contracts-and-refusals.md
- .scratch/shared-message-contracts-order-flow/issues/03-shared-ticket-revision-contracts-with-kitchen-owned-pending-changes.md
- .scratch/shared-message-contracts-order-flow/issues/04-shared-payment-authorization-reverse-and-revise-contracts.md
- .scratch/shared-message-contracts-order-flow/issues/05-canonical-order-history-integration-event-consumption.md

## Completed notes

- Added repo-wide ADR-0001 guardrail tests in `common`:
  - `common/src/test/java/net/ftgo/common/orderflow/SharedContractGuardrailsTest.java`
  - Enforces that Eventuate-path code does not reference removed package-local contract classes or legacy order event DTO mapping branches.
  - Verifies local REST DTOs (`order-service` API request shapes) remain service-local.
  - Verifies local in-process saga data (`ReviseOrderSagaData`) remains service-local.
  - Verifies removed legacy contract class files do not reappear outside `common`.
- Added focused shared-contract serialization coverage:
  - `common/src/test/java/net/ftgo/common/orderflow/OrderFlowSharedContractsSerializationTest.java`
  - Covers shared command/reply contracts from issues 02-04:
    - `ReverseAuthorizationCommand`
    - `ReviseAuthorizationCommand`
    - `BeginReviseTicketCommand`
    - `ConfirmReviseTicketCommand`
    - `BeginCancelTicketCommand`
    - `TicketCancellationRefused`
    - `TicketRevisionRefused`
- Extended emitted event-type protection tests for Order outbox:
  - `order-service/src/test/java/net/ftgo/order/messaging/DomainEventPublisherContractTest.java`
  - Added explicit event type assertions for `OrderCreated`, `OrderCancelled`, and `OrderRevised` (existing coverage already verified `OrderApproved`).

Status: ready-for-agent

# PRD: Shared Message Contracts For Order Flow

## Problem Statement

The Order flow currently depends on cross-service messages that are partly canonical and partly duplicated inside individual services. This creates drift between senders, receivers, and downstream projections: the same business message may exist as multiple package-local classes, and a sender can silently disagree with a receiver about required fields.

This is already visible in cancel/revise and Order History paths. The team needs the Order, Ticket, Payment Authorization, Delivery, and Order History flows to share one explicit set of wire contracts so saga dispatch, event projection, and retry behavior remain runnable and testable.

## Solution

Create canonical shared message contracts in the common module for all cross-service Order flow messages. Update senders, receivers, and projections to use those shared contracts directly. Keep local REST DTOs and true in-process saga data local, but treat every serialized Eventuate channel command, saga reply, and consumed integration event as a shared contract.

The implementation should preserve existing emitted event type names while moving classes, avoid long-lived compatibility branches, and add contract tests that prevent package-local duplicates from reappearing.

## User Stories

1. As an Order Service maintainer, I want all cross-service Order flow commands to use canonical contracts, so that senders and receivers cannot drift silently.
2. As a Kitchen Service maintainer, I want Ticket cancel and revise commands to have one shared shape, so that saga messages deserialize consistently.
3. As an Accounting maintainer, I want Payment Authorization commands to preserve Accounting language, so that Accounting does not need Order-specific fields to authorize funds.
4. As an Order History maintainer, I want to consume canonical shared integration events only, so that projections do not need multiple local DTO branches for the same fact.
5. As a Delivery maintainer, I want Order Approved to carry delivery request details but not pickup-location details, so that Delivery can create Delivery records while respecting ownership of restaurant pickup information.
6. As a saga author, I want any command sent through an Eventuate channel to be treated as a shared contract, so that the rule is mechanical and easy to enforce.
7. As a saga author, I want true in-process saga steps to keep local command/data shapes, so that internal implementation details do not leak into common contracts.
8. As a Kitchen Service maintainer, I want Kitchen to own whether Preparation has begun, so that cancel and revise requests are refused by the service that knows the real Ticket state.
9. As an Order Service maintainer, I want Kitchen refusal replies to use stable reason codes, so that compensation behavior does not depend on parsing generic failure strings.
10. As an Order History user, I want refused cancel/revise attempts not to appear as Order state changes, so that the read model reflects durable Order facts rather than saga control flow.
11. As a customer support user, I want Order History to show authorization-approved status from Order Approved, so that it reflects the Order-owned relationship to the current Payment Authorization.
12. As an Accounting maintainer, I want authorize-card commands to use a generic idempotency request ID, so that Payment Authorization remains reusable outside Order-specific naming.
13. As a saga retry mechanism, I want revision commands to carry a stable caller-provided idempotency request ID, so that repeated retries resolve to the same Payment Authorization result.
14. As a developer moving event classes into common, I want to preserve emitted event type names, so that existing wire names do not change accidentally.
15. As a developer reviewing contracts, I want shared contracts grouped by domain concept and message role, so that ownership remains understandable even when classes live in common.
16. As an agent working on the repo, I want contract tests to fail when package-local cross-service messages appear, so that the ADR remains enforced.
17. As a maintainer, I want the migration to happen as one coordinated monorepo change, so that senders and receivers change together without compatibility wrappers.
18. As a future service maintainer, I want business ownership to remain documented separately from package placement, so that moving wire contracts into common does not imply ownership transfer.
19. As a test author, I want serialization tests for shared contracts, so that payload compatibility is verified without running every service.
20. As a developer changing Order Approved, I want to know which Delivery fields are allowed, so that Order Service does not publish pickup-location information it does not own.
21. As a developer changing Ticket revision, I want Kitchen to store proposed line-item changes after begin-revision, so that confirm-revision does not repeat mutable payload details.
22. As a developer changing Order cancellation, I want Payment Authorization reversal to happen only after Kitchen accepts cancellation, so that funds are not released for an Order that can no longer be cancelled.

## Implementation Decisions

- Build a shared contracts module surface inside common for cross-service Order flow wire contracts.
- Group shared contracts by domain concept and message role: Order-owned commands, replies, and events under order-flow packages; Ticket, Delivery, and Payment Authorization events under their own shared subpackages when consumed outside their owning service.
- Treat any command serialized and sent through an Eventuate channel as a shared message contract, even when the destination is Order Service.
- Keep local REST request/response DTOs out of common.
- Keep saga data and command shapes local only when they are executed in-process and are not serialized through Eventuate.
- Move cancel and revise Ticket commands into common and update Order Service and Kitchen Service to use the same classes.
- Move Payment Authorization reverse/revise commands into common and update Order Service and Accounting to use the same classes.
- Add canonical business refusal replies for Ticket cancellation and Ticket revision, with stable reason codes such as `PREPARATION_ALREADY_STARTED`.
- Keep refused cancel/revise attempts as saga outcome/control flow unless the product later introduces rejected-attempt history as domain language.
- Preserve the cancel flow ordering where Payment Authorization reversal happens after Kitchen has accepted cancellation.
- Make Kitchen store proposed Ticket line-item changes when it accepts begin-revision; confirm-revision identifies the Ticket and does not repeat the revised payload.
- Make Payment Authorization revision commands carry caller-provided idempotency request IDs.
- Keep authorize-card commands generic with a request ID rather than an Order ID.
- Derive authorization-approved status in Order History from Order Approved, not from Accounting events carrying an Order identifier.
- Keep Order Approved carrying delivery address and delivery time because those are Order-owned delivery request details.
- Keep pickup-location ownership outside Order Service; Delivery resolves or maintains pickup information using the restaurant reference.
- Move consumed integration events into common and remove runtime compatibility branches for old package-local DTOs.
- Preserve existing emitted event type names while moving event classes into common.
- Treat this as a coordinated monorepo migration: update senders and receivers in the same change without long-lived compatibility wrappers.
- Do not treat package placement in common as ownership transfer; ownership remains with the domain service described in the ADR.

## Testing Decisions

- Good tests should validate external message behavior: serialized contract shape, command routing compatibility, saga replies, and projection outcomes. They should avoid asserting private helper methods or package layout beyond the explicit rule that cross-service contracts come from common.
- Add shared contract serialization tests for saga commands, saga replies, and integration events.
- Add tests that fail when Eventuate channel commands use package-local contract classes.
- Add tests that fail when consumed integration events use package-local DTOs instead of common contracts.
- Add Kitchen command-handler tests for cancel/revise refusal replies, including `PREPARATION_ALREADY_STARTED`.
- Add revise-flow tests proving begin-revision stores proposed Ticket changes and confirm-revision does not need the revised payload again.
- Add Accounting command-handler tests proving revision retry uses a caller-provided idempotency request ID and returns the same Payment Authorization result.
- Add Order History projection tests proving authorization-approved status comes from Order Approved.
- Add Delivery event-consumer tests proving Delivery creation uses delivery address/time from Order Approved and resolves pickup information from the restaurant reference.
- Prior art exists in the repo’s saga tests, command-handler tests, Order History event-handler tests, Delivery event-consumer tests, and service-level integration tests.

## Out of Scope

- Fixing the Accounting persistence mapping failure around account and authorization storage.
- Introducing independent rolling deployment compatibility for old and new contract packages.
- Renaming existing emitted event type names.
- Changing REST request/response DTO contracts.
- Adding rejected cancel/revise attempts to Order History as user-visible history.
- Moving business ownership from Order, Kitchen, Accounting, Delivery, or Order History into common.
- Changing pickup-location ownership or making Order Service publish restaurant pickup data.
- Reworking the outbox implementation beyond what is needed to use canonical shared event contracts.

## Further Notes

This PRD implements the clarified ADR for shared message contracts in the Order flow. The motivation is not cosmetic package cleanup; it is to make the cross-service contract explicit, runnable, and testable. The most valuable deep module is the shared contract surface in common plus contract tests around it. That module should hide wire-shape complexity behind a small, stable interface for senders, receivers, and projections.

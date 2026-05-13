Status: ready-for-agent

# Shared Payment Authorization reverse and revise contracts

## Parent

.scratch/shared-message-contracts-order-flow/PRD.md

## What to build

Make **Payment Authorization** reverse and revise messages use canonical shared contracts across Order Service and Accounting. The completed slice should preserve Accounting language in the shared interface, keep authorize-card behavior based on a generic idempotency request ID, and make revision retries deterministic by requiring the caller to provide a stable idempotency request ID.

This slice does not fix unrelated Accounting persistence mapping issues.

## Acceptance criteria

- [ ] Order Service and Accounting use canonical shared reverse and revise **Payment Authorization** command contracts.
- [ ] The shared contracts use Accounting language and do not require Accounting to accept an Order-specific field to authorize funds.
- [ ] Revision commands carry a caller-provided idempotency request ID.
- [ ] Accounting no longer generates revision request IDs from wall-clock time.
- [ ] Tests prove repeated revision retries with the same request ID resolve to the same **Payment Authorization** result.
- [ ] Existing authorize-card semantics continue to use a generic idempotency request ID.

## Blocked by

None - can start immediately


Status: ready-for-agent

# Shared Ticket revision contracts with Kitchen-owned pending changes

## Parent

.scratch/shared-message-contracts-order-flow/PRD.md

## What to build

Make Ticket revision use canonical shared contracts across Order Service and Kitchen Service. The completed slice should prove that begin-revision carries proposed Ticket line-item changes, Kitchen stores those pending changes, and confirm-revision identifies the **Ticket** without repeating the revised payload.

Kitchen remains the owner of whether **Preparation** has begun and must refuse revision through a typed shared business reply when revision is no longer allowed.

## Acceptance criteria

- [ ] Order Service and Kitchen Service use canonical shared Ticket revision command and reply contracts.
- [ ] Begin-revision carries proposed Ticket line-item changes and Kitchen stores them as pending changes.
- [ ] Confirm-revision identifies only the **Ticket** and does not repeat revised line-item payload details.
- [ ] Kitchen refusal uses a typed shared business reply with a stable reason code such as `PREPARATION_ALREADY_STARTED`.
- [ ] When **Preparation** has begun, the revise saga restores the **Order** to approved state without publishing a new **Order** integration event.
- [ ] Tests cover accepted revision, refusal after **Preparation**, and confirmation using the Kitchen-owned pending changes.

## Blocked by

None - can start immediately


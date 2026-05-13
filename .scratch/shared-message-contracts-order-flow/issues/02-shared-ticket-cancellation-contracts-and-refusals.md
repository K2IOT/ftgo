Status: ready-for-agent

# Shared Ticket cancellation contracts and refusal replies

## Parent

.scratch/shared-message-contracts-order-flow/PRD.md

## What to build

Make Ticket cancellation use canonical shared contracts across Order Service and Kitchen Service. The completed slice should prove that cancel-related commands and replies have one shared wire shape, that Kitchen refuses cancellation with a typed business refusal when **Preparation** has begun, and that Order Service compensates the **Order** back to approved state without publishing a new **Order** integration event for the refused attempt.

The cancel flow must preserve the ordering where **Payment Authorization** reversal happens only after Kitchen has accepted cancellation.

## Acceptance criteria

- [ ] Order Service and Kitchen Service use canonical shared Ticket cancellation command and reply contracts.
- [ ] Kitchen refusal uses a typed shared business reply with a stable reason code such as `PREPARATION_ALREADY_STARTED`.
- [ ] When **Preparation** has begun, the cancel saga restores the **Order** to approved state.
- [ ] A refused cancellation does not publish a new **Order** integration event for Order History.
- [ ] Tests prove **Payment Authorization** reversal remains after Kitchen accepts cancellation.
- [ ] Command-handler and saga tests cover accepted cancellation and refusal after **Preparation**.

## Blocked by

None - can start immediately


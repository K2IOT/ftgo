# Use shared contracts for Create Order saga

Status: ready-for-agent

## What to build

Make Create Order saga messaging use canonical shared command and reply contracts across Order, Consumer, Kitchen, and Accounting. A created Order should move through consumer verification, ticket creation, payment authorization, ticket approval, and final approval using the same message types in the orchestrator and participants.

## Acceptance criteria

- [ ] Create Order saga commands and replies are defined once in the shared contract module and used by both sending and receiving services.
- [ ] Package-local duplicate command/reply classes are removed or replaced for the Create Order flow.
- [ ] Consumer, Kitchen, and Accounting command dispatchers can deserialize and handle the commands sent by Order Service.
- [ ] Create Order saga tests prove the real participant handler classes accept the same command types the orchestrator sends.
- [ ] The approved Order stores the current Ticket and Payment Authorization references for later cancel/revise flows.

## Blocked by

- `.scratch/order-flow/issues/01-make-order-flow-services-runnable.md`

## Comments

Created from ADR 0001. Eventuate command dispatch depends on exact command type compatibility; test-only participants currently hide package-local contract drift.

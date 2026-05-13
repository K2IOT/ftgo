# Use shared contracts for Create Order saga

Status: ready-for-agent

## What to build

Make Create Order saga messaging use canonical shared command and reply contracts across Order, Consumer, Kitchen, and Accounting. A created Order should move through consumer verification, ticket creation, payment authorization, ticket approval, and final approval using the same message types in the orchestrator and participants.

## Acceptance criteria

- [x] Create Order saga commands and replies are defined once in the shared contract module and used by both sending and receiving services.
- [x] Package-local duplicate command/reply classes are removed or replaced for the Create Order flow.
- [x] Consumer, Kitchen, and Accounting command dispatchers can deserialize and handle the commands sent by Order Service.
- [x] Create Order saga tests prove the real participant handler classes accept the same command types the orchestrator sends.
- [x] The approved Order stores the current Ticket and Payment Authorization references for later cancel/revise flows.

## Blocked by

- `.scratch/order-flow/issues/01-make-order-flow-services-runnable.md`

## Comments

Created from ADR 0001. Eventuate command dispatch depends on exact command type compatibility; test-only participants currently hide package-local contract drift.

Implemented on 2026-05-13. Added canonical Create Order command/reply contracts in `common`, updated Order, Consumer, Kitchen, and Accounting to use those shared types, removed package-local Create Order duplicates, and added a focused Create Order saga contract test against the real participant command handler registrations. Verified the focused saga/handler tests pass; the broader Consumer Service suite still has pre-existing Spring test-context failures from invalid Eventuate configuration excludes.

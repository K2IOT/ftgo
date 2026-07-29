# Remediation 04 — Order API Idempotency Progress

Base `dev` SHA: `2925b7cd2995d3413f8bb831957648b8acad4746`.

## Delivered

- Mandatory `Idempotency-Key` for create, cancel and revise.
- Additive `V10__create_api_idempotency_records.sql` migration.
- Durable unique claim by authenticated consumer, operation and key.
- Canonical SHA-256 request binding that excludes bearer and payment tokens.
- Exact stored JSON/status replay for completed requests.
- Stable `409 IDEMPOTENCY_KEY_CONFLICT` for key reuse with changed payload.
- Shared transaction boundary around idempotency claim/completion, order mutation, outbox and saga creation.
- Cancel and revise replay without starting a second mutation saga.
- MySQL rollback coverage proving failed mutations remove both the business write and claim.
- Twenty-request concurrency coverage proving one committed mutation and nineteen replays.
- Live direct-port and Gateway coverage for missing keys, exact replay and changed-payload conflict.
- Live dropped-client-response retry coverage.
- Live assertions for one order, one OrderCreated outbox event, one CreateOrderSaga and one provider AUTHORIZE operation.
- Existing Core Order Flow, Payment Settlement and Distributed Consistency clients now send idempotency keys for order creation.

## TDD and debugging evidence

- RED run `30434971027`: migration contract failed because `V10__create_api_idempotency_records.sql` did not exist.
- Initial GREEN run `30435523930`: migration and focused idempotency tests passed.
- RED run `30435648724`: an existing `PROCESSING` claim incorrectly executed the mutation again.
- GREEN run `30435791242`: only the winning claim can execute the mutation.
- MySQL run `30437068589`: twenty-way concurrency exposed a real lock deadlock when the winning transaction immediately locked its own inserted row.
- The winner path now executes without re-locking; only insert losers wait on `SELECT ... FOR UPDATE` and replay the committed result.
- Phase 04 test failure was traced to an outdated `OrderControllerIdentityTest` fixture missing the new idempotency service dependency; production authorization logic was unchanged.

## Acceptance coverage

- Create, cancel and revise require a valid key.
- Same key and same canonical request replays without executing the mutation again.
- Same key and changed canonical request returns `409 IDEMPOTENCY_KEY_CONFLICT`.
- A stale/in-flight `PROCESSING` claim cannot execute a second mutation.
- Concurrent MySQL transactions produce one business write and one completed idempotency row.
- A thrown mutation exception rolls back both business data and the idempotency claim.
- A client that drops the first response can retry the same key and receive the committed response.

## Final verification

Final-head workflow evidence is recorded in the pull request after all required workflows complete on one unchanged SHA.

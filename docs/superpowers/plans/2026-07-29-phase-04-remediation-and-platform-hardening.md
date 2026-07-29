# FTGO Phase 04 Remediation and Platform Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove every security, reliability, scaling, lifecycle and repository-governance gap found in the Phase 01–04 review while preserving existing saga and distributed-consistency guarantees.

**Architecture:** Deliver ten serial, independently reviewable PRs. Security hotfixes merge first; API and persistence changes use additive migrations and backward-compatible rollout windows; payment retries move from sleeping inside consumer threads to Kafka-backed redelivery; maintenance jobs are bounded and observable; mainline verification must run on the exact merge SHA.

**Tech Stack:** Java 21, Spring Boot, Spring Security OAuth2 Resource Server, Spring Cloud Gateway, Eventuate Tram/Sagas, Kafka, MySQL 8, ScyllaDB/Cassandra driver paging state, Flyway, Gradle, JUnit 5, Testcontainers, Docker Compose, GitHub Actions.

## Global Constraints

- Base every PR on the latest `dev`; do not implement on `master`.
- Preserve Java 21 throughout the remediation.
- Use test-first development: every defect starts with a failing regression test.
- Apply only additive database migrations; do not drop or rewrite existing production columns in this phase.
- Never trust client-supplied consumer, restaurant, courier, role, audience or identity headers.
- Public routes require the `ftgo-api` audience; internal routes require both `ROLE_SERVICE` and `ftgo-internal`.
- OAuth `scope` values must never become application roles.
- Do not log bearer tokens, payment tokens, raw event payloads, delivery addresses or other PII.
- Do not call `Thread.sleep()` from HTTP, Kafka or Eventuate consumer execution paths.
- Do not perform unbounded `findAll()` scans in scheduled jobs.
- Do not enable destructive retention until the configured retention period exceeds Kafka replay and maximum saga-recovery windows.
- A phase or PR is not complete until required tests pass on the exact final SHA and, after merge, on the exact `dev` merge SHA.

---

## Approach Decision

### Rejected: one remediation mega-PR

A single PR would mix authorization, gateway behavior, database migrations, payment semantics, CI and dependency upgrades. A regression would be difficult to isolate and rollback.

### Rejected: fix only Critical/High findings

This leaves unbounded tables, stale documentation, unsupported dependencies and merge-SHA verification gaps. The repository would remain operationally unsafe.

### Selected: ten serial PRs

1. Order History BOLA/IDOR hotfix.
2. JWT role/audience policy, fail-closed authorization and CORS.
3. Signed and query-bound Order History paging tokens.
4. Public order-mutation idempotency.
5. Gateway identity-header removal, correlation and RFC 9457 convergence.
6. Non-blocking settlement retries through Kafka redelivery.
7. Incremental settlement reconciliation.
8. Outbox and processed-command retention lifecycle.
9. Mainline CI, production configuration and repository hygiene.
10. Supported Spring/Gradle dependency migration.

Do not start PR N+1 until PR N is merged and the `dev` merge SHA passes the required regression gate.

---

## Issue Coverage Matrix

| Review finding | Covered by |
|---|---|
| Order History BOLA/IDOR | PR 1 |
| Wildcard credentialed CORS | PR 2 |
| OAuth scope mapped to role | PR 2 |
| Public/API routes authorized without explicit audience | PR 2 |
| Fail-open `.anyRequest().authenticated()` | PR 2 |
| Forgable/unbound paging token and future-month DoS | PR 3 |
| Missing HTTP idempotency for create/cancel/revise | PR 4 |
| Gateway `X-User-*` filter incompatible with FTGO token | PR 5 |
| Split correlation headers and spoofable audit IP | PR 5 |
| Mixed error schemas/raw exception messages | PR 5 |
| Blocking payment retry | PR 6 |
| Full-table settlement reconciliation | PR 7 |
| Outbox and processed-command tables grow forever | PR 8 |
| CI does not verify the merge SHA | PR 9 |
| Insecure datasource defaults and stale repository state | PR 9 |
| Unsupported Spring stack | PR 10 |

---

### Task 1 / PR 1: Close Order History BOLA/IDOR

**Branch:** `agent/remediation-01-order-history-ownership`

**Files:**
- Create: `order-history-service/src/main/java/net/ftgo/orderhistory/security/OrderHistoryAuthorizationService.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/api/OrderHistoryController.java`
- Test: `order-history-service/src/test/java/net/ftgo/orderhistory/security/OrderHistoryAuthorizationServiceTest.java`
- Test: `order-history-service/src/test/java/net/ftgo/orderhistory/api/OrderHistoryControllerAuthorizationTest.java`
- Test: `e2e-tests/src/test/java/net/ftgo/e2e/OrderHistoryAuthorizationE2ETest.java`
- Modify: `.github/workflows/phase-04-security-api.yml`

**Interfaces:**
- Produces: `OrderHistoryAuthorizationService.requireConsumerAccess(Long, FtgoPrincipal): void`
- Produces: `OrderHistoryAuthorizationService.requireOrderAccess(OrderHistoryRecord, FtgoPrincipal): void`
- Consumes: `PrincipalAccess.require(Authentication): FtgoPrincipal`

- [ ] **Step 1: Write service-level failing ownership tests**

```java
@Test
void consumerCannotReadAnotherConsumersHistory() {
    FtgoPrincipal actor = new FtgoPrincipal("consumer-101", 101L, Set.of(), null,
        Set.of("CONSUMER"), Set.of("ftgo-api"));

    assertThrows(AccessDeniedException.class,
        () -> authorization.requireConsumerAccess(202L, actor));
}

@Test
void consumerCannotReadOrderOwnedByAnotherConsumer() {
    OrderHistoryRecord record = record("9001", 202L);
    assertThrows(AccessDeniedException.class,
        () -> authorization.requireOrderAccess(record, consumer(101L)));
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
./gradlew :order-history-service:test \
  --tests '*OrderHistoryAuthorizationServiceTest' \
  --no-daemon --stacktrace
```

Expected: FAIL because `OrderHistoryAuthorizationService` does not exist.

- [ ] **Step 3: Implement owner-or-admin authorization**

```java
public void requireConsumerAccess(Long requestedConsumerId, FtgoPrincipal principal) {
    if (principal.roles().contains("ADMIN")) return;
    if (principal.consumerId() == null || !principal.consumerId().equals(requestedConsumerId)) {
        throw new AccessDeniedException("Order history access denied");
    }
}

public void requireOrderAccess(OrderHistoryRecord record, FtgoPrincipal principal) {
    requireConsumerAccess(record.getConsumerId(), principal);
}
```

- [ ] **Step 4: Move checks into the controller flow before returning data**

`findOrder()` must load the record, call `requireOrderAccess()`, then return it. `findOrderHistory()` must derive the FTGO principal from `Authentication` and call `requireConsumerAccess()` before constructing query criteria.

- [ ] **Step 5: Add direct-service controller tests**

Cover owner `200`, admin `200`, cross-consumer `403`, missing `consumer_id` `403`, nonexistent order `404`, and ensure the unauthorized request does not invoke `OrderHistoryQueryService`.

- [ ] **Step 6: Add Gateway and direct-port E2E abuse tests**

Create consumer 101 and 202 records, query each route using consumer 101, and assert no response contains consumer 202 order IDs, line items, delivery address or payment status.

- [ ] **Step 7: Run focused and Phase 04 security tests**

```bash
./gradlew :order-history-service:test --no-daemon --stacktrace
./gradlew :e2e-tests:test --tests '*OrderHistoryAuthorizationE2ETest' \
  --no-daemon --stacktrace
python -m unittest deployment.tests.test_phase04_resource_server_contract -v
```

- [ ] **Step 8: Commit**

```bash
git add order-history-service e2e-tests .github/workflows/phase-04-security-api.yml
git commit -m "fix: enforce order history ownership"
```

**PR acceptance:** all cross-tenant reads return `403`; owner/admin behavior remains unchanged; no unauthorized record is serialized.

---

### Task 2 / PR 2: Make JWT, Audience, Route and CORS Policy Fail Closed

**Branch:** `agent/remediation-02-security-policy`

**Files:**
- Modify: `common/src/main/java/net/ftgo/common/security/FtgoJwtAuthenticationConverter.java`
- Create: `common/src/main/java/net/ftgo/common/security/FtgoRoles.java`
- Modify: `common/src/test/java/net/ftgo/common/security/FtgoJwtAuthenticationConverterTest.java`
- Create: `api-gateway/src/main/java/net/ftgo/gateway/config/GatewayCorsProperties.java`
- Modify: `api-gateway/src/main/java/net/ftgo/gateway/config/CorsGatewayConfiguration.java`
- Modify: `api-gateway/src/main/resources/application.yml`
- Modify: `api-gateway/src/main/resources/application-docker.yml`
- Modify: `api-gateway/src/main/resources/application-k8s.yml`
- Test: `api-gateway/src/test/java/net/ftgo/gateway/config/CorsGatewayConfigurationTest.java`
- Modify: all eight `SecurityConfiguration.java` files under `api-gateway`, `order-service`, `consumer-service`, `restaurant-service`, `kitchen-service`, `accounting-service`, `delivery-service`, and `order-history-service`
- Test: every module's existing `SecuritySmokeTest.java`
- Test: `e2e-tests/src/test/java/net/ftgo/e2e/AudienceAndRoleBoundaryE2ETest.java`

**Interfaces:**
- Produces: `FtgoRoles.isKnown(String): boolean`
- Produces configuration: `ftgo.gateway.cors.allowed-origins`, `allowed-methods`, `allow-credentials`, `max-age`

- [ ] **Step 1: Write failing claim-mapping tests**

```java
@Test
void scopeDoesNotGrantAdminRole() {
    AbstractAuthenticationToken auth = convert(jwt(
        "user", List.of("ftgo-api"), Map.of("scope", "openid ADMIN SERVICE")));
    assertFalse(authorityNames(auth).contains("ROLE_ADMIN"));
    assertFalse(authorityNames(auth).contains("ROLE_SERVICE"));
}

@Test
void unknownApplicationRoleIsRejected() {
    assertThrows(BadJwtException.class, () -> convert(jwt(
        "user", List.of("ftgo-api"), Map.of("roles", List.of("SUPERUSER")))));
}
```

- [ ] **Step 2: Run and confirm the tests fail**

```bash
./gradlew :common:test --tests '*FtgoJwtAuthenticationConverterTest' \
  --no-daemon --stacktrace
```

- [ ] **Step 3: Restrict application roles**

Only read application roles from `roles` and `realm_access.roles`. Remove role extraction from `scope` and `authorities`. Allow exactly `CONSUMER`, `RESTAURANT`, `COURIER`, `ADMIN`, and `SERVICE`; reject unknown nonblank values.

- [ ] **Step 4: Write failing audience and unknown-route tests in every service**

For a public endpoint, a token with `ROLE_CONSUMER` plus only `AUD_ftgo-internal` must receive `403`. For an authenticated unknown endpoint, expect `403` from `.denyAll()` rather than accidental access.

- [ ] **Step 5: Replace fail-open defaults**

Every service must explicitly declare liveness/readiness, actuator, internal and public route namespaces and finish with:

```java
.anyRequest().denyAll()
```

Public route managers must require role and `AUD_ftgo-api`; internal route managers must require `ROLE_SERVICE` and `AUD_ftgo-internal`.

- [ ] **Step 6: Write failing CORS tests**

Cover an allowed origin, an unlisted origin, credentialed mode with explicit origins, and startup rejection when credentials are enabled with `*`.

- [ ] **Step 7: Replace wildcard CORS with typed properties**

Production defaults are an empty allowlist and `allow-credentials=false`. Docker/Kubernetes read comma-separated explicit origins from `FTGO_CORS_ALLOWED_ORIGINS`; no profile may default to `*`.

- [ ] **Step 8: Run security regression**

```bash
./gradlew :common:test :api-gateway:test :order-service:test \
  :consumer-service:test :restaurant-service:test :kitchen-service:test \
  :accounting-service:test :delivery-service:test :order-history-service:test \
  --tests '*Security*Test' --no-daemon --stacktrace
```

- [ ] **Step 9: Commit**

```bash
git add common api-gateway order-service consumer-service restaurant-service \
  kitchen-service accounting-service delivery-service order-history-service e2e-tests
git commit -m "fix: make security policy fail closed"
```

**PR acceptance:** scopes cannot grant roles; wrong-audience tokens cannot access public routes; unknown routes are denied; credentialed CORS never uses a wildcard.

---

### Task 3 / PR 3: Sign and Bind Order History Paging Tokens

**Branch:** `agent/remediation-03-order-history-paging`

**Files:**
- Create: `order-history-service/src/main/java/net/ftgo/orderhistory/service/OrderHistoryPagingTokenCodec.java`
- Create: `order-history-service/src/main/java/net/ftgo/orderhistory/config/OrderHistoryPagingProperties.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/service/CassandraOrderHistoryQueryStore.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/service/OrderHistoryQueryCriteria.java`
- Modify: `order-history-service/src/main/resources/application.yml`
- Test: `order-history-service/src/test/java/net/ftgo/orderhistory/service/OrderHistoryPagingTokenCodecTest.java`
- Modify: `order-history-service/src/test/java/net/ftgo/orderhistory/api/OrderHistoryPagingIntegrationTest.java`

**Interfaces:**
- Produces: `String encode(OrderHistoryQueryCriteria criteria, int pageSize, YearMonth month, String driverState, Instant expiresAt)`
- Produces: `DecodedPagingToken decode(String token, OrderHistoryQueryCriteria criteria, int pageSize, Instant now)`
- Configuration: secret from `FTGO_ORDER_HISTORY_PAGING_SECRET`, TTL `PT15M`, maximum bucket scans `24`

- [ ] **Step 1: Write failing codec tests**

Cover valid round-trip, one-byte tampering, expired token, changed consumer ID, changed status/restaurant/since/page size, future month, malformed Base64 and blank signing secret.

- [ ] **Step 2: Implement a versioned HMAC-SHA-256 token**

Payload fields are `version`, `queryFingerprint`, `pageSize`, `month`, `driverState`, and `expiresAt`. Encode as `base64url(payload).base64url(signature)` and compare signatures using `MessageDigest.isEqual`.

- [ ] **Step 3: Bound query execution**

Reject cursor months after the current UTC month. Stop after `max-bucket-scans` even when the requested history range is larger; return the next signed month cursor instead of looping without bound.

- [ ] **Step 4: Replace the existing unsigned Base64 cursor**

`CassandraOrderHistoryQueryStore` may no longer parse or emit raw month/driver state. All cursors pass through `OrderHistoryPagingTokenCodec`.

- [ ] **Step 5: Add integration tests**

Assert tampered/cross-query tokens return stable `400 ORDER_HISTORY_QUERY_INVALID`, and a far-future handcrafted token never causes repository calls.

- [ ] **Step 6: Run tests**

```bash
./gradlew :order-history-service:test \
  --tests '*OrderHistoryPagingTokenCodecTest' \
  --tests '*OrderHistoryPagingIntegrationTest' \
  --no-daemon --stacktrace
```

- [ ] **Step 7: Commit**

```bash
git add order-history-service
git commit -m "fix: sign and bind order history paging tokens"
```

**PR acceptance:** token modification, reuse across consumers/filters and future-month cursors are rejected before Cassandra access; each request executes at most 24 bucket queries.

---

### Task 4 / PR 4: Add HTTP Idempotency for Order Mutations

**Branch:** `agent/remediation-04-order-api-idempotency`

**Files:**
- Create: `order-service/src/main/resources/db/migration/V10__create_api_idempotency_records.sql`
- Create: `order-service/src/main/java/net/ftgo/order/idempotency/ApiIdempotencyRecord.java`
- Create: `order-service/src/main/java/net/ftgo/order/idempotency/ApiIdempotencyStore.java`
- Create: `order-service/src/main/java/net/ftgo/order/idempotency/JdbcApiIdempotencyStore.java`
- Create: `order-service/src/main/java/net/ftgo/order/idempotency/OrderMutationIdempotencyService.java`
- Modify: `order-service/src/main/java/net/ftgo/order/api/OrderController.java`
- Modify: `order-service/src/main/java/net/ftgo/order/service/OrderService.java`
- Test: `order-service/src/test/java/net/ftgo/order/idempotency/JdbcApiIdempotencyStoreTest.java`
- Test: `order-service/src/test/java/net/ftgo/order/api/OrderControllerIdempotencyTest.java`
- Test: `e2e-tests/src/test/java/net/ftgo/e2e/OrderMutationIdempotencyE2ETest.java`

**Interfaces:**
- Header: mandatory `Idempotency-Key` for create, cancel and revise
- Produces: `IdempotentResult<T> execute(Long consumerId, String operation, String key, byte[] requestHash, Supplier<T> mutation)`
- Conflict: same key and changed request hash returns `409 IDEMPOTENCY_KEY_CONFLICT`

- [ ] **Step 1: Add the migration and migration test**

The table has unique key `(consumer_id, operation, idempotency_key)`, request SHA-256, state `PROCESSING|COMPLETED`, HTTP status, response JSON, resource ID, created/updated timestamps and expiry timestamp.

- [ ] **Step 2: Write failing duplicate and conflict tests**

```java
@Test
void duplicateCreateReturnsOriginalOrderWithoutStartingSecondSaga() { /* assert one order and one saga */ }

@Test
void sameKeyWithDifferentBodyReturnsConflict() { /* assert 409 and no second mutation */ }
```

- [ ] **Step 3: Implement transactional claim/replay**

Use `INSERT IGNORE`, then `SELECT ... FOR UPDATE`. The loser of a concurrent insert waits for the winner transaction, validates the request hash and replays the completed response. The idempotency record, order mutation, outbox insert and saga creation must commit or roll back together.

- [ ] **Step 4: Hash canonical server inputs**

Create hashes from authenticated consumer ID, operation name, order ID where applicable, and canonical JSON body. Do not include volatile timestamps or bearer tokens.

- [ ] **Step 5: Apply the service to all order mutations**

Operations are `CREATE_ORDER`, `CANCEL_ORDER:{orderId}`, and `REVISE_ORDER:{orderId}`. Repeated cancel/revise requests return the first successful response instead of starting another saga.

- [ ] **Step 6: Add connection-loss and concurrency E2E tests**

Send the same key concurrently and simulate a client retry after the first response is dropped. Assert one order, one mutation saga, one payment authorization request and byte-equivalent replay response.

- [ ] **Step 7: Run tests**

```bash
./gradlew :order-service:test --tests '*Idempotency*Test' --no-daemon --stacktrace
./gradlew :e2e-tests:test --tests '*OrderMutationIdempotencyE2ETest' \
  --no-daemon --stacktrace
```

- [ ] **Step 8: Commit**

```bash
git add order-service e2e-tests
git commit -m "feat: make order mutations idempotent"
```

**PR acceptance:** retries and concurrent requests produce exactly one durable business mutation; changed payloads with a reused key produce `409`.

---

### Task 5 / PR 5: Converge Gateway Identity, Correlation and Error Contracts

**Branch:** `agent/remediation-05-gateway-api-contract`

**Files:**
- Replace: `api-gateway/src/main/java/net/ftgo/gateway/filter/SecurityHeadersFilter.java` with `SecurityResponseHeadersFilter.java`
- Replace: `api-gateway/src/main/java/net/ftgo/gateway/filter/RequestLoggingFilter.java` with `GatewayCorrelationFilter.java`
- Modify: `api-gateway/src/main/java/net/ftgo/gateway/security/ForwardedHeaderPolicy.java`
- Modify: `api-gateway/src/main/java/net/ftgo/gateway/handler/GlobalErrorHandler.java`
- Modify: `common/src/main/java/net/ftgo/common/web/CorrelationIdFilter.java`
- Remove controller-local error bodies from `kitchen-service`, `order-history-service`, and `accounting-service`
- Modify their exception advice to return `FtgoProblemDetail`
- Test: `api-gateway/src/test/java/net/ftgo/gateway/filter/GatewayCorrelationFilterTest.java`
- Test: `api-gateway/src/test/java/net/ftgo/gateway/filter/SecurityResponseHeadersFilterTest.java`
- Modify: `common/src/test/java/net/ftgo/common/web/ProblemDetailContractTest.java`
- Add module-specific `*ProblemDetailContractTest.java`

**Interfaces:**
- Canonical header: `X-Correlation-ID`
- Remove all injection of `X-User-Id` and `X-User-Roles`
- Error media type: `application/problem+json`

- [ ] **Step 1: Write failing tests proving `X-User-*` is absent**

Downstream requests must contain the bearer token but no derived identity headers. Client-supplied `X-User-*` headers must be stripped.

- [ ] **Step 2: Retain only security response headers**

Rename the filter and remove all authentication-type branching. Downstream identity remains the independently validated JWT.

- [ ] **Step 3: Write failing correlation tests**

Cover invalid/oversized client IDs, generated IDs, propagation through Gateway, response echo, servlet-service propagation and MDC cleanup.

- [ ] **Step 4: Use one validated correlation header**

Apply `[A-Za-z0-9._:-]{8,128}` at Gateway and downstream services. Gateway logging obtains the client address only through `ForwardedHeaderPolicy.resolveClientAddress()`.

- [ ] **Step 5: Write failing error-contract tests**

Kitchen conflicts, Order History validation, payment-settlement errors, Gateway authentication/authorization and fallback failures must expose the shared fields `type`, `title`, `status`, `detail`, `instance`, `errorCode`, and `correlationId` without raw exception messages.

- [ ] **Step 6: Remove local ad-hoc error schemas**

Use shared `FtgoProblemResponses`/`FtgoProblemDetail`. Map internal exceptions to stable public messages and log full exceptions only with correlation ID.

- [ ] **Step 7: Run tests**

```bash
./gradlew :common:test :api-gateway:test :kitchen-service:test \
  :accounting-service:test :order-history-service:test \
  --tests '*ProblemDetail*Test' --tests '*Correlation*Test' \
  --tests '*SecurityResponseHeadersFilterTest' --no-daemon --stacktrace
```

- [ ] **Step 8: Commit**

```bash
git add common api-gateway kitchen-service accounting-service order-history-service
git commit -m "fix: unify gateway identity and API error contracts"
```

**PR acceptance:** bearer tokens are the sole downstream identity mechanism; one safe correlation ID spans edge and services; all public errors use RFC 9457.

---

### Task 6 / PR 6: Replace Blocking Settlement Sleep with Kafka-Backed Retry

**Branch:** `agent/remediation-06-settlement-redelivery`

**Files:**
- Delete: `accounting-service/src/main/java/net/ftgo/accounting/settlement/RetryingSettlementGateway.java`
- Delete: `accounting-service/src/test/java/net/ftgo/accounting/settlement/RetryingSettlementGatewayTest.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/config/AccountingKafkaRetryConfiguration.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/settlement/SimulatedSettlementGateway.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/settlement/SimulatedProviderOperation.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/messaging/AccountingServiceCommandHandlers.java`
- Create: `accounting-service/src/test/java/net/ftgo/accounting/messaging/SettlementKafkaRedeliveryIntegrationTest.java`
- Modify: `accounting-service/src/test/java/net/ftgo/accounting/messaging/AccountingSettlementCommandHandlersTest.java`
- Modify: `.github/workflows/phase-02b-payment-settlement.yml`

**Interfaces:**
- Retry schedule: `1s`, `5s`, `30s`; fourth delivery is terminal
- Timeout deliveries throw `SettlementGatewayTimeoutException` so the message transaction rolls back
- Final attempt returns a stable failure reply and is stored by `IdempotentCommandExecutor`

- [ ] **Step 1: Write a static guard test**

Fail if production accounting code contains `Thread.sleep(` or declares `RetryingSettlementGateway`.

- [ ] **Step 2: Write an integration test around a real Kafka listener container**

Publish one accounting command configured for `timeout-once`; assert the first handler invocation rolls back, the second succeeds, the command reply is emitted once, and the Kafka consumer thread is not held during backoff.

- [ ] **Step 3: Configure bounded Kafka error handling**

Use the existing `KafkaDeadLetterSupport` backoff policy. Mark `SettlementGatewayTimeoutException` retryable. Do not retry validation, state-conflict or request-ID-conflict exceptions.

- [ ] **Step 4: Change timeout behavior**

Each delivery performs one provider attempt. Attempts 1–3 throw a retryable timeout; attempt 4 produces `SettlementRetryExhaustedException`, which the command handler converts into a terminal failure reply so the saga is not left without a reply.

- [ ] **Step 5: Preserve command idempotency semantics**

The processed-command claim must abort on retryable timeout and complete only after success or terminal failure. Provider operation request IDs remain stable across all deliveries.

- [ ] **Step 6: Add outage/load tests**

Run at least 20 timeout-always commands with listener concurrency 4. Assert unrelated successful commands progress during backoff, retries are bounded and every terminal command has one stored failure reply.

- [ ] **Step 7: Run settlement regression**

```bash
./gradlew :accounting-service:test \
  --tests '*SettlementKafkaRedeliveryIntegrationTest' \
  --tests '*AccountingSettlementCommandHandlersTest' \
  --tests '*LostReplyIdempotencyTest' --no-daemon --stacktrace
bash scripts/smoke/verify-payment-settlement.sh --runs 2
```

- [ ] **Step 8: Commit**

```bash
git add accounting-service .github/workflows/phase-02b-payment-settlement.yml
git rm accounting-service/src/main/java/net/ftgo/accounting/settlement/RetryingSettlementGateway.java \
  accounting-service/src/test/java/net/ftgo/accounting/settlement/RetryingSettlementGatewayTest.java
git commit -m "fix: retry settlement through Kafka redelivery"
```

**PR acceptance:** no consumer thread sleeps; retry timing is externally observable; transient success and terminal failure both produce exactly one saga reply.

---

### Task 7 / PR 7: Make Settlement Reconciliation Incremental and Bounded

**Branch:** `agent/remediation-07-settlement-reconciliation`

**Files:**
- Create: `accounting-service/src/main/resources/db/migration/V12__create_settlement_reconciliation_work.sql`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/settlement/SettlementReconciliationWork.java`
- Create: `accounting-service/src/main/java/net/ftgo/accounting/settlement/SettlementReconciliationWorkRepository.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/settlement/SettlementReconciler.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/settlement/SettlementReconciliationMonitor.java`
- Modify: settlement mutation paths to enqueue authorization IDs
- Test: `accounting-service/src/test/java/net/ftgo/accounting/settlement/SettlementReconciliationBatchTest.java`
- Modify: `accounting-service/src/test/java/net/ftgo/accounting/settlement/SettlementReconcilerTest.java`

**Interfaces:**
- Configuration: batch size `100`, lease `PT2M`, steady rescan `PT24H`, discrepancy rescan `PT5M`
- Work claim uses MySQL 8 `FOR UPDATE SKIP LOCKED`

- [ ] **Step 1: Add a test that fails if `findAll()` is called**

Use a strict repository mock and 250 queued authorizations. Assert one monitor execution inspects at most 100 records.

- [ ] **Step 2: Create the durable work table**

Columns: `authorization_id` primary key, `next_attempt_at`, `locked_until`, `attempt_count`, `last_error`, `created_at`, `updated_at`; add an index on `(next_attempt_at, locked_until)`.

- [ ] **Step 3: Enqueue work after settlement-relevant mutations**

Authorize, capture, void, refund, reverse and manual repair update the queue in the same local transaction as the authorization mutation.

- [ ] **Step 4: Claim bounded batches**

Claim at most 100 due rows with `FOR UPDATE SKIP LOCKED`, set `locked_until`, inspect provider and ledger state, then reschedule or remove/resolve work.

- [ ] **Step 5: Bound all repository access**

Remove `authorizationRepository.findAll()`. Fetch authorizations by the claimed IDs and ledger totals using grouped aggregate queries rather than one query per authorization.

- [ ] **Step 6: Add multi-replica and failure tests**

Run two monitor instances concurrently. Assert no authorization is processed twice during the lease, failed work is retried after lease expiry, and a single bad provider record does not roll back the whole batch.

- [ ] **Step 7: Run tests**

```bash
./gradlew :accounting-service:test \
  --tests '*SettlementReconciliationBatchTest' \
  --tests '*SettlementReconcilerTest' --no-daemon --stacktrace
```

- [ ] **Step 8: Commit**

```bash
git add accounting-service
git commit -m "fix: reconcile settlement in bounded batches"
```

**PR acceptance:** scheduled reconciliation performs no full-table scan and no N+1 ledger reads; multiple replicas safely share work.

---

### Task 8 / PR 8: Add Safe Messaging Retention Lifecycle

**Branch:** `agent/remediation-08-messaging-retention`

**Files:**
- Create: `common/src/main/java/net/ftgo/common/messaging/MessageRetentionProperties.java`
- Create: `common/src/main/java/net/ftgo/common/messaging/JdbcMessageRetentionWorker.java`
- Modify: `common/src/main/java/net/ftgo/common/messaging/OutboxMetricsConfiguration.java`
- Test: `common/src/test/java/net/ftgo/common/messaging/JdbcMessageRetentionWorkerTest.java`
- Add retention-index migrations:
  - `order-service/.../V11__add_message_retention_indexes.sql`
  - `consumer-service/.../V6__add_message_retention_indexes.sql`
  - `restaurant-service/.../V5__add_message_retention_indexes.sql`
  - `kitchen-service/.../V11__add_message_retention_indexes.sql`
  - `accounting-service/.../V13__add_message_retention_indexes.sql`
  - `delivery-service/.../V4__add_message_retention_indexes.sql`
- Modify all service `application.yml` files
- Modify: `docs/operations/phase-03-distributed-consistency-runbook.md`

**Interfaces:**
- Defaults: cleanup disabled; outbox retention `P30D`; completed-command retention `P30D`; batch size `500`; interval `PT10M`
- Never delete `processed_commands` rows in `PROCESSING`

- [ ] **Step 1: Write failing cleanup tests**

Cover disabled mode, batch limits, cutoff boundaries, preservation of `PROCESSING`, repeatable batches and database failure metrics.

- [ ] **Step 2: Implement bounded cleanup SQL**

Delete at most 500 rows per table per run. Outbox cleanup uses `created_at`; processed-command cleanup uses `processed_at` and `outcome <> 'PROCESSING'`.

- [ ] **Step 3: Add indexes and migration tests**

Indexes must support the cleanup predicates without scanning the whole table.

- [ ] **Step 4: Add safety validation**

Startup rejects retention shorter than `P7D`. Production deployment keeps cleanup disabled until operators confirm Kafka retention and maximum replay windows are below the configured period.

- [ ] **Step 5: Add metrics**

Expose deleted rows, failures, last-success timestamp and cleanup duration by service/table. Do not label metrics with event IDs or payload values.

- [ ] **Step 6: Update the runbook**

Document enablement, dry-run metrics, connector-lag checks, rollback by disabling the worker and the prohibition on manual deletion of active command claims.

- [ ] **Step 7: Run tests**

```bash
./gradlew :common:test --tests '*JdbcMessageRetentionWorkerTest' --no-daemon --stacktrace
./gradlew :order-service:test :consumer-service:test :restaurant-service:test \
  :kitchen-service:test :accounting-service:test :delivery-service:test \
  --tests '*MigrationTest' --no-daemon --stacktrace
```

- [ ] **Step 8: Commit**

```bash
git add common order-service consumer-service restaurant-service kitchen-service \
  accounting-service delivery-service docs
git commit -m "feat: add bounded messaging retention"
```

**PR acceptance:** tables have an explicit, disabled-by-default cleanup lifecycle; each run is bounded, indexed and observable.

---

### Task 9 / PR 9: Protect Mainline and Remove Insecure Repository Defaults

**Branch:** `agent/remediation-09-mainline-platform`

**Files:**
- Modify every `.github/workflows/phase-*.yml`
- Create: `.github/workflows/dev-merge-verification.yml`
- Create: `.github/workflows/secret-scan.yml`
- Modify: `build.gradle`
- Modify: `README.md`
- Modify all service `application.yml` files
- Create/modify service `application-local.yml` files for local-only credentials
- Modify Docker Compose and Kubernetes manifests to pass datasource secrets explicitly
- Modify: `docs/operations/phase-04-security-api-runbook.md` or create it if absent

**Interfaces:**
- CI triggers: `pull_request` to `dev`, `push` to `dev`, `merge_group`, and `workflow_dispatch`
- Production datasource variables: `*_DB_URL`, `*_DB_USERNAME`, `*_DB_PASSWORD`

- [ ] **Step 1: Add workflow-contract tests**

Extend Python workflow tests to require `push.branches: [dev]` and `merge_group` for all required checks and to reject branch-specific Phase 01 push triggers.

- [ ] **Step 2: Add merge-SHA verification**

`dev-merge-verification.yml` runs full Gradle tests and the contract matrix on every push to `dev`; scheduled workflows run the expensive clean-stack E2E suites.

- [ ] **Step 3: Configure required repository checks**

After workflow names stabilize, use GitHub branch protection for `dev`: require PRs, require the merge-verification checks, require conversation resolution and disallow force pushes. Change the repository default branch from `master` to `dev` only after CI is green on `dev`.

- [ ] **Step 4: Externalize datasource credentials**

Base `application.yml` files must not contain `ftgo_user`, `ftgo_password` or `createDatabaseIfNotExist=true`. Local credentials belong only in `application-local.yml`; production profiles fail startup when secrets are absent.

- [ ] **Step 5: Add secret scanning**

Run Gitleaks (or an equivalent pinned scanner) on PRs and pushes; fail on committed credentials and private keys. Add only test fixtures to an explicit reviewed allowlist.

- [ ] **Step 6: Repair repository hygiene**

Update README to Phase 04-remediation status, document `dev` as mainline, remove duplicate `HikariCP` declaration from `order-service`, and archive stale phase status text rather than presenting it as current.

- [ ] **Step 7: Verify the exact merge SHA**

Merge only after PR checks pass, then confirm the new `dev` merge SHA has successful merge-verification checks. Record run IDs and SHA in the plan completion section.

- [ ] **Step 8: Commit**

```bash
git add .github build.gradle README.md '*-service/src/main/resources' deployment docs
git commit -m "chore: protect dev and externalize production configuration"
```

**PR acceptance:** every merge is re-tested on its resulting SHA; `dev` is the protected default branch; production profiles contain no embedded database credentials.

---

### Task 10 / PR 10: Migrate to a Supported Spring Platform

**Branch:** `agent/remediation-10-spring-platform-upgrade`

**Target versions as of 2026-07-29:**
- Java `21`
- Bridge commit only: Spring Boot `3.5.15`, Spring Cloud `2025.0.3`
- Final merged state: Spring Boot `4.0.7`, Spring Cloud `2025.1.2`
- Gradle `8.14.3`
- `io.spring.dependency-management` `1.1.7`

**Files:**
- Modify: `build.gradle`
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `gradle/wrapper/gradle-wrapper.jar`
- Modify: `gradlew`, `gradlew.bat` if regenerated
- Modify module source/config files required by Spring Boot 4 migration
- Modify Dockerfiles and CI Java/Gradle cache configuration
- Create: `deployment/tests/test_supported_dependency_baseline.py`
- Create: `docs/operations/spring-platform-upgrade-runbook.md`

**Interfaces:**
- Keep existing REST, event, command and database contracts unchanged.
- Eventuate compatibility is proved by compile, saga integration, lost-reply and real-stack E2E tests before merge.

- [ ] **Step 1: Add a failing dependency-baseline test**

The test parses `build.gradle` and wrapper properties and rejects Spring Boot `3.2.x`, Spring Cloud `2023.0.x`, Gradle below `8.14`, milestone repositories and duplicate explicitly versioned libraries already managed by the BOM.

- [ ] **Step 2: Upgrade to the 3.5 bridge in an isolated commit**

Set Boot `3.5.15`, Cloud `2025.0.3`, dependency-management `1.1.7`, Gradle `8.14.3`; remove the Spring milestone repository; compile and fix deprecations. This commit is an intermediate migration checkpoint and must not be released independently.

- [ ] **Step 3: Run the full suite on the bridge commit**

```bash
./gradlew clean test --no-daemon --stacktrace
bash scripts/smoke/verify-fresh-stack.sh --runs 2
bash scripts/smoke/verify-core-order-flow.sh --runs 2
bash scripts/smoke/verify-payment-settlement.sh --runs 2
bash scripts/smoke/verify-distributed-consistency.sh --runs 2
```

- [ ] **Step 4: Upgrade the final branch state to Boot 4**

Set Boot `4.0.7` and Cloud `2025.1.2`. Apply official Boot 4 migration changes, including changed starter/module coordinates, servlet/reactive APIs and configuration properties. Keep Java 21.

- [ ] **Step 5: Prove Eventuate compatibility**

Run compile/tests for every Eventuate participant and orchestrator, saga integration tests, processed-command replay, outbox/CDC smoke and all real-stack workflows. Any Eventuate artifact that cannot run on Boot 4 must be upgraded through the Eventuate platform BOM and proven by the same contracts; do not merge with exclusions that disable saga tests.

- [ ] **Step 6: Run dependency and vulnerability checks**

Generate dependency insight for Spring Security, Netty, Jackson, Kafka, MySQL and Testcontainers; run the repository vulnerability scanner; fail on known critical/high vulnerabilities without a documented non-reachable justification.

- [ ] **Step 7: Run the complete final verification on one SHA**

```bash
./gradlew clean test --no-daemon --stacktrace
bash scripts/smoke/verify-fresh-stack.sh --runs 2
bash scripts/smoke/verify-core-order-flow.sh --runs 2
bash scripts/smoke/verify-payment-settlement.sh --runs 2
bash scripts/smoke/verify-distributed-consistency.sh --runs 2
```

All required GitHub workflows must finish successfully on the same final head SHA and again on the `dev` merge SHA.

- [ ] **Step 8: Commit checkpoints**

```bash
git commit -am "build: migrate to spring boot 3.5 bridge"
git commit -am "build: migrate to supported spring boot 4 platform"
```

**PR acceptance:** final `dev` uses Boot `4.0.7`, Cloud `2025.1.2` and Gradle `8.14.3`; every saga, outbox, API and real-stack regression gate remains green.

---

## Final Cross-Phase Verification Gate

Run after PR 10 on a clean checkout and clean Docker volumes:

```bash
bash scripts/ci/verify-gradle-wrapper.sh
./gradlew clean test --no-daemon --stacktrace
python -m unittest discover -s deployment/tests -p 'test_*.py' -v
bash scripts/smoke/verify-fresh-stack.sh --runs 2
bash scripts/smoke/verify-core-order-flow.sh --runs 2
bash scripts/smoke/verify-payment-settlement.sh --runs 2
bash scripts/smoke/verify-distributed-consistency.sh --runs 2
```

Required abuse/regression scenarios:

- Cross-consumer Order History access through Gateway and direct service ports.
- Token with `scope=ADMIN` or `scope=SERVICE` but no trusted role claim.
- Public route called with internal-only audience and internal route called with public-only audience.
- Credentialed CORS from an unlisted origin.
- Tampered, expired, cross-query and future-month paging tokens.
- Duplicate/concurrent create, cancel and revise requests with same and conflicting idempotency keys.
- Provider timeout-once and timeout-always while unrelated commands continue to progress.
- Two reconciliation replicas sharing more than one batch of work.
- Retention batch boundaries and preservation of active command claims.
- Exact `dev` merge SHA has all required status checks.

## Rollout Order

1. Merge PRs 1–3 immediately as security hotfixes.
2. Merge PR 4 with additive migration before making `Idempotency-Key` mandatory at the Gateway.
3. Merge PR 5 after clients are prepared for the canonical correlation header and RFC 9457 schema.
4. Merge PRs 6–7 behind settlement/reconciliation feature flags; canary Accounting first.
5. Merge PR 8 with cleanup disabled; enable only after the runbook checks pass.
6. Merge PR 9 and switch default branch only after merge-SHA CI succeeds.
7. Merge PR 10 last because it has the broadest compatibility blast radius.

## Rollback Rules

- Keep all additive tables and columns during rollback.
- Roll back application binaries before disabling compatibility readers.
- Never delete idempotency, pending-event, payment-ledger, discrepancy or processed-command records to recover a deployment.
- Disable scheduled retry/reconciliation/retention workers through configuration before rolling back their code.
- Do not restore wildcard CORS, scope-derived roles or fail-open route defaults under any rollback scenario.

## Plan Completion Record

When execution finishes, append:

- Final PR numbers and merge SHAs for PRs 1–10.
- Required workflow names, run IDs and conclusions for the final head and merge SHA.
- Migration versions applied in each service.
- Feature-flag rollout state and rollback evidence.
- Remaining risks; this list must be empty for findings covered by the issue matrix.

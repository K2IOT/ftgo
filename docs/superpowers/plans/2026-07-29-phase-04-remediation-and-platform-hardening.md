# FTGO Phase 04 Remediation and Platform Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove every security, reliability, scaling, lifecycle and repository-governance gap found in the Phase 01–04 review while preserving existing saga and distributed-consistency guarantees.

**Architecture:** Deliver ten serial, independently reviewable PRs. Security hotfixes merge first; API and persistence changes use additive migrations and compatibility windows; payment retries move from sleeping inside Eventuate consumer threads to Kafka-backed redelivery; maintenance jobs are bounded and observable; mainline verification runs on the exact merge SHA.

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
10. Supported Spring/Gradle/Eventuate dependency migration.

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
    OrderHistoryRecord record = new OrderHistoryRecord("9001");
    record.setConsumerId(202L);

    assertThrows(AccessDeniedException.class,
        () -> authorization.requireOrderAccess(record, consumer(101L)));
}
```

- [ ] **Step 2: Run the tests and verify they fail**

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

- [ ] **Step 4: Enforce checks before serialization or query execution**

`findOrder()` loads the record, calls `requireOrderAccess()`, then returns it. `findOrderHistory()` derives the FTGO principal from `Authentication`, calls `requireConsumerAccess()`, then constructs `OrderHistoryQueryCriteria`.

- [ ] **Step 5: Add direct-service tests**

Cover owner `200`, admin `200`, cross-consumer `403`, missing `consumer_id` `403`, nonexistent order `404`, and verify the unauthorized request never invokes `OrderHistoryQueryService.query()`.

- [ ] **Step 6: Add Gateway and direct-port E2E abuse tests**

Create records for consumers 101 and 202. Query both route forms using consumer 101 and assert the response never contains consumer 202 order IDs, line items, delivery address or payment status.

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
- Modify: `api-gateway/src/main/java/net/ftgo/gateway/security/SecurityConfiguration.java`
- Modify: `order-service/src/main/java/net/ftgo/order/config/SecurityConfiguration.java`
- Modify: `consumer-service/src/main/java/net/ftgo/consumer/config/SecurityConfiguration.java`
- Modify: `restaurant-service/src/main/java/net/ftgo/restaurant/config/SecurityConfiguration.java`
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/config/SecurityConfiguration.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/config/SecurityConfiguration.java`
- Modify: `delivery-service/src/main/java/net/ftgo/delivery/config/SecurityConfiguration.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/config/SecurityConfiguration.java`
- Modify: each module's existing `SecuritySmokeTest.java`
- Test: `e2e-tests/src/test/java/net/ftgo/e2e/AudienceAndRoleBoundaryE2ETest.java`

**Interfaces:**
- Produces: `FtgoRoles.isKnown(String): boolean`
- Configuration: `ftgo.gateway.cors.allowed-origins`, `allowed-methods`, `allow-credentials`, `max-age`

- [ ] **Step 1: Write failing claim-mapping tests**

```java
@Test
void scopeDoesNotGrantAdminOrServiceRole() {
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

Read application roles only from `roles` and `realm_access.roles`. Remove role extraction from `scope` and `authorities`. Allow exactly `CONSUMER`, `RESTAURANT`, `COURIER`, `ADMIN`, and `SERVICE`; reject unknown nonblank values.

- [ ] **Step 4: Write failing audience and unknown-route tests in every service**

For a public endpoint, a token with the correct role but only `AUD_ftgo-internal` must receive `403`. For an authenticated unknown endpoint, expect `403` from `.denyAll()`.

- [ ] **Step 5: Replace fail-open defaults**

Every service explicitly declares liveness/readiness, actuator, internal and public route namespaces and finishes with:

```java
.anyRequest().denyAll()
```

Public route managers require role and `AUD_ftgo-api`; internal route managers require `ROLE_SERVICE` and `AUD_ftgo-internal`.

- [ ] **Step 6: Write failing CORS tests**

Cover an allowed origin, an unlisted origin, credentialed mode with explicit origins, and startup rejection when credentials are enabled with `*`.

- [ ] **Step 7: Replace wildcard CORS with typed properties**

Production defaults are an empty allowlist and `allow-credentials=false`. Docker/Kubernetes read explicit origins from `FTGO_CORS_ALLOWED_ORIGINS`; no profile defaults to `*`.

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
- Create: `order-history-service/src/main/java/net/ftgo/orderhistory/service/DecodedPagingToken.java`
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

Reject cursor months after the current UTC month. Stop after 24 bucket reads; when more history exists, return a signed cursor for the next month instead of continuing the loop.

- [ ] **Step 4: Replace the unsigned Base64 cursor**

`CassandraOrderHistoryQueryStore` no longer parses or emits raw month/driver state. All cursors pass through `OrderHistoryPagingTokenCodec`.

- [ ] **Step 5: Add integration tests**

Assert tampered/cross-query tokens return `400 ORDER_HISTORY_QUERY_INVALID`, and a far-future handcrafted token causes zero repository calls.

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
- Create: `order-service/src/main/java/net/ftgo/order/idempotency/IdempotentResult.java`
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
void duplicateCreateReturnsOriginalOrderWithoutStartingSecondSaga() throws Exception {
    String body = validCreateOrderJson();

    MvcResult first = mockMvc.perform(post("/orders")
            .header("Idempotency-Key", "create-101-1")
            .contentType(APPLICATION_JSON).content(body)
            .with(consumerJwt(101L)))
        .andExpect(status().isCreated()).andReturn();

    MvcResult second = mockMvc.perform(post("/orders")
            .header("Idempotency-Key", "create-101-1")
            .contentType(APPLICATION_JSON).content(body)
            .with(consumerJwt(101L)))
        .andExpect(status().isCreated()).andReturn();

    assertEquals(first.getResponse().getContentAsString(),
        second.getResponse().getContentAsString());
    assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
}

@Test
void sameKeyWithDifferentBodyReturnsConflict() throws Exception {
    mockMvc.perform(post("/orders").header("Idempotency-Key", "create-101-2")
        .contentType(APPLICATION_JSON).content(validCreateOrderJson()).with(consumerJwt(101L)))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/orders").header("Idempotency-Key", "create-101-2")
        .contentType(APPLICATION_JSON).content(createOrderJsonWithQuantity(2)).with(consumerJwt(101L)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_CONFLICT"));
}
```

- [ ] **Step 3: Implement transactional claim/replay**

Use `INSERT IGNORE`, then `SELECT ... FOR UPDATE`. The loser of a concurrent insert waits for the winner transaction, validates the request hash and replays the completed response. The idempotency record, order mutation, outbox insert and saga creation commit or roll back together.

- [ ] **Step 4: Hash canonical server inputs**

Create SHA-256 from authenticated consumer ID, operation name, order ID where applicable, and canonical JSON body. Exclude timestamps generated by the server and bearer/payment tokens.

- [ ] **Step 5: Apply the service to all order mutations**

Operations are `CREATE_ORDER`, `CANCEL_ORDER:{orderId}`, and `REVISE_ORDER:{orderId}`. Repeated cancel/revise requests return the first successful response instead of starting another saga.

- [ ] **Step 6: Add connection-loss and concurrency E2E tests**

Send the same key concurrently and retry after dropping the first client response. Assert one order, one mutation saga, one provider authorization request and byte-equivalent replay response.

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
- Delete: `api-gateway/src/main/java/net/ftgo/gateway/filter/SecurityHeadersFilter.java`
- Create: `api-gateway/src/main/java/net/ftgo/gateway/filter/SecurityResponseHeadersFilter.java`
- Delete: `api-gateway/src/main/java/net/ftgo/gateway/filter/RequestLoggingFilter.java`
- Create: `api-gateway/src/main/java/net/ftgo/gateway/filter/GatewayCorrelationFilter.java`
- Modify: `api-gateway/src/main/java/net/ftgo/gateway/security/ForwardedHeaderPolicy.java`
- Modify: `api-gateway/src/main/java/net/ftgo/gateway/handler/GlobalErrorHandler.java`
- Modify: `common/src/main/java/net/ftgo/common/web/CorrelationIdFilter.java`
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/api/KitchenController.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/api/OrderHistoryExceptionHandler.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/api/admin/PaymentSettlementExceptionHandler.java`
- Test: `api-gateway/src/test/java/net/ftgo/gateway/filter/GatewayCorrelationFilterTest.java`
- Test: `api-gateway/src/test/java/net/ftgo/gateway/filter/SecurityResponseHeadersFilterTest.java`
- Modify: `common/src/test/java/net/ftgo/common/web/ProblemDetailContractTest.java`
- Create: `kitchen-service/src/test/java/net/ftgo/kitchen/api/KitchenProblemDetailContractTest.java`
- Create: `order-history-service/src/test/java/net/ftgo/orderhistory/api/OrderHistoryProblemDetailContractTest.java`
- Create: `accounting-service/src/test/java/net/ftgo/accounting/api/admin/PaymentSettlementProblemDetailContractTest.java`

**Interfaces:**
- Canonical header: `X-Correlation-ID`
- Remove all injection of `X-User-Id` and `X-User-Roles`
- Error media type: `application/problem+json`

- [ ] **Step 1: Write failing tests proving `X-User-*` is absent**

Downstream requests contain the bearer token but no derived identity headers. Client-supplied `X-User-*` headers are stripped.

- [ ] **Step 2: Retain only security response headers**

`SecurityResponseHeadersFilter` adds response hardening headers and performs no identity extraction. Downstream identity remains the independently validated JWT.

- [ ] **Step 3: Write failing correlation tests**

Cover invalid/oversized client IDs, generated IDs, propagation through Gateway, response echo, servlet-service propagation and MDC cleanup.

- [ ] **Step 4: Use one validated correlation header**

Apply `[A-Za-z0-9._:-]{8,128}` at Gateway and downstream services. Gateway logging obtains the client address only through `ForwardedHeaderPolicy.resolveClientAddress()`.

- [ ] **Step 5: Write failing error-contract tests**

Kitchen conflicts, Order History validation, payment-settlement errors, Gateway authentication/authorization and fallback failures expose `type`, `title`, `status`, `detail`, `instance`, `errorCode`, and `correlationId` without raw exception messages.

- [ ] **Step 6: Remove ad-hoc error schemas**

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

- [ ] **Step 2: Write a real Kafka redelivery integration test**

Publish one accounting command configured for `timeout-once`; assert the first handler transaction rolls back, the second succeeds, the command reply is emitted once, and a second unrelated command completes before the first retry delay expires.

- [ ] **Step 3: Configure bounded Kafka error handling**

Use `KafkaDeadLetterSupport.errorHandler(...)`. Mark `SettlementGatewayTimeoutException` retryable. Validation, state conflict and request-ID conflict remain non-retryable.

- [ ] **Step 4: Change timeout behavior**

Each delivery performs one provider attempt. Attempts 1–3 throw a retryable timeout; attempt 4 throws `SettlementRetryExhaustedException`, which the command handler converts into a terminal failure reply so the saga always receives a reply.

- [ ] **Step 5: Preserve command idempotency**

The processed-command claim aborts on retryable timeout and completes only after success or terminal failure. Provider operation request IDs remain stable across deliveries.

- [ ] **Step 6: Add outage/load tests**

Run 20 timeout-always commands with listener concurrency 4. Assert unrelated successful commands progress during backoff, retries are bounded and every terminal command has one stored failure reply.

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
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/messaging/AccountingServiceCommandHandlers.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/settlement/ManualPaymentSettlementService.java`
- Test: `accounting-service/src/test/java/net/ftgo/accounting/settlement/SettlementReconciliationBatchTest.java`
- Modify: `accounting-service/src/test/java/net/ftgo/accounting/settlement/SettlementReconcilerTest.java`

**Interfaces:**
- Configuration: batch size `100`, lease `PT2M`, steady rescan `PT24H`, discrepancy rescan `PT5M`
- Work claim uses MySQL 8 `FOR UPDATE SKIP LOCKED`

- [ ] **Step 1: Add a test that fails if `findAll()` is called**

Use a strict repository mock and 250 queued authorizations. Assert one monitor execution inspects exactly 100 records and leaves 150 due records for later runs.

- [ ] **Step 2: Create the durable work table**

Columns: `authorization_id` primary key, `next_attempt_at`, `locked_until`, `attempt_count`, `last_error`, `created_at`, `updated_at`; index `(next_attempt_at, locked_until)`.

- [ ] **Step 3: Enqueue work in mutation transactions**

Authorize, capture, void, refund, reverse and manual repair update the queue in the same local transaction as the authorization mutation.

- [ ] **Step 4: Claim bounded batches**

Claim at most 100 due rows with `FOR UPDATE SKIP LOCKED`, set `locked_until`, inspect provider and ledger state, then reschedule or resolve work.

- [ ] **Step 5: Remove N+1 reads**

Remove `authorizationRepository.findAll()`. Fetch authorizations by claimed IDs and add one grouped ledger aggregate query returning capture/refund totals by authorization ID.

- [ ] **Step 6: Add multi-replica and failure tests**

Run two monitor instances concurrently. Assert no authorization is processed twice during the lease, failed work retries after lease expiry, and one bad provider record does not roll back the batch.

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
- Create: `order-service/src/main/resources/db/migration/V11__add_message_retention_indexes.sql`
- Create: `consumer-service/src/main/resources/db/migration/V6__add_message_retention_indexes.sql`
- Create: `restaurant-service/src/main/resources/db/migration/V5__add_message_retention_indexes.sql`
- Create: `kitchen-service/src/main/resources/db/migration/V11__add_message_retention_indexes.sql`
- Create: `accounting-service/src/main/resources/db/migration/V13__add_message_retention_indexes.sql`
- Create: `delivery-service/src/main/resources/db/migration/V4__add_message_retention_indexes.sql`
- Modify: `order-service/src/main/resources/application.yml`
- Modify: `consumer-service/src/main/resources/application.yml`
- Modify: `restaurant-service/src/main/resources/application.yml`
- Modify: `kitchen-service/src/main/resources/application.yml`
- Modify: `accounting-service/src/main/resources/application.yml`
- Modify: `delivery-service/src/main/resources/application.yml`
- Modify: `docs/operations/phase-03-distributed-consistency-runbook.md`

**Interfaces:**
- Defaults: cleanup disabled; outbox retention `P30D`; completed-command retention `P30D`; batch size `500`; interval `PT10M`
- Never delete `processed_commands` rows in `PROCESSING`

- [ ] **Step 1: Write failing cleanup tests**

Cover disabled mode, batch limits, cutoff boundaries, preservation of `PROCESSING`, repeatable batches and database failure metrics.

- [ ] **Step 2: Implement bounded cleanup SQL**

Delete at most 500 rows per table per run. Outbox cleanup uses `created_at`; processed-command cleanup uses `processed_at` and `outcome <> 'PROCESSING'`.

- [ ] **Step 3: Add indexes and migration tests**

Each migration adds an outbox `created_at` index and processed-command `(outcome, processed_at)` index when the table exists in that service.

- [ ] **Step 4: Add safety validation**

Startup rejects retention shorter than `P7D`. Production keeps cleanup disabled until operators confirm Kafka retention and maximum replay windows are below the configured period.

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

**PR acceptance:** tables have a disabled-by-default cleanup lifecycle; each run is bounded, indexed and observable.

---

### Task 9 / PR 9: Protect Mainline and Remove Insecure Repository Defaults

**Branch:** `agent/remediation-09-mainline-platform`

**Files:**
- Modify: `.github/workflows/phase-01-debezium-smoke.yml`
- Modify: `.github/workflows/phase-01-fresh-stack.yml`
- Modify: `.github/workflows/phase-01-full-test.yml`
- Modify: `.github/workflows/phase-01-module-diagnostics.yml`
- Modify: `.github/workflows/phase-01-verification.yml`
- Modify: `.github/workflows/phase-02-core-order-flow-e2e.yml`
- Modify: `.github/workflows/phase-02-core-order-flow.yml`
- Modify: `.github/workflows/phase-02b-payment-settlement.yml`
- Modify: `.github/workflows/phase-03-distributed-consistency.yml`
- Modify: `.github/workflows/phase-03-distributed-failure-e2e.yml`
- Modify: `.github/workflows/phase-03-operations.yml`
- Modify: `.github/workflows/phase-03-order-history.yml`
- Modify: `.github/workflows/phase-04-api-contract.yml`
- Modify: `.github/workflows/phase-04-security-api.yml`
- Modify: `.github/workflows/phase-04-web-diagnostic.yml`
- Create: `.github/workflows/dev-merge-verification.yml`
- Create: `.github/workflows/secret-scan.yml`
- Create: `scripts/ci/scan-secrets.sh`
- Modify: `build.gradle`
- Modify: `README.md`
- Modify all eight service `application.yml` files
- Create all eight service `application-local.yml` files
- Modify: `deployment/tests/docker-compose.core-order-flow.yml`
- Modify production Docker Compose/Kubernetes manifests under `deployment/`
- Create: `docs/operations/phase-04-security-api-runbook.md`

**Interfaces:**
- CI triggers: `pull_request` to `dev`, `push` to `dev`, `merge_group`, and `workflow_dispatch`
- Production datasource variables: `*_DB_URL`, `*_DB_USERNAME`, `*_DB_PASSWORD`
- Secret scanner command: `docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:v8.24.3 detect --source /repo --no-banner`

- [ ] **Step 1: Add workflow-contract tests**

Extend Python workflow tests to require `push.branches: [dev]` and `merge_group` for required checks and reject branch-specific Phase 01 push triggers.

- [ ] **Step 2: Add merge-SHA verification**

`dev-merge-verification.yml` runs full Gradle tests and the contract matrix on every push to `dev`; scheduled workflows run expensive clean-stack E2E suites.

- [ ] **Step 3: Configure repository protection**

After workflow names stabilize, require PRs and merge-verification checks for `dev`, require conversation resolution, disallow force pushes, then change the repository default branch from `master` to `dev` after a green `dev` push run.

- [ ] **Step 4: Externalize datasource credentials**

Base `application.yml` files contain no `ftgo_user`, `ftgo_password` or `createDatabaseIfNotExist=true`. Local credentials live in `application-local.yml`; production profiles fail startup when environment secrets are absent.

- [ ] **Step 5: Add secret scanning**

`secret-scan.yml` invokes `scripts/ci/scan-secrets.sh` on PR and push events. Test-only fixture exceptions are listed in `.gitleaks.toml` with exact path and reason.

- [ ] **Step 6: Repair repository hygiene**

Update README to remediation status, document `dev` as mainline, remove the duplicate `HikariCP` declaration from `order-service`, and move stale status text to historical documentation.

- [ ] **Step 7: Verify the exact merge SHA**

Merge only after PR checks pass, then confirm the new `dev` merge SHA has successful merge-verification checks. Record run IDs and SHA in the completion record.

- [ ] **Step 8: Commit**

```bash
git add .github scripts/ci build.gradle README.md api-gateway/src/main/resources \
  order-service/src/main/resources consumer-service/src/main/resources \
  restaurant-service/src/main/resources kitchen-service/src/main/resources \
  accounting-service/src/main/resources delivery-service/src/main/resources \
  order-history-service/src/main/resources deployment docs
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
- Eventuate platform BOM `2024.0.RELEASE`

**Files:**
- Modify: `build.gradle`
- Modify: `common/build.gradle`
- Modify: `api-gateway/build.gradle`
- Modify: `order-service/build.gradle`
- Modify: `consumer-service/build.gradle`
- Modify: `restaurant-service/build.gradle`
- Modify: `kitchen-service/build.gradle`
- Modify: `accounting-service/build.gradle`
- Modify: `delivery-service/build.gradle`
- Modify: `order-history-service/build.gradle`
- Modify: `e2e-tests/build.gradle`
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Regenerate: `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`
- Modify: `common/src/main/java/net/ftgo/common/security/FtgoJwtDecoders.java`
- Modify: `common/src/main/java/net/ftgo/common/security/FtgoReactiveJwtDecoders.java`
- Modify the eight security configuration files listed in Task 2 when required by Spring Security 7 APIs
- Modify: `api-gateway/src/main/java/net/ftgo/gateway/config/GatewayConfiguration.java`
- Modify: `delivery-service/src/main/java/net/ftgo/delivery/config/KafkaConsumerConfiguration.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/config/KafkaConfiguration.java`
- Modify: `accounting-service/src/main/java/net/ftgo/accounting/config/AccountingKafkaRetryConfiguration.java`
- Modify all service `application.yml` files for renamed Boot 4 properties
- Modify Dockerfiles and all GitHub workflow Gradle setup steps
- Create: `deployment/tests/test_supported_dependency_baseline.py`
- Create: `docs/operations/spring-platform-upgrade-runbook.md`

**Interfaces:**
- Keep existing REST, event, command and database contracts unchanged.
- Resolve Eventuate through `io.eventuate.platform:eventuate-platform-dependencies:2024.0.RELEASE`; remove the separate core/sagas BOM declarations.
- Eventuate compatibility is proved by compile, saga integration, lost-reply and real-stack E2E tests before merge.

- [ ] **Step 1: Add a failing dependency-baseline test**

The test parses `build.gradle` and wrapper properties and rejects Spring Boot `3.2.x`, Spring Cloud `2023.0.x`, Gradle below `8.14`, the Spring milestone repository, duplicate BOMs and duplicate explicitly versioned libraries already managed by Boot/Eventuate BOMs.

- [ ] **Step 2: Upgrade to the 3.5 bridge in an isolated commit**

Set Boot `3.5.15`, Cloud `2025.0.3`, dependency-management `1.1.7`, Gradle `8.14.3` and Eventuate platform `2024.0.RELEASE`; remove the milestone repository; compile and resolve every deprecation warning that becomes an error under Boot 4. This commit is a checkpoint and is not released independently.

- [ ] **Step 3: Run the full suite on the bridge commit**

```bash
./gradlew clean test --no-daemon --stacktrace
bash scripts/smoke/verify-fresh-stack.sh --runs 2
bash scripts/smoke/verify-core-order-flow.sh --runs 2
bash scripts/smoke/verify-payment-settlement.sh --runs 2
bash scripts/smoke/verify-distributed-consistency.sh --runs 2
```

- [ ] **Step 4: Upgrade the final branch state to Boot 4**

Set Boot `4.0.7` and Cloud `2025.1.2`. Update the listed Security, Gateway, Kafka and configuration files to the Boot 4/Spring Security 7 APIs. Keep Java 21 and Gradle 8.14.3.

- [ ] **Step 5: Prove Eventuate compatibility**

Run compile/tests for every Eventuate participant/orchestrator, saga integration tests, processed-command replay, outbox/CDC smoke and all real-stack workflows. Do not add exclusions that skip Eventuate or saga tests; a failing Eventuate compatibility test blocks the PR.

- [ ] **Step 6: Run dependency and vulnerability checks**

Generate dependency insight for Spring Security, Netty, Jackson, Kafka, MySQL and Testcontainers; run the repository vulnerability scanner; fail on known critical/high vulnerabilities unless `docs/operations/spring-platform-upgrade-runbook.md` records the dependency path, non-reachability proof and removal deadline.

- [ ] **Step 7: Run complete final verification on one SHA**

```bash
./gradlew clean test --no-daemon --stacktrace
bash scripts/smoke/verify-fresh-stack.sh --runs 2
bash scripts/smoke/verify-core-order-flow.sh --runs 2
bash scripts/smoke/verify-payment-settlement.sh --runs 2
bash scripts/smoke/verify-distributed-consistency.sh --runs 2
```

All required GitHub workflows finish successfully on the same final head SHA and again on the `dev` merge SHA.

- [ ] **Step 8: Commit checkpoints**

```bash
git add build.gradle gradle common api-gateway order-service consumer-service \
  restaurant-service kitchen-service accounting-service delivery-service \
  order-history-service e2e-tests deployment docs .github
git commit -m "build: migrate to spring boot 3.5 bridge"

git add build.gradle gradle common api-gateway order-service consumer-service \
  restaurant-service kitchen-service accounting-service delivery-service \
  order-history-service e2e-tests deployment docs .github
git commit -m "build: migrate to supported spring boot 4 platform"
```

**PR acceptance:** final `dev` uses Boot `4.0.7`, Cloud `2025.1.2`, Eventuate platform `2024.0.RELEASE` and Gradle `8.14.3`; every saga, outbox, API and real-stack regression gate remains green.

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
2. Merge PR 4 with the additive migration before making `Idempotency-Key` mandatory at the Gateway.
3. Merge PR 5 after clients accept the canonical correlation header and RFC 9457 schema.
4. Merge PRs 6–7 behind settlement/reconciliation feature flags; canary Accounting first.
5. Merge PR 8 with cleanup disabled; enable only after runbook checks pass.
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

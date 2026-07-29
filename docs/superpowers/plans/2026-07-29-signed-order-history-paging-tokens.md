# Signed Order History Paging Tokens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace forgeable Order History cursors with query-bound HMAC-SHA-256 tokens and limit every request to 24 Cassandra bucket reads.

**Architecture:** The public token is owned by `OrderHistoryPagingTokenCodec`, while repositories exchange a typed `OrderHistoryPageCursor`. `OrderHistoryQueryService` verifies the token before invoking the store, and `CassandraOrderHistoryQueryStore` enforces a strict per-call bucket budget while returning a continuation cursor at the first unread bucket.

**Tech Stack:** Java 21, Spring Boot 3.2, Jackson, JUnit 5, Mockito, Spring MVC Test, Spring Data Cassandra.

## Global Constraints

- Token format is `base64url(payload).base64url(signature)` without padding.
- Signature algorithm is HMAC-SHA-256 and verification uses `MessageDigest.isEqual`.
- Token TTL defaults to `PT15M`.
- Signing secret comes from `FTGO_ORDER_HISTORY_PAGING_SECRET` and blank secrets fail closed.
- Token is bound to consumer ID, status, restaurant ID, `since` and page size.
- Future UTC bucket months are rejected before the query store is called.
- Every request performs at most 24 Cassandra bucket queries.
- Invalid tokens map to `400 ORDER_HISTORY_QUERY_INVALID`.

---

### Task 1: Typed Cursor and Signed Codec

**Files:**
- Create: `order-history-service/src/main/java/net/ftgo/orderhistory/service/OrderHistoryPageCursor.java`
- Create: `order-history-service/src/main/java/net/ftgo/orderhistory/service/OrderHistoryPagingTokenCodec.java`
- Create: `order-history-service/src/test/java/net/ftgo/orderhistory/service/OrderHistoryPagingTokenCodecTest.java`

**Interfaces:**
- Produces: `record OrderHistoryPageCursor(YearMonth bucketMonth, String driverPagingState)`.
- Produces: `String OrderHistoryPagingTokenCodec.encode(OrderHistoryQueryCriteria criteria, int pageSize, OrderHistoryPageCursor cursor)`.
- Produces: `OrderHistoryPageCursor OrderHistoryPagingTokenCodec.decode(String token, OrderHistoryQueryCriteria criteria, int pageSize)`.

- [ ] **Step 1: Write codec tests first**

Create tests using a fixed `Clock` and `ObjectMapper` for:

```java
@Test
void roundTripsValidToken() {
    OrderHistoryPageCursor cursor = new OrderHistoryPageCursor(YearMonth.of(2026, 7), "AQID");
    String token = codec.encode(criteria, 20, cursor);
    assertEquals(cursor, codec.decode(token, criteria, 20));
}
```

Add independent tests that mutate one payload byte, advance the clock beyond 15 minutes, change consumer ID/status/restaurant/`since`/page size, supply malformed base64 and construct the codec with a blank secret.

- [ ] **Step 2: Run the focused test and capture RED**

Run:

```bash
./gradlew :order-history-service:test --tests '*OrderHistoryPagingTokenCodecTest'
```

Expected: compilation failure because the cursor and codec do not exist.

- [ ] **Step 3: Implement the typed cursor**

```java
public record OrderHistoryPageCursor(YearMonth bucketMonth, String driverPagingState) {
    public OrderHistoryPageCursor {
        Objects.requireNonNull(bucketMonth, "bucketMonth is required");
        if (driverPagingState != null && driverPagingState.isBlank()) {
            driverPagingState = null;
        }
    }
}
```

- [ ] **Step 4: Implement the codec**

Use a versioned private payload record with `version`, `queryFingerprint`, `pageSize`, `bucketMonth`, `driverPagingState` and `expiresAt`. Canonicalize criteria as four length-prefixed fields, hash with SHA-256 and base64url-encode the digest. Sign exact JSON payload bytes using `Mac.getInstance("HmacSHA256")`. Split tokens on exactly one period, decode both parts with `Base64.getUrlDecoder()`, verify with `MessageDigest.isEqual`, deserialize, validate version, fingerprint, page size, expiry and `bucketMonth <= YearMonth.now(clock.withZone(UTC))`.

- [ ] **Step 5: Run the focused test and capture GREEN**

```bash
./gradlew :order-history-service:test --tests '*OrderHistoryPagingTokenCodecTest'
```

Expected: all codec tests pass.

- [ ] **Step 6: Commit**

```bash
git add order-history-service/src/main/java/net/ftgo/orderhistory/service/OrderHistoryPageCursor.java \
        order-history-service/src/main/java/net/ftgo/orderhistory/service/OrderHistoryPagingTokenCodec.java \
        order-history-service/src/test/java/net/ftgo/orderhistory/service/OrderHistoryPagingTokenCodecTest.java
git commit -m "feat: sign order history paging tokens"
```

---

### Task 2: Verify Tokens Before Store Access

**Files:**
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/service/OrderHistoryQueryStore.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/service/OrderHistoryQueryService.java`
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/service/InMemoryOrderHistoryQueryStore.java`
- Modify: `order-history-service/src/test/java/net/ftgo/orderhistory/api/OrderHistoryPagingIntegrationTest.java`
- Create: `order-history-service/src/test/java/net/ftgo/orderhistory/service/OrderHistoryQueryServicePagingSecurityTest.java`

**Interfaces:**
- `OrderHistoryQueryStore.fetch(criteria, pageSize, OrderHistoryPageCursor cursor)` returns `QueryPage(records, OrderHistoryPageCursor nextCursor)`.
- `OrderHistoryQueryService` depends on `OrderHistoryPagingTokenCodec` and a UTC `Clock` only through the codec.

- [ ] **Step 1: Write failing service-security tests**

Use a counting fake store. Generate a valid token, then assert that changing each query field or page size throws `IllegalArgumentException` and leaves `fetchCalls == 0`. Generate a correctly signed future-month token through `codec.encode(...)`, call `queryService.query(...)`, and assert `fetchCalls == 0`.

- [ ] **Step 2: Run the focused tests and capture RED**

```bash
./gradlew :order-history-service:test \
  --tests '*OrderHistoryQueryServicePagingSecurityTest' \
  --tests '*OrderHistoryPagingIntegrationTest'
```

Expected: compilation failure because the store still exchanges raw strings and the service has no codec.

- [ ] **Step 3: Refactor the store contract**

Replace raw paging-state strings with `OrderHistoryPageCursor`. Preserve `QueryPage.hasMore()` by checking `nextCursor != null`.

- [ ] **Step 4: Refactor the query service**

Validate page size and criteria first. For a blank token, pass `null` to the store. Otherwise call `codec.decode(...)` before `queryStore.fetch(...)`. Encode a non-null returned cursor using the same criteria and page size, preserving the API response property name `nextPagingState`.

- [ ] **Step 5: Adapt the in-memory store**

Decode/encode the existing offset only in `driverPagingState`. Use `YearMonth.now(ZoneOffset.UTC)` as the cursor month for deterministic API paging contracts. No public unsigned token may be produced by the store.

- [ ] **Step 6: Update paging integration setup**

Construct `OrderHistoryQueryService` with a codec using a fixed non-blank test secret and fixed clock. Preserve the existing 20/20/15 gap-and-duplicate assertions.

- [ ] **Step 7: Run focused tests and capture GREEN**

```bash
./gradlew :order-history-service:test \
  --tests '*OrderHistoryPagingTokenCodecTest' \
  --tests '*OrderHistoryQueryServicePagingSecurityTest' \
  --tests '*OrderHistoryPagingIntegrationTest'
```

Expected: all tests pass and invalid cursors cause zero fake-store calls.

- [ ] **Step 8: Commit**

```bash
git add order-history-service/src/main/java/net/ftgo/orderhistory/service \
        order-history-service/src/test/java/net/ftgo/orderhistory/api/OrderHistoryPagingIntegrationTest.java \
        order-history-service/src/test/java/net/ftgo/orderhistory/service/OrderHistoryQueryServicePagingSecurityTest.java
git commit -m "refactor: validate paging tokens before query access"
```

---

### Task 3: Enforce Cassandra Bucket Budget

**Files:**
- Modify: `order-history-service/src/main/java/net/ftgo/orderhistory/service/CassandraOrderHistoryQueryStore.java`
- Create: `order-history-service/src/test/java/net/ftgo/orderhistory/service/CassandraOrderHistoryQueryStoreTest.java`

**Interfaces:**
- Constructor adds `@Value("${ftgo.order-history.max-bucket-reads:24}") int maxBucketReads`.
- The store consumes and returns `OrderHistoryPageCursor` directly.

- [ ] **Step 1: Write a failing 24-bucket test**

Mock all three repositories. For a consumer-only query, return an empty `Slice` for every bucket. Use `historyMonths = 120` and `maxBucketReads = 24`. Assert exactly 24 calls to `consumerRepository.findPage(...)`, zero calls to the other repositories, and a non-null continuation cursor at the first unread month.

Add a test starting from a supplied driver state to prove the first bucket receives the Cassandra page request and subsequent buckets clear it.

- [ ] **Step 2: Run the focused test and capture RED**

```bash
./gradlew :order-history-service:test --tests '*CassandraOrderHistoryQueryStoreTest'
```

Expected: constructor/signature mismatch or more than 24 repository calls.

- [ ] **Step 3: Remove unsigned cursor encoding from the Cassandra store**

Delete `encodeCursor` and `decodeCursor`. Start from `cursor.bucketMonth()` and `cursor.driverPagingState()` when a cursor is supplied; otherwise start at the current UTC month with a null driver state.

- [ ] **Step 4: Add the bucket counter**

Increment before each repository query. Stop when `bucketReads == maxBucketReads`. If more eligible buckets remain, return a continuation cursor at the current first unread month with null driver state. Preserve same-month driver continuation when `slice.hasNext()`.

- [ ] **Step 5: Run the focused test and capture GREEN**

```bash
./gradlew :order-history-service:test --tests '*CassandraOrderHistoryQueryStoreTest'
```

Expected: all tests pass and repository verification reports exactly 24 maximum calls.

- [ ] **Step 6: Commit**

```bash
git add order-history-service/src/main/java/net/ftgo/orderhistory/service/CassandraOrderHistoryQueryStore.java \
        order-history-service/src/test/java/net/ftgo/orderhistory/service/CassandraOrderHistoryQueryStoreTest.java
git commit -m "fix: bound order history bucket reads"
```

---

### Task 4: Wire Configuration and HTTP Regression Coverage

**Files:**
- Modify: `order-history-service/src/main/resources/application.yml`
- Modify: `deployment/tests/docker-compose.core-order-flow.yml`
- Modify: `order-history-service/src/test/java/net/ftgo/orderhistory/api/OrderHistoryControllerAuthorizationTest.java`
- Create: `order-history-service/src/test/java/net/ftgo/orderhistory/api/OrderHistoryPagingTokenMvcTest.java`
- Modify: `.github/workflows/phase-03-order-history.yml`

**Interfaces:**
- Environment variable: `FTGO_ORDER_HISTORY_PAGING_SECRET`.
- Properties: `ftgo.order-history.paging-secret`, `ftgo.order-history.paging-token-ttl`, `ftgo.order-history.max-bucket-reads`.

- [ ] **Step 1: Write failing MVC tests**

Use `@WebMvcTest(OrderHistoryController.class)` with mocked authorization/repository and a real query service plus codec. Assert a tampered token and a token generated for a different status return HTTP 400 and JSON error code `ORDER_HISTORY_QUERY_INVALID`. Verify the mocked query store has no interactions.

- [ ] **Step 2: Run MVC tests and capture RED**

```bash
./gradlew :order-history-service:test --tests '*OrderHistoryPagingTokenMvcTest'
```

Expected: context/configuration failure until the codec is wired with a secret, or wrong response behavior before the test fixture is complete.

- [ ] **Step 3: Add application configuration**

```yaml
ftgo:
  order-history:
    paging-secret: ${FTGO_ORDER_HISTORY_PAGING_SECRET:}
    paging-token-ttl: ${FTGO_ORDER_HISTORY_PAGING_TOKEN_TTL:PT15M}
    max-bucket-reads: ${FTGO_ORDER_HISTORY_MAX_BUCKET_READS:24}
```

Inject `paging-secret` and `paging-token-ttl` into the codec constructor.

- [ ] **Step 4: Add runtime secret to the E2E compose fixture**

Set a deterministic non-production value for the Order History container so existing secured E2E contexts continue to start. Do not add a default production secret to source control.

- [ ] **Step 5: Extend the Order History workflow gate**

Run codec, query-service security, Cassandra budget and MVC token tests explicitly before the full module test.

- [ ] **Step 6: Run module regression**

```bash
FTGO_ORDER_HISTORY_PAGING_SECRET=test-order-history-paging-secret \
./gradlew :order-history-service:clean :order-history-service:test
```

Expected: all Order History tests pass.

- [ ] **Step 7: Commit**

```bash
git add order-history-service/src/main/resources/application.yml \
        deployment/tests/docker-compose.core-order-flow.yml \
        order-history-service/src/test/java/net/ftgo/orderhistory/api \
        .github/workflows/phase-03-order-history.yml
git commit -m "test: enforce signed paging token contracts"
```

---

### Task 5: Full Verification and PR Evidence

**Files:**
- Modify: `docs/superpowers/plans/2026-07-29-signed-order-history-paging-tokens.md`

- [ ] **Step 1: Run static checks for forbidden unsigned cursor code**

```bash
grep -R "encodeCursor\|decodeCursor" order-history-service/src/main/java/net/ftgo/orderhistory/service && exit 1 || true
grep -R "MessageDigest.isEqual" order-history-service/src/main/java/net/ftgo/orderhistory/service/OrderHistoryPagingTokenCodec.java
```

- [ ] **Step 2: Run the full required verification matrix on one head SHA**

Require the same head SHA to pass:

```text
Phase 03 Order History Consistency
Phase 04 Security and API
Phase 04 API Contract
Phase 01 Module Diagnostics
Phase 01 Full Gradle Verification
Phase 01 Verification
Phase 01 Fresh Stack Smoke
Phase 02 Core Order Flow
Phase 02 Core Order Flow E2E
Phase 02B Payment Settlement
Phase 03 Distributed Consistency
Phase 03 Distributed Failure E2E
Phase 03 Operations Reconciliation
```

- [ ] **Step 3: Record exact run IDs and final SHA**

Update this plan with the final SHA, workflow run IDs and acceptance checklist only after all required runs are green on that SHA.

- [ ] **Step 4: Commit verification record**

```bash
git add docs/superpowers/plans/2026-07-29-signed-order-history-paging-tokens.md
git commit -m "docs: record paging token verification"
```

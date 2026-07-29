# Signed Order History Paging Tokens Design

## Goal

Replace the unsigned Order History cursor with a short-lived, query-bound HMAC token and cap Cassandra bucket work per HTTP request.

## Token contract

The public token format is:

```text
base64url(payload).base64url(signature)
```

The payload is versioned JSON with these fields:

- `version`: integer token format version, initially `1`;
- `queryFingerprint`: SHA-256 fingerprint of the canonical consumer ID, status, restaurant ID and `since` values;
- `pageSize`: the page size used when the token was created;
- `bucketMonth`: Cassandra bucket in `yyyy-MM` UTC form;
- `driverPagingState`: nullable Cassandra driver paging state encoded as base64url;
- `expiresAt`: absolute UTC expiry as epoch seconds.

The signature is HMAC-SHA-256 over the exact payload bytes. Verification uses `MessageDigest.isEqual`. Base64url encoding omits padding.

## Configuration

- `FTGO_ORDER_HISTORY_PAGING_SECRET` supplies the signing secret and must be non-blank.
- `ftgo.order-history.paging-token-ttl` defaults to `PT15M`.
- `ftgo.order-history.max-bucket-reads` defaults to `24` and is clamped to at least `1`.

The application fails closed during component construction when the signing secret is missing or blank.

## Components

### `OrderHistoryPagingTokenCodec`

Owns canonical query fingerprinting, JSON serialization, HMAC signing, constant-time signature comparison, expiry checks, page-size/query binding and future-month rejection. It exposes an encode operation for a typed cursor and a decode operation that returns the validated cursor.

### `OrderHistoryPageCursor`

A typed internal value containing `YearMonth bucketMonth` and nullable `String driverPagingState`. The store interface uses this value instead of receiving or returning public token strings.

### `OrderHistoryQueryService`

Validates page size, validates and decodes any supplied public token before calling the query store, invokes the store with the typed cursor, then signs the returned cursor. Invalid, expired, tampered, cross-query, cross-page-size and future-month tokens fail before any Cassandra repository call.

### `CassandraOrderHistoryQueryStore`

Consumes and returns typed cursors. It performs at most `maxBucketReads` bucket queries per invocation. When the cap is reached before the logical query is exhausted, it returns a cursor pointing to the first unread bucket so the next request can continue without gaps or duplicates.

### `InMemoryOrderHistoryQueryStore`

Uses the typed cursor for deterministic contract tests. The cursor month is the current UTC month and the driver state contains the existing encoded offset.

## Error behavior

All malformed or invalid paging tokens raise `IllegalArgumentException`. Existing `OrderHistoryExceptionHandler` maps that exception to HTTP `400` with error code `ORDER_HISTORY_QUERY_INVALID`.

## Test strategy

- Codec tests cover valid round trip, one-byte tampering, expiry, malformed base64, blank secret and query/page-size binding.
- Query-service tests prove invalid and future-month tokens do not invoke the store.
- Cassandra-store tests prove no request performs more than 24 bucket reads and continuation starts at the first unread bucket.
- MVC tests prove tampered and cross-query tokens return `400 ORDER_HISTORY_QUERY_INVALID`.
- Existing stable paging tests continue to prove no gaps or duplicates.

## Non-goals

- Key rotation and multi-key verification are deferred.
- Server-side cursor persistence is not introduced.
- The response field remains `nextPagingState` for API compatibility.

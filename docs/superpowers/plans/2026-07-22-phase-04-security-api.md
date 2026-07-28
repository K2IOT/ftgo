# FTGO Phase 04 Security and API Implementation Plan

**Status:** Complete.

**Verified runtime code SHA:** `8542a713d28c004d8b5e68c946d5cc67a41d7756`

This file is the completed execution record for Phase 04. The commit that marks this plan complete changes documentation only; runtime verification was completed against the code SHA above.

## Goal

Ngăn forged identity, BOLA/IDOR và direct-service bypass; chuẩn hóa public API, validation, error contract, gateway behavior và actuator exposure.

## Delivered Architecture

Gateway remains the edge policy-enforcement point, while every downstream HTTP service is also an OAuth2 Resource Server. Consumer, restaurant and courier identities are derived from signed JWT claims. Ownership checks run in application/service layers, not only in route matchers. Internal endpoints require the internal audience and service role.

## Global Constraints

- [x] Do not trust client-supplied `consumerId`, `restaurantId`, `courierId` or role headers.
- [x] Do not use unsigned internal identity headers.
- [x] Downstream services validate JWT issuer, audience and expiry independently.
- [x] Ownership checks run in the application/service layer.
- [x] Public errors do not expose stack traces, SQL, tokens, PII or internal hosts.

---

## Task 1: Shared JWT Principal Model

- [x] Added `FtgoPrincipal` with subject, consumer, restaurant, courier, role and audience claims.
- [x] Added deterministic JWT claim conversion and principal access helpers.
- [x] Added issuer/audience-aware blocking and reactive JWT decoder helpers.
- [x] Covered consumer, restaurant, courier, admin, malformed claims and missing audiences.
- [x] Verified shared JWT principal contracts.

Key files:

- `common/src/main/java/net/ftgo/common/security/FtgoPrincipal.java`
- `common/src/main/java/net/ftgo/common/security/FtgoJwtAuthenticationConverter.java`
- `common/src/main/java/net/ftgo/common/security/PrincipalAccess.java`
- `common/src/test/java/net/ftgo/common/security/FtgoJwtAuthenticationConverterTest.java`

---

## Task 2: Secure Every HTTP Service as a Resource Server

- [x] Added Spring Security resource-server dependencies and configuration to Order, Consumer, Restaurant, Kitchen, Accounting, Delivery and Order History.
- [x] Configured issuer, JWKS and explicit public/internal audience validation.
- [x] Enabled safe bearer-token relay through the Gateway.
- [x] Protected `/internal/**` with the internal audience and service role.
- [x] Kept anonymous access limited to liveness/readiness and selected restaurant reads.
- [x] Added security smoke tests for every downstream service.
- [x] Verified direct unauthenticated, expired, wrong-issuer, wrong-audience and insufficient-role requests are rejected.

---

## Task 3: Derive and Enforce Consumer Identity

- [x] Removed trusted use of body-supplied `consumerId` for order creation.
- [x] Derived consumer identity from the authenticated principal.
- [x] Enforced order ownership for get, cancel and revise operations in the service layer.
- [x] Restricted consumer profile read/update to the owner or admin.
- [x] Prevented registration callers from setting privileged credit policy.
- [x] Moved credit-limit changes to an explicit admin endpoint.
- [x] Secured API composition before ticket/delivery information is fetched.
- [x] Added cross-consumer BOLA/IDOR tests and trusted-identity tests.

---

## Task 4: Enforce Restaurant Ownership

- [x] Added restaurant authorization based on persisted resource ownership and JWT `restaurant_ids` claims.
- [x] Made restaurant creation admin-only.
- [x] Enforced restaurant ownership before menu mutations.
- [x] Derived ticket restaurant ownership from the persisted ticket.
- [x] Added cross-restaurant controller, service and authorization tests.

---

## Task 5: Enforce Courier Assignment and Delivery Ownership

- [x] Removed trusted courier identity from assignment request bodies.
- [x] Derived courier identity from JWT claims.
- [x] Implemented atomic delivery claim semantics with exactly one winner.
- [x] Enforced courier ownership for read, pickup and delivery transitions.
- [x] Added concurrency, authorization, controller identity and service tests.

---

## Task 6: Standardize RFC 9457 Errors and Validation

- [x] Added shared `FtgoProblemDetail`, correlation filtering and web auto-configuration.
- [x] Centralized MVC exception mapping and removed controller-local exception handlers.
- [x] Added stable status/error codes without leaking internal exception details.
- [x] Added `/api/v1/**` public aliases and one-release deprecation metadata for legacy paths.
- [x] Added positive ID, item-count, quantity, address/text and payment-token limits.
- [x] Added a configurable maximum delivery scheduling window.
- [x] Added safe idempotency-key and 256 KiB request-size contracts.
- [x] Added stable `415 application/problem+json` handling.
- [x] Added Gateway edge validation so unsupported mutation media types remain `415` even when a downstream circuit is open.
- [x] Added API contract and validation tests.

Key files:

- `common/src/main/java/net/ftgo/common/web/FtgoProblemDetail.java`
- `common/src/main/java/net/ftgo/common/web/GlobalExceptionHandler.java`
- `common/src/main/java/net/ftgo/common/web/CorrelationIdFilter.java`
- `common/src/main/java/net/ftgo/common/web/RequestLimits.java`
- `api-gateway/src/main/java/net/ftgo/gateway/filter/ApiVersioningFilter.java`
- `api-gateway/src/main/java/net/ftgo/gateway/filter/JsonContentTypeFilter.java`

---

## Task 7: Harden Gateway and Actuator Exposure

- [x] Allowed anonymous liveness/readiness without component details.
- [x] Protected full health, metrics and Gateway actuator introspection.
- [x] Set health details to `when_authorized` across services.
- [x] Applied 256 KiB body and in-memory limits.
- [x] Restricted retries to idempotent GET requests.
- [x] Added trusted forwarded-header handling and subject-based rate-limit identity.
- [x] Prevented spoofed forwarding headers from bypassing rate limits.
- [x] Updated Fresh Stack and Distributed Failure harnesses to use public liveness probes.
- [x] Added Gateway hardening, token-relay, versioning and media-type tests.

---

## Task 8: Security E2E and Abuse Tests

- [x] Added a real RS256 test identity provider with discovery and JWKS endpoints.
- [x] Added authorization tests through the Gateway and direct service ports.
- [x] Covered forged body identity and cross-tenant resource IDs.
- [x] Covered expired, malformed, wrong-audience and unsigned JWTs.
- [x] Covered oversized bodies and unsupported content types.
- [x] Covered duplicate idempotency keys with changed payloads.
- [x] Covered malicious forwarded headers and rate-limit bypass attempts.
- [x] Verified no unauthorized durable mutation occurs.
- [x] Added concise CI artifact diagnostics for real-process failures.
- [x] Ran full Phase 04 and regression verification.

---

## Verification Evidence

All required workflows completed successfully on runtime code SHA `8542a713d28c004d8b5e68c946d5cc67a41d7756`:

- [x] Phase 04 API Contract — run `30339302457`.
- [x] Phase 04 Security and API — run `30339302694`.
- [x] Phase 04 Web Diagnostic — run `30339302517`.
- [x] Phase 01 Verification — run `30339302525`.
- [x] Phase 01 Module Diagnostics — run `30339302536`.
- [x] Phase 01 Full Gradle Verification — run `30339302485`.
- [x] Phase 01 Fresh Stack Smoke, two clean-volume cycles — run `30339302531`.
- [x] Phase 02 Core Order Flow contracts and full tests — run `30339302461`.
- [x] Phase 02 Core Order Flow E2E, two clean-state cycles — run `30339302553`.
- [x] Phase 02B Payment Settlement unit/contracts and two clean-state E2E cycles — run `30339302530`.
- [x] Phase 03 Distributed Consistency — run `30339302561`.
- [x] Phase 03 Distributed Failure E2E, two clean-state cycles — run `30339302478`.
- [x] Phase 03 Operations Reconciliation — run `30339302586`.
- [x] Phase 03 Order History Consistency — run `30339302452`.

## Phase Completion Checklist

- [x] Every HTTP service validates JWT independently.
- [x] Consumer, restaurant and courier ownership is enforced in the service layer.
- [x] Client-controlled identity fields are removed or ignored for mutation APIs.
- [x] Internal endpoints require service audience and role.
- [x] RFC 9457 errors and validation are consistent.
- [x] Gateway and actuator exposure are hardened.
- [x] Security E2E proves unauthorized requests cannot mutate durable state.
- [x] Full unit, contract, smoke and distributed regression gates are green.

## Deferred Platform Work

Kubernetes NetworkPolicy, production IdP deployment automation, secret distribution and broader platform observability remain Phase 05 platform responsibilities. Application-level authentication, authorization and internal-audience enforcement required by Phase 04 are complete.

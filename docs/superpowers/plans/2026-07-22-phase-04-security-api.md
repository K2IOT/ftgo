# FTGO Phase 04 Security and API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ngăn forged identity, BOLA/IDOR và direct-service bypass; chuẩn hóa public API, validation, error contract và actuator exposure.

**Architecture:** Gateway tiếp tục là edge policy enforcement nhưng mọi downstream HTTP service cũng trở thành OAuth2 Resource Server và tự xác minh ownership. Consumer/restaurant/courier identity lấy từ JWT claims; internal endpoints được bảo vệ bằng service audience/role và network policy.

**Tech Stack:** Spring Security OAuth2 Resource Server, JWT, Spring Cloud Gateway TokenRelay, Bean Validation, RFC 9457 ProblemDetail, WireMock, Testcontainers, Kubernetes NetworkPolicy.

## Global Constraints

- Không tin `consumerId`, `restaurantId`, `courierId` hoặc role header do client gửi.
- Không dùng unsigned internal identity headers.
- Downstream service phải xác minh JWT issuer, audience và expiry.
- Mọi ownership check nằm trong application/service layer, không chỉ controller matcher.
- Public error không chứa stack trace, SQL, token, PII hoặc internal host.

---

### Task 1: Add Shared JWT Principal Model

**Files:**
- Create: `common/src/main/java/net/ftgo/common/security/FtgoPrincipal.java`
- Create: `common/src/main/java/net/ftgo/common/security/FtgoJwtAuthenticationConverter.java`
- Create: `common/src/main/java/net/ftgo/common/security/PrincipalAccess.java`
- Create: `common/src/test/java/net/ftgo/common/security/FtgoJwtAuthenticationConverterTest.java`
- Modify: `build.gradle`

**Interfaces:**

```java
public record FtgoPrincipal(
    String subject,
    Long consumerId,
    Set<Long> restaurantIds,
    Long courierId,
    Set<String> roles,
    Set<String> audiences
) {}
```

- [ ] **Step 1: Write claim mapping tests**

Cover:

- consumer token with `consumer_id`
- restaurant token with `restaurant_ids`
- courier token with `courier_id`
- admin token
- malformed numeric claim
- missing required audience

- [ ] **Step 2: Add security dependencies to shared compilation scope**

Use Spring Security APIs in `common`, while each service adds the resource-server starter explicitly.

- [ ] **Step 3: Implement converter**

Map Keycloak/issuer roles from configured claim names without accepting arbitrary `X-Roles` headers. Reject invalid claim types with authentication failure.

- [ ] **Step 4: Run tests**

```bash
./gradlew :common:test --tests '*FtgoJwtAuthenticationConverterTest'
```

Expected: all identity forms mapped deterministically; malformed claim rejected.

- [ ] **Step 5: Commit**

```bash
git add common build.gradle
git commit -m "feat: define authenticated ftgo principal"
```

---

### Task 2: Secure Every HTTP Service as a Resource Server

**Files:**
- Create one `SecurityConfiguration.java` under each service `config` package.
- Modify: each service `src/main/resources/application.yml`.
- Modify: `api-gateway/src/main/resources/application.yml`.
- Modify: `api-gateway/src/main/java/net/ftgo/gateway/security/SecurityConfiguration.java`.
- Create: `common/src/testFixtures/java/net/ftgo/testsupport/JwtTestFactory.java`
- Create: one context/security smoke test per service.

**Interfaces:**
- Public audience: `ftgo-api`.
- Internal audience: `ftgo-internal`.
- Gateway relays bearer token to routed downstream requests.

- [ ] **Step 1: Add resource-server dependencies**

For Order, Consumer, Restaurant, Kitchen, Accounting, Delivery and Order History:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.security:spring-security-oauth2-resource-server'
implementation 'org.springframework.security:spring-security-oauth2-jose'
```

- [ ] **Step 2: Write unauthorized/direct-service tests**

Assert:

- no token -> 401
- expired/wrong issuer/wrong audience -> 401
- valid token without role -> 403
- health liveness/readiness -> allowed with hidden details

- [ ] **Step 3: Configure issuer and audience validators**

Each service creates `JwtDecoder` with issuer validation plus explicit audience validator. Do not rely on Gateway validation alone.

- [ ] **Step 4: Enable token relay at Gateway**

Add `TokenRelay` for service routes. Verify Authorization header is replaced/relayed safely and not logged.

- [ ] **Step 5: Separate internal paths**

Endpoints under `/internal/**` require `ftgo-internal` audience and `ROLE_SERVICE`; public user tokens cannot call them.

- [ ] **Step 6: Run service security smoke tests**

```bash
./gradlew test --tests '*SecuritySmokeTest'
```

Expected: every service rejects direct unauthenticated access.

- [ ] **Step 7: Commit**

```bash
git add build.gradle common api-gateway */src/main/java/*/config */src/main/resources/application.yml */src/test
git commit -m "feat: authenticate every http service"
```

---

### Task 3: Derive Consumer Identity from JWT

**Files:**
- Modify: `order-service/src/main/java/net/ftgo/order/api/CreateOrderRequest.java`
- Modify: `order-service/src/main/java/net/ftgo/order/api/OrderController.java`
- Modify: `order-service/src/main/java/net/ftgo/order/service/OrderService.java`
- Modify: `consumer-service/src/main/java/net/ftgo/consumer/api/ConsumerController.java`
- Create: `order-service/src/main/java/net/ftgo/order/security/OrderAuthorizationService.java`
- Create: `order-service/src/test/java/net/ftgo/order/security/OrderAuthorizationIntegrationTest.java`
- Modify: `api-gateway/src/main/java/net/ftgo/gateway/controller/OrderDetailsController.java`

**Interfaces:**
- `OrderAuthorizationService.requireOwner(Long orderId, FtgoPrincipal principal)`.
- Admin bypass is explicit and audit logged.

- [ ] **Step 1: Write BOLA tests**

Create order owned by consumer 101. Assert token for consumer 202 receives `403` for get/cancel/revise/order-details/history.

- [ ] **Step 2: Remove `consumerId` from Create Order body**

Controller obtains:

```java
FtgoPrincipal principal = PrincipalAccess.require(authentication);
Long consumerId = principal.consumerId();
```

Reject consumer role without `consumer_id` claim.

- [ ] **Step 3: Enforce owner in service layer**

`getOrder`, `cancelOrder` and `reviseOrder` accept principal/actor ID or call an authorization service before mutation. Do not rely solely on URL matcher.

- [ ] **Step 4: Restrict consumer profile operations**

Consumer can read/update own profile only; registration endpoint must not allow caller to set privileged fields or arbitrary credit limit. Admin-only credit limit operations move to `/admin/consumers/{id}/credit-limit`.

- [ ] **Step 5: Secure API composition**

Order Details Controller verifies ownership before issuing downstream calls, or calls Order Service first and uses its authorization result. Partial response must never expose unauthorized ticket/delivery data.

- [ ] **Step 6: Run tests**

```bash
./gradlew :order-service:test --tests '*OrderAuthorizationIntegrationTest'
./gradlew :consumer-service:test --tests '*Authorization*'
./gradlew :api-gateway:test --tests '*OrderDetails*Security*'
```

Expected: cross-consumer access always 403; own resource succeeds.

- [ ] **Step 7: Commit**

```bash
git add order-service consumer-service api-gateway
git commit -m "fix: derive and enforce consumer ownership"
```

---

### Task 4: Enforce Restaurant Ownership

**Files:**
- Create: `restaurant-service/src/main/java/net/ftgo/restaurant/security/RestaurantAuthorizationService.java`
- Modify: `restaurant-service/src/main/java/net/ftgo/restaurant/api/RestaurantController.java`
- Modify: `kitchen-service/src/main/java/net/ftgo/kitchen/api/KitchenController.java`
- Create: `kitchen-service/src/main/java/net/ftgo/kitchen/security/TicketAuthorizationService.java`
- Create: `restaurant-service/src/test/java/net/ftgo/restaurant/security/RestaurantOwnershipIntegrationTest.java`
- Create: `kitchen-service/src/test/java/net/ftgo/kitchen/security/TicketOwnershipIntegrationTest.java`

**Interfaces:**
- `requireRestaurantAccess(restaurantId, principal)` accepts admin or membership in `restaurantIds` claim.
- Ticket access derives restaurant ID from persisted ticket, not request header.

- [ ] **Step 1: Write cross-restaurant tests**

Token for restaurant 10 attempts update/delete menu at restaurant 20 and accept ticket belonging to restaurant 20. Expect `403` and no state change.

- [ ] **Step 2: Apply ownership before mutations**

Controller/application service loads resource, checks persisted restaurant ID, then mutates. Avoid check-then-use race by running authorization and mutation in one transaction.

- [ ] **Step 3: Restrict restaurant creation**

Choose explicit policy:

- Admin creates restaurant and assigns owner; or
- Restaurant principal may create one restaurant and receives assignment through identity administration.

Baseline implementation uses admin-only `POST /restaurants`; restaurant role manages assigned IDs only.

- [ ] **Step 4: Run tests**

```bash
./gradlew :restaurant-service:test --tests '*RestaurantOwnershipIntegrationTest'
./gradlew :kitchen-service:test --tests '*TicketOwnershipIntegrationTest'
```

Expected: only assigned restaurant resources mutate.

- [ ] **Step 5: Commit**

```bash
git add restaurant-service kitchen-service
git commit -m "fix: enforce restaurant resource ownership"
```

---

### Task 5: Enforce Courier Assignment and Delivery Ownership

**Files:**
- Create: `delivery-service/src/main/java/net/ftgo/delivery/security/DeliveryAuthorizationService.java`
- Modify: `delivery-service/src/main/java/net/ftgo/delivery/api/DeliveryController.java`
- Modify: `delivery-service/src/main/java/net/ftgo/delivery/domain/Delivery.java`
- Modify: `delivery-service/src/main/java/net/ftgo/delivery/repository/DeliveryRepository.java`
- Create: `delivery-service/src/test/java/net/ftgo/delivery/security/DeliveryOwnershipIntegrationTest.java`

**Interfaces:**
- Assignment policy: unassigned delivery may be claimed only through atomic compare-and-set endpoint, or assigned by dispatcher/admin.
- Pickup/deliver require `delivery.courierId == principal.courierId`.

- [ ] **Step 1: Write assignment race tests**

Two courier tokens claim the same delivery concurrently; exactly one succeeds. Losing courier gets `409` or `403` according to final state.

- [ ] **Step 2: Remove courier ID from assignment body**

Courier identity comes from token. If dispatcher assignment is required, expose separate admin/dispatcher endpoint with explicit target courier ID.

- [ ] **Step 3: Add optimistic version/CAS**

Ensure Delivery has `@Version`. Claim transition only from unassigned state and persists principal courier ID.

- [ ] **Step 4: Enforce pickup/deliver owner**

Check persisted courier assignment in same transaction as state transition.

- [ ] **Step 5: Run tests**

```bash
./gradlew :delivery-service:test --tests '*DeliveryOwnershipIntegrationTest'
```

Expected: one claim winner; non-owner cannot pickup/deliver/read sensitive route details.

- [ ] **Step 6: Commit**

```bash
git add delivery-service
git commit -m "fix: enforce courier delivery ownership"
```

---

### Task 6: Standardize RFC 9457 Errors and Validation

**Files:**
- Create: `common/src/main/java/net/ftgo/common/web/FtgoProblemDetail.java`
- Create: `common/src/main/java/net/ftgo/common/web/GlobalExceptionHandler.java`
- Create: `common/src/main/java/net/ftgo/common/web/CorrelationIdFilter.java`
- Create: `common/src/main/java/net/ftgo/common/web/RequestLimits.java`
- Replace controller-local exception handlers across all MVC services.
- Modify: `api-gateway/src/main/java/net/ftgo/gateway/controller/FallbackController.java`
- Create: `common/src/testFixtures/java/net/ftgo/testsupport/ProblemDetailContract.java`
- Create: API contract tests in each service.

**Interfaces:**

```json
{
  "type": "https://ftgo.example/problems/order-not-found",
  "title": "Order not found",
  "status": 404,
  "detail": "Order 123 does not exist",
  "instance": "/api/v1/orders/123",
  "errorCode": "ORDER_NOT_FOUND",
  "correlationId": "..."
}
```

- [ ] **Step 1: Write shared error contract tests**

Verify content type, mandatory fields, correlation ID propagation and no stack trace/internal exception class.

- [ ] **Step 2: Add validation constraints**

Apply exact baseline:

- IDs positive
- delivery time future and within configured maximum scheduling window
- item count 1..50
- quantity 1..100
- address and text length limits
- idempotency key 8..255 printable safe characters
- request body limit 256 KiB for business APIs

- [ ] **Step 3: Centralize exception mapping**

Map domain/business exceptions to stable codes and status. Unexpected exception returns generic 500 and logs correlation ID server-side.

- [ ] **Step 4: Add API version prefix**

Expose public routes under `/api/v1/**`. Gateway may temporarily keep old routes with deprecation headers for one release; internal routes remain `/internal/**`.

- [ ] **Step 5: Run contract tests**

```bash
./gradlew test --tests '*ProblemDetail*' --tests '*Validation*Contract*'
```

Expected: all services return the same error shape.

- [ ] **Step 6: Commit**

```bash
git add common api-gateway */src/main/java/*/api */src/test
git commit -m "feat: standardize api validation and errors"
```

---

### Task 7: Harden Gateway and Actuator Exposure

**Files:**
- Modify: `api-gateway/src/main/resources/application.yml`
- Modify: every service `src/main/resources/application.yml`.
- Modify: `api-gateway/src/main/java/net/ftgo/gateway/config/GatewayConfiguration.java`
- Create: `api-gateway/src/main/java/net/ftgo/gateway/security/ForwardedHeaderPolicy.java`
- Create: `api-gateway/src/test/java/net/ftgo/gateway/security/GatewayHardeningIntegrationTest.java`

**Interfaces:**
- Public anonymous endpoints: readiness/liveness only and selected public restaurant browsing.
- Full actuator, gateway route introspection and metrics require admin/internal access.

- [ ] **Step 1: Write exposure tests**

Anonymous calls:

- `/actuator/health/liveness` -> 200 without component details
- `/actuator/health` -> 401/403 or sanitized
- `/actuator/gateway/routes` -> 401/403
- spoofed `X-Forwarded-For` from untrusted peer does not bypass rate-limit identity

- [ ] **Step 2: Set health detail policy**

Use `show-details: when_authorized`. Separate liveness/readiness groups and exclude optional downstream dependencies from liveness.

- [ ] **Step 3: Tighten request size and timeouts**

Set route-specific body limit 256 KiB for JSON APIs, shorter connection/read timeout where appropriate and maximum in-memory WebFlux buffer.

- [ ] **Step 4: Define trusted proxy handling**

Only honor forwarded headers from ingress/service mesh trust boundary. Rate-limit fallback uses normalized remote address only when unauthenticated route is allowed.

- [ ] **Step 5: Run tests**

```bash
./gradlew :api-gateway:test --tests '*GatewayHardeningIntegrationTest'
```

Expected: actuator data hidden and spoofed forwarding headers ignored.

- [ ] **Step 6: Commit**

```bash
git add api-gateway */src/main/resources/application.yml
git commit -m "fix: harden gateway and actuator exposure"
```

---

### Task 8: Security E2E and Abuse Tests

**Files:**
- Create: `e2e-tests/src/test/java/net/ftgo/e2e/SecurityAuthorizationTest.java`
- Create: `e2e-tests/src/test/java/net/ftgo/e2e/ApiAbuseTest.java`
- Create: `e2e-tests/src/test/java/net/ftgo/e2e/support/TestIdentityProvider.java`

**Interfaces:**
- Test identity provider issues signed JWTs for consumer, restaurant, courier, admin and service audiences.

- [ ] **Step 1: Implement authorization matrix**

Test every role/resource combination for allow/deny, including direct calls to service ports in test network.

- [ ] **Step 2: Implement abuse scenarios**

- forged body consumer ID
- cross-tenant IDs
- expired/wrong-audience token
- unsigned token
- oversized body
- duplicate idempotency key with changed payload
- malicious forwarded headers
- unsupported content type

- [ ] **Step 3: Run suite**

```bash
./gradlew :e2e-tests:test --tests '*SecurityAuthorizationTest' --tests '*ApiAbuseTest'
```

Expected: every unauthorized scenario denied and no durable state mutation occurs.

- [ ] **Step 4: Run full phase verification**

```bash
./gradlew clean test
```

Expected: exit code `0`.

- [ ] **Step 5: Commit**

```bash
git add e2e-tests
git commit -m "test: verify ownership and api abuse defenses"
```

## Phase Completion Checklist

- [ ] Every HTTP service validates JWT independently.
- [ ] Consumer, restaurant and courier ownership enforced in service layer.
- [ ] Client-controlled identity fields removed from mutation APIs.
- [ ] Internal endpoints require service audience.
- [ ] RFC 9457 errors and validation are consistent.
- [ ] Gateway/actuator exposure is hardened.
- [ ] Security E2E suite proves no unauthorized mutation.

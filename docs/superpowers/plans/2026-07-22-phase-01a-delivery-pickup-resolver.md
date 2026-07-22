# FTGO Phase 01A Delivery Pickup Resolver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cung cấp implementation cụ thể cho `RestaurantPickupAddressResolver` để Delivery Service khởi động và xử lý `OrderApproved` trước khi Phase 02A chuyển sang event snapshot.

**Architecture:** Restaurant Service expose internal read-only pickup-address endpoint; Delivery Service dùng bounded HTTP client implementation của resolver. Đây là compatibility bridge có test và timeout rõ ràng, không phải kiến trúc cuối cùng.

**Tech Stack:** Spring MVC, WebClient/RestClient, Resilience4j, JWT internal audience, WireMock, Testcontainers.

## Global Constraints

- Plan này **thay thế Task 5** trong `2026-07-22-phase-01-runtime-foundation.md`.
- Không xóa `RestaurantPickupAddressResolver` trong Phase 01.
- Không fallback sang địa chỉ giả hoặc null.
- Phase 02A phải xóa dependency đồng bộ này sau khi pickup address được snapshot vào event.

---

### Task 1: Expose Internal Restaurant Pickup Address

**Files:**
- Create: `restaurant-service/src/main/java/net/ftgo/restaurant/api/internal/RestaurantLocationController.java`
- Create: `restaurant-service/src/main/java/net/ftgo/restaurant/api/internal/RestaurantPickupAddressResponse.java`
- Modify: `restaurant-service/src/main/java/net/ftgo/restaurant/service/RestaurantService.java`
- Create: `restaurant-service/src/test/java/net/ftgo/restaurant/api/internal/RestaurantLocationControllerTest.java`

**Interfaces:**
- `GET /internal/restaurants/{restaurantId}/pickup-address`.
- Response: `{ "restaurantId": 1, "address": { ... } }`.

- [ ] **Step 1: Write failing endpoint tests**

Assert existing restaurant returns persisted `Address`; missing restaurant returns RFC 9457 `404 RESTAURANT_NOT_FOUND`.

- [ ] **Step 2: Implement read-only endpoint**

Use `RestaurantService.findRestaurant(restaurantId)` and map only ID/address. Do not expose menu, owner or internal timestamps.

- [ ] **Step 3: Restrict endpoint contract**

Until Phase 04 enables JWT internal audience, bind endpoint to internal network profile and document the Phase 04 security dependency. It must not be routed by API Gateway public routes.

- [ ] **Step 4: Run tests**

```bash
./gradlew :restaurant-service:test --tests '*RestaurantLocationControllerTest'
```

Expected: exact address round-trip and 404 contract pass.

- [ ] **Step 5: Commit**

```bash
git add restaurant-service
git commit -m "feat: expose internal restaurant pickup address"
```

---

### Task 2: Implement HTTP Restaurant Pickup Address Resolver

**Files:**
- Keep: `delivery-service/src/main/java/net/ftgo/delivery/messaging/RestaurantPickupAddressResolver.java`
- Create: `delivery-service/src/main/java/net/ftgo/delivery/messaging/HttpRestaurantPickupAddressResolver.java`
- Create: `delivery-service/src/main/java/net/ftgo/delivery/config/RestaurantClientConfiguration.java`
- Modify: `delivery-service/src/main/resources/application.yml`
- Create: `delivery-service/src/test/java/net/ftgo/delivery/messaging/HttpRestaurantPickupAddressResolverTest.java`
- Modify: `delivery-service/src/test/java/net/ftgo/delivery/DeliveryServiceIntegrationTest.java`

**Interfaces:**
- `resolvePickupAddress(Long restaurantId) -> Address`.
- Base URL property: `services.restaurant-service.url`.
- Connect timeout 1 second; response timeout 2 seconds; max 2 retries for connection/5xx only.

- [ ] **Step 1: Write failing WireMock tests**

Cover success, 404, malformed response, timeout and 503. Assert 404 is non-retryable and timeout/503 are retried at most twice.

- [ ] **Step 2: Implement resolver**

Call:

```text
GET {restaurantServiceUrl}/internal/restaurants/{restaurantId}/pickup-address
```

Map 404 to `RestaurantPickupAddressNotFoundException`; transient errors to `RestaurantServiceUnavailableException`. Never return null.

- [ ] **Step 3: Register exactly one bean**

Annotate implementation with `@Component` or create one explicit `@Bean`, not both. Add context test:

```java
assertThat(context.getBeansOfType(RestaurantPickupAddressResolver.class)).hasSize(1);
```

- [ ] **Step 4: Verify OrderEventConsumer startup**

Start Delivery Spring context with WireMock Restaurant endpoint and process one `OrderApproved`. Assert Delivery stores the persisted pickup address.

- [ ] **Step 5: Run tests**

```bash
./gradlew :delivery-service:test \
  --tests '*HttpRestaurantPickupAddressResolverTest' \
  --tests '*DeliveryServiceIntegrationTest'
```

Expected: context starts and event processing stores exact restaurant address.

- [ ] **Step 6: Commit**

```bash
git add delivery-service
git commit -m "fix: resolve delivery pickup address from restaurant"
```

## Completion Checklist

- [ ] Restaurant internal endpoint returns authoritative address.
- [ ] Delivery has exactly one resolver bean.
- [ ] No fake/null fallback exists.
- [ ] Resolver timeout and retry are bounded.
- [ ] Phase 01 fresh-stack smoke passes with real Restaurant Service.

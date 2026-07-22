# FTGO Phase 02A Pickup Address Snapshot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Snapshot restaurant pickup address during order pricing and đưa snapshot vào `OrderApproved`, sau đó loại synchronous Restaurant dependency khỏi Delivery consumer.

**Architecture:** Restaurant menu-snapshot response bao gồm pickup address cùng menu version. Order lưu address snapshot; `OrderApproved` mang pickup/delivery addresses. Delivery chỉ consume event và không gọi Restaurant trong event transaction.

**Tech Stack:** Java 21, Spring MVC/WebClient, JPA, Flyway, Kafka, Debezium, Testcontainers.

## Global Constraints

- Plan này chạy sau Task 1-2 của Phase 02 và trước business-flow E2E gate.
- Pickup address là immutable snapshot của order; thay đổi restaurant address sau đó không sửa order/delivery cũ.
- Event contract thay đổi phải có serialization compatibility test.
- Sau completion phải xóa bridge từ Phase 01A.

---

### Task 1: Extend Menu Snapshot with Pickup Address

**Files:**
- Modify: `common/src/main/java/net/ftgo/common/menu/MenuSnapshotResponse.java`
- Modify: `restaurant-service/src/main/java/net/ftgo/restaurant/service/MenuSnapshotService.java`
- Modify: `restaurant-service/src/test/java/net/ftgo/restaurant/service/MenuSnapshotServiceTest.java`
- Modify: `restaurant-service/src/test/java/net/ftgo/restaurant/api/MenuSnapshotControllerTest.java`

**Interfaces:**

```java
public record MenuSnapshotResponse(
    Long restaurantId,
    long menuVersion,
    Address pickupAddress,
    List<MenuItemSnapshot> items
) {}
```

- [ ] **Step 1: Write failing snapshot test**

Assert response address equals Restaurant aggregate address and survives JSON round-trip.

- [ ] **Step 2: Populate address in same read transaction**

`MenuSnapshotService` loads Restaurant and menu items, then returns restaurant address plus authoritative item data. Missing address is invariant violation and fails request.

- [ ] **Step 3: Run tests**

```bash
./gradlew :restaurant-service:test --tests '*MenuSnapshot*'
```

Expected: response contains exact pickup address.

- [ ] **Step 4: Commit**

```bash
git add common restaurant-service
git commit -m "feat: include pickup address in menu snapshot"
```

---

### Task 2: Persist Pickup Address on Order

**Files:**
- Modify: `order-service/src/main/java/net/ftgo/order/domain/Order.java`
- Modify: `order-service/src/main/java/net/ftgo/order/service/OrderService.java`
- Modify: `order-service/src/main/java/net/ftgo/order/menu/RestaurantMenuSnapshotClient.java`
- Create: `order-service/src/main/resources/db/migration/V7__add_order_pickup_address.sql`
- Create: `order-service/src/test/java/net/ftgo/order/domain/OrderPickupAddressPersistenceTest.java`

**Interfaces:**
- `Order` stores embedded `pickupAddress` separately from delivery address.
- Create Order construction requires pickup address from `MenuSnapshotResponse`.

- [ ] **Step 1: Write failing persistence round-trip test**

Persist order with different pickup and delivery addresses, clear EntityManager, reload and assert both remain distinct and equal to inputs.

- [ ] **Step 2: Add embedded address columns**

Migration adds non-null columns:

```text
pickup_address_street
pickup_address_city
pickup_address_state
pickup_address_zip_code
```

For existing development rows, migration uses a documented sentinel only in non-production profile or requires explicit data backfill script before NOT NULL. Production migration must not silently invent real pickup addresses.

- [ ] **Step 3: Construct Order from snapshot**

Order Service passes `snapshot.pickupAddress()` into aggregate and saga data. No direct Restaurant lookup occurs later in approval.

- [ ] **Step 4: Run tests**

```bash
./gradlew :order-service:test --tests '*OrderPickupAddressPersistenceTest' --tests '*RestaurantMenuSnapshotClientTest'
```

Expected: address persists and client mapping is exact.

- [ ] **Step 5: Commit**

```bash
git add order-service
git commit -m "feat: persist order pickup address snapshot"
```

---

### Task 3: Publish Pickup Address in OrderApproved

**Files:**
- Modify: `common/src/main/java/net/ftgo/common/orderflow/events/OrderApproved.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSagaLocalSteps.java`
- Modify: `common/src/test/java/net/ftgo/common/orderflow/events/OrderApprovedContractTest.java`
- Modify: `contract-tests/src/test/java/net/ftgo/contracts/DomainEventContractTest.java`

**Interfaces:**
- `OrderApproved` includes `pickupAddress`, `deliveryAddress` and `deliveryTime`.

- [ ] **Step 1: Write failing event contract test**

Serialize event and assert both addresses plus delivery time are present. Deserialize the previous release fixture through a compatibility constructor/default policy only during the compatibility window.

- [ ] **Step 2: Populate event from Order aggregate**

Approval handler reads persisted snapshot; it does not call Restaurant Service.

- [ ] **Step 3: Run contract tests**

```bash
./gradlew :common:test --tests '*OrderApprovedContractTest'
./gradlew :contract-tests:test --tests '*DomainEventContractTest'
```

Expected: current contract passes and prior fixture compatibility behavior is explicit.

- [ ] **Step 4: Commit**

```bash
git add common order-service contract-tests
git commit -m "feat: publish delivery addresses with order approval"
```

---

### Task 4: Remove Delivery's Synchronous Restaurant Dependency

**Files:**
- Modify: `delivery-service/src/main/java/net/ftgo/delivery/messaging/OrderEventConsumer.java`
- Delete: `delivery-service/src/main/java/net/ftgo/delivery/messaging/RestaurantPickupAddressResolver.java`
- Delete: `delivery-service/src/main/java/net/ftgo/delivery/messaging/HttpRestaurantPickupAddressResolver.java`
- Delete: `delivery-service/src/main/java/net/ftgo/delivery/config/RestaurantClientConfiguration.java`
- Modify: `delivery-service/src/main/resources/application.yml`
- Modify: `delivery-service/src/test/java/net/ftgo/delivery/DeliveryServiceIntegrationTest.java`
- Delete: `delivery-service/src/test/java/net/ftgo/delivery/messaging/HttpRestaurantPickupAddressResolverTest.java`

**Interfaces:**
- Delivery consumer constructs Delivery solely from `OrderApproved` payload.

- [ ] **Step 1: Write no-network event test**

Start Delivery without Restaurant URL/WireMock, consume `OrderApproved`, and assert Delivery is created with event pickup address.

- [ ] **Step 2: Replace construction**

```java
Delivery delivery = new Delivery(
    event.getOrderId(),
    event.getPickupAddress(),
    event.getDeliveryAddress(),
    event.getDeliveryTime()
);
```

- [ ] **Step 3: Remove bridge code and configuration**

Search must return no references:

```bash
git grep -n 'RestaurantPickupAddressResolver\|services.restaurant-service.url' delivery-service
```

Expected: no output.

- [ ] **Step 4: Run tests**

```bash
./gradlew :delivery-service:test --tests '*DeliveryServiceIntegrationTest' --tests '*OrderEventConsumer*'
```

Expected: Delivery context/event flow succeeds with no Restaurant dependency.

- [ ] **Step 5: Commit**

```bash
git add -A delivery-service
git commit -m "refactor: create deliveries from order snapshots"
```

## Completion Checklist

- [ ] Restaurant snapshot includes pickup address.
- [ ] Order persists immutable pickup address.
- [ ] OrderApproved carries pickup/delivery address snapshots.
- [ ] Delivery consumer performs no synchronous Restaurant call.
- [ ] Phase 01A bridge files/configuration are removed.

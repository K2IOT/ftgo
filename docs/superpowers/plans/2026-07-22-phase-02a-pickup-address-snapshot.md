# FTGO Phase 02A Pickup Address Snapshot Implementation Plan

> **Execution mode:** Implemented with `superpowers:executing-plans`, strict RED/GREEN checkpoints, systematic CI debugging, and final-SHA verification.

**Status:** Implementation complete. Final verification evidence is recorded in PR #14.

**Goal:** Capture the Restaurant pickup address during authoritative order-menu validation, persist it as an immutable Order snapshot, publish it in `OrderApproved`, and remove Delivery Service's synchronous Restaurant dependency.

## Architecture Adaptation

The original plan was written before Phase 02 replaced the proposed HTTP `MenuSnapshotResponse` flow with Eventuate command/reply validation:

```text
ValidateOrderMenuCommand
        ↓
Restaurant OrderMenuValidationService
        ↓
OrderMenuValidated
```

Phase 02A preserves the approved business behavior while integrating the pickup address into the architecture that is now present on `dev`:

```text
Restaurant aggregate address
        ↓
OrderMenuValidated.pickupAddress
        ↓
CreateOrderSagaData.pickupAddress
        ↓
existing final CreateOrderSaga local step
        ↓
Order.pickupAddress immutable snapshot
        ↓
OrderApproved.pickupAddress
        ↓
Delivery created only from the event payload
```

No service boundary was changed. The Phase 02 `CreateOrderSaga` remains at seven steps; Phase 02A does not insert a new step or shift persisted Eventuate step indexes.

## Global Constraints

- [x] Pickup address is an immutable snapshot of the order.
- [x] Restaurant address changes cannot mutate an existing Order or Delivery.
- [x] Current and previous event/reply payloads have explicit serialization compatibility tests.
- [x] No raw payment token or payment data handling was changed.
- [x] Migration does not invent pickup addresses for existing rows.
- [x] Phase 01A bridge code, endpoint, configuration, tests, and smoke wiring are removed.
- [x] The Phase 02 saga step topology is preserved for rolling upgrades.
- [x] Unsafe rollout conditions are detected by an executable fail-closed preflight.

---

## Task 1: Return Pickup Address with Authoritative Menu Validation

### Actual files

- Modify: `common/src/main/java/net/ftgo/common/orderflow/replies/OrderMenuValidated.java`
- Modify: `restaurant-service/src/main/java/net/ftgo/restaurant/service/OrderMenuValidationService.java`
- Modify: `restaurant-service/src/test/java/net/ftgo/restaurant/service/OrderMenuValidationServiceTest.java`
- Create: `common/src/test/java/net/ftgo/common/orderflow/OrderMenuValidatedPickupAddressContractTest.java`

### Completed work

- [x] Added failing test requiring the authoritative pickup address.
- [x] Added `pickupAddress` to `OrderMenuValidated`.
- [x] Preserved the previous constructor and payload compatibility.
- [x] Returned `Restaurant.address` in the same read transaction as menu validation.
- [x] Added JSON round-trip and old-payload compatibility coverage.
- [x] Verified Restaurant validation and common contract tests.

---

## Task 2: Persist Immutable Pickup Address on Order

### Actual files

- Modify: `order-service/src/main/java/net/ftgo/order/domain/Order.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSaga.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSagaData.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSagaLocalSteps.java`
- Create: `order-service/src/main/resources/db/migration/V7__add_order_pickup_address.sql`
- Create: `order-service/src/test/java/net/ftgo/order/saga/OrderPickupAddressPersistenceTest.java`
- Create: `order-service/src/test/java/net/ftgo/order/saga/CreateOrderSagaRollingUpgradeTest.java`

### Completed work

- [x] Added failing persistence and immutability test.
- [x] Added embedded pickup-address columns to `Order`.
- [x] Added a rolling-compatible migration with nullable legacy rows and an all-null/all-present check constraint.
- [x] Did not use a sentinel or fabricate production address data.
- [x] Added idempotent `snapshotPickupAddress`: the same address is a no-op and a different address is rejected.
- [x] Carried the validation reply through saga data.
- [x] Preserved the Phase 02 seven-step saga definition.
- [x] Persisted the pickup snapshot and remote resource identifiers atomically in the existing final local step.
- [x] Added a source-level topology guard that fails if a new saga step is inserted.
- [x] Verified migration, Spring context, topology, and persistence round trip.

---

## Task 3: Publish Pickup Address in `OrderApproved`

### Actual files

- Modify: `common/src/main/java/net/ftgo/common/orderflow/events/OrderApproved.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSagaLocalSteps.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/ConfirmOrderSagaLocalSteps.java`
- Create: `common/src/test/java/net/ftgo/common/orderflow/events/OrderApprovedContractTest.java`
- Modify: `common/src/test/java/net/ftgo/common/orderflow/events/OrderApprovedContractSerializationTest.java`
- Modify: `order-service/src/test/java/net/ftgo/order/messaging/DomainEventPublisherContractTest.java`

### Completed work

- [x] Added failing canonical event-contract test.
- [x] Added `pickupAddress` alongside `deliveryAddress` and `deliveryTime`.
- [x] Preserved constructors used by the previous release.
- [x] Verified previous payloads without pickup address remain deserializable during the compatibility window.
- [x] Published the persisted aggregate snapshot from confirmation and legacy approval paths.
- [x] Prevented final confirmation of a new order that has no pickup snapshot.
- [x] Verified outbox payload contains both address snapshots.

---

## Task 4: Remove Delivery's Synchronous Restaurant Dependency

### Modified files

- Modify: `delivery-service/src/main/java/net/ftgo/delivery/messaging/OrderEventConsumer.java`
- Modify: `delivery-service/src/main/resources/application.yml`
- Modify: `delivery-service/src/test/java/net/ftgo/delivery/messaging/OrderEventConsumerTest.java`
- Modify: `delivery-service/src/test/java/net/ftgo/delivery/messaging/DeliveryPickupAddressIntegrationTest.java`
- Modify: `delivery-service/src/test/java/net/ftgo/delivery/config/DeliveryPickupResolverContextTest.java`
- Modify: `scripts/smoke/fresh-stack.sh`
- Modify: `deployment/tests/test_fresh_stack_contract.py`

### Removed bridge files

- `delivery-service/src/main/java/net/ftgo/delivery/messaging/RestaurantPickupAddressResolver.java`
- `delivery-service/src/main/java/net/ftgo/delivery/messaging/HttpRestaurantPickupAddressResolver.java`
- `delivery-service/src/main/java/net/ftgo/delivery/config/RestaurantClientConfiguration.java`
- Restaurant-client-specific exceptions and unit tests
- `restaurant-service/src/main/java/net/ftgo/restaurant/api/internal/RestaurantLocationController.java`
- Its controller test
- `scripts/smoke/assert-delivery-pickup-bridge.sh`

### Completed work

- [x] Added no-network Delivery integration test.
- [x] Constructed Delivery solely from `OrderApproved` snapshots.
- [x] Removed resolver injection and all Restaurant REST calls from event processing.
- [x] Removed Restaurant client URL, retry, and timeout configuration from Delivery.
- [x] Removed the obsolete Restaurant internal pickup endpoint.
- [x] Removed bridge smoke wiring and replaced it with a no-bridge fresh-stack contract.
- [x] Confirmed repository search returns no `RestaurantPickupAddressResolver` or `services.restaurant-service.url` references.

---

## Task 5: Make Rolling Deployment Safety Executable

### Actual files

- Create: `scripts/operations/phase-02a-rollout-preflight.sh`
- Create: `deployment/tests/test_phase02a_rollout_preflight.py`
- Modify: `.github/workflows/phase-01-verification.yml`

### Completed work

- [x] Added RED tests for a clean rollout, legacy in-flight orders, positive Delivery lag, and unverifiable broker output.
- [x] Added a fail-closed MySQL check for incomplete pickup snapshots in `APPROVAL_PENDING`, `AWAITING_RESTAURANT_ACCEPTANCE`, and `CONFIRMATION_PENDING` orders.
- [x] Added a fail-closed Kafka consumer-group check for `delivery-service`.
- [x] Required two zero-lag samples separated by a configurable stability interval.
- [x] Kept historical data correction explicit; the script never copies the current Restaurant address into an old Order.
- [x] Added the preflight contract to required CI verification.

### Required rollout sequence

1. Apply additive migration `V7`.
2. Deploy the Restaurant and Order producer path while the existing Delivery consumer remains available.
3. Drain, cancel, or explicitly backfill any legacy in-flight orders reported by the preflight. Backfill must use an audited historical source, not the Restaurant's current address.
4. Run:

```bash
bash scripts/operations/phase-02a-rollout-preflight.sh
```

5. Deploy the new Delivery consumer only after the command exits successfully.

The preflight blocks rollout when any potentially emitting legacy order lacks one or more pickup-address fields, when Delivery has positive Kafka lag, or when either check cannot be verified.

---

## CI and Verification Gates

- [x] Phase 02A focused contract matrix:
  - common reply serialization
  - Restaurant authoritative snapshot
  - Order persistence and immutability
  - Delivery no-network event handling
- [x] CreateOrderSaga rolling-upgrade topology guard
- [x] Phase 02A rollout preflight contract
- [x] Order and Kitchen migration tests
- [x] Order Spring context wiring
- [x] Debezium connector contracts
- [x] Seven-module diagnostics matrix
- [x] Full Gradle `clean test`
- [x] Phase 02 contract guardrails and full suite
- [x] Fresh-stack smoke across two clean-volume cycles
- [x] Dedicated Phase 02 E2E across two clean-state cycles

## Completion Checklist

- [x] Restaurant validation reply includes pickup address.
- [x] Order persists an immutable pickup-address snapshot.
- [x] `OrderApproved` carries pickup and delivery snapshots.
- [x] Delivery performs no synchronous Restaurant call.
- [x] Phase 01A bridge files and configuration are removed.
- [x] Current and legacy serialization behavior is explicit.
- [x] Migration is forward-only and rolling-deployment compatible.
- [x] CreateOrderSaga step indexes remain compatible with Phase 02.
- [x] Rollout safety is enforced by an executable preflight.
- [x] Full repository verification is green on the implementation SHA.

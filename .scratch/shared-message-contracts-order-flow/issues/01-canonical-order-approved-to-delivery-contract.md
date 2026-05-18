Status: done

# Canonical Order Approved to Delivery contract

## Parent

.scratch/shared-message-contracts-order-flow/PRD.md

## What to build

Make **Order Approved** the canonical shared contract used end to end by Order Service and Delivery for Delivery creation. The completed slice should prove that Order Service publishes **Order Approved** with Order-owned delivery request details, Delivery consumes that same shared contract, and Delivery still resolves pickup information from the restaurant reference rather than from Order Service.

Existing emitted event type naming must be preserved while the shared contract is verified or moved.

## Acceptance criteria

- [x] Order Service and Delivery use the same canonical shared **Order Approved** contract for Delivery creation.
- [x] **Order Approved** carries delivery address and delivery time, and does not carry restaurant pickup-location details.
- [x] Delivery creation continues to resolve or maintain pickup information from the restaurant reference.
- [x] Contract serialization tests cover the **Order Approved** payload and preserve the current emitted event type name.
- [x] Delivery event-consumer tests prove that an **Order Approved** message creates one **Delivery** idempotently.

## Blocked by

None - can start immediately

## Completed notes

- Added shared contract serialization coverage in `common/src/test/java/net/ftgo/common/orderflow/events/OrderApprovedContractSerializationTest.java`.
- Added Order Service outbox event-type preservation coverage in `order-service/src/test/java/net/ftgo/order/messaging/DomainEventPublisherContractTest.java`.
- Verified Delivery consumer idempotent creation behavior in `delivery-service/src/test/java/net/ftgo/delivery/messaging/OrderEventConsumerTest.java`.

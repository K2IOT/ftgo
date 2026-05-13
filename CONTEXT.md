# FTGO Domain

FTGO is an online food ordering and delivery domain. This context captures business language shared across ordering, kitchen preparation, payment authorization, delivery, and order history.

## Language

**Order**:
A consumer's request for a restaurant to prepare food for delivery.
_Avoid_: Purchase, transaction

**Order Created**:
The point when an **Order** has been accepted into the order workflow and is waiting for cross-service approval.
_Avoid_: Order approved, order completed

**Order Approved**:
The point when an **Order** has passed consumer, kitchen, and payment checks and can proceed to fulfillment.
_Avoid_: Order created, order placed

**Ticket**:
The kitchen's view of an **Order** that tracks food preparation.
_Avoid_: Kitchen order

**Payment Authorization**:
A reservation of funds for an **Order** before capture or release.
_Avoid_: Payment, charge

**Delivery**:
The fulfillment activity that moves an approved **Order** from restaurant pickup to the consumer.
_Avoid_: Shipment

**Preparation**:
The stage when the kitchen has started making the food for an **Order**.
_Avoid_: Fulfillment

## Relationships

- An **Order** has exactly one **Ticket** once kitchen preparation has been requested.
- An **Order** has exactly one **Payment Authorization** once payment has been authorized.
- An **Order Approved** event may create one **Delivery**.
- **Order Created** precedes **Order Approved**.
- An **Order** may be cancelled or revised only before **Preparation** begins.

## Example Dialogue

> **Dev:** "When an **Order** is created, should **Delivery** start immediately?"
> **Domain expert:** "No. **Order Created** only means the order entered the workflow. **Delivery** starts after **Order Approved**."

## Flagged Ambiguities

- "created" was used as if it meant ready for fulfillment; resolved: **Order Created** means accepted into the workflow, while **Order Approved** means approval completed.
- "cancel/revise any time before pickup" conflicted with kitchen operations; resolved: cancel and revise are normal customer actions only before **Preparation** begins.

# Shared message contracts for order flow

Order flow spans Order, Consumer, Kitchen, Accounting, Delivery, and Order History, so saga commands, replies, and integration events must use canonical shared contract classes rather than package-local duplicates. We chose shared contracts in the `common` module because Eventuate command dispatch and downstream event projection depend on exact message type and payload compatibility; the trade-off is tighter compile-time coupling in exchange for a runnable, explicit cross-service contract.

Order Service owns order facts such as order identity, consumer, restaurant, totals, delivery request details, ticket ID, and authorization ID. Delivery creation is triggered by **Order Approved**, but pickup-location ownership remains outside Order Service; Delivery should resolve or maintain pickup information using the restaurant reference instead of requiring Order Service to publish data it does not own.

Kitchen Service owns whether food preparation has begun. Order Service may start a cancel or revise saga by placing the Order in a pending state, but Kitchen rejects the ticket command if preparation has already begun; the saga then compensates Order back to its approved state.

Cancel order flow keeps the payment reversal after Kitchen has accepted the cancellation request. This prevents releasing funds for an order that Kitchen can no longer cancel because preparation has already begun.

Revision flow treats payment adjustment as replacing the previous authorization with a new authorization. Order Service must store the new authorization reference when the revision is confirmed so later cancel or revise flows act on the current authorization.

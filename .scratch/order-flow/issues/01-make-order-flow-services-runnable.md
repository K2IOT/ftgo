# Make order-flow services runnable locally

Status: ready-for-agent

## What to build

Make the order-flow services boot against the Docker Compose infrastructure with validated schemas. A developer should be able to start Order, Consumer, Restaurant, Kitchen, Accounting, Delivery, and Order History locally and get healthy services before testing any saga behavior.

## Acceptance criteria

- [x] The service datasource ports match the Docker Compose MySQL port mappings for every MySQL-backed service.
- [x] Order Service schema validation succeeds with columns needed for current saga state, including the ticket and payment authorization references stored on an Order.
- [x] `GET /actuator/health` can report healthy database connectivity for the order-flow services after infrastructure startup.
- [x] Documentation or config examples no longer imply that multiple local MySQL service databases all run on `localhost:3306`.

## Blocked by

None - can start immediately

## Comments

Created from the order-flow grill on 2026-05-13. This issue addresses runtime blockers found before fixing deeper saga behavior.

Implemented on 2026-05-13. Added focused tests for Docker Compose datasource-port alignment and Order Service MySQL migration compatibility, fixed local datasource ports, added missing Order saga-reference columns, corrected a MySQL key-length migration failure, and updated local run docs.

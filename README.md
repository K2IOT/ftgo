# FTGO Microservices Platform

FTGO is a Java 21 microservices reference platform for online food ordering and delivery. It demonstrates saga orchestration, transactional outbox messaging, Kafka-based integration, per-service persistence, and restart-safe business workflows.

## Current Status

Phase 02, **Core Order Flow**, is implemented on `agent/phase-02-core-order-flow` and tracked by PR #4.

The final merge-readiness decision is based on all required workflows passing on the same branch SHA. Exact workflow run IDs and the final SHA are recorded in PR #4.

## Architecture

### Services

| Service | Port | Database | Responsibility |
|---------|------|----------|----------------|
| API Gateway | 8080 | Redis | Authentication, routing, rate limiting, API composition |
| Order Service | 8081 | MySQL | Order lifecycle and saga orchestration |
| Consumer Service | 8082 | MySQL | Consumer accounts and durable credit reservations |
| Restaurant Service | 8083 | MySQL | Restaurant state and authoritative menu validation |
| Kitchen Service | 8084 | MySQL | Ticket lifecycle and restaurant acceptance decisions |
| Accounting Service | 8085 | MySQL | Payment authorization, capture, void, and refund |
| Delivery Service | 8086 | MySQL | Delivery assignment and tracking |
| Order History Service | 8087 | ScyllaDB | CQRS order-history projection |

### Technology Stack

- Java 21 and Spring Boot 3.2
- Gradle 8.5 multi-project build
- Eventuate Tram commands, replies, and saga orchestration
- Apache Kafka
- MySQL 8 per transactional service
- Debezium and Eventuate CDC
- Flyway migrations
- JUnit 5, jqwik, Testcontainers, and real-stack E2E tests
- Docker Compose for local and CI verification

## Phase 02 Core Order Flow

Phase 02 turns checkout into an authoritative, restart-safe workflow with durable participant state and explicit compensation.

### Create Order

`CreateOrderSaga` performs the following operations:

1. Validate the requested menu against the authoritative Restaurant Service snapshot.
2. Reserve consumer credit using the order ID as the business idempotency key.
3. Create a Kitchen ticket.
4. Authorize payment using the payment token supplied by the REST request.
5. Approve the ticket for restaurant review.
6. Move the order to `AWAITING_RESTAURANT_ACCEPTANCE`.

Every completed remote operation has an idempotent compensation. A failure before restaurant acceptance rejects the order and releases any acquired resources.

### Restaurant Decision

Kitchen Service serializes accept, reject, and timeout decisions under a row lock. Exactly one decision wins:

- `ACCEPTED`
- `REJECTED_BY_RESTAURANT`
- `REJECTED_TIMEOUT`

Decision events are written to the Kitchen outbox in the same transaction as the ticket state change. Debezium publishes them to `net.ftgo.kitchenservice.domain.Ticket`.

Consumers accept both direct JSON events and Kafka Connect schema-wrapped payloads.

### Confirm Order

A valid acceptance atomically claims the order transition:

```text
AWAITING_RESTAURANT_ACCEPTANCE -> CONFIRMATION_PENDING
```

`ConfirmOrderSaga` then:

1. Captures the payment authorization.
2. Commits the consumer credit reservation.
3. Approves the order.

Payment capture is the pivot. Operations after the pivot are retriable and idempotent.

### Reject Order

A restaurant rejection or timeout atomically claims:

```text
AWAITING_RESTAURANT_ACCEPTANCE -> REJECTION_PENDING
```

`RejectOrderSaga` then:

1. Voids the payment authorization.
2. Releases the consumer credit reservation.
3. Rejects the order with a stable failure code and message.

Duplicate or stale decision events are acknowledged as no-ops.

### Transaction and Messaging Guarantees

- Business rejections produce typed failure replies without poisoning the surrounding Eventuate message transaction.
- Order-local saga commands use one consolidated dispatcher with a complete handler set on `orderService`.
- Credit and payment operations are idempotent by order-scoped or request-scoped business keys.
- Aggregate mutation and outbox insertion share one local transaction.
- Native MySQL enums are migration-tested against their complete Java lifecycle enums.
- Order History projects explicit restaurant rejection and timeout states.

### Feature Flag

Phase 02 can be disabled without reverting the deployment:

```bash
FTGO_ORDER_PHASE2_ENABLED=false
```

When disabled, new Phase 02 order creation is rejected by the Order Service. Existing in-flight sagas must be drained or handled according to the rollout runbook before disabling participants.

## Verification

### Full Test Suite

```bash
./gradlew --no-daemon clean test --stacktrace
```

### Module Matrix

```bash
./gradlew :api-gateway:test \
  :order-service:test \
  :consumer-service:test \
  :restaurant-service:test \
  :kitchen-service:test \
  :accounting-service:test \
  :order-history-service:test
```

### Phase 02 Contracts

```bash
./gradlew :common:test \
  --tests '*Phase02ContractGuardrailsTest' \
  --tests '*Phase02OrderFlowContractsSerializationTest'
```

### Real Core Order Flow E2E

The dedicated E2E harness starts real service processes, MySQL, Kafka, Eventuate CDC, Debezium Connect, and a Kitchen outbox connector.

```bash
bash scripts/smoke/verify-core-order-flow.sh --runs 2
```

Each clean-state cycle executes eight scenarios:

1. Restaurant acceptance and successful confirmation.
2. Stale menu snapshot rejection.
3. Insufficient consumer credit.
4. Payment-provider denial.
5. Explicit restaurant rejection.
6. Restaurant acceptance timeout.
7. Acceptance-versus-timeout race.
8. Duplicate decision delivery.

The workflow must pass all eight scenarios in two independent clean-state cycles.

### Fresh-Stack Smoke

```bash
bash scripts/smoke/verify-fresh-stack.sh --runs 2
```

CI also verifies the Docker Compose model, Flyway migrations, Spring contexts, shared contracts, and the checked-in Gradle wrapper.

## Quick Start

### Prerequisites

- Java 21
- Docker with Docker Compose
- Bash, curl, and jq for the verification scripts

### Build

```bash
./gradlew build
```

### Start Shared Infrastructure

```bash
cd deployment
docker compose -f docker-compose.infra.yml up -d
```

### Run a Service

```bash
./gradlew :order-service:bootRun
```

Repeat for the participant services required by the workflow.

## Project Structure

```text
ftgo/
├── api-gateway/
├── order-service/
├── consumer-service/
├── restaurant-service/
├── kitchen-service/
├── accounting-service/
├── delivery-service/
├── order-history-service/
├── common/
├── e2e-tests/
├── deployment/
├── docs/
├── scripts/
├── build.gradle
└── settings.gradle
```

## Messaging Channels

### Domain Event Topics

- `net.ftgo.orderservice.domain.Order`
- `net.ftgo.consumerservice.domain.Consumer`
- `net.ftgo.restaurantservice.domain.Restaurant`
- `net.ftgo.kitchenservice.domain.Ticket`
- `net.ftgo.accountingservice.domain.Account`
- `net.ftgo.deliveryservice.domain.Delivery`

### Command Channels

- `orderService`
- `consumerService`
- `restaurantService`
- `kitchenService`
- `accountingService`
- `deliveryService`

### Saga Reply Channels

- `createOrderSagaReply`
- `cancelOrderSagaReply`
- `reviseOrderSagaReply`

## Rollout and Rollback

Use the Phase 02 operations runbook for migration ordering, compatibility checks, canary rollout, kill-switch operation, rollback constraints, and recovery procedures:

- [Phase 02 Core Order Flow Rollout and Rollback](docs/operations/phase-02-core-order-flow-rollout.md)

Important rules:

- Apply backward-compatible migrations before deploying code that writes new states.
- Keep mixed-version event and command compatibility during rolling deployment.
- Do not roll back database enums while rows still contain Phase 02 values.
- Use the feature flag to stop new Phase 02 orders before rolling back services.

## Documentation

- [Phase 02 Design](docs/superpowers/specs/2026-07-23-phase-02-core-order-flow-design.md)
- [Phase 02 Implementation Record](docs/superpowers/plans/2026-07-23-phase-02-core-order-flow.md)
- [Phase 02 Operations Runbook](docs/operations/phase-02-core-order-flow-rollout.md)
- [Infrastructure Setup](deployment/README.md)
- [Kubernetes Deployment](deployment/kubernetes/README.md)
- [Architecture Plan](ftgo_architecture_plan.md)

## Troubleshooting

### Saga does not progress

Check the service logs, command topics, and the order saga tables:

```bash
docker exec ftgo-mysql-order mysql \
  -uftgo_user -pftgo_password ftgo_order \
  -e 'SELECT * FROM saga_instance ORDER BY last_updated DESC;'
```

For participant failures, inspect `message`, `received_messages`, and the participant business tables in the corresponding service schema.

### Kitchen decision is not consumed

Verify the Debezium connector and inspect the event payload format. Consumers support both direct event JSON and schema envelopes with a top-level `payload` field.

### Migration fails on an enum column

Compare `information_schema.columns.column_type` with the complete Java enum. Phase 02 includes regression tests for authorization, ticket, and order state lifecycles.

## License

This project is for educational purposes and is based on the patterns described in *Microservices Patterns* by Chris Richardson.

## References

- [Microservices Patterns](https://microservices.io/patterns/index.html)
- [Eventuate Tram Sagas](https://eventuate.io/docs/manual/eventuate-tram/latest/getting-started-eventuate-tram-sagas.html)
- [Debezium](https://debezium.io/)
- [Spring Boot](https://spring.io/projects/spring-boot)

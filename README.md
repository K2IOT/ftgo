# FTGO Microservices Platform

A production-grade microservices platform for online food ordering and delivery, implementing distributed transaction management using saga orchestration patterns.

## Overview

FTGO (Food To Go) is a comprehensive microservices system that demonstrates:
- **Saga Orchestration** for distributed transactions
- **Event-Driven Architecture** with Kafka
- **Transactional Outbox Pattern** with Debezium CDC
- **CQRS** for scalable read operations
- **Service Mesh** with Istio for mTLS and resilience
- **Property-Based Testing** for correctness validation

## Architecture

### Microservices

| Service | Port | Database | Description |
|---------|------|----------|-------------|
| **API Gateway** | 8080 | Redis | Authentication, routing, rate limiting, API composition |
| **Order Service** | 8081 | MySQL | Order lifecycle management, saga orchestration |
| **Consumer Service** | 8082 | MySQL | Consumer account management, credit verification |
| **Restaurant Service** | 8083 | MySQL | Restaurant profiles, menu management |
| **Kitchen Service** | 8084 | MySQL | Kitchen ticket management, preparation tracking |
| **Accounting Service** | 8085 | MySQL | Payment authorization, transaction management |
| **Delivery Service** | 8086 | MySQL | Courier assignment, delivery tracking |
| **Order History Service** | 8087 | ScyllaDB | CQRS read model for order queries |

### Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.2
- **Build System**: Gradle 8.5 (multi-project)
- **Messaging**: Apache Kafka 3.x + Eventuate Tram
- **Databases**: MySQL 8 (per service), ScyllaDB (CQRS)
- **CDC**: Debezium for change data capture
- **Container Runtime**: Docker
- **Orchestration**: Kubernetes with Istio service mesh
- **Observability**: OpenTelemetry, Jaeger, Prometheus, ELK Stack

## Quick Start

### Prerequisites

- Java 21
- Docker and Docker Compose
- kubectl (for Kubernetes deployment)
- k3s or kind (for local Kubernetes)

### 1. Build the Project

```bash
./gradlew build
```

### 2. Start Infrastructure

```bash
cd deployment
docker-compose -f docker-compose.infra.yml up -d
```

This starts:
- 3 Kafka brokers
- 6 MySQL databases
- ScyllaDB
- Redis
- Debezium Connect
- HashiCorp Vault
- Spring Cloud Config Server

### 3. Configure Debezium CDC

```bash
cd deployment
./configure-debezium.sh
```

### 4. Initialize Vault

```bash
cd deployment
./init-vault.sh
```

### 5. Run Services Locally

```bash
# Order Service
./gradlew :order-service:bootRun

# Consumer Service
./gradlew :consumer-service:bootRun

# (Repeat for other services)
```

### 6. Verify Setup

```bash
# Check Kafka topics
docker exec ftgo-kafka-1 kafka-topics --list --bootstrap-server localhost:9092

# Check Debezium connectors
curl http://localhost:8083/connectors

# Check service health
curl http://localhost:8081/actuator/health
```

## Project Structure

```
ftgo/
├── api-gateway/              API Gateway service
├── order-service/            Order Service (saga orchestrator)
├── consumer-service/         Consumer Service
├── restaurant-service/       Restaurant Service
├── kitchen-service/          Kitchen Service
├── accounting-service/       Accounting Service
├── delivery-service/         Delivery Service
├── order-history-service/    Order History Service (CQRS)
├── common/                   Shared DTOs, value objects, channel names
├── deployment/               Infrastructure and deployment configs
│   ├── docker-compose.infra.yml     Infrastructure stack
│   ├── configure-debezium.sh        Debezium setup
│   ├── init-vault.sh                Vault initialization
│   ├── config-repo/                 Spring Cloud Config files
│   └── kubernetes/                  K8s manifests and Istio configs
├── build.gradle              Root multi-project build
├── settings.gradle           Project structure
└── README.md                 This file
```

## Key Patterns

### Saga Orchestration

Order Service orchestrates distributed transactions using Eventuate Tram Sagas:

- **CreateOrderSaga**: Verify consumer → Create ticket → Authorize payment → Approve order
- **CancelOrderSaga**: Begin cancel → Reverse payment → Confirm cancel
- **ReviseOrderSaga**: Begin revise → Revise payment → Confirm revise

Each saga has:
- **Compensatable steps**: Can be undone if saga fails
- **Pivot point**: First non-compensatable step (payment authorization)
- **Retriable steps**: Must eventually succeed after pivot

### Transactional Outbox

All services use the Transactional Outbox pattern:

1. Service updates entity and inserts event into outbox table (single ACID transaction)
2. Debezium CDC monitors MySQL binlog and detects outbox inserts
3. Debezium publishes event to Kafka
4. Consumers process events idempotently using processed_messages table

### CQRS

Order History Service maintains a denormalized read model in ScyllaDB:

- Subscribes to events from all services
- Updates read model for fast queries
- Supports filtering, pagination, and full-text search
- Eventually consistent with write model

### Semantic Locking

Order Service uses pending states to prevent concurrent modifications:

- `APPROVAL_PENDING`: During CreateOrderSaga
- `CANCEL_PENDING`: During CancelOrderSaga
- `REVISION_PENDING`: During ReviseOrderSaga

Concurrent requests return 409 Conflict during pending states.

## Kafka Topics

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
- `kitchenService`
- `accountingService`
- `deliveryService`

### Saga Reply Channels

- `createOrderSagaReply`
- `cancelOrderSagaReply`
- `reviseOrderSagaReply`

All topics use partition key: `aggregateType + "#" + aggregateId` for ordering guarantees.

## Testing

### Unit Tests

```bash
./gradlew test
```

### Integration Tests (with Testcontainers)

```bash
./gradlew integrationTest
```

### Property-Based Tests (jqwik)

```bash
./gradlew test --tests "*Property*"
```

11 correctness properties validated:
1. Order Creation Idempotency
2. Order Total Invariant
3. Consumer Credit Invariant
4. Authorization Idempotency
5. Delivery Temporal Ordering
6. Event Processing Idempotency
7. JWT Validation Correctness
8. Saga Compensation Correctness
9. Kafka Partition Key Consistency
10. Configuration Round-Trip
11. Event Schema Conformance

## Kubernetes Deployment

### Set up Local Cluster

```bash
cd deployment/kubernetes
./setup-k8s.sh
```

This configures:
- ftgo-production namespace with Istio injection
- mTLS in STRICT mode
- Circuit breaker (5 consecutive 5xx errors, 30s open state)
- Retry policy (3 attempts, 500ms base delay)
- Timeout (5s for all requests)
- RBAC policies

### Deploy Services

```bash
kubectl apply -f deployment/kubernetes/order-service/
kubectl apply -f deployment/kubernetes/consumer-service/
# (Repeat for other services)
```

### Check Status

```bash
kubectl get pods -n ftgo-production
kubectl get services -n ftgo-production
kubectl logs -f deployment/ftgo-order-service -n ftgo-production
```

## Observability

### Health Checks

All services expose `/actuator/health` with:
- Database connectivity check
- Kafka producer connectivity check
- Returns 200 UP when healthy, 503 DOWN when unhealthy

### Metrics

All services expose `/actuator/prometheus` with:
- Order placement counter
- Saga duration histogram
- Authorization outcome counter
- Custom business metrics

### Distributed Tracing

OpenTelemetry instrumentation with Jaeger:
- W3C Trace Context propagation
- Trace context in Kafka message headers
- Trace ID in logs (MDC)
- End-to-end saga tracing

### Structured Logging

JSON structured logging to stdout:
- Timestamp, level, service name, trace ID, message
- Collected by Fluentd DaemonSet
- Forwarded to Elasticsearch
- Visualized in Kibana

## Configuration Management

### Spring Cloud Config Server

Centralized configuration with Git backend:
- `application.yml`: Common configuration
- `application-dev.yml`: Development profile
- `application-staging.yml`: Staging profile
- `application-production.yml`: Production profile

### HashiCorp Vault

Secrets management:
- Database passwords
- API keys
- Service-specific tokens
- Never stored in Docker images or ConfigMaps

## Security

- **mTLS**: All service-to-service communication encrypted with TLS 1.3
- **JWT**: API Gateway validates JWT signatures and expiration
- **RBAC**: Kubernetes role-based access control
- **Secrets**: Managed by Vault, not in source control
- **Network Policies**: Istio authorization policies

## Resilience

- **Circuit Breaker**: Istio circuit breaker (5 consecutive 5xx → 30s open)
- **Retry**: Istio retry policy (3 attempts, exponential backoff)
- **Timeout**: 5s timeout for all service-to-service requests
- **Connection Pooling**: HikariCP (max 20, min idle 5, timeout 30s)
- **Saga Compensation**: Automatic rollback on failure before pivot

## Documentation

- [Infrastructure Setup](deployment/README.md)
- [Kubernetes Deployment](deployment/kubernetes/README.md)
- [Architecture Plan](ftgo_architecture_plan.md)
- [Requirements](. kiro/specs/ftgo-microservices-platform/requirements.md)
- [Design](. kiro/specs/ftgo-microservices-platform/design.md)
- [Tasks](. kiro/specs/ftgo-microservices-platform/tasks.md)

## Development

### Build Specific Service

```bash
./gradlew :order-service:build
```

### Run Tests for Specific Service

```bash
./gradlew :order-service:test
```

### Build Docker Image

```bash
./gradlew :order-service:dockerBuild
```

### Clean Build

```bash
./gradlew clean build
```

## Troubleshooting

### Kafka not starting

Check the KRaft broker logs:
```bash
docker logs ftgo-kafka-1
```

### Debezium connector fails

Check MySQL binlog is enabled:
```bash
docker exec ftgo-mysql-order mysql -uroot -prootpassword -e "SHOW VARIABLES LIKE 'log_bin'"
```

Check connector status:
```bash
curl http://localhost:8083/connectors/order-connector/status
```

### Service can't connect to database

Check database is running:
```bash
docker ps | grep mysql
```

Check connection string in application.yml

### Saga not completing

Check Kafka topics exist:
```bash
docker exec ftgo-kafka-1 kafka-topics --list --bootstrap-server localhost:9092
```

Check saga instance table:
```bash
docker exec ftgo-mysql-order mysql -uftgo_user -pftgo_password ftgo_order -e "SELECT * FROM saga_instance"
```

## Contributing

1. Create feature branch from main
2. Implement changes with tests
3. Run full test suite: `./gradlew test`
4. Submit pull request

## License

This project is for educational purposes based on "Microservices Patterns" by Chris Richardson.

## References

- [Microservices Patterns](https://microservices.io/patterns/index.html)
- [Eventuate Tram Sagas](https://eventuate.io/docs/manual/eventuate-tram/latest/getting-started-eventuate-tram-sagas.html)
- [Debezium](https://debezium.io/)
- [Istio](https://istio.io/)
- [Spring Boot](https://spring.io/projects/spring-boot)

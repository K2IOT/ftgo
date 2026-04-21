# Project Structure

## Repository Layout

This is a **monorepo** containing all FTGO microservices and shared infrastructure.

```
ftgo/
├── api-gateway/              API Gateway service
├── order-service/            Order Service (saga orchestrator)
├── consumer-service/         Consumer Service
├── restaurant-service/       Restaurant Service
├── kitchen-service/          Kitchen Service
├── accounting-service/       Accounting Service
├── delivery-service/         Delivery Service
├── order-history-service/    Order History Service (CQRS read model)
├── common/                   Shared DTOs, saga channel names, utilities
├── deployment/               Infrastructure and deployment configs
│   ├── docker-compose.yml           Full local stack
│   ├── docker-compose.infra.yml     Infrastructure only
│   └── kubernetes/                  K8s manifests per service
├── build.gradle              Root multi-project build
└── ftgo_architecture_plan.md Architecture documentation
```

## Service Structure Convention

Each service follows this standard layout:

```
{service-name}/
├── src/
│   ├── main/
│   │   ├── java/net/ftgo/{service}/
│   │   │   ├── domain/          Aggregates, entities, value objects
│   │   │   ├── saga/            Saga definitions (orchestrators only)
│   │   │   ├── api/             REST controllers, DTOs
│   │   │   ├── messaging/       Kafka command handlers, event publishers
│   │   │   ├── repository/      JPA repositories
│   │   │   └── config/          Spring configuration classes
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/    Flyway/Liquibase migrations
│   └── test/
│       ├── java/                Unit and integration tests
│       └── resources/           Test configurations
├── Dockerfile
└── build.gradle
```

## Domain-Driven Design Organization

### Package Structure by Layer

- **domain/**: Core business logic (aggregates, domain events, state machines)
  - Aggregates: `Order`, `Consumer`, `Restaurant`, `Ticket`, `Delivery`, `Account`
  - State enums: `OrderState`, `TicketState`, `DeliveryState`
  - Domain events: `OrderCreated`, `OrderApproved`, `TicketAccepted`

- **saga/**: Saga orchestrators (Order Service only)
  - `CreateOrderSaga`, `CancelOrderSaga`, `ReviseOrderSaga`
  - Saga state machines and compensation logic

- **api/**: External interface layer
  - REST controllers
  - Request/response DTOs
  - API composition logic (API Gateway)

- **messaging/**: Asynchronous communication
  - Kafka command handlers
  - Event publishers (via Transactional Outbox)
  - Saga reply handlers

- **repository/**: Data persistence
  - JPA repositories
  - Custom query methods
  - Outbox repository

- **config/**: Spring configuration
  - Eventuate Tram configuration
  - Kafka producer/consumer setup
  - Security configuration
  - Database configuration

## Bounded Context Mapping

Each service represents a DDD bounded context:

| Service | Bounded Context | Core Aggregate(s) |
|---------|----------------|-------------------|
| Order Service | Order Context | Order |
| Consumer Service | Consumer Context | Consumer |
| Restaurant Service | Restaurant Context | Restaurant |
| Kitchen Service | Kitchen Context | Ticket |
| Accounting Service | Accounting Context | Account, CreditCardAuthorization |
| Delivery Service | Delivery Context | Delivery, Courier |
| Order History Service | Query Context | OrderHistory (read model) |

## Database Schema Convention

Each service owns its database with these standard tables:

- **{aggregate}_table**: Main aggregate data (e.g., `orders`, `tickets`, `consumers`)
- **outbox**: Transactional Outbox pattern for reliable event publishing
- **processed_messages**: Idempotent message processing (duplicate detection)
- **saga_instance**: Saga state persistence (orchestrators only)
- **saga_lock_table**: Saga concurrency control

## Deployment Structure

### Kubernetes Organization

```
deployment/kubernetes/
├── namespace.yaml                    ftgo-production namespace
├── {service-name}/
│   ├── deployment.yaml              Pod template, replicas, resources
│   ├── service.yaml                 ClusterIP/LoadBalancer
│   ├── configmap.yaml               Environment-specific config
│   └── secret.yaml                  Credentials (Vault-injected)
└── infrastructure/
    ├── kafka/                       StatefulSet for Kafka brokers
    ├── mysql/                       StatefulSet per service DB
    ├── redis/                       Deployment for cache
    └── istio/                       VirtualService, DestinationRule
```

## Common Module

The `common/` directory contains shared code:

- **DTOs**: Cross-service data transfer objects
- **Channel Names**: Kafka topic/channel constants
- **Money**: Value object for currency amounts
- **Address**: Value object for delivery addresses
- **Events**: Shared event definitions
- **Exceptions**: Common exception types

## Configuration Management

- **application.yml**: Service-specific defaults
- **application-{profile}.yml**: Environment-specific overrides (dev, staging, prod)
- **Spring Cloud Config**: Externalized configuration server
- **Vault**: Secrets management (DB passwords, API keys)

## Naming Conventions

- **Services**: kebab-case (e.g., `order-service`, `api-gateway`)
- **Java packages**: lowercase (e.g., `net.ftgo.order.domain`)
- **Classes**: PascalCase (e.g., `CreateOrderSaga`, `OrderController`)
- **Kafka topics**: dot-separated FQN (e.g., `net.ftgo.orderservice.domain.Order`)
- **Command channels**: camelCase service name (e.g., `orderService`, `kitchenService`)
- **Database tables**: snake_case (e.g., `order_line_items`, `processed_messages`)

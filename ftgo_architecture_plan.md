# FTGO — Production Microservices Architecture Plan
> Based on *Microservices Patterns* by Chris Richardson  
> Stack: Spring Boot 3.2 · Java 21 · Eventuate Tram · Kafka · MySQL · Kubernetes

---

## 1. System Overview & Design Goals

| Attribute | Target |
|---|---|
| Architecture | Microservices (DDD Bounded Contexts) |
| Transaction model | Saga (Orchestration + Choreography) |
| Messaging | Apache Kafka + Transactional Outbox |
| Consistency | Eventual (ACD) within sagas, ACID within single service |
| Service coupling | Loosely coupled via events and command/reply channels |
| Deployability | Each service independently deployable via CI/CD pipeline |
| Observability | Health Check API + Log Aggregation + Distributed Tracing + Metrics |

---

## 2. Domain Decomposition (DDD Bounded Contexts)

### 2.1 Bounded Contexts → Services

```
FTGO Domain
├── Consumer Context       → Consumer Service
├── Restaurant Context     → Restaurant Service
├── Order Context          → Order Service  (saga orchestrator)
├── Kitchen Context        → Kitchen Service
├── Accounting Context     → Accounting Service
├── Delivery Context       → Delivery Service
└── Query Context          → Order History Service (CQRS Read Model)
```

### 2.2 Why These Boundaries?

- **Order** is the central aggregate — it orchestrates all cross-service sagas.
- **Kitchen** owns `Ticket` — a kitchen-perspective view of an order. Not part of Order Service because kitchen teams need independent deployability.
- **Accounting** is isolated — credit card authorization is a distinct financial concern, independently scalable.
- **Order History** is a pure query model — separated so the write side (Order Service) never slows down for reads.

---

## 3. Service Catalog

### 3.1 API Gateway
| Property | Detail |
|---|---|
| Responsibility | Single entry point. Auth filter, rate limiting, request routing, API composition |
| Database | Redis (session, rate limit counters) |
| IPC (outbound) | REST → all downstream services |
| Patterns | Circuit Breaker, Access Token (JWT), API Composition |
| Key endpoints | `POST /login`, `GET /orders/:id`, `POST /orders`, `GET /restaurants` |

### 3.2 Order Service ⭐ (Core Orchestrator)
| Property | Detail |
|---|---|
| Responsibility | Owns Order lifecycle. Creates and drives all order-related sagas |
| Database | MySQL `orders` (DB-per-service) |
| IPC (inbound) | REST from API Gateway |
| IPC (outbound) | Command channels → Consumer, Kitchen, Accounting, Delivery |
| Patterns | **Saga Orchestrator**, Transactional Outbox, CQRS, Domain Events, Semantic Lock |
| Key aggregates | `Order` (states: APPROVAL_PENDING → APPROVED / REJECTED) |
| Sagas owned | CreateOrderSaga, CancelOrderSaga, ReviseOrderSaga |

### 3.3 Consumer Service
| Property | Detail |
|---|---|
| Responsibility | Consumer accounts, credit limit verification |
| Database | MySQL `consumers` |
| IPC | Kafka command channel (`consumerService`) |
| Patterns | Saga Participant, Domain Events |
| Key aggregates | `Consumer` |

### 3.4 Restaurant Service
| Property | Detail |
|---|---|
| Responsibility | Restaurant profiles, menu management, availability hours |
| Database | MySQL `restaurants` |
| IPC | REST (query), Kafka events (domain events) |
| Patterns | Domain Events, CQRS source |
| Key aggregates | `Restaurant` (menu items, opening hours) |

### 3.5 Kitchen Service
| Property | Detail |
|---|---|
| Responsibility | Kitchen ticket management, order preparation tracking |
| Database | MySQL `tickets` |
| IPC | Kafka command channel (`kitchenService`) |
| Patterns | Saga Participant, Domain Events, Ticket Aggregate |
| Key aggregates | `Ticket` (states: CREATE_PENDING → AWAITING_ACCEPTANCE → ACCEPTED → READY) |

### 3.6 Accounting Service
| Property | Detail |
|---|---|
| Responsibility | Credit card authorization, account management |
| Database | MySQL `accounts` |
| IPC | Kafka command channel (`accountingService`) |
| Patterns | Saga Participant, Event Sourcing (optional), Idempotency |
| Key aggregates | `Account`, `CreditCardAuthorization` |
| Notes | Pivot transaction in CreateOrderSaga — must be exactly-once |

### 3.7 Delivery Service
| Property | Detail |
|---|---|
| Responsibility | Courier assignment, delivery scheduling, status tracking |
| Database | MySQL `deliveries` |
| IPC | Kafka events from Order Service, command channel |
| Patterns | Domain Events, Saga Participant, Scheduler |
| Key aggregates | `Delivery`, `Courier` |

### 3.8 Order History Service (CQRS View)
| Property | Detail |
|---|---|
| Responsibility | Queryable read model across all order-related data |
| Database | ScyllaDB |
| IPC | Kafka consumer (subscribes to Order, Delivery, Kitchen, Accounting events) |
| Patterns | **CQRS Read Model**, Event Handler, Eventual Consistency |
| Key queries | `findOrderHistory(consumerId, filter)`, `findOrder(orderId)` |

---

## 4. Data Architecture

### 4.1 DB-per-Service Strategy

```
Service              Database Type       Schema
─────────────────────────────────────────────────────────
Order Service        MySQL               orders, order_line_items, outbox
Consumer Service     MySQL               consumers
Restaurant Service   MySQL               restaurants, menu_items
Kitchen Service      MySQL               tickets, ticket_line_items
Accounting Service   MySQL               accounts, authorizations
Delivery Service     MySQL               deliveries, couriers
Order History Svc    ScyllaDB            order_history (CQRS view)
API Gateway          Redis               sessions, rate_limit_counters
```

### 4.2 Key Tables (per service)

**Order Service — orders table**
```sql
orders(id, version, state, consumer_id, restaurant_id,
       delivery_time, delivery_address, order_total,
       payment_token, created_at)
-- state enum: APPROVAL_PENDING, APPROVED, REJECTED,
--             CANCEL_PENDING, CANCELLED, REVISION_PENDING
```

**Order Service — outbox table (Transactional Outbox)**
```sql
outbox(id, destination, time, payload, published)
-- Message Relay polls/tails this table → publishes to Kafka
```

**Kitchen Service — tickets table**
```sql
tickets(id, restaurant_id, state, ready_by, accepted_at, prepared_at)
-- state enum: CREATE_PENDING, AWAITING_ACCEPTANCE, ACCEPTED,
--             PREPARING, READY_FOR_PICKUP, PICKED_UP
```

### 4.3 CQRS View Design (Order History — ScyllaDB)

Primary table: `order_history` with partition key `order_id`  
Materialized View: `order_history_by_consumer` with partition key `consumer_id` and clustering key `creation_date DESC` → supports `findOrderHistory()`  
Columns: `order_id, consumer_id, restaurant_id, status, order_total, line_items (frozen list), delivery_status, keywords (set)`  
Duplicate detection: Separate `processed_messages` table tracking message IDs

---

## 5. Communication Architecture

### 5.1 Synchronous (REST/gRPC)
| Use case | Mechanism |
|---|---|
| External clients → API Gateway | HTTPS + JWT |
| API Gateway → services | REST (internal HTTP) |
| Service-to-service queries | REST (sparingly, prefer events) |

### 5.2 Asynchronous (Kafka Topics)

**Domain Event Topics** (publish/subscribe)
```
net.ftgo.orderservice.domain.Order          → Order History Service, Delivery Service
net.ftgo.restaurantservice.domain.Restaurant → Order Service (CQRS replica), Order History
net.ftgo.kitchenservice.domain.Ticket        → Order Service (saga replies)
net.ftgo.deliveryservice.domain.Delivery     → Order History Service
net.ftgo.accountingservice.domain.Account    → (audit logging)
```

**Command/Reply Channels** (point-to-point, Saga Orchestration)
```
orderService           (commands TO Order Service from sagas)
consumerService        (CreateOrderSaga → Consumer Service)
kitchenService         (CreateOrderSaga → Kitchen Service)
accountingService      (CreateOrderSaga → Accounting Service)
deliveryService        (CreateOrderSaga → Delivery Service)
createOrderSagaReply   (all saga participants reply here)
cancelOrderSagaReply
reviseOrderSagaReply
```

**Kafka Partition Strategy**  
- Partition key = `aggregateType + aggregateId` → guarantees ordering per entity
- Consumer group per service → each service sees all events exactly once
- Retention: 7 days minimum (allow replay for CQRS view rebuild)

---

## 6. Saga Designs

### 6.1 Create Order Saga (Orchestration)

```
Orchestrator: CreateOrderSaga (in Order Service)

Step 1 [COMPENSATABLE] Order Service:
  → createOrder() sets state = APPROVAL_PENDING  (semantic lock)
  ↩ compensate: rejectOrder() → REJECTED

Step 2 [COMPENSATABLE] Consumer Service:
  → verifyConsumer(consumerId, orderTotal)
  ↩ compensate: (no-op, read-only)

Step 3 [COMPENSATABLE] Kitchen Service:
  → createTicket() → state = CREATE_PENDING
  ↩ compensate: cancelTicket()

Step 4 [PIVOT] Accounting Service:
  → authorizeCard(consumerId, amount)
  ← SUCCESS → proceed | FAILURE → run compensations

Step 5 [RETRIABLE] Kitchen Service:
  → approveTicket() → state = AWAITING_ACCEPTANCE

Step 6 [RETRIABLE] Order Service:
  → approveOrder() → state = APPROVED
```

### 6.2 Cancel Order Saga (Orchestration)

```
Step 1 [COMPENSATABLE] Order Service: beginCancel() → CANCEL_PENDING
Step 2 [COMPENSATABLE] Kitchen Service: beginCancelTicket()
Step 3 [PIVOT] Accounting Service: reverseAuthorization()
Step 4 [RETRIABLE] Kitchen Service: confirmCancelTicket()
Step 5 [RETRIABLE] Order Service: confirmCancel() → CANCELLED

Compensations (if pivot fails):
  Kitchen Service: undoCancelTicket()
  Order Service: undoCancel() → APPROVED
```

### 6.3 Revise Order Saga (Orchestration)

```
Step 1 [COMPENSATABLE] Order Service: beginRevise() → REVISION_PENDING
Step 2 [COMPENSATABLE] Kitchen Service: beginReviseTicket(revision)
Step 3 [PIVOT] Accounting Service: reviseCreditCardAuthorization(newTotal)
Step 4 [RETRIABLE] Kitchen Service: confirmReviseTicket()
Step 5 [RETRIABLE] Order Service: confirmRevise() → APPROVED
```

### 6.4 Isolation Countermeasures

| Anomaly | Countermeasure | Where Applied |
|---|---|---|
| Lost updates | **Semantic Lock** | Order state = *_PENDING during saga |
| Dirty reads | **Pessimistic View** | Cancel saga: cancel delivery BEFORE freeing credit |
| Dirty reads | **Reread Value** | Re-read Order before final approveOrder() |
| Out-of-order | **Version File** | Accounting records duplicate authorize/cancel requests |
| Concurrent cancel during create | Semantic Lock | APPROVAL_PENDING blocks cancel saga |

---

## 7. Transactional Messaging Architecture

### 7.1 Transactional Outbox Pattern

```
Service TX (atomic):
  INSERT INTO orders (...) VALUES (...)
  INSERT INTO outbox (destination, payload) VALUES (...)
         ↑ same ACID transaction

Message Relay (Debezium CDC):
  Reads MySQL binlog → captures OUTBOX inserts
  Publishes to Kafka topic
  Marks outbox rows as published

Consumer:
  Idempotency check (message_id in PROCESSED_MESSAGES)
  Process event
  Record message_id
```

### 7.2 Duplicate Detection Strategy

**Producer side:** Unique message ID generated per event  
**Consumer side:** PROCESSED_MESSAGES table with (message_id, consumed_at) — inserted atomically with business update  
**Result:** Exactly-once processing semantics despite at-least-once delivery

---

## 8. CQRS Implementation

### 8.1 Order History Service Event Handlers

```
Triggers rebuild on:
  OrderCreated      → addOrder()         (from Order Service)
  OrderApproved     → noteApproved()     (from Order Service)
  OrderCancelled    → noteCancelled()    (from Order Service)
  TicketAccepted    → noteTicketAccepted() (from Kitchen Service)
  DeliveryPickedUp  → notePickedUp()     (from Delivery Service)
  DeliveryDelivered → noteDelivered()    (from Delivery Service)
  CardAuthorized    → noteCardAuthorized() (from Accounting Service)
```

### 8.2 Query Support

```
findOrder(orderId)
  → DynamoDB GetItem by PK(orderId)

findOrderHistory(consumerId, {since, status, keyword, page})
  → DynamoDB Query on GSI (consumerId + creationDate)
  → Filter expression on status, keywords[]
  → Pagination via LastEvaluatedKey
```

---

## 9. Infrastructure Stack

### 9.1 Core Infrastructure

| Component | Technology | Purpose |
|---|---|---|
| Message broker | Apache Kafka 3.x | Event streaming, command channels |
| Transactional DB | MySQL 8 (per service) | ACID transactions, Outbox table |
| CDC relay | Debezium | Tail binlog → publish to Kafka |
| CQRS store | ScyllaDB | Order History read model |
| Cache | Redis | API Gateway sessions, hot data |
| Search | Elasticsearch | Restaurant geo-search, product search |
| Container runtime | Docker | Package each service as image |
| Orchestration | Kubernetes | Scheduling, scaling, service discovery |
| Service mesh | Istio | mTLS, circuit breaking, traffic routing |
| Tracing | Jaeger | Distributed request traces |
| Metrics | Prometheus + Grafana | RED metrics, SLO dashboards |
| Logging | ELK Stack | Centralized log aggregation |
| Config | Spring Cloud Config + Vault | Externalized configuration |
| CI/CD | GitHub Actions / Jenkins | Automated build, test, deploy |

### 9.2 Kubernetes Deployment Model

```
Namespace: ftgo-production

Deployments (1 per service):
  ftgo-api-gateway          replicas: 3
  ftgo-order-service        replicas: 3
  ftgo-consumer-service     replicas: 2
  ftgo-restaurant-service   replicas: 2
  ftgo-kitchen-service      replicas: 2
  ftgo-accounting-service   replicas: 2
  ftgo-delivery-service     replicas: 2
  ftgo-order-history-svc    replicas: 2

Infrastructure (StatefulSets):
  kafka (3 brokers + KRaft)
  mysql-order     mysql-consumer     mysql-restaurant
  mysql-kitchen   mysql-accounting   mysql-delivery
  redis           debezium-connect   elasticsearch

Services:
  ClusterIP for internal   NodePort/LoadBalancer for API Gateway
```

### 9.3 Service Mesh (Istio) Policy

```
Traffic:
  All services: VirtualService with DestinationRule
  Canary releases: traffic split v1/v2 (e.g. 95%/5%)
  
Resilience:
  Circuit breaker: consecutive5xxErrors=5, interval=30s
  Retry: 3 attempts, 500ms base, on 5xx only
  Timeout: 5s default per route
  
Security:
  PeerAuthentication: mTLS STRICT mode cluster-wide
  AuthorizationPolicy: per-service RBAC
```

---

## 10. Observability Implementation

### 10.1 Health Check API (per service)
```
GET /actuator/health
  → checks DB connection (test query)
  → checks Kafka producer connectivity
  → returns {status: UP/DOWN, components: {...}}

Kubernetes:
  readinessProbe: /actuator/health, delay=30s, period=10s
  livenessProbe:  /actuator/health, delay=60s, period=20s
```

### 10.2 Distributed Tracing
- OpenTelemetry Java Agent → auto-instruments all HTTP + Kafka messages
- W3C Trace Context propagation headers across service boundaries
- Trace ID available in every log line via MDC
- Full saga execution visible as single trace in Jaeger
- OTLP (OpenTelemetry Protocol) export to Jaeger backend

### 10.3 Application Metrics (Micrometer → Prometheus)
```
Custom metrics per service:
  order_service_placed_orders_total       (counter)
  order_service_approved_orders_total     (counter)
  order_service_saga_duration_seconds     (histogram)
  kitchen_service_ticket_creation_total   (counter)
  accounting_service_authorization_total  (counter, labeled by outcome)
```

### 10.4 Log Aggregation
- All services log structured JSON to stdout
- Fluentd DaemonSet collects pod logs → Elasticsearch
- Kibana dashboards per service
- Alert on ERROR log rate spike

---

## 11. Security Architecture

### 11.1 Authentication Flow (OAuth2 + JWT)
```
1. Client POST /login → API Gateway
2. API Gateway → OAuth2 Authorization Server (Spring Security OAuth2)
3. Authorization Server validates credentials → returns JWT (access_token + refresh_token)
4. API Gateway sets JWT cookies → client
5. Subsequent requests: client sends JWT cookie
6. API Gateway validates JWT → extracts {userId, roles} → forwards as Authorization: Bearer header
7. Services validate JWT signature → extract principal
```

### 11.2 JWT Claims Structure
```json
{
  "sub": "consumer-123",
  "roles": ["ROLE_CONSUMER"],
  "iat": 1720000000,
  "exp": 1720003600
}
```

### 11.3 Authorization Rules
```
ROLE_CONSUMER:
  POST /orders ✓
  GET /orders/{id} ✓ (own orders only, verified by Order Service)
  POST /orders/{id}/cancel ✓ (own orders only)

ROLE_RESTAURANT:
  GET /tickets ✓ (own restaurant only)
  POST /tickets/{id}/accept ✓

ROLE_COURIER:
  GET /deliveries ✓ (assigned deliveries)
  POST /deliveries/{id}/pickup ✓

ROLE_ADMIN:
  ALL ✓
```

---

## 12. Implementation Plan (Phases)

### Phase 1 — Infrastructure Foundation (Week 1–2)
- [ ] Kafka cluster (local Docker Compose for dev)
- [ ] MySQL instances per service
- [ ] Debezium connector setup
- [ ] Spring Cloud Config Server + Vault
- [ ] Base Docker images for Java 21 / Spring Boot 3.2
- [ ] GitHub Actions CI pipeline (build → test → push image)
- [ ] Kubernetes cluster (local: k3s/kind, production: EKS/GKE)

### Phase 2 — Core Services (Week 3–5)
- [ ] **Consumer Service** (simplest — no saga participation beyond validate)
- [ ] **Restaurant Service** (domain events, menu management)
- [ ] **Accounting Service** (idempotent card authorization)
- [ ] Unit tests for all aggregates
- [ ] Integration tests: persistence + Kafka messaging

### Phase 3 — Order Service + Sagas (Week 6–9)
- [ ] Order aggregate with full state machine
- [ ] Eventuate Tram Sagas setup
- [ ] **CreateOrderSaga** implementation (orchestration)
- [ ] **CancelOrderSaga** implementation
- [ ] **ReviseOrderSaga** implementation
- [ ] Transactional Outbox + Debezium relay
- [ ] Saga unit tests (Eventuate Saga testing framework)
- [ ] Consumer-driven contract tests (Spring Cloud Contract)

### Phase 4 — Kitchen & Delivery (Week 10–11)
- [ ] **Kitchen Service** (Ticket aggregate, saga participation)
- [ ] **Delivery Service** (Delivery aggregate, courier management)
- [ ] Domain event handlers for downstream sync
- [ ] Component tests per service

### Phase 5 — API Layer (Week 12–13)
- [ ] **API Gateway** (Spring Cloud Gateway)
- [ ] JWT authentication filter
- [ ] Route definitions (REST proxy + API composition)
- [ ] Rate limiting (Redis token bucket)
- [ ] Circuit breaker (Resilience4j)

### Phase 6 — CQRS Read Model (Week 14)
- [ ] **Order History Service**
- [ ] Kafka consumer for all event types
- [ ] DynamoDB / MongoDB schema + GSI
- [ ] `findOrderHistory()` with filtering + pagination
- [ ] Idempotent event processing (duplicate detection)

### Phase 7 — Observability & Production Hardening (Week 15–16)
- [ ] Health check endpoints all services
- [ ] Prometheus metrics (custom counters per service)
- [ ] Grafana dashboards (RED metrics + saga completion rates)
- [ ] Jaeger distributed tracing integration
- [ ] ELK structured logging
- [ ] Kubernetes resource limits + HPA autoscaling
- [ ] Istio mTLS + circuit breaker policies
- [ ] Canary deployment workflow (Istio traffic splitting)

### Phase 8 — End-to-End Testing (Week 17)
- [ ] Docker Compose full stack test environment
- [ ] Cucumber E2E scenarios (Place Order happy path)
- [ ] Chaos engineering: kill individual services, verify saga compensation
- [ ] Load test: `k6` or `Gatling` against API Gateway

---

## 13. Project Structure (Monorepo Layout)

```
ftgo/
├── ftgo-api-gateway/
│   ├── src/main/java/...
│   ├── src/test/java/...
│   ├── Dockerfile
│   └── build.gradle
├── ftgo-order-service/
│   ├── src/
│   │   ├── main/java/net/ftgo/order/
│   │   │   ├── domain/               Order, OrderLineItem, OrderState
│   │   │   ├── saga/                 CreateOrderSaga, CancelOrderSaga
│   │   │   ├── api/                  OrderController, OrderDTO
│   │   │   ├── messaging/            OrderCommandHandlers, OrderEventPublisher
│   │   │   └── config/               OrderServiceConfiguration
│   │   └── test/java/...
│   └── build.gradle
├── ftgo-consumer-service/
├── ftgo-restaurant-service/
├── ftgo-kitchen-service/
├── ftgo-accounting-service/
├── ftgo-delivery-service/
├── ftgo-order-history-service/
├── ftgo-common/                       Shared DTOs, saga channel names
├── deployment/
│   ├── docker-compose.yml             Full local stack
│   ├── docker-compose.infra.yml       Kafka, MySQL, Redis, Debezium
│   └── kubernetes/
│       ├── order-service/             Deployment.yaml, Service.yaml, ConfigMap.yaml
│       └── ...
└── build.gradle                       Root multi-project build
```

---

## 14. Key Architectural Decisions & Trade-offs

| Decision | Chosen | Rationale |
|---|---|---|
| Transaction model | Sagas over 2PC | 2PC blocks at scale; Kafka unavailable during 2PC; eventual consistency acceptable for food ordering |
| Saga coordination | Orchestration for Create/Revise/Cancel | Better visibility, simpler debugging, no cyclic event dependencies |
| Event relay | Debezium CDC over polling | Zero polling overhead, reliable binlog-based ordering, handles high write throughput |
| CQRS store | ScyllaDB over RDBMS | `findOrderHistory()` needs materialized views for efficient queries across consumerId + date; ScyllaDB provides high performance and horizontal scalability |
| Service mesh | Istio | Offloads circuit breaking + mTLS from application code; enables canary releases without code change |
| Saga isolation | Semantic Lock + Pessimistic View | Prevents lost updates and dirty reads without distributed locks (which kill availability) |
| DB | MySQL over PostgreSQL | Debezium CDC maturity for MySQL binlog; Eventuate Local has excellent MySQL support |

---

## 15. CAP Theorem Position

| Service | Position | Reason |
|---|---|---|
| Order Service | CP | Financial transaction — no duplicate or lost orders |
| Accounting Service | CP | Credit card authorization must be exactly-once |
| Consumer Service | CP | Account integrity required |
| Kitchen Service | AP | Brief stale ticket state acceptable |
| Order History | AP | Read model — eventual consistency acceptable |
| Delivery Service | AP | Location updates eventually consistent acceptable |

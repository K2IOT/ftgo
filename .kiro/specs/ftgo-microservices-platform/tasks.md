# Implementation Tasks: FTGO Microservices Platform

## Phase 1: Infrastructure Foundation

### 1.1 Project Setup and Build Configuration
- [ ] Create root Gradle multi-project build with Java 21 and Spring Boot 3.2
- [ ] Configure common dependencies module (ftgo-common) with shared DTOs and constants
- [ ] Set up Docker base images for Java 21 services
- [ ] Configure GitHub Actions CI pipeline for build, test, and Docker image push
- [ ] Create local development Docker Compose file for infrastructure services

### 1.2 Kafka Infrastructure
- [ ] Deploy Kafka 3.x cluster with 3 brokers using Docker Compose
- [ ] Configure Kafka topics with 3 partitions and 7-day retention
- [ ] Create domain event topics for all 8 services
- [ ] Create command channels for saga orchestration
- [ ] Create saga reply channels (createOrderSagaReply, cancelOrderSagaReply, reviseOrderSagaReply)
- [ ] Verify Kafka partition strategy with aggregateType + aggregateId keys

### 1.3 Database Infrastructure
- [ ] Deploy MySQL 8 instances for each service (6 instances total)
- [ ] Deploy ScyllaDB cluster for Order History Service
- [ ] Configure database connection pools with HikariCP settings
- [ ] Set up Flyway for schema migration management
- [ ] Create initial schema migration scripts for all services

### 1.4 Debezium CDC Setup
- [ ] Deploy Debezium Connect cluster
- [ ] Configure MySQL binlog settings for CDC
- [ ] Create Debezium connectors for all 6 MySQL databases
- [ ] Configure outbox table monitoring and Kafka publishing
- [ ] Test CDC event flow from outbox to Kafka

### 1.5 Configuration Management
- [ ] Deploy Spring Cloud Config Server
- [ ] Create Git repository for service configurations
- [ ] Deploy HashiCorp Vault for secrets management
- [ ] Configure service-specific Vault tokens
- [ ] Create configuration profiles (dev, staging, production)

### 1.6 Kubernetes Cluster Setup
- [ ] Set up local Kubernetes cluster (k3s or kind)
- [ ] Install Istio service mesh with mTLS STRICT mode
- [ ] Configure Istio circuit breaker policies (5 failures, 30s timeout)
- [ ] Configure Istio retry policies (3 attempts, 500ms delay)
- [ ] Set up namespace and RBAC policies

## Phase 2: Core Services Implementation

### 2.1 Consumer Service
- [ ] Create Consumer aggregate with credit limit validation (Requirement 4)
- [ ] Implement Consumer repository with JPA
- [ ] Create Flyway migration V1__create_consumers_table.sql
- [ ] Implement REST API for consumer registration and profile updates
- [ ] Implement verifyConsumer command handler for saga participation
- [ ] Implement transactional outbox for ConsumerUpdated events
- [ ] Add unit tests for Consumer aggregate state transitions
- [ ] Write property-based test for Property 3: Consumer Credit Invariant
- [ ] Create integration tests with Testcontainers (MySQL + Kafka)

### 2.2 Restaurant Service
- [ ] Create Restaurant aggregate with menu management (Requirement 5)
- [ ] Create MenuItem entity with price validation
- [ ] Implement Restaurant and MenuItem repositories
- [ ] Create Flyway migration V1__create_restaurants_and_menu_items.sql
- [ ] Implement REST API for restaurant and menu CRUD operations
- [ ] Implement transactional outbox for RestaurantMenuChanged events
- [ ] Add unit tests for menu item price validation
- [ ] Write property-based test for Property 6: Menu Price Temporal Consistency
- [ ] Create integration tests with Testcontainers

### 2.3 Accounting Service
- [ ] Create Account aggregate with authorization management (Requirement 7)
- [ ] Create Authorization entity with idempotency using requestId
- [ ] Implement Account and Authorization repositories
- [ ] Create Flyway migration V1__create_accounts_and_authorizations.sql
- [ ] Implement authorizeCard command handler with idempotency check
- [ ] Implement reverseAuthorization and reviseAuthorization handlers
- [ ] Implement transactional outbox for CardAuthorized/CardReversed events
- [ ] Add unit tests for authorization idempotency
- [ ] Write property-based test for Property 4: Authorization Idempotency
- [ ] Write property-based test for Property 8: Saga Compensation Correctness
- [ ] Create integration tests with Testcontainers

## Phase 3: Order Service and Saga Orchestration

### 3.1 Order Service Core
- [ ] Create Order aggregate with state machine (Requirement 1, 2, 3)
- [ ] Implement OrderState enum (APPROVAL_PENDING, APPROVED, REJECTED, CANCEL_PENDING, CANCELLED, REVISION_PENDING)
- [ ] Create OrderLineItem entity
- [ ] Implement Order repository with optimistic locking (version field)
- [ ] Create Flyway migration V1__create_orders_and_line_items.sql
- [ ] Implement semantic lock validation (reject operations during pending states)
- [ ] Add unit tests for Order state machine transitions
- [ ] Write property-based test for Property 1: Order Creation Idempotency
- [ ] Write property-based test for Property 2: Order Total Invariant

### 3.2 Eventuate Tram Sagas Setup
- [ ] Add Eventuate Tram Sagas dependencies to Order Service
- [ ] Configure Eventuate Tram with Kafka connection
- [ ] Create SagaConfiguration class with saga instance factory
- [ ] Set up saga command channels for all participant services
- [ ] Configure saga reply channels

### 3.3 CreateOrderSaga Implementation
- [ ] Create CreateOrderSagaData class with order details
- [ ] Implement CreateOrderSaga with 6 steps (Requirement 1)
- [ ] Step 1: invokeLocal createOrder (state=APPROVAL_PENDING) with compensation rejectOrder
- [ ] Step 2: invokeParticipant verifyConsumer to Consumer Service
- [ ] Step 3: invokeParticipant createTicket to Kitchen Service with compensation cancelTicket
- [ ] Step 4: invokeParticipant authorizeCard to Accounting Service (PIVOT POINT)
- [ ] Step 5: invokeParticipant approveTicket to Kitchen Service (retriable)
- [ ] Step 6: invokeLocal approveOrder (state=APPROVED) (retriable)
- [ ] Implement saga failure handling with compensation execution
- [ ] Add saga unit tests using Eventuate Tram testing framework
- [ ] Create integration test for CreateOrderSaga end-to-end flow

### 3.4 CancelOrderSaga Implementation
- [ ] Create CancelOrderSagaData class
- [ ] Implement CancelOrderSaga with 5 steps (Requirement 2)
- [ ] Step 1: invokeLocal beginCancel (state=CANCEL_PENDING) with compensation undoCancel
- [ ] Step 2: invokeParticipant beginCancelTicket to Kitchen Service with compensation undoCancelTicket
- [ ] Step 3: invokeParticipant reverseAuthorization to Accounting Service (PIVOT)
- [ ] Step 4: invokeParticipant confirmCancelTicket to Kitchen Service (retriable)
- [ ] Step 5: invokeLocal confirmCancel (state=CANCELLED) (retriable)
- [ ] Add saga unit tests
- [ ] Create integration test for CancelOrderSaga

### 3.5 ReviseOrderSaga Implementation
- [ ] Create ReviseOrderSagaData class with revision details
- [ ] Implement ReviseOrderSaga with 5 steps (Requirement 3)
- [ ] Step 1: invokeLocal beginRevise (state=REVISION_PENDING) with compensation undoRevise
- [ ] Step 2: invokeParticipant beginReviseTicket to Kitchen Service with compensation undoReviseTicket
- [ ] Step 3: invokeParticipant reviseCreditCardAuthorization to Accounting Service (PIVOT)
- [ ] Step 4: invokeParticipant confirmReviseTicket to Kitchen Service (retriable)
- [ ] Step 5: invokeLocal confirmRevise (state=APPROVED) (retriable)
- [ ] Add saga unit tests
- [ ] Create integration test for ReviseOrderSaga

### 3.6 Order Service REST API
- [ ] Implement POST /orders endpoint (initiates CreateOrderSaga)
- [ ] Implement GET /orders/{orderId} endpoint
- [ ] Implement POST /orders/{orderId}/cancel endpoint (initiates CancelOrderSaga)
- [ ] Implement POST /orders/{orderId}/revise endpoint (initiates ReviseOrderSaga)
- [ ] Add validation for concurrent modification requests (409 Conflict)
- [ ] Implement transactional outbox for OrderApproved/OrderCancelled/OrderRevised events
- [ ] Add REST API integration tests

### 3.7 Transactional Outbox Implementation
- [ ] Create outbox table schema for all 6 services (Requirement 11)
- [ ] Create processed_messages table for idempotency tracking
- [ ] Implement OutboxRepository for each service
- [ ] Implement domain event publishing with outbox insertion
- [ ] Implement idempotent event consumption with message ID checking
- [ ] Add unit tests for outbox pattern
- [ ] Write property-based test for Property 6: Event Processing Idempotency
- [ ] Create integration test verifying exactly-once event publishing

## Phase 4: Kitchen and Delivery Services

### 4.1 Kitchen Service
- [ ] Create Ticket aggregate with state machine (Requirement 6)
- [ ] Implement TicketState enum (CREATE_PENDING, AWAITING_ACCEPTANCE, ACCEPTED, PREPARING, READY_FOR_PICKUP, PICKED_UP, CANCELLED)
- [ ] Create TicketLineItem entity
- [ ] Implement Ticket repository
- [ ] Create Flyway migration V1__create_tickets_and_line_items.sql
- [ ] Implement createTicket command handler (saga participant)
- [ ] Implement approveTicket command handler (saga participant)
- [ ] Implement cancelTicket command handler (saga participant)
- [ ] Implement beginReviseTicket and confirmReviseTicket handlers
- [ ] Implement REST API for kitchen staff (accept ticket, mark ready)
- [ ] Implement transactional outbox for TicketAccepted/TicketReady/TicketCancelled events
- [ ] Add unit tests for Ticket state machine
- [ ] Create integration tests with saga participation

### 4.2 Delivery Service
- [ ] Create Delivery aggregate with status tracking (Requirement 8)
- [ ] Create Courier entity
- [ ] Implement Delivery and Courier repositories
- [ ] Create Flyway migration V1__create_deliveries_and_couriers.sql
- [ ] Implement OrderApproved event handler to create delivery record
- [ ] Implement REST API for courier assignment and status updates
- [ ] Implement estimated delivery time calculation
- [ ] Implement transactional outbox for DeliveryAssigned/DeliveryPickedUp/DeliveryDelivered events
- [ ] Add unit tests for delivery status transitions
- [ ] Write property-based test for Property 5: Delivery Temporal Ordering
- [ ] Create integration tests with event consumption

## Phase 5: API Gateway Layer

### 5.1 API Gateway Core
- [ ] Create Spring Cloud Gateway project (Requirement 10)
- [ ] Configure Redis for session storage and rate limiting
- [ ] Implement JWT authentication filter
- [ ] Implement JWT signature validation with OAuth2 public key
- [ ] Implement role-based authorization (ROLE_CONSUMER, ROLE_RESTAURANT, ROLE_COURIER)
- [ ] Add unit tests for JWT validation
- [ ] Write property-based test for Property 7: JWT Validation Correctness

### 5.2 Gateway Routing and Filters
- [ ] Configure routes for Order Service (/orders/**)
- [ ] Configure routes for Consumer Service (/consumers/**)
- [ ] Configure routes for Restaurant Service (/restaurants/**)
- [ ] Configure routes for Kitchen Service (/tickets/**)
- [ ] Configure routes for Delivery Service (/deliveries/**)
- [ ] Configure routes for Order History Service (/order-history/**)
- [ ] Implement circuit breaker filter with Resilience4j (5 failures, 30s open state)
- [ ] Implement rate limiting filter (100 req/min per consumer)
- [ ] Add integration tests for circuit breaker behavior

### 5.3 API Composition
- [ ] Implement /order-details/{orderId} endpoint
- [ ] Aggregate data from Order Service, Kitchen Service, and Delivery Service
- [ ] Use Mono.zip for parallel service calls
- [ ] Implement fallback for service unavailability
- [ ] Add integration tests for API composition

## Phase 6: CQRS Read Model (Order History Service)

### 6.1 ScyllaDB Schema
- [ ] Create order_history table with order_id as partition key (Requirement 9)
- [ ] Create line_item user-defined type
- [ ] Create order_history_by_consumer materialized view (partition key: consumer_id, clustering key: creation_date DESC)
- [ ] Create processed_messages table for idempotency
- [ ] Configure ScyllaDB connection with CassandraTemplate

### 6.2 Event Handlers
- [ ] Implement OrderCreated event handler (create order history record)
- [ ] Implement OrderApproved event handler (update status to APPROVED)
- [ ] Implement OrderCancelled event handler (update status to CANCELLED)
- [ ] Implement OrderRevised event handler (update order details)
- [ ] Implement TicketAccepted event handler (update ticketStatus)
- [ ] Implement TicketReady event handler (update ticketStatus)
- [ ] Implement DeliveryPickedUp event handler (update deliveryStatus)
- [ ] Implement DeliveryDelivered event handler (update deliveryStatus)
- [ ] Implement CardAuthorized event handler (update authorizationStatus)
- [ ] Add idempotency check using processed_messages table
- [ ] Add unit tests for event handlers

### 6.3 Query API
- [ ] Implement GET /orders/{orderId} endpoint (query by order_id)
- [ ] Implement GET /consumers/{consumerId}/orders endpoint (query materialized view)
- [ ] Add filtering by status, date range, restaurant, keyword
- [ ] Implement pagination with page size and continuation token
- [ ] Add integration tests with ScyllaDB Testcontainer
- [ ] Test eventual consistency with write model

## Phase 7: Observability and Production Hardening

### 7.1 Health Checks
- [ ] Add Spring Boot Actuator to all services (Requirement 13)
- [ ] Implement /actuator/health endpoint with database connectivity check
- [ ] Implement Kafka producer connectivity check
- [ ] Configure Kubernetes readiness probes (30s initial delay, 10s period)
- [ ] Configure Kubernetes liveness probes (60s initial delay, 20s period)
- [ ] Add integration tests for health check endpoints

### 7.2 Distributed Tracing
- [ ] Add OpenTelemetry Java Agent to all services (Requirement 14)
- [ ] Configure W3C Trace Context propagation (traceparent, tracestate headers)
- [ ] Configure trace context propagation in Kafka message headers
- [ ] Configure OTLP export to Jaeger
- [ ] Add trace ID to MDC for log correlation
- [ ] Deploy Jaeger backend
- [ ] Write integration test verifying trace spans across all services
- [ ] Write property-based test for trace ID propagation

### 7.3 Metrics
- [ ] Add Micrometer dependencies to all services (Requirement 15)
- [ ] Implement order_service_placed_orders_total counter
- [ ] Implement order_service_approved_orders_total counter
- [ ] Implement order_service_saga_duration_seconds histogram
- [ ] Implement kitchen_service_ticket_creation_total counter
- [ ] Implement accounting_service_authorization_total counter (labeled by outcome)
- [ ] Expose /actuator/prometheus endpoint for all services
- [ ] Deploy Prometheus with 15s scrape interval
- [ ] Create Grafana dashboards for RED metrics (Rate, Errors, Duration)
- [ ] Create Grafana dashboard for saga completion and compensation rates
- [ ] Write integration test verifying metrics are recorded

### 7.4 Structured Logging
- [ ] Configure Logback for JSON structured logging (Requirement 16)
- [ ] Add timestamp, level, service name, trace ID, and message fields
- [ ] Deploy Fluentd DaemonSet in Kubernetes
- [ ] Configure Fluentd to forward logs to Elasticsearch
- [ ] Deploy Kibana with log search dashboards
- [ ] Configure alert for error log rate > 10 errors/min
- [ ] Set log retention to 30 days

### 7.5 Security and mTLS
- [ ] Configure Istio PeerAuthentication with mTLS STRICT mode (Requirement 17)
- [ ] Configure Istio AuthorizationPolicy for service-to-service communication
- [ ] Implement automatic certificate rotation
- [ ] Implement JWT signature validation in API Gateway
- [ ] Add sensitive data filtering in logs (credit card numbers, passwords)
- [ ] Write integration test verifying mTLS encryption
- [ ] Write property-based test for TLS 1.3 enforcement

### 7.6 Resilience Patterns
- [ ] Configure Istio circuit breaker (5 consecutive 5xx errors, 30s open) (Requirement 18)
- [ ] Configure Istio retry policy (3 attempts, 500ms base delay, 5xx only)
- [ ] Configure Istio timeout (5s for all service-to-service requests)
- [ ] Implement connection pool monitoring with HikariCP
- [ ] Implement deadlock retry with @Retryable annotation
- [ ] Add chaos engineering tests (kill services during saga execution)
- [ ] Test circuit breaker behavior under load

### 7.7 Kafka Configuration
- [ ] Configure Kafka partition key strategy (aggregateType + aggregateId) (Requirement 19)
- [ ] Verify event ordering per aggregate
- [ ] Configure consumer groups for each service
- [ ] Test partition rebalancing
- [ ] Write property-based test for Property 9: Kafka Partition Key Consistency

### 7.8 Deployment Configuration
- [ ] Create Kubernetes Deployment manifests for all 8 services (Requirement 20)
- [ ] Configure API Gateway with 3 replicas
- [ ] Configure Order Service with 3 replicas
- [ ] Configure other services with 2 replicas each
- [ ] Configure Horizontal Pod Autoscaler (HPA) for CPU > 70%
- [ ] Configure rolling update strategy (maxSurge=1, maxUnavailable=0)
- [ ] Configure resource limits (CPU, memory) for each service
- [ ] Create Istio VirtualService for canary deployments (95% stable, 5% canary)
- [ ] Test zero-downtime deployment

## Phase 8: Configuration and Schema Management

### 8.1 Configuration Management
- [ ] Create service configurations in Git repository (Requirement 21)
- [ ] Configure Spring Cloud Config Server with Git backend
- [ ] Create configuration profiles (dev, staging, production)
- [ ] Store database passwords in Vault
- [ ] Store API keys in Vault
- [ ] Implement Vault token-based secret retrieval
- [ ] Test configuration refresh without service restart

### 8.2 Database Schema Management
- [ ] Create Flyway migration scripts for all services (Requirement 22)
- [ ] Implement V1__initial_schema.sql for each service
- [ ] Ensure backward compatibility for rolling deployments
- [ ] Configure Flyway to record migrations in schema_version table
- [ ] Test migration failure handling
- [ ] Write property-based test for schema backward compatibility

### 8.3 Event Schema Validation
- [ ] Deploy schema registry for domain events (Requirement 25)
- [ ] Create JSON schemas for all domain event types
- [ ] Implement event validation at publish time
- [ ] Implement event validation at consume time
- [ ] Configure dead letter queue for invalid events
- [ ] Ensure backward compatibility for schema evolution
- [ ] Write property-based test for Property 11: Event Schema Conformance

### 8.4 Configuration Parser and Serialization
- [ ] Implement Config_Parser for YAML configuration files (Requirement 24)
- [ ] Implement Config_Pretty_Printer for YAML formatting
- [ ] Add validation for required fields (service name, database URL, Kafka brokers)
- [ ] Add validation for port numbers (1-65535)
- [ ] Format YAML with 2-space indentation and sorted keys
- [ ] Write property-based test for Property 10: Configuration Round-Trip

## Phase 9: End-to-End Testing

### 9.1 Docker Compose Test Environment
- [ ] Create docker-compose.yml with all 8 services (Requirement 23)
- [ ] Include Kafka, MySQL, ScyllaDB, Redis, Debezium
- [ ] Include Jaeger, Prometheus, Elasticsearch
- [ ] Configure service dependencies and health checks
- [ ] Test full stack startup

### 9.2 Cucumber Scenarios
- [ ] Implement Cucumber test for Create Order happy path
- [ ] Implement Cucumber test for Create Order with insufficient credit
- [ ] Implement Cucumber test for Cancel Order workflow
- [ ] Implement Cucumber test for Revise Order workflow
- [ ] Verify order history eventual consistency in scenarios

### 9.3 Chaos Engineering Tests
- [ ] Implement test killing Kitchen Service during CreateOrderSaga
- [ ] Verify saga compensation (ticket cancelled, order rejected)
- [ ] Implement test killing Accounting Service after authorization
- [ ] Verify saga completion without re-authorization
- [ ] Implement test with random service failures (30% failure rate)
- [ ] Verify all orders reach terminal state (APPROVED or REJECTED)

### 9.4 Load Testing
- [ ] Create k6 load test script for order placement
- [ ] Test 1000 concurrent order placements
- [ ] Verify 95th percentile latency < 500ms
- [ ] Verify failure rate < 1%
- [ ] Test system behavior under sustained load

### 9.5 Property-Based Test Suite
- [ ] Run all 11 property-based tests with 100 tries each
- [ ] Property 1: Order Creation Idempotency
- [ ] Property 2: Order Total Invariant
- [ ] Property 3: Consumer Credit Invariant
- [ ] Property 4: Authorization Idempotency
- [ ] Property 5: Delivery Temporal Ordering
- [ ] Property 6: Event Processing Idempotency
- [ ] Property 7: JWT Validation Correctness
- [ ] Property 8: Saga Compensation Correctness
- [ ] Property 9: Kafka Partition Key Consistency
- [ ] Property 10: Configuration Round-Trip
- [ ] Property 11: Event Schema Conformance

### 9.6 Integration Test Suite
- [ ] Run all service integration tests with Testcontainers
- [ ] Test CreateOrderSaga end-to-end with real Kafka and MySQL
- [ ] Test CancelOrderSaga end-to-end
- [ ] Test ReviseOrderSaga end-to-end
- [ ] Test CQRS eventual consistency with ScyllaDB
- [ ] Test distributed tracing across all services
- [ ] Test metrics collection and Prometheus scraping
- [ ] Test circuit breaker behavior
- [ ] Test API Gateway authentication and authorization

## Phase 10: Documentation and Deployment

### 10.1 Documentation
- [ ] Create README.md with system overview and architecture
- [ ] Document local development setup instructions
- [ ] Document Docker Compose usage for testing
- [ ] Document Kubernetes deployment instructions
- [ ] Create API documentation for all REST endpoints
- [ ] Document saga workflows with sequence diagrams
- [ ] Document troubleshooting guide

### 10.2 Production Deployment
- [ ] Deploy to production Kubernetes cluster (EKS/GKE)
- [ ] Configure production Kafka cluster
- [ ] Configure production MySQL instances with replication
- [ ] Configure production ScyllaDB cluster
- [ ] Deploy Istio service mesh
- [ ] Deploy observability stack (Jaeger, Prometheus, Grafana, ELK)
- [ ] Configure production secrets in Vault
- [ ] Set up monitoring alerts and dashboards
- [ ] Perform smoke tests in production
- [ ] Monitor saga completion rates and error rates

---

## Notes

- All tasks should be implemented following the design patterns specified in design.md
- Each service must implement the Transactional Outbox pattern for reliable event publishing
- All sagas must follow the orchestration pattern with proper compensation logic
- Property-based tests must use jqwik framework with @Property annotation
- Integration tests must use Testcontainers for infrastructure dependencies
- All services must expose health checks, metrics, and structured logging
- Security must be enforced at all layers (JWT, mTLS, secrets management)

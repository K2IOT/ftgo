# Implementation Tasks: FTGO Microservices Platform

## Phase 1: Infrastructure Foundation

- [x] 1. Set up project structure and infrastructure
  - [x] 1.1 Create root Gradle multi-project build with Java 21 and Spring Boot 3.2
    - Configure multi-project build.gradle with all 8 services
    - Set up common module with shared DTOs, Money value object, Address value object
    - Configure Gradle wrapper and dependency management
    - _Requirements: All services_
  
  - [x] 1.2 Deploy Kafka infrastructure
    - Deploy Kafka 3.x cluster with 3 brokers using Docker Compose
    - Configure Kafka topics with 3 partitions, replication factor 3, 7-day retention
    - Create domain event topics: net.ftgo.orderservice.domain.Order, net.ftgo.consumerservice.domain.Consumer, net.ftgo.restaurantservice.domain.Restaurant, net.ftgo.kitchenservice.domain.Ticket, net.ftgo.accountingservice.domain.Account, net.ftgo.deliveryservice.domain.Delivery
    - Create command channels: orderService, consumerService, kitchenService, accountingService, deliveryService
    - Create saga reply channels: createOrderSagaReply, cancelOrderSagaReply, reviseOrderSagaReply
    - Configure partition key strategy (aggregateType + "#" + aggregateId)
    - _Requirements: 11, 19_
  
  - [x] 1.3 Deploy database infrastructure
    - Deploy MySQL 8 instances for Order, Consumer, Restaurant, Kitchen, Accounting, Delivery services
    - Deploy ScyllaDB cluster for Order History Service
    - Configure HikariCP connection pools (max 20, min idle 5, timeout 30s)
    - Set up Flyway for schema migration management
    - _Requirements: 22_
  
  - [x] 1.4 Set up Debezium CDC
    - Deploy Debezium Connect cluster
    - Configure MySQL binlog settings (binlog_format=ROW, binlog_row_image=FULL)
    - Create Debezium connectors for all 6 MySQL databases
    - Configure outbox table monitoring and Kafka publishing
    - Test CDC event flow from outbox to Kafka
    - _Requirements: 11_
  
  - [x] 1.5 Deploy configuration management
    - Deploy Spring Cloud Config Server with Git backend
    - Create Git repository for service configurations
    - Deploy HashiCorp Vault for secrets management
    - Configure service-specific Vault tokens
    - Create configuration profiles (dev, staging, production)
    - _Requirements: 21_
  
  - [x] 1.6 Set up Kubernetes cluster
    - Set up local Kubernetes cluster (k3s or kind)
    - Create ftgo-production namespace
    - Install Istio service mesh with mTLS STRICT mode
    - Configure Istio circuit breaker policies (5 consecutive 5xx errors, 30s open state)
    - Configure Istio retry policies (3 attempts, 500ms base delay, 5xx only)
    - Configure Istio timeout (5s for all service-to-service requests)
    - Set up RBAC policies
    - _Requirements: 17, 18, 20_

## Phase 2: Core Services Implementation

- [ ] 2. Implement Consumer Service
  - [x] 2.1 Create Consumer aggregate and database schema
    - Create Consumer aggregate with id, name, email, creditLimit, availableCredit fields
    - Implement credit limit validation (positive decimal value)
    - Create Flyway migration V1__create_consumers_table.sql with outbox and processed_messages tables
    - Implement Consumer repository with JPA
    - _Requirements: 4.1, 4.2, 4.4_
  
  - [x] 2.2 Implement Consumer command handlers and API
    - Implement REST API for consumer registration (POST /consumers)
    - Implement REST API for profile updates (PUT /consumers/{id})
    - Implement verifyConsumer command handler for saga participation
    - Validate order total does not exceed available credit limit
    - Implement transactional outbox for ConsumerUpdated events
    - _Requirements: 1.3, 4.3_
  
  - [x] 2.3 Write Consumer Service tests
    - Add unit tests for Consumer aggregate state transitions
    - Add unit tests for credit limit validation
    - Create integration tests with Testcontainers (MySQL + Kafka)
    - _Requirements: 4_
  
  - [x]* 2.4 Write property test for Consumer Credit Invariant
    - **Property 3: Consumer Credit Invariant**
    - **Validates: Requirements 4.5**
    - Test that availableCredit = creditLimit - reservedAmounts for all consumer states
    - Use jqwik with @Property annotation and 100 tries
    - _Requirements: 4.5_

- [ ] 3. Implement Restaurant Service
  - [x] 3.1 Create Restaurant aggregate and database schema
    - Create Restaurant aggregate with id, name, address, openingHours fields
    - Create MenuItem entity with id, restaurantId, name, description, price, available fields
    - Implement price validation (positive decimal values)
    - Create Flyway migration V1__create_restaurants_and_menu_items.sql with outbox and processed_messages tables
    - Implement Restaurant and MenuItem repositories
    - _Requirements: 5.1, 5.2, 5.4_
  
  - [x] 3.2 Implement Restaurant API and event publishing
    - Implement REST API for restaurant creation (POST /restaurants)
    - Implement REST API for menu item management (POST/PUT/DELETE /restaurants/{id}/menu-items)
    - Implement REST API for menu item availability updates
    - Implement transactional outbox for RestaurantMenuChanged events
    - Implement menu item validation for order placement
    - _Requirements: 5.3, 5.5_
  
  - [x]* 3.3 Write Restaurant Service tests
    - Add unit tests for menu item price validation
    - Add unit tests for menu item availability checks
    - Create integration tests with Testcontainers
    - _Requirements: 5_

- [ ] 4. Implement Accounting Service
  - [x] 4.1 Create Account aggregate and database schema
    - Create Account aggregate with id, consumerId fields
    - Create Authorization entity with id, accountId, requestId (idempotency key), amount, status, createdAt, reversedAt fields
    - Implement AuthorizationStatus enum (APPROVED, DENIED, REVERSED)
    - Create Flyway migration V1__create_accounts_and_authorizations.sql with outbox and processed_messages tables
    - Implement Account and Authorization repositories with requestId index
    - _Requirements: 7.1, 7.5_
  
  - [x] 4.2 Implement authorization command handlers
    - Implement authorizeCard command handler with idempotency check using requestId
    - Return cached result for duplicate authorization requests with same requestId
    - Implement reverseAuthorization command handler
    - Implement reviseAuthorization command handler (adjust to new amount)
    - Record all authorization attempts with timestamp, amount, and outcome for audit
    - _Requirements: 7.2, 7.3, 7.4, 7.6_
  
  - [x] 4.3 Implement event publishing and saga participation
    - Implement transactional outbox for CardAuthorized events
    - Implement transactional outbox for CardReversed events
    - Configure Accounting Service as saga participant on accountingService command channel
    - _Requirements: 7.2, 7.3_
  
  - [x] 4.4 Write Accounting Service tests
    - Add unit tests for authorization idempotency (same requestId returns same result)
    - Add unit tests for authorization reversal
    - Add unit tests for authorization revision
    - Create integration tests with Testcontainers
    - _Requirements: 7_
  
  - [x] 4.5 Write property tests for authorization correctness
    - **Property 4: Authorization Idempotency**
    - **Validates: Requirements 7.7**
    - Test that processing same requestId multiple times produces same outcome
    - **Property 8: Saga Compensation Correctness**
    - **Validates: Requirements 2.8, 7.8, 12.8**
    - Test that reversing authorization then re-authorizing is equivalent to never reversing
    - Use jqwik with @Property annotation and 100 tries
    - _Requirements: 7.7, 7.8_

## Phase 3: Order Service and Saga Orchestration

- [x] 5. Implement Order Service core aggregate
  - [x] 5.1 Create Order aggregate with state machine
    - Create Order aggregate with id, version (optimistic locking), state, consumerId, restaurantId, lineItems, deliveryInfo, paymentInfo, orderTotal, createdAt fields
    - Implement OrderState enum (APPROVAL_PENDING, APPROVED, REJECTED, CANCEL_PENDING, CANCELLED, REVISION_PENDING)
    - Create OrderLineItem entity with menuItemId, name, price, quantity fields
    - Implement state machine transitions: approve(), reject(), beginCancel(), confirmCancel(), beginRevise(), confirmRevise()
    - Implement semantic lock validation (reject operations during pending states with 409 Conflict)
    - Create Flyway migration V1__create_orders_and_line_items.sql with outbox, processed_messages, saga_instance, saga_lock_table tables
    - Implement Order repository with optimistic locking (version field)
    - _Requirements: 1.1, 2.1, 2.7, 3.1, 3.7, 12.1, 12.2, 12.3_
  
  - [x] 5.2 Write Order aggregate tests
    - Add unit tests for Order state machine transitions
    - Add unit tests for semantic lock validation (reject concurrent modifications)
    - Test optimistic locking with version field
    - _Requirements: 1, 2, 3, 12_
  
  - [x] 5.3 Write property tests for Order invariants
    - **Property 1: Order Creation Idempotency**
    - **Validates: Requirements 1.9**
    - Test that creating order then querying returns equivalent order details
    - **Property 2: Order Total Invariant**
    - **Validates: Requirements 3.8**
    - Test that revised order total equals sum of line item prices plus delivery fee
    - Use jqwik with @Property annotation and 100 tries
    - _Requirements: 1.9, 3.8_

- [x] 6. Set up Eventuate Tram Sagas framework
  - [x] 6.1 Configure Eventuate Tram Sagas
    - Add Eventuate Tram Sagas dependencies to Order Service
    - Configure Eventuate Tram with Kafka connection
    - Create SagaConfiguration class with saga instance factory
    - Set up saga command channels for all participant services
    - Configure saga reply channels (createOrderSagaReply, cancelOrderSagaReply, reviseOrderSagaReply)
    - Configure saga instance repository with MySQL
    - _Requirements: 1, 2, 3_

- [x] 7. Implement CreateOrderSaga
  - [x] 7.1 Create CreateOrderSaga definition
    - Create CreateOrderSagaData class with orderId, consumerId, restaurantId, lineItems, orderTotal, ticketId, authorizationId fields
    - Implement CreateOrderSaga with 6 steps using Eventuate Tram Sagas framework
    - Step 1: invokeLocal createOrder (state=APPROVAL_PENDING) with compensation rejectOrder
    - Step 2: invokeParticipant verifyConsumer to Consumer Service
    - Step 3: invokeParticipant createTicket to Kitchen Service with compensation cancelTicket
    - Step 4: invokeParticipant authorizeCard to Accounting Service (PIVOT POINT - first non-compensatable step)
    - Step 5: invokeParticipant approveTicket to Kitchen Service (retriable with maxRetries=10, backoffMs=1000)
    - Step 6: invokeLocal approveOrder (state=APPROVED) (retriable)
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 1.6, 1.10_
  
  - [x] 7.2 Implement CreateOrderSaga failure handling
    - Implement compensation execution for failures before pivot (cancelTicket, rejectOrder)
    - Implement retry logic for failures after pivot (approveTicket, approveOrder)
    - Publish OrderApproved event when saga completes successfully
    - Publish OrderRejected event when saga fails before pivot
    - Log saga failures and increment metrics counters
    - _Requirements: 1.7, 1.8_
  
  - [x] 7.3 Write CreateOrderSaga tests
    - Add saga unit tests using Eventuate Tram Sagas testing framework
    - Test success path (all steps succeed)
    - Test failure before pivot (authorization fails, compensation executes)
    - Test failure after pivot (approveTicket fails, retry succeeds)
    - Create integration test for CreateOrderSaga end-to-end flow with real Kafka and MySQL
    - _Requirements: 1_

- [x] 8. Implement CancelOrderSaga
  - [x] 8.1 Create CancelOrderSaga definition
    - Create CancelOrderSagaData class with orderId, ticketId, authorizationId fields
    - Implement CancelOrderSaga with 5 steps
    - Step 1: invokeLocal beginCancel (state=CANCEL_PENDING) with compensation undoCancel (restore to APPROVED)
    - Step 2: invokeParticipant beginCancelTicket to Kitchen Service with compensation undoCancelTicket
    - Step 3: invokeParticipant reverseAuthorization to Accounting Service (PIVOT POINT)
    - Step 4: invokeParticipant confirmCancelTicket to Kitchen Service (retriable)
    - Step 5: invokeLocal confirmCancel (state=CANCELLED) (retriable)
    - _Requirements: 2.1, 2.2, 2.3, 2.4_
  
  - [x] 8.2 Implement CancelOrderSaga failure handling
    - Implement compensation execution for failures before pivot (undoCancelTicket, undoCancel)
    - Implement retry logic for failures after pivot
    - Publish OrderCancelled event when saga completes successfully
    - _Requirements: 2.5, 2.6_
  
  - [x] 8.3 Write CancelOrderSaga tests
    - Add saga unit tests
    - Test success path
    - Test failure before pivot (reversal fails, compensation restores order to APPROVED)
    - Test failure after pivot (confirmCancelTicket fails, retry succeeds)
    - Create integration test for CancelOrderSaga end-to-end flow
    - _Requirements: 2_
  
  - [x] 8.4 Write property test for compensation correctness
    - **Property 8: Saga Compensation Correctness**
    - **Validates: Requirements 2.8**
    - Test that reversing authorization then re-authorizing restores original state
    - Use jqwik with @Property annotation and 100 tries
    - _Requirements: 2.8_

- [x] 9. Implement ReviseOrderSaga
  - [x] 9.1 Create ReviseOrderSaga definition
    - Create ReviseOrderSagaData class with orderId, revisedLineItems, revisedTotal, ticketId, authorizationId fields
    - Implement ReviseOrderSaga with 5 steps
    - Step 1: invokeLocal beginRevise (state=REVISION_PENDING) with compensation undoRevise (restore original order)
    - Step 2: invokeParticipant beginReviseTicket to Kitchen Service with compensation undoReviseTicket
    - Step 3: invokeParticipant reviseCreditCardAuthorization to Accounting Service (PIVOT POINT)
    - Step 4: invokeParticipant confirmReviseTicket to Kitchen Service (retriable)
    - Step 5: invokeLocal confirmRevise (state=APPROVED, update order details) (retriable)
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  
  - [x] 9.2 Implement ReviseOrderSaga failure handling
    - Implement compensation execution for failures before pivot (undoReviseTicket, undoRevise)
    - Implement retry logic for failures after pivot
    - Publish OrderRevised event when saga completes successfully
    - _Requirements: 3.5, 3.6_
  
  - [x] 9.3 Write ReviseOrderSaga tests
    - Add saga unit tests
    - Test success path
    - Test failure before pivot (authorization revision fails, compensation restores original order)
    - Test failure after pivot (confirmReviseTicket fails, retry succeeds)
    - Create integration test for ReviseOrderSaga end-to-end flow
    - _Requirements: 3_

- [x] 10. Implement Order Service REST API
  - [x] 10.1 Create Order REST controllers
    - Implement POST /orders endpoint (initiates CreateOrderSaga)
    - Implement GET /orders/{orderId} endpoint
    - Implement POST /orders/{orderId}/cancel endpoint (initiates CancelOrderSaga)
    - Implement POST /orders/{orderId}/revise endpoint (initiates ReviseOrderSaga)
    - Add validation for concurrent modification requests (return 409 Conflict during pending states)
    - Implement request validation (valid consumerId, restaurantId, menu items, delivery address, payment token)
    - _Requirements: 1.1, 2.1, 3.1, 12.1, 12.2, 12.3_
  
  - [x] 10.2 Implement transactional outbox for Order events
    - Create OutboxRepository for Order Service
    - Implement domain event publishing with outbox insertion (same transaction as order update)
    - Publish OrderApproved, OrderCancelled, OrderRevised events
    - Ensure Debezium CDC publishes events from outbox to Kafka
    - _Requirements: 1.8, 2.6, 3.6, 11.1, 11.2, 11.3_
  
  - [x] 10.3 Write Order Service API tests
    - Add REST API integration tests
    - Test POST /orders creates order and initiates saga
    - Test GET /orders/{orderId} returns order details
    - Test POST /orders/{orderId}/cancel initiates CancelOrderSaga
    - Test POST /orders/{orderId}/revise initiates ReviseOrderSaga
    - Test concurrent modification returns 409 Conflict
    - _Requirements: 1, 2, 3, 12_

## Phase 4: Kitchen and Delivery Services

- [x] 11. Implement Kitchen Service
  - [x] 11.1 Create Ticket aggregate with state machine
    - Create Ticket aggregate with id, restaurantId, orderId, state, lineItems, readyBy, acceptedAt, preparedAt fields
    - Implement TicketState enum (CREATE_PENDING, AWAITING_ACCEPTANCE, ACCEPTED, PREPARING, READY_FOR_PICKUP, PICKED_UP, CANCELLED)
    - Create TicketLineItem entity with menuItemId, name, quantity fields
    - Implement state machine transitions: approve(), accept(), preparing(), readyForPickup(), pickedUp()
    - Enforce valid state transitions per state machine
    - Create Flyway migration V1__create_tickets_and_line_items.sql with outbox and processed_messages tables
    - Implement Ticket repository
    - _Requirements: 6.1, 6.2, 6.6_
  
  - [x] 11.2 Implement Kitchen command handlers for saga participation
    - Implement createTicket command handler (creates ticket in CREATE_PENDING state)
    - Implement approveTicket command handler (transitions to AWAITING_ACCEPTANCE)
    - Implement cancelTicket command handler (compensation for CreateOrderSaga)
    - Implement beginCancelTicket command handler (for CancelOrderSaga)
    - Implement confirmCancelTicket command handler (transitions to CANCELLED)
    - Implement undoCancelTicket command handler (compensation for CancelOrderSaga)
    - Implement beginReviseTicket command handler (for ReviseOrderSaga)
    - Implement confirmReviseTicket command handler (updates line items)
    - Implement undoReviseTicket command handler (compensation for ReviseOrderSaga)
    - Configure Kitchen Service as saga participant on kitchenService command channel
    - _Requirements: 1.4, 1.6, 2.2, 2.4, 3.2, 3.4, 6.1, 6.2, 6.5_
  
  - [x] 11.3 Implement Kitchen REST API for kitchen staff
    - Implement GET /tickets endpoint (query tickets by restaurantId and state)
    - Implement POST /tickets/{ticketId}/accept endpoint (kitchen staff accepts ticket)
    - Implement POST /tickets/{ticketId}/preparing endpoint (mark as preparing)
    - Implement POST /tickets/{ticketId}/ready endpoint (mark as ready for pickup)
    - Implement transactional outbox for TicketAccepted, TicketPreparing, TicketReady, TicketCancelled events
    - _Requirements: 6.3, 6.4, 6.5_
  
  - [x] 11.4 Write Kitchen Service tests
    - Add unit tests for Ticket state machine transitions
    - Add unit tests for command handlers
    - Test state transition validation (reject invalid transitions)
    - Create integration tests with saga participation
    - Test ticket line items match order line items
    - _Requirements: 6_

- [x] 12. Implement Delivery Service
  - [x] 12.1 Create Delivery aggregate and database schema
    - Create Delivery aggregate with id, orderId, courierId, pickupAddress, deliveryAddress, scheduledTime, pickupTime, deliveryTime, status fields
    - Create Courier entity with id, name, phone, available fields
    - Implement DeliveryStatus enum (PENDING, ASSIGNED, PICKED_UP, DELIVERED)
    - Create Flyway migration V1__create_deliveries_and_couriers.sql with outbox and processed_messages tables
    - Implement Delivery and Courier repositories
    - _Requirements: 8.1_
  
  - [x] 12.2 Implement Delivery event handlers and API
    - Implement OrderApproved event handler (creates delivery record with pickup/delivery addresses and scheduled time)
    - Implement REST API for courier assignment (POST /deliveries/{deliveryId}/assign)
    - Implement REST API for pickup (POST /deliveries/{deliveryId}/pickup)
    - Implement REST API for delivery completion (POST /deliveries/{deliveryId}/deliver)
    - Implement estimated delivery time calculation based on distance
    - Implement transactional outbox for DeliveryAssigned, DeliveryPickedUp, DeliveryDelivered events
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_
  
  - [x] 12.3 Write Delivery Service tests
    - Add unit tests for delivery status transitions
    - Add unit tests for estimated delivery time calculation
    - Create integration tests with event consumption
    - Test OrderApproved event creates delivery record
    - _Requirements: 8_
  
  - [x] 12.4 Write property test for Delivery Temporal Ordering
    - **Property 5: Delivery Temporal Ordering**
    - **Validates: Requirements 8.6**
    - Test that pickupTime < deliveryTime for all completed deliveries
    - Use jqwik with @Property annotation and 100 tries
    - _Requirements: 8.6_

## Phase 5: API Gateway and Authentication

- [x] 13. Implement API Gateway core
  - [x] 13.1 Create Spring Cloud Gateway project
    - Create api-gateway module with Spring Cloud Gateway dependencies
    - Configure Redis for session storage and rate limiting
    - Set up application.yml with gateway routes
    - _Requirements: 10_
  
  - [x] 13.2 Implement JWT authentication and authorization
    - Implement JWT authentication filter
    - Implement JWT signature validation with OAuth2 public key
    - Extract user ID and roles from JWT token
    - Implement role-based authorization (ROLE_CONSUMER, ROLE_RESTAURANT, ROLE_COURIER, ROLE_ADMIN)
    - Return 401 Unauthorized for expired or invalid JWT
    - _Requirements: 10.1, 10.2, 10.3, 10.4_
  
  - [x] 13.3 Write JWT validation tests
    - Add unit tests for JWT validation
    - Test valid JWT is accepted
    - Test expired JWT is rejected
    - Test invalid signature is rejected
    - Test role-based authorization
    - _Requirements: 10_
  
  - [x] 13.4 Write property test for JWT Validation Correctness
    - **Property 7: JWT Validation Correctness**
    - **Validates: Requirements 10.8**
    - Test that JWT is accepted iff signature is valid AND not expired
    - Use jqwik with @Property annotation and 100 tries
    - _Requirements: 10.8_

- [x] 14. Implement Gateway routing and resilience
  - [x] 14.1 Configure gateway routes
    - Configure routes for Order Service (/orders/**)
    - Configure routes for Consumer Service (/consumers/**)
    - Configure routes for Restaurant Service (/restaurants/**)
    - Configure routes for Kitchen Service (/tickets/**)
    - Configure routes for Delivery Service (/deliveries/**)
    - Configure routes for Order History Service (/order-history/**)
    - _Requirements: 10_
  
  - [x] 14.2 Implement circuit breaker and rate limiting
    - Implement circuit breaker filter with Resilience4j (5 consecutive failures trigger open state for 30s)
    - Implement rate limiting filter (100 requests per minute per consumer)
    - Implement fallback responses for circuit breaker open state
    - Return 429 Too Many Requests when rate limit exceeded
    - _Requirements: 10.5, 10.7, 18_
  
  - [x] 14.3 Write circuit breaker tests
    - Add integration tests for circuit breaker behavior
    - Test circuit opens after 5 consecutive failures
    - Test circuit closes after successful test request in half-open state
    - Test rate limiting returns 429 after 100 requests
    - _Requirements: 10.7, 18_

- [x] 15. Implement API composition
  - [x] 15.1 Create order details composition endpoint
    - Implement GET /order-details/{orderId} endpoint
    - Aggregate data from Order Service, Kitchen Service, and Delivery Service
    - Use Mono.zip for parallel service calls
    - Implement fallback for service unavailability (return cached data or partial response)
    - _Requirements: 10.6_
  
  - [x] 15.2 Write API composition tests
    - Add integration tests for API composition
    - Test successful aggregation from all services
    - Test fallback when one service is unavailable
    - Test parallel service calls reduce latency
    - _Requirements: 10.6_

## Phase 6: CQRS Read Model (Order History Service)

- [x] 16. Implement Order History Service with ScyllaDB
  - [x] 16.1 Create ScyllaDB schema
    - Create order_history table with order_id as partition key
    - Create line_item user-defined type (menu_item_id, name, price, quantity)
    - Create order_history_by_consumer materialized view (partition key: consumer_id, clustering key: creation_date DESC)
    - Create processed_messages table for idempotency tracking
    - Configure ScyllaDB connection with CassandraTemplate
    - _Requirements: 9.1, 9.6_
  
  - [x] 16.2 Implement event handlers for read model updates
    - Implement OrderCreated event handler (create order history record)
    - Implement OrderApproved event handler (update status to APPROVED)
    - Implement OrderCancelled event handler (update status to CANCELLED)
    - Implement OrderRevised event handler (update order details and line items)
    - Implement TicketAccepted event handler (update ticketStatus)
    - Implement TicketReady event handler (update ticketStatus to READY)
    - Implement DeliveryPickedUp event handler (update deliveryStatus to PICKED_UP)
    - Implement DeliveryDelivered event handler (update deliveryStatus to DELIVERED)
    - Implement CardAuthorized event handler (update authorizationStatus to APPROVED)
    - Add idempotency check using processed_messages table (check messageId before processing)
    - _Requirements: 9.1, 9.2, 9.6_
  
  - [x] 16.3 Write event handler tests
    - Add unit tests for event handlers
    - Test OrderCreated creates new record
    - Test subsequent events update existing record
    - Test idempotency (processing same event twice produces same state)
    - _Requirements: 9_

- [-] 17. Implement Order History query API
  - [x] 17.1 Create query endpoints
    - Implement GET /orders/{orderId} endpoint (query by order_id)
    - Implement GET /consumers/{consumerId}/orders endpoint (query materialized view)
    - Add filtering by status, date range, restaurant, keyword
    - Implement pagination with page size and continuation token (paging state)
    - Return complete order details including line items and delivery status
    - _Requirements: 9.3, 9.4, 9.5, 9.7_
  
  - [x] 17.2 Write query API tests
    - Add integration tests with ScyllaDB Testcontainer
    - Test query by order_id returns correct record
    - Test query by consumer_id returns orders sorted by creation_date DESC
    - Test filtering by status, date range, restaurant
    - Test pagination with continuation token
    - Test eventual consistency with write model (create order, wait for event propagation, query read model)
    - _Requirements: 9_
  
  - [x] 17.3 Write property test for Event Processing Idempotency
    - **Property 6: Event Processing Idempotency**
    - **Validates: Requirements 9.9, 11.8**
    - Test that processing same event multiple times produces same final state
    - Use jqwik with @Property annotation and 100 tries
    - _Requirements: 9.9, 11.8_

## Phase 7: Observability and Production Hardening

- [ ] 18. Implement health checks
  - [ ] 18.1 Add Spring Boot Actuator to all services
    - Add Spring Boot Actuator dependencies to all 8 services
    - Implement /actuator/health endpoint with database connectivity check (execute test query)
    - Implement Kafka producer connectivity check
    - Return HTTP 200 with status UP when all dependencies healthy
    - Return HTTP 503 with status DOWN and component details when any dependency unhealthy
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5_
  
  - [ ] 18.2 Configure Kubernetes probes
    - Configure readiness probes to call /actuator/health (30s initial delay, 10s period)
    - Configure liveness probes to call /actuator/health (60s initial delay, 20s period)
    - Test that Kubernetes removes pod from load balancing when readiness probe fails
    - _Requirements: 13.6, 13.7, 13.8_
  
  - [ ]* 18.3 Write health check tests
    - Add integration tests for health check endpoints
    - Test health check returns UP when dependencies healthy
    - Test health check returns DOWN when database unavailable
    - Test health check returns DOWN when Kafka unavailable
    - _Requirements: 13_

- [ ] 19. Implement distributed tracing
  - [ ] 19.1 Add OpenTelemetry instrumentation
    - Add OpenTelemetry Java Agent to all services
    - Configure W3C Trace Context propagation (traceparent, tracestate headers)
    - Configure trace context propagation in Kafka message headers
    - Configure OTLP export to Jaeger
    - Add trace ID to MDC (Mapped Diagnostic Context) for log correlation
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.7_
  
  - [ ] 19.2 Deploy Jaeger backend
    - Deploy Jaeger all-in-one or production deployment
    - Configure Jaeger UI for trace visualization
    - Ensure all services export spans to Jaeger
    - _Requirements: 14.5_
  
  - [ ] 19.3 Implement saga tracing
    - Ensure all saga steps share the same trace ID for end-to-end visibility
    - Propagate trace context through saga command channels
    - Verify complete request path across all services visible as single trace
    - _Requirements: 14.6, 14.8_
  
  - [ ]* 19.4 Write distributed tracing tests
    - Write integration test verifying trace spans across all services
    - Test that CreateOrderSaga generates spans for Order, Consumer, Kitchen, Accounting services
    - Test that all spans share same trace ID
    - Test W3C Trace Context propagation in HTTP headers
    - Test trace context propagation in Kafka message headers
    - _Requirements: 14_

- [ ] 20. Implement metrics and monitoring
  - [ ] 20.1 Add Micrometer metrics to all services
    - Add Micrometer dependencies to all services
    - Implement order_service_placed_orders_total counter (increment on order creation)
    - Implement order_service_approved_orders_total counter (increment on order approval)
    - Implement order_service_saga_duration_seconds histogram (record saga execution time)
    - Implement kitchen_service_ticket_creation_total counter (increment on ticket creation)
    - Implement accounting_service_authorization_total counter labeled by outcome (success, failure)
    - Expose /actuator/prometheus endpoint for all services
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6_
  
  - [ ] 20.2 Deploy Prometheus and Grafana
    - Deploy Prometheus with 15s scrape interval
    - Configure Prometheus to scrape /actuator/prometheus from all services
    - Deploy Grafana
    - Create Grafana dashboards for RED metrics (Rate, Errors, Duration) per service
    - Create Grafana dashboard for saga completion rate and compensation rate
    - _Requirements: 15.7, 15.8, 15.9_
  
  - [ ]* 20.3 Write metrics tests
    - Write integration test verifying metrics are recorded
    - Test order_service_placed_orders_total increments on order creation
    - Test order_service_saga_duration_seconds records saga duration
    - Test accounting_service_authorization_total labeled by outcome
    - _Requirements: 15_

- [ ] 21. Implement structured logging
  - [ ] 21.1 Configure structured JSON logging
    - Configure Logback for JSON structured logging in all services
    - Add timestamp, level, service name, trace ID, and message fields to all log entries
    - Log all messages to stdout
    - _Requirements: 16.1, 16.2_
  
  - [ ] 21.2 Deploy ELK stack
    - Deploy Fluentd DaemonSet in Kubernetes to collect logs from all pods
    - Configure Fluentd to forward logs to Elasticsearch
    - Deploy Kibana with log search dashboards per service
    - Configure alert for error log rate > 10 errors per minute
    - Set log retention to 30 days in Elasticsearch
    - _Requirements: 16.3, 16.4, 16.5, 16.6, 16.7_

- [ ] 22. Implement security and mTLS
  - [ ] 22.1 Configure Istio mTLS
    - Configure Istio PeerAuthentication with mTLS STRICT mode for all service-to-service communication
    - Configure Istio AuthorizationPolicy allowing only authorized services to communicate
    - Implement automatic certificate rotation
    - Test that all service-to-service traffic is encrypted with TLS 1.3
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.7_
  
  - [ ] 22.2 Implement JWT validation in API Gateway
    - Implement JWT signature validation using public key from OAuth2 authorization server
    - Validate JWT expiration time
    - Extract user ID and roles from JWT claims
    - _Requirements: 10.2, 17.5_
  
  - [ ] 22.3 Implement sensitive data filtering
    - Add sensitive data filtering in logs (credit card numbers, passwords)
    - Never log or expose sensitive data in error messages
    - _Requirements: 17.6_
  
  - [ ]* 22.4 Write security tests
    - Write integration test verifying mTLS encryption
    - Test that service-to-service traffic uses TLS 1.3
    - Test JWT signature validation
    - Test sensitive data is not logged
    - _Requirements: 17_

- [ ] 23. Implement resilience patterns
  - [ ] 23.1 Configure Istio circuit breaker and retry
    - Configure Istio circuit breaker (5 consecutive 5xx errors trigger open state for 30s)
    - Configure Istio retry policy (3 attempts, 500ms base delay, 5xx errors only)
    - Configure Istio timeout (5s for all service-to-service requests)
    - Test circuit breaker transitions to open state after 5 failures
    - Test circuit breaker transitions to half-open state after 30s
    - Test circuit breaker closes after successful test request
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 18.6, 18.7, 18.8_
  
  - [ ] 23.2 Implement connection pool monitoring
    - Implement connection pool monitoring with HikariCP
    - Configure connection pool limits (max 20, min idle 5, timeout 30s)
    - Implement deadlock retry with @Retryable annotation (3 attempts, 100ms delay, exponential backoff)
    - _Requirements: Database failures handling_
  
  - [ ]* 23.3 Write resilience tests
    - Add chaos engineering tests (kill services during saga execution)
    - Test circuit breaker behavior under load
    - Test that system recovers from transient failures
    - _Requirements: 18_

- [ ] 24. Implement Kafka configuration and testing
  - [ ] 24.1 Configure Kafka partition strategy
    - Configure Kafka partition key strategy (aggregateType + "#" + aggregateId)
    - Verify event ordering per aggregate (same partition key → same partition)
    - Configure consumer groups for each service
    - Test partition rebalancing when consumer joins/leaves group
    - _Requirements: 19.1, 19.2, 19.3, 19.4, 19.5, 19.6_
  
  - [ ]* 24.2 Write property test for Kafka Partition Key Consistency
    - **Property 9: Kafka Partition Key Consistency**
    - **Validates: Requirements 19.7**
    - Test that two events for same aggregate have same partition key
    - Use jqwik with @Property annotation and 100 tries
    - _Requirements: 19.7_

- [ ] 25. Configure deployment and scaling
  - [ ] 25.1 Create Kubernetes deployment manifests
    - Create Kubernetes Deployment manifests for all 8 services
    - Configure API Gateway with 3 replicas
    - Configure Order Service with 3 replicas
    - Configure Consumer, Restaurant, Kitchen, Accounting, Delivery, Order History services with 2 replicas each
    - Configure resource limits (CPU, memory) for each service
    - _Requirements: 20.1, 20.2, 20.3, 20.4, 20.8_
  
  - [ ] 25.2 Configure autoscaling and rolling updates
    - Configure Horizontal Pod Autoscaler (HPA) to scale services based on CPU utilization > 70%
    - Configure rolling update strategy (maxSurge=1, maxUnavailable=0)
    - Create Istio VirtualService for canary deployments (95% stable, 5% canary)
    - Test zero-downtime deployment
    - _Requirements: 20.5, 20.6, 20.7, 20.9_

## Phase 8: Configuration, Schema Management, and Event Validation

- [ ] 26. Implement configuration management
  - [ ] 26.1 Set up Spring Cloud Config Server
    - Create service configurations in Git repository
    - Configure Spring Cloud Config Server with Git backend
    - Create configuration profiles (dev, staging, production)
    - Configure services to fetch configuration from Config Server based on service name and profile
    - _Requirements: 21.1, 21.2, 21.3_
  
  - [ ] 26.2 Integrate HashiCorp Vault for secrets
    - Store database passwords in Vault
    - Store API keys in Vault
    - Implement Vault token-based secret retrieval for each service
    - Never include secrets in Docker images or Kubernetes ConfigMaps
    - Test configuration refresh without service restart (where supported)
    - _Requirements: 21.4, 21.5, 21.6, 21.7_

- [ ] 27. Implement database schema management
  - [ ] 27.1 Create Flyway migration scripts
    - Create Flyway migration scripts for all services (V1__initial_schema.sql, V2__add_columns.sql, etc.)
    - Ensure backward compatibility for rolling deployments
    - Configure Flyway to execute pending migrations on service startup
    - Configure Flyway to record applied migrations in schema_version table
    - Enforce that migration scripts are immutable once applied to production
    - Test migration failure handling (mark as failed, prevent service startup)
    - _Requirements: 22.1, 22.2, 22.3, 22.4, 22.5, 22.6_
  
  - [ ]* 27.2 Write schema management tests
    - Test Flyway migrations execute successfully
    - Test backward compatibility of schema changes
    - Test migration failure prevents service startup
    - _Requirements: 22_

- [ ] 28. Implement configuration parser and serialization
  - [ ] 28.1 Create Config_Parser for YAML configuration
    - Implement Config_Parser to parse YAML configuration files into Configuration objects
    - Return descriptive error with line number and issue for invalid YAML
    - Validate that required fields (service name, database URL, Kafka brokers) are present
    - Validate that port numbers are in valid range 1-65535
    - _Requirements: 24.1, 24.2, 24.5, 24.6_
  
  - [ ] 28.2 Create Config_Pretty_Printer for YAML formatting
    - Implement Config_Pretty_Printer to format Configuration objects back into YAML
    - Format YAML with consistent indentation (2 spaces)
    - Sort keys alphabetically
    - _Requirements: 24.3, 24.7_
  
  - [ ]* 28.3 Write configuration parser tests
    - Test valid YAML is parsed correctly
    - Test invalid YAML returns descriptive error
    - Test required field validation
    - Test port number validation
    - Test YAML formatting with 2-space indentation and sorted keys
    - _Requirements: 24_
  
  - [ ]* 28.4 Write property test for Configuration Round-Trip
    - **Property 10: Configuration Round-Trip**
    - **Validates: Requirements 24.4**
    - Test that parsing then printing then parsing produces equivalent Configuration object
    - Use jqwik with @Property annotation and 100 tries
    - _Requirements: 24.4_

- [ ] 29. Implement event schema validation
  - [ ] 29.1 Deploy schema registry
    - Deploy schema registry for domain events
    - Create JSON schemas for all domain event types (OrderCreated, OrderApproved, OrderCancelled, TicketCreated, CardAuthorized, etc.)
    - Register schemas in schema registry with versioning
    - _Requirements: 25.5_
  
  - [ ] 29.2 Implement event validation at publish time
    - Implement Event_Publisher to validate events against registered JSON schema before publishing
    - Throw exception and prevent publishing if event fails schema validation
    - Log validation errors and increment metrics counter
    - _Requirements: 25.1, 25.3_
  
  - [ ] 29.3 Implement event validation at consume time
    - Implement Event_Consumer to validate events against expected JSON schema on consumption
    - Log error, send event to dead letter queue, and continue processing if validation fails
    - Do not throw exception (acknowledge and skip invalid events)
    - _Requirements: 25.2, 25.4_
  
  - [ ] 29.4 Ensure schema backward compatibility
    - Ensure backward compatibility when event schema evolves (make new fields optional)
    - Test that old consumers can process events with new schema
    - Test that new consumers can process events with old schema
    - _Requirements: 25.6_
  
  - [ ]* 29.5 Write event schema validation tests
    - Test valid event passes schema validation
    - Test invalid event fails schema validation at publish time
    - Test invalid event is sent to dead letter queue at consume time
    - Test schema backward compatibility
    - _Requirements: 25_
  
  - [ ]* 29.6 Write property test for Event Schema Conformance
    - **Property 11: Event Schema Conformance**
    - **Validates: Requirements 25.7**
    - Test that all published events conform to registered JSON schema
    - Use jqwik with @Property annotation and 100 tries
    - _Requirements: 25.7_

## Phase 9: End-to-End Testing and Validation

- [ ] 30. Set up Docker Compose test environment
  - [ ] 30.1 Create comprehensive Docker Compose file
    - Create docker-compose.yml with all 8 services
    - Include Kafka (3 brokers), MySQL (6 instances), ScyllaDB, Redis, Debezium
    - Include Jaeger, Prometheus, Grafana, Elasticsearch, Kibana, Fluentd
    - Configure service dependencies and health checks
    - Test full stack startup
    - _Requirements: 23.1_

- [ ] 31. Implement Cucumber end-to-end scenarios
  - [ ] 31.1 Create Cucumber test for Create Order happy path
    - Given consumer with credit limit and restaurant with menu
    - When consumer places order
    - Then order should be APPROVED, ticket created, credit authorized, order history updated
    - _Requirements: 23.2_
  
  - [ ] 31.2 Create Cucumber test for Create Order with insufficient credit
    - Given consumer with low credit limit
    - When consumer places expensive order
    - Then order should be REJECTED, no ticket created, no authorization
    - _Requirements: 23.2_
  
  - [ ] 31.3 Create Cucumber test for Cancel Order workflow
    - Given approved order
    - When consumer cancels order
    - Then order should be CANCELLED, ticket cancelled, authorization reversed
    - _Requirements: 23.3_
  
  - [ ] 31.4 Create Cucumber test for Revise Order workflow
    - Given approved order
    - When consumer revises order with new line items
    - Then order should be updated, ticket revised, authorization adjusted
    - _Requirements: 23.3_
  
  - [ ] 31.5 Verify order history eventual consistency
    - For all scenarios, verify order history reflects final state
    - _Requirements: 23.5_

- [ ] 32. Implement chaos engineering tests
  - [ ] 32.1 Test saga compensation with Kitchen Service failure
    - Kill Kitchen Service during CreateOrderSaga after ticket creation
    - Verify saga compensation (ticket cancelled, order rejected)
    - _Requirements: 23.4, 23.5_
  
  - [ ] 32.2 Test saga completion with Accounting Service failure after authorization
    - Kill Accounting Service after authorization succeeds
    - Verify saga completes without re-authorizing (retriable steps succeed)
    - _Requirements: 23.6_
  
  - [ ] 32.3 Test system resilience with random service failures
    - Inject random failures into services (30% failure rate)
    - Create 100 orders concurrently
    - Verify all orders reach terminal state (APPROVED or REJECTED)
    - _Requirements: 23.7, 23.8_

- [ ] 33. Implement load testing
  - [ ] 33.1 Create k6 load test script
    - Create k6 script for order placement
    - Test 1000 concurrent order placements
    - Verify 95th percentile latency < 500ms
    - Verify failure rate < 1%
    - Test system behavior under sustained load
    - _Requirements: 23.7_

- [ ] 34. Run comprehensive property-based test suite
  - [ ]* 34.1 Run all 11 property-based tests with 100 tries each
    - Property 1: Order Creation Idempotency (Requirements 1.9)
    - Property 2: Order Total Invariant (Requirements 3.8)
    - Property 3: Consumer Credit Invariant (Requirements 4.5)
    - Property 4: Authorization Idempotency (Requirements 7.7)
    - Property 5: Delivery Temporal Ordering (Requirements 8.6)
    - Property 6: Event Processing Idempotency (Requirements 9.9, 11.8)
    - Property 7: JWT Validation Correctness (Requirements 10.8)
    - Property 8: Saga Compensation Correctness (Requirements 2.8, 7.8, 12.8)
    - Property 9: Kafka Partition Key Consistency (Requirements 19.7)
    - Property 10: Configuration Round-Trip (Requirements 24.4)
    - Property 11: Event Schema Conformance (Requirements 25.7)
    - _Requirements: All correctness properties_

- [ ] 35. Run comprehensive integration test suite
  - [ ]* 35.1 Run all service integration tests with Testcontainers
    - Test CreateOrderSaga end-to-end with real Kafka and MySQL
    - Test CancelOrderSaga end-to-end
    - Test ReviseOrderSaga end-to-end
    - Test CQRS eventual consistency with ScyllaDB
    - Test distributed tracing across all services
    - Test metrics collection and Prometheus scraping
    - Test circuit breaker behavior
    - Test API Gateway authentication and authorization
    - _Requirements: All integration requirements_


## Phase 10: Documentation and Production Deployment

- [ ] 36. Create comprehensive documentation
  - [ ] 36.1 Create system documentation
    - Create README.md with system overview and architecture
    - Document local development setup instructions
    - Document Docker Compose usage for testing
    - Document Kubernetes deployment instructions
    - Create API documentation for all REST endpoints (OpenAPI/Swagger)
    - Document saga workflows with sequence diagrams
    - Create troubleshooting guide
    - _Requirements: All_

- [ ] 37. Deploy to production
  - [ ] 37.1 Deploy infrastructure
    - Deploy to production Kubernetes cluster (EKS/GKE/AKS)
    - Configure production Kafka cluster (3+ brokers, replication factor 3)
    - Configure production MySQL instances with replication
    - Configure production ScyllaDB cluster (3+ nodes)
    - Deploy Redis cluster for API Gateway
    - _Requirements: 20_
  
  - [ ] 37.2 Deploy service mesh and observability
    - Deploy Istio service mesh with mTLS STRICT mode
    - Deploy observability stack (Jaeger, Prometheus, Grafana, ELK)
    - Configure production secrets in Vault
    - Set up monitoring alerts and dashboards
    - _Requirements: 13, 14, 15, 16, 17_
  
  - [ ] 37.3 Deploy services and validate
    - Deploy all 8 services with production configuration
    - Perform smoke tests in production
    - Monitor saga completion rates and error rates
    - Verify zero-downtime deployment with rolling updates
    - Monitor metrics, traces, and logs
    - _Requirements: All_

---

## Summary

This implementation plan covers all 25 requirements across 37 major tasks organized into 10 phases:

1. **Phase 1**: Infrastructure foundation (Kafka, databases, CDC, config, Kubernetes)
2. **Phase 2**: Core services (Consumer, Restaurant, Accounting)
3. **Phase 3**: Order Service and saga orchestration (CreateOrder, CancelOrder, ReviseOrder sagas)
4. **Phase 4**: Kitchen and Delivery services
5. **Phase 5**: API Gateway with authentication, authorization, circuit breaking, rate limiting
6. **Phase 6**: CQRS read model with ScyllaDB
7. **Phase 7**: Observability (health checks, tracing, metrics, logging, security, resilience)
8. **Phase 8**: Configuration management, schema management, event validation
9. **Phase 9**: End-to-end testing (Cucumber, chaos engineering, load testing, property tests)
10. **Phase 10**: Documentation and production deployment

### Key Implementation Patterns

- **Transactional Outbox**: All services implement outbox pattern for reliable event publishing
- **Saga Orchestration**: Order Service orchestrates distributed transactions with compensation logic
- **Semantic Locking**: Pending states prevent concurrent modifications during saga execution
- **Idempotent Processing**: All event consumers check messageId before processing
- **Property-Based Testing**: 11 universal properties validated with jqwik framework
- **Chaos Engineering**: Services tested under failure conditions to verify resilience

### Testing Strategy

- **Unit Tests**: Test aggregates, state machines, and business logic in isolation
- **Property-Based Tests**: Validate universal properties across all inputs (11 properties)
- **Integration Tests**: Test service interactions with real Kafka and databases using Testcontainers
- **End-to-End Tests**: Cucumber scenarios for complete workflows
- **Chaos Tests**: Kill services during saga execution to verify compensation
- **Load Tests**: k6 tests for 1000 concurrent orders with latency and failure rate validation

### Requirements Coverage

All 25 requirements are covered:
- Requirements 1-3: Order placement, cancellation, revision (Phases 3, 9)
- Requirements 4-8: Consumer, Restaurant, Accounting, Delivery, Kitchen services (Phases 2, 4)
- Requirement 9: Order History CQRS (Phase 6)
- Requirement 10: API Gateway (Phase 5)
- Requirement 11: Transactional Outbox (Phase 3)
- Requirement 12: Saga isolation (Phase 3)
- Requirements 13-18: Observability and resilience (Phase 7)
- Requirement 19: Kafka ordering (Phase 7)
- Requirement 20: Deployment and scaling (Phase 7)
- Requirement 21: Configuration management (Phase 8)
- Requirement 22: Schema management (Phase 8)
- Requirement 23: End-to-end testing (Phase 9)
- Requirement 24: Configuration parser (Phase 8)
- Requirement 25: Event schema validation (Phase 8)

### Notes

- Tasks marked with `*` are optional test-related sub-tasks that can be skipped for faster MVP
- Each task references specific requirements for traceability
- Property-based tests use jqwik framework with @Property annotation and 100 tries
- Integration tests use Testcontainers for infrastructure dependencies
- All services expose health checks, metrics, and structured logging
- Security enforced at all layers (JWT, mTLS, secrets management)
- Saga orchestration uses Eventuate Tram Sagas framework
- CQRS read model uses ScyllaDB for high-performance queries

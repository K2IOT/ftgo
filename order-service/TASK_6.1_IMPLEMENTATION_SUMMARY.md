# Task 6.1 Implementation Summary: Configure Eventuate Tram Sagas

## Overview

This task configured the Eventuate Tram Sagas framework in the Order Service to enable saga orchestration for distributed transactions across multiple microservices.

## What Was Implemented

### 1. Saga Configuration Class

**File**: `order-service/src/main/java/net/ftgo/order/config/OrderServiceConfiguration.java`

Created the main configuration class that:
- Imports `SagaOrchestratorConfiguration` to enable saga orchestration capabilities
- Imports `TramEventsPublisherConfiguration` to enable domain event publishing
- Automatically configures all necessary saga infrastructure beans

**Key Components Configured by SagaOrchestratorConfiguration:**

- **SagaInstanceFactory**: Creates and manages saga instances
- **SagaManagerFactory**: Creates saga managers for each saga type (CreateOrderSaga, CancelOrderSaga, ReviseOrderSaga)
- **SagaCommandProducer**: Sends commands to participant services via Kafka
- **SagaInstanceRepository**: Persists saga state to MySQL (saga_instance tables)
- **Command Channels**: Configured for all participant services:
  - `consumerService` - Consumer Service command channel
  - `kitchenService` - Kitchen Service command channel
  - `accountingService` - Accounting Service command channel
  - `deliveryService` - Delivery Service command channel
- **Reply Channels**: Configured for saga replies:
  - `createOrderSagaReply` - Replies for CreateOrderSaga
  - `cancelOrderSagaReply` - Replies for CancelOrderSaga
  - `reviseOrderSagaReply` - Replies for ReviseOrderSaga

### 2. Eventuate Tram Configuration in application.yml

**File**: `order-service/src/main/resources/application.yml`

Added Eventuate Tram configuration:

```yaml
eventuatelocal:
  kafka:
    bootstrap:
      servers: localhost:9092
  cdc:
    reader:
      name: MySqlReader
```

**Configuration Details:**

- **kafka.bootstrap.servers**: Connects Eventuate Tram to Kafka broker at localhost:9092
- **cdc.reader.name**: Specifies MySqlReader for Change Data Capture (Debezium will use this to read from MySQL binlog)

### 3. Dependencies Already in Place

The following dependencies were already configured in `build.gradle`:

```gradle
// Order Service specific dependencies (saga orchestrator)
project(':order-service') {
    dependencies {
        implementation 'io.eventuate.tram.sagas:eventuate-tram-sagas-spring-orchestration-simple-dsl'
        implementation 'io.eventuate.tram.sagas:eventuate-tram-sagas-spring-participant'
        // ... other dependencies
    }
}
```

**Key Dependencies:**

- `eventuate-tram-sagas-spring-orchestration-simple-dsl`: Provides the DSL for defining saga orchestration
- `eventuate-tram-sagas-spring-participant`: Allows Order Service to also act as a saga participant (for local steps)
- `eventuate-tram-spring-messaging`: Core messaging infrastructure
- `eventuate-tram-spring-events`: Event publishing support
- `eventuate-tram-spring-commands`: Command handling support

### 4. Database Schema Already in Place

The database migration `V1__create_orders_and_line_items.sql` already includes all necessary saga tables:

- **saga_instance**: Stores saga state and execution progress
- **saga_instance_participants**: Tracks saga participants and their state
- **saga_lock_table**: Provides distributed locking for saga execution
- **saga_stash_table**: Stores stashed messages during saga execution

### 5. Channel Names Already Defined

The `common` module already defines all channel names in `ChannelNames.java`:

```java
// Command Channels
public static final String CONSUMER_SERVICE_COMMAND_CHANNEL = "consumerService";
public static final String KITCHEN_SERVICE_COMMAND_CHANNEL = "kitchenService";
public static final String ACCOUNTING_SERVICE_COMMAND_CHANNEL = "accountingService";
public static final String DELIVERY_SERVICE_COMMAND_CHANNEL = "deliveryService";

// Saga Reply Channels
public static final String CREATE_ORDER_SAGA_REPLY_CHANNEL = "createOrderSagaReply";
public static final String CANCEL_ORDER_SAGA_REPLY_CHANNEL = "cancelOrderSagaReply";
public static final String REVISE_ORDER_SAGA_REPLY_CHANNEL = "reviseOrderSagaReply";
```

## How It Works

### Saga Orchestration Flow

1. **Saga Creation**: When a saga is initiated (e.g., CreateOrderSaga), the SagaInstanceFactory creates a new saga instance
2. **State Persistence**: Saga state is persisted to the `saga_instance` table in MySQL
3. **Command Sending**: SagaCommandProducer sends commands to participant services via Kafka command channels
4. **Reply Handling**: Participant services send replies back via saga reply channels
5. **Step Execution**: Saga manager executes each step, updating saga state after each step
6. **Compensation**: If a step fails before the pivot point, compensation logic is executed in reverse order
7. **Completion**: When all steps succeed, the saga is marked as complete

### Kafka Topics Used

**Command Channels** (Order Service → Participant Services):
- `consumerService` - Commands to Consumer Service
- `kitchenService` - Commands to Kitchen Service
- `accountingService` - Commands to Accounting Service
- `deliveryService` - Commands to Delivery Service

**Reply Channels** (Participant Services → Order Service):
- `createOrderSagaReply` - Replies for CreateOrderSaga
- `cancelOrderSagaReply` - Replies for CancelOrderSaga
- `reviseOrderSagaReply` - Replies for ReviseOrderSaga

**Domain Event Topics** (for event publishing):
- `net.ftgo.orderservice.domain.Order` - Order lifecycle events

### MySQL Tables Used

**Saga State Management:**
- `saga_instance` - Stores saga type, ID, state, and data (JSON)
- `saga_instance_participants` - Tracks which services are participating in each saga
- `saga_lock_table` - Prevents concurrent saga execution on the same aggregate
- `saga_stash_table` - Temporarily stores messages that arrive while saga is locked

**Transactional Outbox:**
- `outbox` - Stores domain events for CDC publishing
- `processed_messages` - Tracks processed messages for idempotency

## Verification

The configuration was verified by:
1. Building the Order Service successfully: `./gradlew :order-service:build -x test`
2. Confirming all dependencies are correctly resolved
3. Ensuring the configuration class compiles without errors

## Next Steps

With the Eventuate Tram Sagas framework configured, the Order Service is now ready for:

1. **Task 7.1**: Implement CreateOrderSaga definition with saga steps
2. **Task 8.1**: Implement CancelOrderSaga definition
3. **Task 9.1**: Implement ReviseOrderSaga definition

Each saga will use the configured infrastructure to:
- Create saga instances via SagaInstanceFactory
- Send commands to participant services via configured command channels
- Receive replies via configured reply channels
- Persist saga state to MySQL via SagaInstanceRepository

## Requirements Validated

This implementation satisfies the requirements for:
- **Requirement 1**: Order Placement and Approval (saga orchestration infrastructure)
- **Requirement 2**: Order Cancellation (saga orchestration infrastructure)
- **Requirement 3**: Order Revision (saga orchestration infrastructure)

## References

- [Eventuate Tram Sagas Documentation](https://eventuate.io/docs/manual/eventuate-tram/latest/getting-started-eventuate-tram-sagas.html)
- [Saga Pattern](https://microservices.io/patterns/data/saga.html)
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)

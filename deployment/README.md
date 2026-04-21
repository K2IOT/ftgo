# FTGO Infrastructure Deployment

This directory contains Docker Compose configurations and scripts for deploying the FTGO microservices infrastructure.

## Infrastructure Components

### Kafka Cluster (KRaft Mode)
- **3 Kafka brokers** (kafka-1, kafka-2, kafka-3) running in KRaft mode
- **No Zookeeper required** - uses Kafka's native Raft consensus
- Pre-configured topics with 3 partitions and replication factor 3
- 7-day retention policy
- Image: `bitnami/kafka:latest`

> **Note**: Migrated from Zookeeper-based Kafka to KRaft mode. See [KAFKA_KRAFT_MIGRATION.md](KAFKA_KRAFT_MIGRATION.md) for details.

### MySQL Databases
- 6 MySQL 8.0 instances (one per service)
- Configured with binlog for CDC (ROW format, FULL image)
- Ports: 3306-3311

### ScyllaDB
- Single-node ScyllaDB cluster for Order History Service
- Port: 9042

### Redis
- Redis for API Gateway session storage and rate limiting
- Port: 6379

### Debezium Connect
- Debezium Connect cluster for CDC
- Monitors outbox tables and publishes to Kafka
- Port: 8083

## Quick Start

### 1. Start Infrastructure

```bash
cd deployment
docker-compose -f docker-compose.infra.yml up -d
```

This will start:
- 3 Kafka brokers (KRaft mode - no Zookeeper)
- 6 MySQL databases
- ScyllaDB
- Redis
- Debezium Connect

### 2. Wait for Services to be Ready

```bash
# Check Kafka is ready
docker logs ftgo-kafka-init

# Check Debezium is ready
curl http://localhost:8083/
```

### 3. Configure Debezium Connectors

```bash
./configure-debezium.sh
```

This creates CDC connectors for all 6 MySQL databases.

### 4. Verify Setup

```bash
# List Kafka topics
docker exec ftgo-kafka-1 kafka-topics.sh --list --bootstrap-server localhost:9092

# Check Debezium connectors
curl http://localhost:8083/connectors

# Verify Kafka cluster health
docker exec ftgo-kafka-1 kafka-broker-api-versions.sh \
  --bootstrap-server kafka-1:9092,kafka-2:9092,kafka-3:9092
```

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

## Partition Key Strategy

All events use partition key: `aggregateType + "#" + aggregateId`

Example: `Order#12345`

This ensures:
- All events for the same aggregate go to the same partition
- Kafka guarantees ordering within a partition
- Consumers process events for each aggregate in order

## Database Connections

| Service | Host | Port | Database | User | Password |
|---------|------|------|----------|------|----------|
| Order | localhost | 3306 | ftgo_order | ftgo_user | ftgo_password |
| Consumer | localhost | 3307 | ftgo_consumer | ftgo_user | ftgo_password |
| Restaurant | localhost | 3308 | ftgo_restaurant | ftgo_user | ftgo_password |
| Kitchen | localhost | 3309 | ftgo_kitchen | ftgo_user | ftgo_password |
| Accounting | localhost | 3310 | ftgo_accounting | ftgo_user | ftgo_password |
| Delivery | localhost | 3311 | ftgo_delivery | ftgo_user | ftgo_password |

## ScyllaDB Connection

- Host: localhost
- Port: 9042
- Keyspace: ftgo_order_history

## Stopping Infrastructure

```bash
docker-compose -f docker-compose.infra.yml down
```

To remove volumes (data will be lost):

```bash
docker-compose -f docker-compose.infra.yml down -v
```

## Troubleshooting

### Kafka not starting
- Check all brokers are running: `docker-compose -f docker-compose.infra.yml ps`
- Verify KRaft cluster formation: `docker logs ftgo-kafka-1`
- Ensure ports 9092, 9094, 9095, 19092-19094 are not in use
- See [KAFKA_KRAFT_MIGRATION.md](KAFKA_KRAFT_MIGRATION.md) for detailed troubleshooting

### Debezium connector fails
- Check MySQL binlog is enabled: `docker exec ftgo-mysql-order mysql -uroot -prootpassword -e "SHOW VARIABLES LIKE 'log_bin'"`
- Verify outbox table exists in database
- Check connector status: `curl http://localhost:8083/connectors/order-connector/status`

### ScyllaDB connection issues
- Wait for ScyllaDB to fully start (can take 30-60 seconds)
- Check logs: `docker logs ftgo-scylladb`

## Monitoring

### Kafka
- Kafka Manager: Not included (can add Kafdrop or AKHQ)
- CLI: `docker exec ftgo-kafka-1 kafka-topics --list --bootstrap-server localhost:9092`

### Debezium
- REST API: http://localhost:8083/
- Connectors: http://localhost:8083/connectors
- Connector status: http://localhost:8083/connectors/{connector-name}/status

### MySQL
- Connect: `docker exec -it ftgo-mysql-order mysql -uftgo_user -pftgo_password ftgo_order`

### ScyllaDB
- Connect: `docker exec -it ftgo-scylladb cqlsh`

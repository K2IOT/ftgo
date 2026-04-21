# Kafka KRaft Mode Migration Guide

## Overview

The FTGO infrastructure has been migrated from Kafka with Zookeeper to **Kafka KRaft mode** (Kafka Raft metadata mode), which eliminates the dependency on Zookeeper.

## What Changed

### Before (Zookeeper-based)
- **Image**: `confluentinc/cp-kafka:7.5.0`
- **Coordination**: Required separate Zookeeper service
- **Ports**: 
  - Zookeeper: 2181
  - Kafka brokers: 9092, 9093, 9094 (internal) + 19092, 19093, 19094 (external)

### After (KRaft mode)
- **Image**: `bitnami/kafka:latest`
- **Coordination**: Self-managed via KRaft consensus protocol
- **Ports**:
  - Kafka brokers: 9092 (internal) + 19092, 19093, 19094 (external)
  - Controller: 9093 (internal only, used for cluster coordination)

## Key Benefits

1. **Simplified Architecture**: No separate Zookeeper cluster to manage
2. **Faster Startup**: Reduced coordination overhead
3. **Better Scalability**: Improved metadata handling
4. **Production Ready**: KRaft is production-ready as of Kafka 3.3+
5. **Future Proof**: Zookeeper support will be removed in Kafka 4.0

## Configuration Details

### KRaft Cluster Configuration

Each Kafka broker runs in **combined mode** (both controller and broker):

```yaml
KAFKA_CFG_PROCESS_ROLES: controller,broker
KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093
KAFKA_KRAFT_CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk
```

### Listener Configuration

- **PLAINTEXT** (port 9092): Inter-broker and internal service communication
- **CONTROLLER** (port 9093): KRaft controller communication (internal only)
- **EXTERNAL** (ports 19092-19094): External access from localhost

### Replication Settings

- **Replication Factor**: 3 (all topics replicated across all brokers)
- **Min In-Sync Replicas**: 2 (ensures durability)
- **Transaction Log Replication**: 3 with min ISR of 2

## Usage

### Starting the Infrastructure

```bash
# Start all infrastructure services
cd deployment
docker-compose -f docker-compose.infra.yml up -d

# Check Kafka cluster status
docker-compose -f docker-compose.infra.yml logs kafka-1 kafka-2 kafka-3

# Verify topics were created
docker exec -it ftgo-kafka-1 kafka-topics.sh \
  --bootstrap-server localhost:9092 --list
```

### Stopping the Infrastructure

```bash
# Stop all services
docker-compose -f docker-compose.infra.yml down

# Stop and remove volumes (clean slate)
docker-compose -f docker-compose.infra.yml down -v
```

### Accessing Kafka

#### From Host Machine (localhost)

```bash
# Connect to broker 1
kafka-console-consumer.sh --bootstrap-server localhost:19092 --topic <topic-name>

# Connect to broker 2
kafka-console-consumer.sh --bootstrap-server localhost:19093 --topic <topic-name>

# Connect to broker 3
kafka-console-consumer.sh --bootstrap-server localhost:19094 --topic <topic-name>
```

#### From Docker Network (ftgo-network)

```bash
# Services should connect to all brokers
bootstrap.servers=kafka-1:9092,kafka-2:9092,kafka-3:9092
```

### Monitoring Cluster Health

```bash
# Check cluster metadata
docker exec -it ftgo-kafka-1 kafka-metadata.sh \
  --snapshot /bitnami/kafka/data/__cluster_metadata-0/00000000000000000000.log \
  --print

# Check broker status
docker exec -it ftgo-kafka-1 kafka-broker-api-versions.sh \
  --bootstrap-server kafka-1:9092,kafka-2:9092,kafka-3:9092

# Describe cluster
docker exec -it ftgo-kafka-1 kafka-cluster.sh \
  --bootstrap-server kafka-1:9092 cluster-id
```

## Application Configuration

### Spring Boot Services

Update `application.yml` in each service:

```yaml
spring:
  kafka:
    bootstrap-servers: kafka-1:9092,kafka-2:9092,kafka-3:9092
    producer:
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        isolation.level: read_committed
```

### Eventuate Tram Configuration

No changes needed - Eventuate Tram works seamlessly with KRaft mode.

## Troubleshooting

### Brokers Not Starting

**Symptom**: Brokers fail to start or crash immediately

**Solution**:
1. Ensure all brokers have the same `KAFKA_KRAFT_CLUSTER_ID`
2. Check that `KAFKA_CFG_CONTROLLER_QUORUM_VOTERS` lists all brokers
3. Verify network connectivity between brokers

```bash
# Check logs
docker-compose -f docker-compose.infra.yml logs kafka-1

# Restart with clean state
docker-compose -f docker-compose.infra.yml down -v
docker-compose -f docker-compose.infra.yml up -d
```

### Topics Not Created

**Symptom**: `kafka-init` container exits but topics don't exist

**Solution**:
```bash
# Check kafka-init logs
docker-compose -f docker-compose.infra.yml logs kafka-init

# Manually create topics
docker exec -it ftgo-kafka-1 kafka-topics.sh \
  --create --bootstrap-server kafka-1:9092,kafka-2:9092,kafka-3:9092 \
  --topic test-topic --partitions 3 --replication-factor 3
```

### Connection Refused from Services

**Symptom**: Services can't connect to Kafka

**Solution**:
1. Ensure services are on the `ftgo-network`
2. Use internal addresses: `kafka-1:9092,kafka-2:9092,kafka-3:9092`
3. Don't use external ports (19092-19094) from Docker network

```bash
# Test connectivity from another container
docker run --rm --network ftgo-network bitnami/kafka:latest \
  kafka-broker-api-versions.sh --bootstrap-server kafka-1:9092
```

### Cluster ID Mismatch

**Symptom**: Brokers fail with "Cluster ID mismatch" error

**Solution**:
```bash
# Stop all brokers
docker-compose -f docker-compose.infra.yml stop kafka-1 kafka-2 kafka-3

# Remove volumes
docker volume rm deployment_kafka-1-data deployment_kafka-2-data deployment_kafka-3-data

# Start fresh
docker-compose -f docker-compose.infra.yml up -d kafka-1 kafka-2 kafka-3
```

## Performance Tuning

### For Development

Current settings are optimized for development:
- 3 brokers for high availability testing
- Moderate resource allocation
- 7-day retention

### For Production

Consider these adjustments:

```yaml
# Increase network threads
KAFKA_CFG_NUM_NETWORK_THREADS: 8

# Increase I/O threads
KAFKA_CFG_NUM_IO_THREADS: 16

# Tune buffer sizes
KAFKA_CFG_SOCKET_SEND_BUFFER_BYTES: 1048576
KAFKA_CFG_SOCKET_RECEIVE_BUFFER_BYTES: 1048576

# Increase max request size
KAFKA_CFG_SOCKET_REQUEST_MAX_BYTES: 104857600

# Add resource limits
deploy:
  resources:
    limits:
      cpus: '2'
      memory: 2G
    reservations:
      cpus: '1'
      memory: 1G
```

## Migration Checklist

- [x] Remove Zookeeper service
- [x] Update Kafka to Bitnami image with KRaft
- [x] Configure KRaft cluster with 3 nodes
- [x] Update listener configuration
- [x] Update kafka-init to use Bitnami tools
- [x] Update Debezium bootstrap servers
- [x] Add Kafka data volumes
- [ ] Update service application.yml files (if needed)
- [ ] Update Kubernetes manifests (if using K8s)
- [ ] Test full saga workflows
- [ ] Update monitoring/alerting

## References

- [Kafka KRaft Documentation](https://kafka.apache.org/documentation/#kraft)
- [Bitnami Kafka Docker Image](https://hub.docker.com/r/bitnami/kafka)
- [KRaft Migration Guide](https://kafka.apache.org/documentation/#kraft_zk_migration)
- [Eventuate Tram Documentation](https://eventuate.io/docs/manual/eventuate-tram/latest/)

## Support

For issues or questions:
1. Check Docker Compose logs: `docker-compose -f docker-compose.infra.yml logs`
2. Verify network connectivity: `docker network inspect ftgo-network`
3. Review Kafka broker logs: `docker exec -it ftgo-kafka-1 cat /opt/bitnami/kafka/logs/server.log`

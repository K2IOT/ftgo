# Getting Started with FTGO

This guide will help you get the FTGO microservices platform up and running on your local machine.

## Prerequisites

Before you begin, ensure you have the following installed:

- **Java 21** - [Download](https://adoptium.net/)
- **Docker** and **Docker Compose** - [Download](https://www.docker.com/products/docker-desktop)
- **Git** - [Download](https://git-scm.com/)

Optional (for Kubernetes deployment):
- **kubectl** - [Install](https://kubernetes.io/docs/tasks/tools/)
- **k3s** or **kind** - [k3s](https://k3s.io/) | [kind](https://kind.sigs.k8s.io/)

## Step-by-Step Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd ftgo
```

### 2. Verify Java Installation

```bash
java -version
# Should show Java 21
```

### 3. Build the Project

```bash
./gradlew build
```

This will:
- Download all dependencies
- Compile all 8 microservices
- Run unit tests
- Create executable JARs

**Expected output**: `BUILD SUCCESSFUL`

### 4. Start Infrastructure

```bash
cd deployment
docker-compose -f docker-compose.infra.yml up -d
```

This starts:
- ✅ Zookeeper
- ✅ 3 Kafka brokers
- ✅ 6 MySQL databases
- ✅ ScyllaDB
- ✅ Redis
- ✅ Debezium Connect
- ✅ HashiCorp Vault
- ✅ Spring Cloud Config Server

**Wait time**: ~60 seconds for all services to be ready

### 5. Verify Infrastructure

```bash
# Check all containers are running
docker ps

# You should see 14 containers running:
# - ftgo-zookeeper
# - ftgo-kafka-1, ftgo-kafka-2, ftgo-kafka-3
# - ftgo-mysql-order, ftgo-mysql-consumer, ftgo-mysql-restaurant
# - ftgo-mysql-kitchen, ftgo-mysql-accounting, ftgo-mysql-delivery
# - ftgo-scylladb
# - ftgo-redis
# - ftgo-debezium
# - ftgo-vault
# - ftgo-config-server
```

### 6. Check Kafka Topics

```bash
docker exec ftgo-kafka-1 kafka-topics --list --bootstrap-server localhost:9092
```

**Expected output**: You should see 14 topics including:
- Domain event topics (net.ftgo.orderservice.domain.Order, etc.)
- Command channels (orderService, consumerService, etc.)
- Saga reply channels (createOrderSagaReply, etc.)

### 7. Configure Debezium CDC

```bash
cd deployment
./configure-debezium.sh
```

This creates CDC connectors for all 6 MySQL databases.

**Verify**:
```bash
curl http://localhost:8083/connectors
```

You should see 6 connectors: order-connector, consumer-connector, etc.

### 8. Initialize Vault

```bash
cd deployment
./init-vault.sh
```

This stores database credentials and creates service tokens.

**Verify**:
```bash
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='ftgo-root-token'
vault kv get secret/order-service
```

### 9. Run a Service Locally

Open a new terminal and run:

```bash
./gradlew :order-service:bootRun
```

**Expected output**:
```
Started OrderServiceApplication in X.XXX seconds
```

The service will be available at: http://localhost:8081

### 10. Check Service Health

```bash
curl http://localhost:8081/actuator/health
```

**Expected output**:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "kafka": {"status": "UP"}
  }
}
```

## Running Multiple Services

To run all services, open separate terminals for each:

```bash
# Terminal 1 - Order Service
./gradlew :order-service:bootRun

# Terminal 2 - Consumer Service
./gradlew :consumer-service:bootRun

# Terminal 3 - Restaurant Service
./gradlew :restaurant-service:bootRun

# Terminal 4 - Kitchen Service
./gradlew :kitchen-service:bootRun

# Terminal 5 - Accounting Service
./gradlew :accounting-service:bootRun

# Terminal 6 - Delivery Service
./gradlew :delivery-service:bootRun

# Terminal 7 - Order History Service
./gradlew :order-history-service:bootRun

# Terminal 8 - API Gateway
./gradlew :api-gateway:bootRun
```

## Accessing Services

Once all services are running:

| Service | URL | Health Check |
|---------|-----|--------------|
| API Gateway | http://localhost:8080 | http://localhost:8080/actuator/health |
| Order Service | http://localhost:8081 | http://localhost:8081/actuator/health |
| Consumer Service | http://localhost:8082 | http://localhost:8082/actuator/health |
| Restaurant Service | http://localhost:8083 | http://localhost:8083/actuator/health |
| Kitchen Service | http://localhost:8084 | http://localhost:8084/actuator/health |
| Accounting Service | http://localhost:8085 | http://localhost:8085/actuator/health |
| Delivery Service | http://localhost:8086 | http://localhost:8086/actuator/health |
| Order History Service | http://localhost:8087 | http://localhost:8087/actuator/health |

## Monitoring Infrastructure

### Kafka

```bash
# List topics
docker exec ftgo-kafka-1 kafka-topics --list --bootstrap-server localhost:9092

# Describe a topic
docker exec ftgo-kafka-1 kafka-topics --describe --topic net.ftgo.orderservice.domain.Order --bootstrap-server localhost:9092

# Consume messages from a topic
docker exec ftgo-kafka-1 kafka-console-consumer --topic net.ftgo.orderservice.domain.Order --from-beginning --bootstrap-server localhost:9092
```

### Debezium

```bash
# List connectors
curl http://localhost:8083/connectors

# Check connector status
curl http://localhost:8083/connectors/order-connector/status

# View connector config
curl http://localhost:8083/connectors/order-connector
```

### MySQL

```bash
# Connect to Order Service database
docker exec -it ftgo-mysql-order mysql -uftgo_user -pftgo_password ftgo_order

# Show tables
SHOW TABLES;

# Check outbox table
SELECT * FROM outbox;
```

### Vault

```bash
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='ftgo-root-token'

# List secrets
vault kv list secret/

# Get a secret
vault kv get secret/order-service
```

## Stopping Services

### Stop Running Services
Press `Ctrl+C` in each terminal running a service.

### Stop Infrastructure

```bash
cd deployment
docker-compose -f docker-compose.infra.yml down
```

To also remove volumes (data will be lost):

```bash
docker-compose -f docker-compose.infra.yml down -v
```

## Troubleshooting

### Issue: Kafka not starting

**Solution**:
```bash
# Check Zookeeper logs
docker logs ftgo-zookeeper

# Restart Kafka
docker-compose -f docker-compose.infra.yml restart kafka-1 kafka-2 kafka-3
```

### Issue: Service can't connect to database

**Solution**:
```bash
# Check MySQL is running
docker ps | grep mysql

# Check database logs
docker logs ftgo-mysql-order

# Verify connection string in application.yml
```

### Issue: Debezium connector fails

**Solution**:
```bash
# Check connector status
curl http://localhost:8083/connectors/order-connector/status

# Check MySQL binlog is enabled
docker exec ftgo-mysql-order mysql -uroot -prootpassword -e "SHOW VARIABLES LIKE 'log_bin'"

# Restart connector
curl -X POST http://localhost:8083/connectors/order-connector/restart
```

### Issue: Port already in use

**Solution**:
```bash
# Find process using port (e.g., 8081)
lsof -i :8081

# Kill the process
kill -9 <PID>
```

### Issue: Out of memory

**Solution**:
```bash
# Increase Docker memory limit in Docker Desktop settings
# Recommended: 8GB RAM minimum

# Or reduce number of running services
```

## Next Steps

Now that your environment is set up:

1. **Explore the codebase**: Check out the service structure in each module
2. **Read the documentation**: See [README.md](README.md) for architecture details
3. **Check implementation status**: See [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)
4. **Start implementing**: Begin with Phase 2 tasks (Consumer, Restaurant, Accounting services)
5. **Run tests**: `./gradlew test`

## Useful Commands

```bash
# Build without tests
./gradlew build -x test

# Clean build
./gradlew clean build

# Run tests only
./gradlew test

# Run specific service tests
./gradlew :order-service:test

# Check dependencies
./gradlew dependencies

# View project structure
./gradlew projects
```

## Development Workflow

1. **Make changes** to service code
2. **Run tests**: `./gradlew :service-name:test`
3. **Build**: `./gradlew :service-name:build`
4. **Run locally**: `./gradlew :service-name:bootRun`
5. **Test integration** with other services
6. **Commit changes** to Git

## Getting Help

- **Documentation**: Check [README.md](README.md) and [deployment/README.md](deployment/README.md)
- **Architecture**: See [ftgo_architecture_plan.md](ftgo_architecture_plan.md)
- **Spec files**: Check `.kiro/specs/ftgo-microservices-platform/`
- **Logs**: Use `docker logs <container-name>` for infrastructure
- **Service logs**: Check console output when running services

## Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Eventuate Tram Sagas](https://eventuate.io/docs/manual/eventuate-tram/latest/getting-started-eventuate-tram-sagas.html)
- [Apache Kafka](https://kafka.apache.org/documentation/)
- [Debezium](https://debezium.io/documentation/)
- [Istio](https://istio.io/latest/docs/)
- [Microservices Patterns](https://microservices.io/patterns/index.html)

---

**Congratulations!** 🎉 You now have a fully functional FTGO development environment.

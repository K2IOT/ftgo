# Technology Stack

## Core Technologies

- **Language**: Java 21
- **Framework**: Spring Boot 3.2
- **Build System**: Gradle (multi-project build)
- **Messaging**: Apache Kafka 3.x + Eventuate Tram
- **Databases**: MySQL 8 (per service), ScyllaDB (CQRS), Redis (caching/sessions)
- **Container Runtime**: Docker
- **Orchestration**: Kubernetes
- **Service Mesh**: Istio

## Key Libraries & Frameworks

- **Saga Orchestration**: Eventuate Tram Sagas
- **CDC (Change Data Capture)**: Debezium
- **Distributed Tracing**: OpenTelemetry Java Agent → Jaeger
- **Metrics**: Micrometer → Prometheus
- **Resilience**: Resilience4j (circuit breaker, retry)
- **API Gateway**: Spring Cloud Gateway
- **Security**: Spring Security OAuth2 + JWT
- **Configuration**: Spring Cloud Config + Vault
- **Logging**: Structured JSON to stdout → ELK Stack

## Infrastructure Components

- **Message Broker**: Apache Kafka (event streaming, command channels)
- **Transactional DB**: MySQL 8 with DB-per-service pattern
- **CDC Relay**: Debezium (binlog → Kafka)
- **CQRS Store**: ScyllaDB (Order History read model)
- **Cache**: Redis (sessions, rate limiting)
- **Search**: Elasticsearch (restaurant/product search)
- **Tracing**: Jaeger
- **Metrics**: Prometheus + Grafana
- **Logging**: ELK Stack (Elasticsearch, Logstash, Kibana)

## Common Commands

### Build
```bash
# Build all services
./gradlew build

# Build specific service
./gradlew :order-service:build

# Build without tests
./gradlew build -x test
```

### Test
```bash
# Run all tests
./gradlew test

# Run tests for specific service
./gradlew :order-service:test

# Run integration tests
./gradlew integrationTest
```

### Docker
```bash
# Build Docker image for a service
./gradlew :order-service:dockerBuild

# Start local infrastructure (Kafka, MySQL, Redis, Debezium)
docker-compose -f deployment/docker-compose.infra.yml up -d

# Start full stack locally
docker-compose -f deployment/docker-compose.yml up -d

# Stop and clean up
docker-compose down -v
```

### Kubernetes
```bash
# Deploy service to Kubernetes
kubectl apply -f deployment/kubernetes/order-service/

# View service logs
kubectl logs -f deployment/ftgo-order-service -n ftgo-production

# Port forward for local testing
kubectl port-forward svc/ftgo-api-gateway 8080:80 -n ftgo-production

# Scale service
kubectl scale deployment ftgo-order-service --replicas=5 -n ftgo-production
```

### Development
```bash
# Run service locally (requires infrastructure running)
./gradlew :order-service:bootRun

# Run with specific profile
./gradlew :order-service:bootRun --args='--spring.profiles.active=dev'

# Clean build artifacts
./gradlew clean
```

## Observability Endpoints

Each service exposes Spring Boot Actuator endpoints:

- **Health Check**: `GET /actuator/health`
- **Metrics**: `GET /actuator/prometheus`
- **Info**: `GET /actuator/info`

Kubernetes probes:
- **Readiness**: `/actuator/health` (delay=30s, period=10s)
- **Liveness**: `/actuator/health` (delay=60s, period=20s)

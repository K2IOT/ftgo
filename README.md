# FTGO Microservices Platform

FTGO is a Java 21 microservices reference platform for online food ordering and delivery. It demonstrates saga orchestration, transactional outbox messaging, Kafka-based integration, per-service persistence, CQRS order history, payment settlement, and restart-safe distributed workflows.

## Mainline and Current Status

`dev` is the integration mainline for active FTGO development. Changes are expected to land through pull requests and must be verified again on the resulting `dev` merge SHA.

Phase 04, **Remediation and Platform Hardening**, is in progress. Tasks 1–8 are merged; Task 9 hardens mainline CI, production configuration, secret scanning, and repository hygiene. The detailed implementation source of truth is:

- [Phase 04 Remediation and Platform Hardening Plan](docs/superpowers/plans/2026-07-29-phase-04-remediation-and-platform-hardening.md)

Historical Phase 01–03 design and rollout records remain under `docs/superpowers/` and `docs/operations/`; old agent branches are not mainline branches.

## Services

| Service | Port | Persistence | Responsibility |
|---------|------|-------------|----------------|
| API Gateway | 8080 | Redis | Authentication, routing, rate limiting, API composition |
| Order Service | 8081 | MySQL | Order lifecycle and saga orchestration |
| Consumer Service | 8082 | MySQL | Consumer accounts and durable credit reservations |
| Restaurant Service | 8083 | MySQL | Restaurant state and authoritative menu validation |
| Kitchen Service | 8084 | MySQL | Ticket lifecycle and restaurant decisions |
| Accounting Service | 8085 | MySQL | Payment authorization, capture, refund and reconciliation |
| Delivery Service | 8086 | MySQL | Delivery assignment and tracking |
| Order History Service | 8087 | Cassandra/ScyllaDB | CQRS order-history projection |

## Technology Stack

- Java 21
- Spring Boot / Spring Cloud
- Gradle multi-project build
- Eventuate Tram and Eventuate Sagas
- Apache Kafka
- MySQL 8
- Cassandra/ScyllaDB
- Debezium / Eventuate CDC
- Flyway
- JUnit 5, jqwik and Testcontainers
- Docker Compose and GitHub Actions

## Configuration Policy

Production datasource configuration is fail-closed. Transactional services require service-specific environment variables and do not embed database credentials in base `application.yml` files.

Examples:

```text
ORDER_DB_URL
ORDER_DB_USERNAME
ORDER_DB_PASSWORD

CONSUMER_DB_URL
CONSUMER_DB_USERNAME
CONSUMER_DB_PASSWORD
```

The same pattern applies to Restaurant, Kitchen, Accounting and Delivery. Developer-only localhost credentials are stored in `application-local.yml` and are activated explicitly with the `local` profile.

Example:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :order-service:bootRun
```

Do not use the local profile in production.

## Verification

Run the checked-in wrapper and repository contracts:

```bash
bash scripts/ci/verify-gradle-wrapper.sh
./gradlew --no-daemon clean test --stacktrace
python3 -m unittest discover -s deployment/tests -p 'test_*.py' -v
```

Important real-stack gates are also available:

```bash
bash scripts/smoke/verify-fresh-stack.sh --runs 2
bash scripts/smoke/verify-core-order-flow.sh --runs 2
bash scripts/smoke/verify-payment-settlement.sh --runs 2
bash scripts/smoke/verify-distributed-consistency.sh --runs 2
```

On every push to `dev`, the **Dev Merge Verification** workflow checks out the exact `${{ github.sha }}` and re-runs the full Gradle suite plus repository contract matrix. Secret scanning is enforced through the pinned gitleaks workflow.

## Quick Start

### Prerequisites

- Java 21
- Docker with Docker Compose
- Bash, Python 3, curl and jq

### Build

```bash
./gradlew build
```

### Start shared infrastructure

```bash
cd deployment
docker compose -f docker-compose.infra.yml up -d
```

### Run a service locally

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :order-service:bootRun
```

## Project Structure

```text
ftgo/
├── api-gateway/
├── order-service/
├── consumer-service/
├── restaurant-service/
├── kitchen-service/
├── accounting-service/
├── delivery-service/
├── order-history-service/
├── common/
├── e2e-tests/
├── deployment/
├── docs/
├── scripts/
├── build.gradle
└── settings.gradle
```

## Operations Documentation

- [Phase 04 Security and API Runbook](docs/operations/phase-04-security-api-runbook.md)
- [Phase 03 Operations Runbook](docs/operations/phase-03-operations-runbook.md)
- [Phase 02 Core Order Flow Rollout and Rollback](docs/operations/phase-02-core-order-flow-rollout.md)
- [Infrastructure Setup](deployment/README.md)
- [Kubernetes Deployment](deployment/kubernetes/README.md)

## Repository Rules

- Base remediation work on `dev`, not `master`.
- Use pull requests for mainline changes.
- Do not force-push protected mainline history.
- Resolve review conversations before merge.
- Do not commit production credentials or bearer/payment secrets.
- A remediation PR is not complete until required checks pass on its final head SHA and again on the resulting `dev` merge SHA.

## License

This project is for educational purposes and is based on patterns described in *Microservices Patterns* by Chris Richardson.

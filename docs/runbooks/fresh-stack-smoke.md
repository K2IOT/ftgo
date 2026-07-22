# Fresh-stack smoke test

The Phase 01 smoke test proves that a clean dependency stack can boot every FTGO runtime and that the MySQL outbox is routed through Debezium to Kafka with the expected topic, key, headers and payload.

## Prerequisites

- Docker Engine with Docker Compose v2
- Java 21
- `curl` and `jq`

The repository contains a checksum-pinned wrapper bootstrap. On first use, `gradlew` downloads the official Gradle 8.5 wrapper JAR into the user Gradle cache and verifies its published SHA-256 before execution.

## Run

```bash
chmod +x gradlew scripts/smoke/*.sh deployment/tests/run-debezium-smoke.sh
./scripts/smoke/fresh-stack.sh --runs 2
```

Each run performs the following steps from clean volumes:

1. Starts pinned MySQL 8.0.36, Kafka 7.5.0, Redis 7.2.4 and Scylla 5.2 containers with health checks.
2. Creates the six relational databases and the Order History keyspace.
3. Starts each Spring Boot application sequentially and requires `/actuator/health` to return `UP`.
4. Tears down the dependency stack and removes volumes.
5. Runs the Debezium outbox smoke test and validates the Order event contract.

Logs are written to `build/fresh-stack-smoke/`. Container and application logs are retained when a check fails. The script always tears down containers and volumes on exit.

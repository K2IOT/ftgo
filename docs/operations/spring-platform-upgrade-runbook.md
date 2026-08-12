# Spring Platform Upgrade Runbook

## Purpose

This runbook records the operational and compatibility controls for Phase 04 Task 10, the migration of FTGO to the supported Spring platform. The upgrade must not change existing REST, event, command, saga, outbox/CDC, or database contracts.

## Platform baseline

The migration used an intermediate compatibility bridge and a final supported target:

| Layer | Bridge checkpoint | Final PR state |
| --- | --- | --- |
| Java | 21 | 21 |
| Spring Boot | 3.5.15 | 4.0.7 |
| Spring Cloud | 2025.0.3 | 2025.1.2 |
| io.spring.dependency-management | 1.1.7 | 1.1.7 |
| Gradle | 8.14.3 | 8.14.3 |
| Eventuate platform BOM | 2024.0.RELEASE | 2024.0.RELEASE |

The final build resolves Eventuate through the single `io.eventuate.platform:eventuate-platform-dependencies:2024.0.RELEASE` BOM. Separate Eventuate core and saga BOM declarations are not permitted.

## Compatibility decisions

### Spring Boot 4 modular starters

Boot 4 separates several capabilities that older builds received transitively. FTGO therefore uses explicit Boot 4 modules/starters at the boundaries that need them, including WebMVC/WebFlux test support, blocking REST-client test support, Flyway database support, and Kafka auto-configuration for services that require Boot-managed Kafka infrastructure.

### Spring Security 7

Servlet and reactive authorization code is migrated to the Spring Security 7 APIs. Production authorization semantics and trusted-role/audience rules remain unchanged; tests continue to cover both direct-service and Gateway enforcement.

### Spring Cloud Gateway 5

The Gateway uses `spring-cloud-starter-gateway-server-webflux` and the `spring.cloud.gateway.server.webflux` configuration namespace. Rate limiting, correlation, security headers, circuit breaking, and RFC 9457 error contracts remain regression-gated.

### Jackson compatibility boundary

Spring Boot 4 defaults to the newer Jackson generation, while FTGO/Eventuate compatibility code and existing event contracts still use Jackson 2 APIs. FTGO therefore keeps the BOM-managed Jackson 2 compatibility modules where event serialization requires them, including `jackson-datatype-jsr310` for Java time values. This is a compatibility boundary, not a wire-format change; migration away from Jackson 2 must be a separate contract-preserving change.

### Testcontainers 2

Tests use the Testcontainers 2 module coordinates managed by the Spring Boot BOM, including `testcontainers-junit-jupiter`, `testcontainers-kafka`, `testcontainers-mysql`, and `testcontainers-cassandra`. Explicit duplicate versions are prohibited by `deployment/tests/test_supported_dependency_baseline.py`.

### Persistence and Flyway

MySQL-backed services use the Boot 4 Flyway starter plus the MySQL Flyway database module. Test fixes caused by Hibernate merge semantics are kept at the persistence test boundary; domain contracts are unchanged.

## Dependency insight evidence

Run:

```bash
bash scripts/ci/verify-supported-dependencies.sh | tee dependency-insight.log
```

The script generates Gradle `dependencyInsight` output for these required dependency families and representative runtime/test configurations:

- Spring Security — API Gateway `runtimeClasspath`
- Netty — API Gateway `runtimeClasspath`
- Jackson — Order Service `runtimeClasspath`
- Kafka — Delivery Service `runtimeClasspath`
- MySQL — Order Service `runtimeClasspath`
- Testcontainers — Accounting Service `testRuntimeClasspath`

The `Remediation 10 Platform Security` workflow uploads `dependency-insight.log` as evidence. The workflow artifact for the exact final head SHA is the source of truth for resolved transitive versions.

## Vulnerability gate

The security workflow first builds executable Spring Boot jars:

```bash
./gradlew bootJar --no-daemon --stacktrace
```

It stages the eight runtime service jars into `build/trivy-rootfs` and runs the pinned Trivy **rootfs** vulnerability scanner against those artifacts. Repository/filesystem mode is intentionally not used for this gate because it can ignore binary JAR artifacts and produce a zero-target result.

Trivy writes `trivy-results.json` with `list-all-pkgs` enabled and fails on `HIGH` or `CRITICAL` vulnerabilities. Independently, `scripts/ci/verify-trivy-java-report.py` rejects the run unless the JSON report contains at least one `Type=jar` result with detected packages. A scanner run with zero Java targets therefore fails even when Trivy itself exits zero.

The scanner gate must not be changed to `continue-on-error`, a zero vulnerability exit code, a lower severity policy, or a configuration that permits an empty Java scan merely to unblock the PR.

### Exception policy

There are no pre-approved HIGH/CRITICAL exceptions for this upgrade. If the scanner reports one, the preferred action is to upgrade, constrain, or remove the vulnerable dependency and rerun the full regression suite.

An exception is allowed only when all of the following are recorded in this section before merge:

1. vulnerability identifier and affected resolved version;
2. complete Gradle dependency path from the application module to the vulnerable component;
3. concrete non-reachability or compensating-control evidence for FTGO's deployed usage;
4. named owner and a removal/upgrade deadline;
5. a tracking issue or change reference.

Until that evidence exists, a HIGH/CRITICAL finding blocks the PR.

## Verification before merge

On one final head SHA, run:

```bash
bash scripts/ci/verify-gradle-wrapper.sh
./gradlew clean test --no-daemon --stacktrace
python -m unittest discover -s deployment/tests -p 'test_*.py' -v
bash scripts/ci/verify-supported-dependencies.sh
bash scripts/smoke/verify-fresh-stack.sh --runs 2
bash scripts/smoke/verify-core-order-flow.sh --runs 2
bash scripts/smoke/verify-payment-settlement.sh --runs 2
bash scripts/smoke/verify-distributed-consistency.sh --runs 2
```

In addition, every required GitHub workflow, including `Remediation 10 Platform Security`, must be green on that same head SHA. Eventuate participant/orchestrator tests, saga tests, processed-command/lost-reply tests, outbox/CDC tests, API contract tests, and real-stack E2E workflows must not be excluded.

## Rollback

If the final platform produces a deployment regression that escaped the gates:

1. stop rollout and preserve the failing application/container logs plus the exact image and Git SHA;
2. roll services back to the last known-good pre-Task-10 deployment as a unit when event/saga compatibility is uncertain;
3. do not roll database migrations backward destructively; the Task 10 migration is required to retain existing database contracts;
4. confirm Kafka consumers, Eventuate outbox/CDC processing, and saga command/reply flows are healthy on the rollback version;
5. reproduce the failure against the Task 10 head and add a regression test before attempting the upgrade again.

The bridge versions (Boot 3.5.15 / Cloud 2025.0.3) are an engineering checkpoint, not a supported production rollback target for this task.

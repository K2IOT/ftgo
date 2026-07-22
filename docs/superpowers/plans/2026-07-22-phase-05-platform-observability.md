# FTGO Phase 05 Production Platform and Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tạo production deployment baseline có immutable images, Kubernetes workload controls, secret/network protection, distributed tracing, SLO alerting và backup/restore được kiểm chứng.

**Architecture:** Dùng multi-stage container images và Kustomize base/overlays. External Secrets/Vault cấp secret; Istio mTLS và NetworkPolicy giới hạn traffic. OpenTelemetry liên kết HTTP, Eventuate, Kafka và outbox; Prometheus/Grafana theo dõi SLO.

**Tech Stack:** Docker/BuildKit, Kubernetes, Kustomize, Istio, External Secrets, Vault, OpenTelemetry, Micrometer, Prometheus, Grafana, Loki/structured logs, Kafka, MySQL, Redis, ScyllaDB.

## Global Constraints

- Không dùng image tag `latest`.
- Container chạy non-root, read-only root filesystem, drop Linux capabilities.
- Không commit secret thật; production secret chỉ tham chiếu external secret store.
- Liveness không phụ thuộc downstream; readiness phản ánh khả năng nhận traffic an toàn.
- Metric label không chứa user ID, order ID, email, address hoặc unbounded values.
- Backup được coi là chưa tồn tại nếu chưa restore thử.

---

### Task 1: Build Hardened Immutable Service Images

**Files:**
- Create: `docker/service.Dockerfile`
- Create: `.dockerignore`
- Create: `scripts/build/build-images.sh`
- Create: `scripts/build/verify-image.sh`
- Modify: `docker-compose.yml`
- Create: `docs/runbooks/image-build.md`

**Interfaces:**
- Build args: `SERVICE`, `VERSION`, `GIT_SHA`.
- Runtime image exposes only service port and `/actuator/health/*`.

- [ ] **Step 1: Write image verification script first**

`verify-image.sh` must fail unless:

```bash
docker inspect "$IMAGE" --format '{{.Config.User}}' | grep -Ev '^(|0|root)$'
docker inspect "$IMAGE" --format '{{json .Config.Healthcheck}}' | grep -q 'health'
docker run --rm --read-only --tmpfs /tmp "$IMAGE" java -version
```

Also scan for shell/package-manager absence when distroless runtime is used.

- [ ] **Step 2: Implement multi-stage Dockerfile**

Build one service bootJar in Gradle stage, then copy only JRE/application artifacts into pinned runtime base. Add OCI labels for source, revision and version.

- [ ] **Step 3: Set runtime hardening**

- numeric non-root user
- read-only filesystem compatibility
- writable `/tmp` only
- JVM container memory settings
- graceful shutdown timeout
- no embedded credentials

- [ ] **Step 4: Pin Compose images**

Pin exact versions for Kafka, MySQL, Redis, Scylla, Debezium and Vault. Move credentials to `.env.example` variables; ensure `.env` is ignored.

- [ ] **Step 5: Build and verify all images**

```bash
./scripts/build/build-images.sh 1.0.0-local "$(git rev-parse HEAD)"
for service in api-gateway order-service consumer-service restaurant-service kitchen-service accounting-service delivery-service order-history-service; do
  ./scripts/build/verify-image.sh "ftgo/${service}:1.0.0-local"
done
```

Expected: all verification commands exit `0`.

- [ ] **Step 6: Commit**

```bash
git add docker .dockerignore scripts/build docker-compose.yml docs/runbooks/image-build.md
git commit -m "build: harden service container images"
```

---

### Task 2: Create Kubernetes Base and Environment Overlays

**Files:**
- Create: `deployment/kubernetes/base/kustomization.yaml`
- Create per service: `deployment/kubernetes/base/<service>/deployment.yaml`
- Create per service: `deployment/kubernetes/base/<service>/service.yaml`
- Create per service: `deployment/kubernetes/base/<service>/serviceaccount.yaml`
- Create: `deployment/kubernetes/overlays/dev/kustomization.yaml`
- Create: `deployment/kubernetes/overlays/staging/kustomization.yaml`
- Create: `deployment/kubernetes/overlays/production/kustomization.yaml`
- Create: `scripts/k8s/validate-manifests.sh`

**Interfaces:**
- Base contains workload invariants.
- Overlays contain replicas, image digest, hostnames and environment-specific resources only.

- [ ] **Step 1: Write manifest validation gates**

Use `kustomize build`, `kubeconform` and policy tests. Fail if any Deployment lacks:

- startup/readiness/liveness probes
- requests/limits
- securityContext
- serviceAccountName
- topology spread
- rolling-update strategy

- [ ] **Step 2: Define service Deployment template contract**

Each deployment uses:

```yaml
securityContext:
  runAsNonRoot: true
  seccompProfile:
    type: RuntimeDefault
containers:
  - securityContext:
      allowPrivilegeEscalation: false
      readOnlyRootFilesystem: true
      capabilities:
        drop: ["ALL"]
    startupProbe:
      httpGet: { path: /actuator/health/liveness, port: http }
      failureThreshold: 30
      periodSeconds: 10
    readinessProbe:
      httpGet: { path: /actuator/health/readiness, port: http }
    livenessProbe:
      httpGet: { path: /actuator/health/liveness, port: http }
```

- [ ] **Step 3: Add PDB and topology spread**

Create PDB for replicated stateless services and spread pods across hostname/zone. Gateway and Order Service production replicas minimum 3; other stateless services minimum 2 unless load data justifies more.

- [ ] **Step 4: Add HPA**

Gateway scales by CPU plus HTTP concurrency metric; consumers scale by Kafka lag through a supported metrics adapter/KEDA. Set bounded min/max and stabilization windows.

- [ ] **Step 5: Validate overlays**

```bash
./scripts/k8s/validate-manifests.sh dev
./scripts/k8s/validate-manifests.sh staging
./scripts/k8s/validate-manifests.sh production
```

Expected: no schema/policy violations.

- [ ] **Step 6: Commit**

```bash
git add deployment/kubernetes scripts/k8s
git commit -m "ops: define kubernetes workload baseline"
```

---

### Task 3: Implement Secrets, mTLS and Network Isolation

**Files:**
- Create: `deployment/kubernetes/base/secrets/cluster-secret-store.yaml`
- Create per service: `deployment/kubernetes/base/<service>/external-secret.yaml`
- Modify: `deployment/kubernetes/istio/peer-authentication.yaml`
- Create: `deployment/kubernetes/istio/authorization-policy.yaml`
- Create: `deployment/kubernetes/base/network-policies/default-deny.yaml`
- Create: `deployment/kubernetes/base/network-policies/service-allow-list.yaml`
- Create: `scripts/k8s/security-smoke.sh`
- Modify: application configuration to read secrets only from environment/config tree.

**Interfaces:**
- Secrets include DB credentials, Kafka credentials, JWT issuer/JWK settings, payment provider credentials and Vault references.
- Service-to-service traffic uses SPIFFE/Istio identity.

- [ ] **Step 1: Add policy tests**

Fail manifest validation when a Secret contains inline production values or workload references literal passwords.

- [ ] **Step 2: Define default-deny NetworkPolicy**

Allow only:

- ingress -> API Gateway
- Gateway -> public HTTP services
- required service -> DB/cache
- services -> Kafka
- Debezium -> MySQL/Kafka
- monitoring -> metrics endpoints
- DNS and identity control-plane traffic

No public ingress to service/database ports.

- [ ] **Step 3: Enforce Istio STRICT mTLS**

Apply namespace-level STRICT and service AuthorizationPolicy. Internal `/internal/**` routes accept only approved service principals.

- [ ] **Step 4: Configure Kafka security in production overlay**

Use TLS/SASL and per-service ACLs. Each service receives only consume/produce permissions for its required topics and groups.

- [ ] **Step 5: Run security smoke**

Deploy a temporary probe pod and assert forbidden connections fail while allowed paths succeed.

```bash
./scripts/k8s/security-smoke.sh ftgo-production
```

Expected: direct probe-to-MySQL and probe-to-service traffic denied; Gateway and authorized service paths succeed.

- [ ] **Step 6: Commit**

```bash
git add deployment/kubernetes scripts/k8s */src/main/resources
git commit -m "security: isolate service traffic and secrets"
```

---

### Task 4: Propagate Traces Through HTTP, Saga, Kafka and Outbox

**Files:**
- Modify: `build.gradle`
- Create: `common/src/main/java/net/ftgo/common/observability/CorrelationContext.java`
- Create: `common/src/main/java/net/ftgo/common/observability/MessagingTracePropagator.java`
- Modify: Gateway correlation filter.
- Modify: every domain event publisher and Kafka consumer.
- Modify: Eventuate command builders/handlers where extension hooks permit headers.
- Create: `e2e-tests/src/test/java/net/ftgo/e2e/TracePropagationTest.java`
- Create: `deployment/observability/otel-collector-config.yaml`

**Interfaces:**
- W3C Trace Context headers: `traceparent`, `tracestate`.
- Business metadata: `correlationId`, `causationId`.

- [ ] **Step 1: Write propagation E2E test**

Send Create Order with a known correlation ID; collect test spans and assert one trace includes Gateway, Order HTTP, Restaurant HTTP, saga participant spans, outbox publish and Order History consume spans.

- [ ] **Step 2: Use supported OpenTelemetry instrumentation**

Prefer OpenTelemetry Java agent for framework instrumentation and add manual spans only around saga/outbox/reconciliation boundaries not automatically covered.

- [ ] **Step 3: Propagate messaging metadata**

Outbox envelope and Kafka headers carry trace context. Consumer creates a new span linked/parented to extracted context and sets event ID/topic attributes without high-cardinality business IDs as metrics.

- [ ] **Step 4: Mask sensitive log fields**

Structured logging excludes Authorization, payment method values, email and full address. Add log unit tests for redaction.

- [ ] **Step 5: Run trace test**

```bash
./gradlew :e2e-tests:test --tests '*TracePropagationTest'
```

Expected: one connected trace, no payment token or raw address in captured logs.

- [ ] **Step 6: Commit**

```bash
git add build.gradle common api-gateway */src/main/java e2e-tests deployment/observability
git commit -m "obs: trace http saga and event flows"
```

---

### Task 5: Define SLO Metrics, Dashboards and Alerts

**Files:**
- Create: `docs/slo/ftgo-service-level-objectives.md`
- Create: `deployment/observability/prometheus-rules.yaml`
- Create: `deployment/observability/grafana/ftgo-overview.json`
- Create: `deployment/observability/grafana/order-flow.json`
- Create: `deployment/observability/grafana/messaging.json`
- Create: `deployment/observability/grafana/payment.json`
- Create: `scripts/observability/validate-rules.sh`
- Modify metrics instrumentation across services.

**Interfaces:**
- Initial SLOs are explicit, measurable and revisable after baseline data.

- [ ] **Step 1: Document initial SLOs**

Use baseline targets:

- Gateway availability: 99.9% monthly.
- Create Order accepted API p95: under 500 ms excluding saga completion.
- Create Order saga completion p95: under 10 s, p99 under 30 s.
- Domain event projection lag p95: under 5 s.
- Payment reconciliation mismatch: 0 unexplained mismatches.
- DLT rate: under 0.01% of events, with immediate alert for payment/order topics.

- [ ] **Step 2: Add low-cardinality metrics**

Record operation type/status, saga type/status, topic, consumer group, provider and exception category. Do not label order/user/event IDs.

- [ ] **Step 3: Add multi-window burn-rate alerts**

Create fast and slow SLO burn alerts, plus alerts for:

- stuck saga
- outbox oldest age
- Debezium connector not RUNNING
- Kafka consumer lag
- DLT increase
- DB pool saturation
- payment reconciliation mismatch
- backup failure

- [ ] **Step 4: Validate rules and dashboards**

```bash
./scripts/observability/validate-rules.sh
promtool check rules deployment/observability/prometheus-rules.yaml
```

Expected: valid rules; dashboard JSON parses and references existing metrics.

- [ ] **Step 5: Commit**

```bash
git add docs/slo deployment/observability scripts/observability */src/main/java
git commit -m "obs: define ftgo slos dashboards and alerts"
```

---

### Task 6: Implement Database and Messaging Backup/Restore Runbooks

**Files:**
- Create: `docs/runbooks/backup-restore-mysql.md`
- Create: `docs/runbooks/backup-restore-scylla.md`
- Create: `docs/runbooks/backup-restore-kafka-connect.md`
- Create: `docs/runbooks/disaster-recovery.md`
- Create: `scripts/backup/mysql-backup.sh`
- Create: `scripts/backup/mysql-restore-test.sh`
- Create: `scripts/backup/scylla-backup.sh`
- Create: `scripts/backup/scylla-restore-test.sh`
- Create: `scripts/backup/debezium-config-backup.sh`
- Create: `deployment/kubernetes/base/backup/cronjobs.yaml`

**Interfaces:**
- Backup artifacts are encrypted, checksummed, versioned and retained by environment policy.
- Restore tests use isolated databases/namespace.

- [ ] **Step 1: Define RPO/RTO baseline**

Document:

- transactional MySQL RPO <= 5 minutes, RTO <= 60 minutes
- Scylla read model RPO can be rebuilt from retained events; snapshot RPO <= 24 hours
- Kafka/Connect config and topic definitions stored as code

- [ ] **Step 2: Implement backup scripts with verification**

Scripts must use strict mode, record metadata/checksum, encrypt before upload and fail on partial output.

- [ ] **Step 3: Implement restore tests**

Restore into isolated targets, run Flyway validation and business consistency queries. Scylla restore verifies sample order-history reads.

- [ ] **Step 4: Run restore drill locally/staging**

```bash
./scripts/backup/mysql-backup.sh test
./scripts/backup/mysql-restore-test.sh test
./scripts/backup/scylla-backup.sh test
./scripts/backup/scylla-restore-test.sh test
```

Expected: all exit `0`; checksums and invariant queries pass.

- [ ] **Step 5: Commit**

```bash
git add docs/runbooks scripts/backup deployment/kubernetes/base/backup
git commit -m "ops: automate backup and restore verification"
```

---

### Task 7: Add Deployment, Rollout and Rollback Verification

**Files:**
- Create: `scripts/k8s/deploy.sh`
- Create: `scripts/k8s/smoke-test.sh`
- Create: `scripts/k8s/rollback-test.sh`
- Create: `docs/runbooks/deployment.md`
- Create: `docs/runbooks/rollback.md`
- Create: `deployment/kubernetes/overlays/staging/rollout-policy.yaml`

**Interfaces:**
- Deployment sequence: migration job -> workload rollout -> readiness -> smoke -> promotion.
- Rollback never rolls database migration backward destructively.

- [ ] **Step 1: Add migration job gate**

Deployment aborts if Flyway migration job fails. Application release N must be compatible with schema during rollout of N-1/N pods.

- [ ] **Step 2: Implement smoke suite**

Validate authentication, restaurant snapshot, create-order operation, saga completion, delivery projection and order-history read.

- [ ] **Step 3: Implement controlled rollout**

Use rolling update or canary with explicit maxUnavailable/maxSurge. Monitor error rate, latency, saga failure and DLT metrics before full promotion.

- [ ] **Step 4: Test rollback**

Deploy previous application image against forward-compatible schema, run smoke tests and confirm no event/schema incompatibility.

```bash
./scripts/k8s/deploy.sh staging "$IMAGE_SET"
./scripts/k8s/smoke-test.sh staging
./scripts/k8s/rollback-test.sh staging "$PREVIOUS_IMAGE_SET"
```

Expected: deploy and rollback smoke both pass.

- [ ] **Step 5: Run full platform validation**

```bash
./scripts/k8s/validate-manifests.sh production
./scripts/observability/validate-rules.sh
./scripts/k8s/security-smoke.sh ftgo-staging
```

Expected: all commands exit `0`.

- [ ] **Step 6: Commit**

```bash
git add scripts/k8s docs/runbooks deployment/kubernetes
git commit -m "ops: verify deployment and rollback paths"
```

## Phase Completion Checklist

- [ ] All images are pinned, non-root and read-only compatible.
- [ ] Production manifests satisfy workload policy validation.
- [ ] Secrets, mTLS and NetworkPolicy prevent bypass and credential exposure.
- [ ] One trace spans HTTP, saga, Kafka and projection.
- [ ] SLO dashboards and actionable alerts exist.
- [ ] MySQL/Scylla backup restores pass.
- [ ] Staging deploy and rollback tests pass.

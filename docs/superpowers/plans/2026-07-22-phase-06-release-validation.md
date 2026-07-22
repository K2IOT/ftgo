# FTGO Phase 06 Release Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Biến build, migration, contracts, security, performance, failure recovery và operational drills thành quality gate bắt buộc trước production release.

**Architecture:** GitHub Actions chạy fast PR gates trước, sau đó integration/E2E/security gates; staging promotion chạy load/chaos/restore/rollback suites và tạo release evidence artifact. Không release dựa trên kiểm thử thủ công không lưu bằng chứng.

**Tech Stack:** GitHub Actions, Gradle, Testcontainers, Docker Buildx, Trivy, Syft, Grype/Dependency-Check, CodeQL, Gitleaks, k6/Gatling, Toxiproxy, Kubernetes staging, Prometheus.

## Global Constraints

- Workflow dùng action SHA hoặc trusted major version đã được review; không dùng unpinned arbitrary action.
- Required checks không được `continue-on-error`.
- Flaky test không được retry vô hạn; phải quarantine có issue/owner/expiry rõ ràng.
- Performance và chaos thresholds là pass/fail criteria, không chỉ tạo report.
- Release artifact phải truy vết tới commit SHA, image digest, migration version, SBOM và test evidence.

---

### Task 1: Add Fast Pull Request Quality Gate

**Files:**
- Create: `.github/workflows/pr-fast.yml`
- Create: `.github/dependabot.yml`
- Create: `config/checkstyle/checkstyle.xml`
- Create: `config/spotbugs/exclude.xml`
- Modify: `build.gradle`
- Create: `scripts/ci/verify-clean-repository.sh`

**Interfaces:**
- Required checks: compile, unit tests, static analysis, migration compile, secret scan.

- [ ] **Step 1: Add local aggregate task**

Create Gradle task:

```groovy
tasks.register('prFast') {
    dependsOn subprojects.collect { project ->
        ["${project.path}:compileJava", "${project.path}:test"]
    }.flatten()
}
```

Add Checkstyle/SpotBugs with explicit baseline policy; new high-confidence violations fail build.

- [ ] **Step 2: Create workflow**

Workflow triggers on pull request to `dev` and runs:

1. checkout full enough for diff/scans
2. setup Java 21 with Gradle cache
3. `./gradlew clean prFast`
4. `./scripts/ci/verify-clean-repository.sh`
5. upload JUnit and static-analysis reports on failure

- [ ] **Step 3: Add secret scan**

Run Gitleaks against full repository history/diff and fail on verified secret patterns. Maintain allowlist only for documented fake fixtures.

- [ ] **Step 4: Validate workflow locally where possible**

```bash
./gradlew clean prFast
./scripts/ci/verify-clean-repository.sh
```

Expected: exit code `0`; working tree remains clean after tests/code generation.

- [ ] **Step 5: Commit**

```bash
git add .github build.gradle config scripts/ci
git commit -m "ci: add fast pull request quality gate"
```

---

### Task 2: Add Migration and Service Integration Gate

**Files:**
- Create: `.github/workflows/pr-integration.yml`
- Create: `scripts/ci/run-migration-matrix.sh`
- Create: `scripts/ci/run-service-integration-matrix.sh`
- Modify: migration tests from Phases 01-03.

**Interfaces:**
- Matrix covers every relational service and Scylla schema bootstrap.
- Tests run fresh install and supported upgrade path.

- [ ] **Step 1: Define migration matrix**

For each service:

- fresh database -> latest migration -> JPA validate
- previous release schema fixture -> latest migration -> invariant queries
- rerun Flyway validate -> no checksum drift

- [ ] **Step 2: Create integration workflow**

Run module matrices in parallel with bounded concurrency. Testcontainers uses GitHub-hosted Docker; no shared mutable database between jobs.

- [ ] **Step 3: Publish migration evidence**

Upload Flyway info output, schema checks and failing container logs as workflow artifact.

- [ ] **Step 4: Run locally**

```bash
./scripts/ci/run-migration-matrix.sh
./scripts/ci/run-service-integration-matrix.sh
```

Expected: every service exits `0` on fresh and upgrade scenarios.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/pr-integration.yml scripts/ci */src/test
git commit -m "ci: verify migrations and service integrations"
```

---

### Task 3: Add REST, Command and Event Contract Gates

**Files:**
- Create: `contract-tests/build.gradle`
- Create: `contract-tests/src/test/java/net/ftgo/contracts/RestContractTest.java`
- Create: `contract-tests/src/test/java/net/ftgo/contracts/SagaCommandContractTest.java`
- Create: `contract-tests/src/test/java/net/ftgo/contracts/DomainEventContractTest.java`
- Create: `contract-tests/src/test/resources/contracts/`
- Modify: `settings.gradle`
- Create: `.github/workflows/pr-contracts.yml`

**Interfaces:**
- Contracts include public REST JSON/schema/status, Eventuate command/reply types and domain event envelope versions.

- [ ] **Step 1: Snapshot current supported contracts**

Store machine-readable JSON schemas/OpenAPI fragments and representative command/event fixtures. Include schema version and compatibility rules.

- [ ] **Step 2: Write compatibility tests**

Rules:

- additive optional response/event fields allowed
- removing/renaming required field fails
- changing numeric/string type fails
- changing event type/topic fails without new version
- consumers can deserialize previous release fixture

- [ ] **Step 3: Generate/OpenAPI validation**

Validate every public controller against committed OpenAPI 3.1 document or generated spec diff. No undocumented public route.

- [ ] **Step 4: Add workflow**

```bash
./gradlew :contract-tests:test
```

Expected: current and previous-version fixtures deserialize; breaking change fails with named contract.

- [ ] **Step 5: Commit**

```bash
git add contract-tests settings.gradle .github/workflows/pr-contracts.yml
git commit -m "test: enforce api and event contracts"
```

---

### Task 4: Add Real-Service End-to-End Gate

**Files:**
- Create: `.github/workflows/pr-e2e.yml`
- Create: `scripts/ci/start-e2e-stack.sh`
- Create: `scripts/ci/collect-e2e-diagnostics.sh`
- Modify: `e2e-tests/build.gradle`
- Modify: existing E2E suites from Phases 02-04.

**Interfaces:**
- E2E stack includes all services, MySQL databases, Kafka, Debezium, Redis, Scylla and test identity provider.

- [ ] **Step 1: Remove mocked saga participants from release E2E path**

Unit/integration tests may retain mocks, but required E2E workflow must run real participant services and real messaging infrastructure.

- [ ] **Step 2: Define required scenarios**

- create success
- consumer credit rejection
- menu unavailable/price snapshot
- payment decline and compensation
- cancel success/duplicate
- revise success/retry
- delivery lifecycle
- order-history projection
- ownership denial
- lost reply/restart/out-of-order/poison event

- [ ] **Step 3: Add deterministic diagnostics**

On failure collect service logs, connector status, Kafka offsets, DLT records, saga rows, operations, outbox rows and DB health without exposing secrets.

- [ ] **Step 4: Run locally twice**

```bash
./scripts/ci/start-e2e-stack.sh
./gradlew :e2e-tests:test
./gradlew :e2e-tests:test
```

Expected: both runs pass from clean state.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/pr-e2e.yml scripts/ci e2e-tests
git commit -m "ci: gate releases on real service e2e flows"
```

---

### Task 5: Add Supply-Chain and Application Security Gates

**Files:**
- Create: `.github/workflows/security.yml`
- Create: `.github/workflows/codeql.yml`
- Create: `security/dependency-check-suppressions.xml`
- Create: `security/trivy-policy.rego`
- Create: `scripts/security/generate-sbom.sh`
- Create: `scripts/security/verify-no-critical-findings.sh`
- Create: `docs/security/vulnerability-management.md`

**Interfaces:**
- Outputs: CycloneDX/SPDX SBOM per image, vulnerability reports and signed provenance where registry supports it.

- [ ] **Step 1: Add dependency and SAST scans**

Run Gradle dependency analysis/OWASP Dependency-Check and CodeQL Java. Critical/high findings fail unless suppression includes CVE, reason, owner and expiry date.

- [ ] **Step 2: Scan images and IaC**

Trivy scans filesystem, image and Kubernetes configs. Fail on critical vulnerabilities and dangerous misconfiguration such as root user, privileged, public database service or missing limits.

- [ ] **Step 3: Generate SBOM and provenance**

Generate one SBOM per image, attach commit SHA and image digest, upload as workflow/release artifact. Sign images/attestations using approved CI identity.

- [ ] **Step 4: Add secret/history scan**

Scan full history and current image layers for secrets. Any real credential triggers rotation procedure, not merely file deletion.

- [ ] **Step 5: Run local scan commands**

```bash
./scripts/security/generate-sbom.sh
./scripts/security/verify-no-critical-findings.sh
```

Expected: no unresolved critical finding; reports generated.

- [ ] **Step 6: Commit**

```bash
git add .github/workflows security scripts/security docs/security
git commit -m "security: gate builds on supply chain scans"
```

---

### Task 6: Define and Gate Performance/Soak Tests

**Files:**
- Create: `performance/k6/create-order.js`
- Create: `performance/k6/order-query.js`
- Create: `performance/k6/mixed-workload.js`
- Create: `performance/k6/soak.js`
- Create: `performance/data/seed.js`
- Create: `scripts/performance/run.sh`
- Create: `.github/workflows/staging-performance.yml`
- Create: `docs/performance/baseline.md`

**Interfaces:**
- Test profiles: smoke, baseline, peak, soak.
- Metrics correlate client latency with saga completion and infrastructure saturation.

- [ ] **Step 1: Encode thresholds**

Initial gates:

```javascript
thresholds: {
  http_req_failed: ['rate<0.001'],
  http_req_duration: ['p(95)<500', 'p(99)<1000'],
  checks: ['rate>0.999']
}
```

Create-order script additionally polls operation completion and requires saga p95 < 10 seconds and p99 < 30 seconds under baseline load.

- [ ] **Step 2: Seed realistic bounded data**

Create consumers, restaurants, menus and couriers through supported setup APIs/fixtures. Use unique idempotency keys and avoid hot-key-only traffic.

- [ ] **Step 3: Run baseline and capture capacity**

```bash
./scripts/performance/run.sh staging baseline
./scripts/performance/run.sh staging soak
```

Soak duration minimum 2 hours for release candidate. Record CPU/memory, DB pool, Kafka lag, GC, error rate and DLT.

- [ ] **Step 4: Fail on saturation/leak signals**

Gate fails if memory grows without stabilization, consumer lag does not recover, DB pool remains saturated, error threshold breaches or DLT increases.

- [ ] **Step 5: Commit**

```bash
git add performance scripts/performance .github/workflows/staging-performance.yml docs/performance
git commit -m "perf: gate staging on load and soak targets"
```

---

### Task 7: Add Chaos and Recovery Gates

**Files:**
- Create: `chaos/scenarios/kafka-connect-outage.yaml`
- Create: `chaos/scenarios/participant-restart.yaml`
- Create: `chaos/scenarios/mysql-latency.yaml`
- Create: `chaos/scenarios/kafka-broker-loss.yaml`
- Create: `chaos/scenarios/scylla-outage.yaml`
- Create: `scripts/chaos/run-scenario.sh`
- Create: `scripts/chaos/assert-invariants.sh`
- Create: `.github/workflows/staging-chaos.yml`
- Create: `docs/runbooks/chaos-testing.md`

**Interfaces:**
- Invariants are checked before, during and after each scenario.

- [ ] **Step 1: Define invariants**

For every scenario assert:

- no duplicate authorization/ticket/delivery per business key
- no unexplained credit difference
- no lost domain event after recovery
- operation reaches success, compensated or explicit manual-review state
- projection catches up after dependency recovery

- [ ] **Step 2: Implement bounded scenarios**

Scenarios:

1. Pause Debezium during order approval, resume and catch up.
2. Kill Accounting/Kitchen pod after commit before reply.
3. Inject MySQL latency/timeouts.
4. Lose one Kafka broker while min ISR remains available.
5. Stop Scylla and verify write-side flow continues with projection backlog/recovery.

- [ ] **Step 3: Run staging chaos suite**

```bash
for scenario in chaos/scenarios/*.yaml; do
  ./scripts/chaos/run-scenario.sh staging "$scenario"
  ./scripts/chaos/assert-invariants.sh staging
done
```

Expected: all invariants pass; no manual database repair required for supported scenarios.

- [ ] **Step 4: Commit**

```bash
git add chaos scripts/chaos .github/workflows/staging-chaos.yml docs/runbooks/chaos-testing.md
git commit -m "test: verify failure recovery with chaos scenarios"
```

---

### Task 8: Automate Backup, Rollback and DR Evidence

**Files:**
- Create: `.github/workflows/staging-operations-drill.yml`
- Create: `scripts/release/run-restore-drill.sh`
- Create: `scripts/release/run-rollback-drill.sh`
- Create: `scripts/release/collect-release-evidence.sh`
- Create: `docs/release/production-readiness-checklist.md`
- Create: `docs/release/release-signoff-template.md`

**Interfaces:**
- Release evidence artifact contains commit, image digests, migration versions, SBOMs, test reports, scan summaries, restore/rollback results and SLO observations.

- [ ] **Step 1: Run restore drill against isolated namespace**

Restore latest backups, deploy current application, run business smoke and consistency queries. Measure actual RPO/RTO and fail when targets are exceeded.

- [ ] **Step 2: Run rollback drill**

Deploy release candidate, execute transactions, roll application images back one version without destructive schema rollback and rerun smoke/contract checks.

- [ ] **Step 3: Generate evidence bundle**

```bash
./scripts/release/collect-release-evidence.sh "$GIT_SHA" "$RELEASE_VERSION"
```

Bundle must be checksummed and uploaded to the workflow/release.

- [ ] **Step 4: Require sign-off checklist**

Checklist includes engineering, security, operations and product/business owner approval for payment/order semantics. Every accepted risk has owner and expiry.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/staging-operations-drill.yml scripts/release docs/release
git commit -m "release: automate readiness and recovery evidence"
```

---

### Task 9: Configure Branch Protection and Release Workflow

**Files:**
- Create: `.github/workflows/release.yml`
- Create: `.github/release.yml`
- Create: `docs/release/branch-protection.md`
- Create: `scripts/release/verify-required-checks.sh`

**Interfaces:**
- Release input is a commit on protected `dev`/release branch with all required checks successful.
- Output is versioned images by digest and deployment candidate metadata.

- [ ] **Step 1: Document required branch checks**

Required:

- PR Fast
- Migration/Integration
- Contracts
- Real-Service E2E
- Security
- Manifest policy

Require reviewed PR, no direct push, resolved conversations and up-to-date branch according to repository policy.

- [ ] **Step 2: Implement release workflow**

Build once, scan once, sign once, promote same image digests through staging/production. Do not rebuild different bits for production.

- [ ] **Step 3: Add pre-release verification**

```bash
./scripts/release/verify-required-checks.sh "$GIT_SHA"
```

Fail if any required check missing, pending, skipped or unsuccessful.

- [ ] **Step 4: Create release candidate**

Workflow creates release notes, attaches evidence/SBOM and records migration compatibility window. Production deployment remains approval-gated.

- [ ] **Step 5: Commit**

```bash
git add .github scripts/release docs/release
git commit -m "release: protect and promote verified artifacts"
```

## Phase Completion Checklist

- [ ] PR cannot merge without compile/unit/static/migration/integration/contract/E2E/security gates.
- [ ] Real-service E2E runs without mocked saga participants.
- [ ] SBOM, image scans and provenance are attached to release candidate.
- [ ] Baseline and soak tests satisfy SLO thresholds.
- [ ] Chaos scenarios preserve financial/order invariants.
- [ ] Backup restore and application rollback drills pass within RPO/RTO.
- [ ] Release evidence bundle and multidisciplinary sign-off exist.
- [ ] Production deploy uses the exact verified image digests.

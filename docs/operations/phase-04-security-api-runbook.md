# Phase 04 Security, Mainline and Production Configuration Runbook

## Purpose

This runbook covers the operational controls introduced by Phase 04 remediation, including protected `dev` mainline verification, fail-closed production datasource configuration, secret scanning, supported dependency/vulnerability verification, canonical CI, and post-merge evidence.

## Mainline Policy

`dev` is the FTGO integration mainline. Configure repository rules so that `dev`:

- requires pull requests before merge;
- requires the canonical `CI Gate` status from `.github/workflows/ci.yml`;
- requires review conversation resolution;
- disallows force pushes and branch deletion;
- is the repository default branch after the first successful canonical CI push run.

Do not promote a merge SHA when the canonical CI workflow is red.

## Canonical CI and Exact Merge-SHA Verification

`.github/workflows/ci.yml` is the only GitHub Actions workflow for repository CI. It runs on pull requests to `dev`, pushes to `dev`, merge queue revisions, the nightly schedule, and manual dispatch.

The workflow checks out `${{ github.sha }}` and verifies that `git rev-parse HEAD` matches `GITHUB_SHA`. It then runs one project-wide stage graph:

1. **Validate**
   - verified Gradle 8.14.3 wrapper;
   - gitleaks secret scan;
   - Docker Compose model validation.
2. **Build**
   - `./gradlew clean assemble --no-daemon --stacktrace`.
3. **Unit Test**
   - `./gradlew clean test --no-daemon --stacktrace`.
4. **Contract Test**
   - `python3 -m unittest discover -s deployment/tests -p 'test_*.py' -v`.
5. **Dependency & Vulnerability**
   - `bash scripts/ci/verify-supported-dependencies.sh`;
   - build all executable service jars;
   - pinned Trivy rootfs scan;
   - fail on HIGH or CRITICAL vulnerabilities;
   - verify the Trivy report contains Java dependency coverage for every executable service.
6. **Smoke Test**
   - Debezium CDC;
   - fresh-stack startup.
7. **Integration Test**
   - core order flow;
   - payment settlement;
   - distributed consistency/failure recovery.
8. **CI Gate**
   - stable aggregate status used by repository rules.

Pull requests and merge queue revisions execute one clean-state cycle for the stateful smoke/integration scenarios. Pushes to `dev`, scheduled runs and manual runs execute two consecutive clean-state cycles.

For each remediation merge, record the `dev` SHA, canonical workflow run ID and `CI Gate` conclusion in the Phase 04 completion record. A green PR head is not sufficient evidence by itself.

## Supported Platform and Vulnerability Gate

The supported Phase 04 platform baseline is:

- Java 21;
- Spring Boot 4.0.7;
- Spring Cloud 2025.1.2;
- Gradle 8.14.3;
- Eventuate platform BOM 2024.0.RELEASE.

The canonical CI dependency-security job preserves the Task 10 gate that previously ran in a dedicated workflow. It generates dependency insight for Spring Security, Netty, Jackson, Kafka, MySQL, PostgreSQL/MSSQL exclusions and Testcontainers, then scans built service jars with the pinned Trivy action.

Do not suppress HIGH/CRITICAL findings merely to make CI green. Any accepted exception must follow the evidence and deadline requirements in `docs/operations/spring-platform-upgrade-runbook.md`.

## Production Datasource Configuration

Base service configuration does not provide MySQL URL, username or password defaults. Production deployments must set:

| Service | Required variables |
|---|---|
| Order | `ORDER_DB_URL`, `ORDER_DB_USERNAME`, `ORDER_DB_PASSWORD` |
| Consumer | `CONSUMER_DB_URL`, `CONSUMER_DB_USERNAME`, `CONSUMER_DB_PASSWORD` |
| Restaurant | `RESTAURANT_DB_URL`, `RESTAURANT_DB_USERNAME`, `RESTAURANT_DB_PASSWORD` |
| Kitchen | `KITCHEN_DB_URL`, `KITCHEN_DB_USERNAME`, `KITCHEN_DB_PASSWORD` |
| Accounting | `ACCOUNTING_DB_URL`, `ACCOUNTING_DB_USERNAME`, `ACCOUNTING_DB_PASSWORD` |
| Delivery | `DELIVERY_DB_URL`, `DELIVERY_DB_USERNAME`, `DELIVERY_DB_PASSWORD` |

An unset variable must fail startup rather than silently connecting with developer credentials. `application-local.yml` contains localhost-only defaults and must be activated explicitly with `SPRING_PROFILES_ACTIVE=local`.

Never activate the `local` profile in production manifests.

## Secret Scanning

Secret scanning is part of the `Validate` stage in `.github/workflows/ci.yml` and invokes:

```bash
bash scripts/ci/scan-secrets.sh
```

The script pins `zricethezav/gitleaks:v8.24.3`. Do not weaken a finding by adding broad allowlists. If a deterministic test fixture must be exempted, use the existing exact fingerprint/path policy and document why the value is non-production and safe to keep.

If a real secret is detected, revoke/rotate it first, then remove it from the current tree and repository history as required by the credential owner.

## Deployment Checklist

Before deployment:

1. Confirm no production workload sets `SPRING_PROFILES_ACTIVE=local`.
2. Confirm all required database variables are supplied from the deployment secret mechanism.
3. Confirm the candidate SHA has a green canonical `CI Gate`.
4. Confirm the dependency/vulnerability stage is green with no unrecorded HIGH/CRITICAL exception.
5. Confirm required PR review and conversation-resolution rules are satisfied.
6. Merge through the protected `dev` branch.
7. Confirm the canonical `CI Gate` is green on the resulting `dev` SHA.
8. Record SHA, workflow run ID and conclusion in the Phase 04 completion record.

## Rollback

Application rollback must not restore embedded database credentials, wildcard CORS, scope-derived application roles, fail-open authorization defaults, unsupported platform versions or disabled vulnerability scanning. Repository rules should remain enabled during rollback.

If canonical CI fails after a merge, stop promotion of that SHA. Fix forward through a new PR against `dev`; do not force-push or rewrite `dev` history.

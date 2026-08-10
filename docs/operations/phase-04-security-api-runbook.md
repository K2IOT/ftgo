# Phase 04 Security, Mainline and Production Configuration Runbook

## Purpose

This runbook covers the operational controls introduced by Phase 04 remediation, including protected `dev` mainline verification, fail-closed production datasource configuration, secret scanning, and post-merge evidence.

## Mainline Policy

`dev` is the FTGO integration mainline. Configure repository rules so that `dev`:

- requires pull requests before merge;
- requires the repository's merge-verification and security checks;
- requires review conversation resolution;
- disallows force pushes and branch deletion;
- is the repository default branch after the first successful `Dev Merge Verification` push run.

Do not change the default branch from `master` until the `dev` merge-verification workflow has completed successfully at least once on an actual `dev` push SHA.

## Exact Merge-SHA Verification

`.github/workflows/dev-merge-verification.yml` runs on every push to `dev`. It checks out `${{ github.sha }}`, verifies that `git rev-parse HEAD` matches that SHA, then runs:

```bash
bash scripts/ci/verify-gradle-wrapper.sh
./gradlew --no-daemon clean test --stacktrace
python3 -m unittest discover -s deployment/tests -p 'test_*.py' -v
```

For each remediation merge, record the `dev` SHA, workflow run ID and conclusion in the Phase 04 completion record. A green PR head is not sufficient evidence by itself.

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

`.github/workflows/secret-scan.yml` runs on pull requests to `dev`, pushes to `dev`, merge-queue checks and manual dispatch. It invokes:

```bash
bash scripts/ci/scan-secrets.sh
```

The script pins `zricethezav/gitleaks:v8.24.3`. Do not weaken a finding by adding broad allowlists. If a deterministic test fixture must be exempted, add an exact-path rule to `.gitleaks.toml` with a comment explaining why the value is non-production and safe to keep.

If a real secret is detected, revoke/rotate it first, then remove it from the current tree and repository history as required by the credential owner.

## Deployment Checklist

Before deployment:

1. Confirm no production workload sets `SPRING_PROFILES_ACTIVE=local`.
2. Confirm all required database variables are supplied from the deployment secret mechanism.
3. Confirm `Secret Scan` is green on the candidate SHA.
4. Confirm required PR checks are green on one final head SHA.
5. Merge through the protected `dev` branch.
6. Confirm `Dev Merge Verification` is green on the resulting `dev` SHA.
7. Record SHA, run IDs and conclusions in the Phase 04 completion record.

## Rollback

Application rollback must not restore embedded database credentials, wildcard CORS, scope-derived application roles or fail-open authorization defaults. Repository rules should remain enabled during rollback.

If merge verification fails after a merge, stop promotion of that SHA. Fix forward through a new PR against `dev`; do not force-push or rewrite `dev` history.

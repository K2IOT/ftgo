# FTGO Immutable Image Build Runbook

## Purpose

Build the eight FTGO runtime services from one reviewed Dockerfile and attach source provenance to every image. Images use explicit release tags; `latest` is prohibited. Promotion must use the verified image digest rather than rebuilding the same version in another environment.

## Prerequisites

- Docker with BuildKit support.
- The checked-in Gradle wrapper.
- A clean repository checkout at the commit being released.
- A semantic or otherwise immutable release version.

## Build all service images

From the repository root:

```bash
VERSION=1.0.0-local
GIT_SHA="$(git rev-parse HEAD)"
./scripts/build/build-images.sh "${VERSION}" "${GIT_SHA}"
```

The script builds:

- `api-gateway`
- `order-service`
- `consumer-service`
- `restaurant-service`
- `kitchen-service`
- `accounting-service`
- `delivery-service`
- `order-history-service`

Each image is tagged as `ftgo/<service>:<version>` and records `GIT_SHA`, version and repository source through OCI labels.

## Verify runtime hardening

Run the verifier for every built image:

```bash
for service in \
  api-gateway order-service consumer-service restaurant-service \
  kitchen-service accounting-service delivery-service order-history-service
do
  ./scripts/build/verify-image.sh "ftgo/${service}:${VERSION}"
done
```

`verify-image.sh` rejects an image unless it:

- declares a numeric non-root user;
- exposes a liveness healthcheck;
- contains source, revision and version labels;
- starts under a read-only root filesystem with a writable `/tmp` tmpfs;
- can execute `java -version` without requiring root.

The application writes logs to standard output. Runtime scratch files are restricted to `/tmp`; Kubernetes must mount that location as an `emptyDir` or equivalent writable tmpfs.

## Record and promote the image digest

After verification, record the immutable image digest:

```bash
docker image inspect "ftgo/api-gateway:${VERSION}" \
  --format '{{index .RepoDigests 0}}'
```

A local image has no repository digest until it is pushed. CI must push once, capture the registry-provided digest and promote that exact digest through staging and production. Never rebuild an existing version tag.

## Infrastructure Compose

Prepare local-only credentials before starting infrastructure:

```bash
cp deployment/.env.example deployment/.env
cd deployment
docker compose -f docker-compose.infra.yml config --quiet
docker compose -f docker-compose.infra.yml up -d
```

`deployment/.env` is ignored by Git. Production must not use this file or Vault dev mode; production values come from the external secret store defined in the Kubernetes phase.

## Rollback

Rollback selects the previous verified image digest. Do not retag or overwrite an existing version, and do not reverse a forward-only database migration destructively.

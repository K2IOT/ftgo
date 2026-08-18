#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKERFILE="${ROOT_DIR}/docker/service.Dockerfile"

SERVICES=(
  api-gateway
  order-service
  consumer-service
  restaurant-service
  kitchen-service
  accounting-service
  delivery-service
  order-history-service
)

usage() {
  echo "Usage: $0 <version> <git-sha>" >&2
}

if [[ $# -ne 2 ]]; then
  usage
  exit 2
fi

VERSION="$1"
GIT_SHA="$2"

if [[ -z "${VERSION}" || "${VERSION,,}" == "latest" ]]; then
  echo "Image version must be non-empty and must not be latest" >&2
  exit 2
fi
if ! [[ "${VERSION}" =~ ^[0-9A-Za-z][0-9A-Za-z._-]*$ ]]; then
  echo "Invalid image version: ${VERSION}" >&2
  exit 2
fi
if ! [[ "${GIT_SHA}" =~ ^[0-9a-fA-F]{7,40}$ ]]; then
  echo "Git SHA must contain 7 to 40 hexadecimal characters: ${GIT_SHA}" >&2
  exit 2
fi
if [[ ! -f "${DOCKERFILE}" ]]; then
  echo "Dockerfile not found: ${DOCKERFILE}" >&2
  exit 1
fi
command -v docker >/dev/null 2>&1 || {
  echo "docker is required" >&2
  exit 1
}

for service in "${SERVICES[@]}"; do
  image="ftgo/${service}:${VERSION}"
  echo "Building ${image} from ${GIT_SHA}"
  DOCKER_BUILDKIT=1 docker build \
    --file "${DOCKERFILE}" \
    --build-arg SERVICE="${service}" \
    --build-arg VERSION="${VERSION}" \
    --build-arg GIT_SHA="${GIT_SHA}" \
    --tag "${image}" \
    "${ROOT_DIR}"
done

echo "Built ${#SERVICES[@]} immutable FTGO service images for version ${VERSION}"

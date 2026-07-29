#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <image>" >&2
  exit 2
fi

IMAGE="$1"
command -v docker >/dev/null 2>&1 || {
  echo "docker is required" >&2
  exit 1
}

docker image inspect "${IMAGE}" >/dev/null

user="$(docker inspect "${IMAGE}" --format '{{.Config.User}}')"
if [[ -z "${user}" || "${user}" == "0" || "${user}" == "root" ]]; then
  echo "Image must declare a non-root user: ${IMAGE}" >&2
  exit 1
fi
if ! [[ "${user}" =~ ^[1-9][0-9]*(:[1-9][0-9]*)?$ ]]; then
  echo "Image user must be numeric for predictable Kubernetes execution: ${user}" >&2
  exit 1
fi

healthcheck="$(docker inspect "${IMAGE}" --format '{{json .Config.Healthcheck}}')"
if [[ "${healthcheck}" == "null" || "${healthcheck}" != *"/actuator/health/liveness"* ]]; then
  echo "Image healthcheck must target the liveness endpoint" >&2
  exit 1
fi

for label in \
  org.opencontainers.image.source \
  org.opencontainers.image.revision \
  org.opencontainers.image.version
do
  value="$(docker inspect "${IMAGE}" --format "{{ index .Config.Labels \"${label}\" }}")"
  if [[ -z "${value}" || "${value}" == "<no value>" ]]; then
    echo "Missing OCI label ${label} on ${IMAGE}" >&2
    exit 1
  fi
done

# The image deliberately uses CMD rather than ENTRYPOINT so this executes java -version.
docker run --rm \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  "${IMAGE}" java -version

echo "Verified hardened image: ${IMAGE}"

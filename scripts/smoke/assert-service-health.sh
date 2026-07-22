#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "Usage: $0 <service-name> <health-url> <pid> <log-file> [attempts]" >&2
  exit 2
fi

service_name="$1"
health_url="$2"
service_pid="$3"
log_file="$4"
attempts="${5:-90}"

for attempt in $(seq 1 "${attempts}"); do
  if ! kill -0 "${service_pid}" >/dev/null 2>&1; then
    echo "${service_name} exited before becoming healthy" >&2
    cat "${log_file}" >&2 || true
    exit 1
  fi

  response="$(curl --silent --show-error --fail "${health_url}" 2>/dev/null || true)"
  if [[ -n "${response}" ]] && jq -e '.status == "UP"' >/dev/null 2>&1 <<<"${response}"; then
    echo "${service_name} is UP"
    exit 0
  fi

  echo "Waiting for ${service_name} health (${attempt}/${attempts})"
  sleep 2
done

echo "${service_name} did not become healthy: ${health_url}" >&2
cat "${log_file}" >&2 || true
exit 1

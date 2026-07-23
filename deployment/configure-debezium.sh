#!/usr/bin/env bash
set -euo pipefail

CONNECT_URL="${DEBEZIUM_CONNECT_URL:-http://localhost:8083}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONNECTOR_DIR="${SCRIPT_DIR}/debezium/connectors"
CONNECTOR_GLOB="${DEBEZIUM_CONNECTOR_GLOB:-*-outbox.json}"
MAX_CONNECT_ATTEMPTS="${DEBEZIUM_CONNECT_ATTEMPTS:-60}"
MAX_STATUS_ATTEMPTS="${DEBEZIUM_STATUS_ATTEMPTS:-30}"

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Required command is missing: ${command_name}" >&2
    exit 1
  fi
}

wait_for_connect() {
  echo "Waiting for Debezium Connect at ${CONNECT_URL}..."
  for attempt in $(seq 1 "${MAX_CONNECT_ATTEMPTS}"); do
    if curl --silent --show-error --fail "${CONNECT_URL}/connectors" >/dev/null; then
      echo "Debezium Connect is ready"
      return 0
    fi
    echo "Debezium Connect is not ready (${attempt}/${MAX_CONNECT_ATTEMPTS})"
    sleep 2
  done

  echo "Debezium Connect did not become ready" >&2
  return 1
}

verify_connector_running() {
  local connector_name="$1"
  local status_url="${CONNECT_URL}/connectors/${connector_name}/status"

  for attempt in $(seq 1 "${MAX_STATUS_ATTEMPTS}"); do
    local status_json
    if ! status_json="$(curl --silent --show-error --fail-with-body "${status_url}" 2>&1)"; then
      echo "Waiting for connector ${connector_name}: status endpoint not ready (${attempt}/${MAX_STATUS_ATTEMPTS})"
      if [[ -n "${status_json}" ]]; then
        echo "${status_json}" >&2
      fi
      sleep 2
      continue
    fi

    local connector_state
    connector_state="$(jq -r '.connector.state // "UNKNOWN"' <<<"${status_json}")"
    local failed_tasks
    failed_tasks="$(jq '[.tasks[]? | select(.state == "FAILED")] | length' <<<"${status_json}")"
    local non_running_tasks
    non_running_tasks="$(jq '[.tasks[]? | select(.state != "RUNNING")] | length' <<<"${status_json}")"

    if [[ "${connector_state}" == "RUNNING" && "${non_running_tasks}" == "0" ]]; then
      echo "Connector ${connector_name} is RUNNING"
      return 0
    fi

    if [[ "${connector_state}" == "FAILED" || "${failed_tasks}" != "0" ]]; then
      echo "Connector ${connector_name} failed:" >&2
      jq . <<<"${status_json}" >&2
      return 1
    fi

    echo "Waiting for connector ${connector_name}: state=${connector_state} (${attempt}/${MAX_STATUS_ATTEMPTS})"
    sleep 2
  done

  echo "Connector ${connector_name} did not reach \"RUNNING\" state" >&2
  curl --silent --show-error "${status_url}" | jq . >&2 || true
  return 1
}

register_connector() {
  local connector_file="$1"
  local connector_name
  connector_name="$(jq -r '.name' "${connector_file}")"

  if [[ -z "${connector_name}" || "${connector_name}" == "null" ]]; then
    echo "Connector file has no name: ${connector_file}" >&2
    return 1
  fi

  echo "Applying connector ${connector_name} from ${connector_file}"
  jq -c '.config' "${connector_file}" |
    curl --silent --show-error --fail-with-body \
      --request PUT \
      --header 'Content-Type: application/json' \
      --data-binary @- \
      "${CONNECT_URL}/connectors/${connector_name}/config" |
    jq .

  verify_connector_running "${connector_name}"
}

main() {
  require_command curl
  require_command jq

  if [[ ! -d "${CONNECTOR_DIR}" ]]; then
    echo "Connector directory does not exist: ${CONNECTOR_DIR}" >&2
    exit 1
  fi

  wait_for_connect

  local connector_count=0
  while IFS= read -r -d '' connector_file; do
    register_connector "${connector_file}"
    connector_count=$((connector_count + 1))
  done < <(find "${CONNECTOR_DIR}" -maxdepth 1 -type f -name "${CONNECTOR_GLOB}" -print0 | sort -z)

  if [[ "${connector_count}" == "0" ]]; then
    echo "No connector files matching ${CONNECTOR_GLOB} found in ${CONNECTOR_DIR}" >&2
    exit 1
  fi

  echo "Applied and verified ${connector_count} Debezium connectors"
}

main "$@"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${PHASE02A_COMPOSE_FILE:-${ROOT_DIR}/deployment/docker-compose.infra.yml}"
ORDER_COUNT_FILE="${PHASE02A_ORDER_COUNT_FILE:-}"
KAFKA_LAG_FILE="${PHASE02A_KAFKA_LAG_FILE:-}"
LAG_STABILITY_SECONDS="${PHASE02A_LAG_STABILITY_SECONDS:-10}"
DELIVERY_GROUP="${PHASE02A_DELIVERY_GROUP:-delivery-service}"
ORDER_DB_USER="${PHASE02A_ORDER_DB_USER:-ftgo_user}"
ORDER_DB_PASSWORD="${PHASE02A_ORDER_DB_PASSWORD:-ftgo_password}"
ORDER_DB_NAME="${PHASE02A_ORDER_DB_NAME:-ftgo_order}"

ORDER_QUERY=$(cat <<'SQL'
SELECT COUNT(*)
FROM orders
WHERE state IN (
  'APPROVAL_PENDING',
  'AWAITING_RESTAURANT_ACCEPTANCE',
  'CONFIRMATION_PENDING'
)
AND (
  pickup_address_street IS NULL
  OR pickup_address_city IS NULL
  OR pickup_address_state IS NULL
  OR pickup_address_zip_code IS NULL
);
SQL
)

fail() {
  echo "Phase 02A rollout preflight failed: $*" >&2
  exit 1
}

require_readable_file() {
  local file="$1"
  [[ -r "${file}" ]] || fail "fixture file is not readable: ${file}"
}

require_docker() {
  command -v docker >/dev/null 2>&1 || fail "docker is required when fixture files are not supplied"
  docker compose -f "${COMPOSE_FILE}" config --quiet >/dev/null 2>&1 \
    || fail "Docker Compose model is unavailable: ${COMPOSE_FILE}"
}

read_legacy_order_count() {
  if [[ -n "${ORDER_COUNT_FILE}" ]]; then
    require_readable_file "${ORDER_COUNT_FILE}"
    cat "${ORDER_COUNT_FILE}"
    return
  fi

  require_docker
  docker compose -f "${COMPOSE_FILE}" exec -T mysql-order \
    mysql --batch --skip-column-names \
      "-u${ORDER_DB_USER}" "-p${ORDER_DB_PASSWORD}" "${ORDER_DB_NAME}" \
      -e "${ORDER_QUERY}"
}

read_delivery_lag() {
  if [[ -n "${KAFKA_LAG_FILE}" ]]; then
    require_readable_file "${KAFKA_LAG_FILE}"
    cat "${KAFKA_LAG_FILE}"
    return
  fi

  require_docker
  docker compose -f "${COMPOSE_FILE}" exec -T kafka-1 \
    kafka-consumer-groups.sh \
      --bootstrap-server kafka-1:9092 \
      --describe \
      --group "${DELIVERY_GROUP}"
}

parse_total_lag() {
  awk -v group="${DELIVERY_GROUP}" '
    $1 == group {
      rows++
      if ($6 !~ /^[0-9]+$/) {
        invalid = 1
      } else {
        total += $6
      }
    }
    END {
      if (rows == 0 || invalid) {
        exit 42
      }
      print total
    }
  '
}

verified_delivery_lag() {
  local output
  local total

  if ! output="$(read_delivery_lag 2>&1)"; then
    fail "could not read ${DELIVERY_GROUP} consumer lag: ${output}"
  fi
  if ! total="$(printf '%s\n' "${output}" | parse_total_lag)"; then
    fail "could not verify ${DELIVERY_GROUP} consumer lag from broker output"
  fi
  printf '%s\n' "${total}"
}

main() {
  if ! [[ "${LAG_STABILITY_SECONDS}" =~ ^[0-9]+$ ]]; then
    fail "PHASE02A_LAG_STABILITY_SECONDS must be a non-negative integer"
  fi

  local legacy_count
  legacy_count="$(read_legacy_order_count | tr -d '[:space:]')"
  if ! [[ "${legacy_count}" =~ ^[0-9]+$ ]]; then
    fail "legacy order query returned a non-integer result: ${legacy_count}"
  fi
  if (( legacy_count > 0 )); then
    fail "${legacy_count} legacy in-flight order(s) are missing a complete pickup snapshot"
  fi

  local first_lag
  first_lag="$(verified_delivery_lag)"
  if (( first_lag > 0 )); then
    fail "${DELIVERY_GROUP} consumer lag is ${first_lag}"
  fi

  if (( LAG_STABILITY_SECONDS > 0 )); then
    sleep "${LAG_STABILITY_SECONDS}"
  fi

  local second_lag
  second_lag="$(verified_delivery_lag)"
  if (( second_lag > 0 )); then
    fail "${DELIVERY_GROUP} consumer lag is ${second_lag} after the stability interval"
  fi

  echo "Phase 02A rollout preflight passed: no legacy in-flight orders and stable zero Delivery lag"
}

main "$@"

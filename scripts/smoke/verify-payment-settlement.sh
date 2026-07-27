#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/deployment/tests/docker-compose.core-order-flow.yml"
COMPOSE=(docker compose --project-name ftgo-phase02b-e2e -f "${COMPOSE_FILE}")
LOG_ROOT="${ROOT_DIR}/payment-settlement-e2e-logs"
RUNS=2
SERVICE_PIDS=()

rm -rf "${LOG_ROOT}"
mkdir -p "${LOG_ROOT}"
exec > >(tee -a "${LOG_ROOT}/runner.log") 2>&1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --runs)
      RUNS="$2"
      shift 2
      ;;
    *)
      echo "Usage: $0 [--runs <count>]" >&2
      exit 2
      ;;
  esac
done

if ! [[ "${RUNS}" =~ ^[1-9][0-9]*$ ]]; then
  echo "Run count must be a positive integer: ${RUNS}" >&2
  exit 2
fi

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is missing: $1" >&2
    exit 1
  }
}

stop_services() {
  local pid
  for pid in "${SERVICE_PIDS[@]:-}"; do
    if [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1; then
      kill "${pid}" >/dev/null 2>&1 || true
      wait "${pid}" >/dev/null 2>&1 || true
    fi
  done
  SERVICE_PIDS=()
}

cleanup() {
  stop_services
  mkdir -p "${LOG_ROOT}"
  "${COMPOSE[@]}" --profile relays logs --no-color >"${LOG_ROOT}/compose-last.log" 2>&1 || true
  "${COMPOSE[@]}" --profile relays down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

service_jar() {
  local module="$1"
  find "${ROOT_DIR}/${module}/build/libs" -maxdepth 1 -type f \
    -name '*.jar' ! -name '*-plain.jar' | sort | head -n 1
}

wait_for_health() {
  local service="$1"
  local url="$2"
  local pid="$3"
  local log_file="$4"
  local attempt
  for attempt in $(seq 1 120); do
    if curl --silent --fail "${url}" | grep -q '"status":"UP"'; then
      echo "${service} is healthy"
      return 0
    fi
    if ! kill -0 "${pid}" >/dev/null 2>&1; then
      echo "${service} exited before becoming healthy" >&2
      cat "${log_file}" >&2 || true
      return 1
    fi
    sleep 1
  done
  echo "${service} did not become healthy" >&2
  cat "${log_file}" >&2 || true
  return 1
}

start_service() {
  local run="$1"
  local module="$2"
  local port="$3"
  local schema="$4"
  shift 4

  local log_dir="${LOG_ROOT}/run-${run}"
  local log_file="${log_dir}/${module}.log"
  local jar
  jar="$(service_jar "${module}")"
  mkdir -p "${log_dir}"

  echo "Starting ${module}"
  env \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx384m" \
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:33306/${schema}?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false" \
    SPRING_DATASOURCE_USERNAME=ftgo_user \
    SPRING_DATASOURCE_PASSWORD=ftgo_password \
    SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:29092 \
    EVENTUATELOCAL_KAFKA_BOOTSTRAP_SERVERS=localhost:29092 \
    "$@" java -jar "${jar}" >"${log_file}" 2>&1 &
  local pid=$!
  SERVICE_PIDS+=("${pid}")
  wait_for_health "${module}" "http://localhost:${port}/actuator/health" "${pid}" "${log_file}"
}

wait_for_url() {
  local name="$1"
  local url="$2"
  local attempt
  for attempt in $(seq 1 120); do
    if curl --silent --fail "${url}" >/dev/null; then
      echo "${name} is ready"
      return 0
    fi
    sleep 1
  done
  echo "${name} did not become ready at ${url}" >&2
  return 1
}

register_kitchen_outbox_connector() {
  cat <<'JSON' | curl --silent --show-error --fail-with-body \
    --request PUT \
    --header 'Content-Type: application/json' \
    --data-binary @- \
    http://localhost:18083/connectors/ftgo-phase02b-kitchen/config >/dev/null
{
  "connector.class": "io.debezium.connector.mysql.MySqlConnector",
  "database.hostname": "mysql",
  "database.port": "3306",
  "database.user": "root",
  "database.password": "rootpassword",
  "database.server.id": "184022",
  "topic.prefix": "ftgo-phase02b-kitchen",
  "database.include.list": "ftgo_kitchen",
  "table.include.list": "ftgo_kitchen.outbox",
  "include.schema.changes": "false",
  "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
  "schema.history.internal.kafka.topic": "schema-history.ftgo-phase02b-kitchen",
  "transforms": "outbox",
  "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
  "transforms.outbox.table.field.event.id": "id",
  "transforms.outbox.table.field.event.key": "aggregate_id",
  "transforms.outbox.table.field.event.type": "event_type",
  "transforms.outbox.table.field.event.payload": "payload",
  "transforms.outbox.route.by.field": "destination",
  "transforms.outbox.route.topic.replacement": "${routedByValue}",
  "transforms.outbox.table.expand.json.payload": "true",
  "transforms.outbox.table.fields.additional.placement": "event_type:header:eventType"
}
JSON

  local attempt
  for attempt in $(seq 1 60); do
    local state
    state="$(curl --silent http://localhost:18083/connectors/ftgo-phase02b-kitchen/status \
      | jq -r '.connector.state // "UNKNOWN"')"
    local failed
    failed="$(curl --silent http://localhost:18083/connectors/ftgo-phase02b-kitchen/status \
      | jq '[.tasks[]? | select(.state == "FAILED")] | length')"
    if [[ "${state}" == "RUNNING" && "${failed}" == "0" ]]; then
      echo "Phase 02B kitchen outbox connector is running"
      return 0
    fi
    if [[ "${state}" == "FAILED" || "${failed}" != "0" ]]; then
      curl --silent http://localhost:18083/connectors/ftgo-phase02b-kitchen/status | jq . >&2
      return 1
    fi
    sleep 1
  done
  echo "Phase 02B kitchen outbox connector did not become ready" >&2
  return 1
}

build_artifacts() {
  chmod +x "${ROOT_DIR}/gradlew"
  "${ROOT_DIR}/gradlew" --no-daemon clean \
    :order-service:bootJar \
    :consumer-service:bootJar \
    :restaurant-service:bootJar \
    :kitchen-service:bootJar \
    :accounting-service:bootJar \
    :e2e-tests:testClasses
}

assert_capture_retried() {
  local attempts
  attempts="$("${COMPOSE[@]}" exec -T mysql mysql \
    --user=ftgo_user --password=ftgo_password --skip-column-names \
    --execute="select max(attempt_count) from ftgo_accounting.simulated_provider_operations where operation_type = 'CAPTURE';")"
  attempts="$(echo "${attempts}" | tr -d '[:space:]')"
  if [[ "${attempts}" != "2" ]]; then
    echo "Expected capture to require exactly two provider attempts, got '${attempts}'" >&2
    return 1
  fi
  echo "Verified retryable capture timeout: provider attempts=${attempts}"
}

run_cycle() {
  local run="$1"
  echo "=== Payment settlement E2E run ${run}/${RUNS} ==="
  stop_services
  "${COMPOSE[@]}" --profile relays down --volumes --remove-orphans >/dev/null 2>&1 || true
  "${COMPOSE[@]}" up --detach --wait --wait-timeout 300 mysql zookeeper kafka connect

  start_service "${run}" restaurant-service 8083 ftgo_restaurant
  start_service "${run}" consumer-service 8082 ftgo_consumer
  start_service "${run}" kitchen-service 8084 ftgo_kitchen \
    FTGO_KITCHEN_ACCEPTANCE_TIMEOUT_SCAN_MS=100
  start_service "${run}" accounting-service 8085 ftgo_accounting \
    FTGO_ACCOUNTING_SETTLEMENT_TIMEOUT_ONCE_REQUEST_IDS=order-1-capture \
    FTGO_ACCOUNTING_SETTLEMENT_RECONCILIATION_INITIAL_DELAY_MS=300000
  start_service "${run}" order-service 8081 ftgo_order

  "${COMPOSE[@]}" --profile relays up --detach eventuate-cdc
  wait_for_url "Eventuate CDC" "http://localhost:18099/actuator/health"
  wait_for_url "Debezium Connect" "http://localhost:18083/connectors"
  register_kitchen_outbox_connector

  FTGO_PHASE02B_E2E_ENABLED=true \
  FTGO_E2E_JDBC_URL=jdbc:mysql://localhost:33306 \
  "${ROOT_DIR}/gradlew" --no-daemon :e2e-tests:test \
    --tests 'net.ftgo.e2e.PaymentSettlementTest' --rerun-tasks --stacktrace

  assert_capture_retried
  "${COMPOSE[@]}" --profile relays logs --no-color >"${LOG_ROOT}/run-${run}/compose.log" 2>&1
  stop_services
  "${COMPOSE[@]}" --profile relays down --volumes --remove-orphans
}

main() {
  require_command curl
  require_command docker
  require_command jq
  build_artifacts

  local run
  for run in $(seq 1 "${RUNS}"); do
    run_cycle "${run}"
  done
  echo "Payment settlement E2E passed ${RUNS} clean-state run(s)"
}

main "$@"
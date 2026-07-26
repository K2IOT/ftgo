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
    --runs) RUNS="$2"; shift 2 ;;
    *) echo "Usage: $0 [--runs <count>]" >&2; exit 2 ;;
  esac
done

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
  find "${ROOT_DIR}/$1/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | sort | head -n 1
}

wait_for_health() {
  local service="$1" url="$2" pid="$3" log_file="$4"
  for _ in $(seq 1 120); do
    if curl --silent --fail "${url}" | grep -q '"status":"UP"'; then
      echo "${service} is healthy"; return 0
    fi
    if ! kill -0 "${pid}" >/dev/null 2>&1; then
      echo "${service} exited before becoming healthy" >&2
      cat "${log_file}" >&2 || true
      return 1
    fi
    sleep 1
  done
  cat "${log_file}" >&2 || true
  return 1
}

start_service() {
  local run="$1" module="$2" port="$3" schema="$4"
  shift 4
  local log_dir="${LOG_ROOT}/run-${run}" log_file jar
  log_file="${log_dir}/${module}.log"
  jar="$(service_jar "${module}")"
  mkdir -p "${log_dir}"
  env JAVA_TOOL_OPTIONS="-Xms64m -Xmx384m" \
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
  local name="$1" url="$2"
  for _ in $(seq 1 120); do
    if curl --silent --fail "${url}" >/dev/null; then echo "${name} is ready"; return 0; fi
    sleep 1
  done
  echo "${name} did not become ready" >&2; return 1
}

register_kitchen_outbox_connector() {
  cat <<'JSON' | curl --silent --show-error --fail-with-body --request PUT \
    --header 'Content-Type: application/json' --data-binary @- \
    http://localhost:18083/connectors/ftgo-payment-settlement-kitchen/config >/dev/null
{
  "connector.class": "io.debezium.connector.mysql.MySqlConnector",
  "database.hostname": "mysql",
  "database.port": "3306",
  "database.user": "root",
  "database.password": "rootpassword",
  "database.server.id": "184022",
  "topic.prefix": "ftgo-payment-settlement-kitchen",
  "database.include.list": "ftgo_kitchen",
  "table.include.list": "ftgo_kitchen.outbox",
  "include.schema.changes": "false",
  "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
  "schema.history.internal.kafka.topic": "schema-history.ftgo-payment-settlement-kitchen",
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
  for _ in $(seq 1 60); do
    local status state failed
    status="$(curl --silent http://localhost:18083/connectors/ftgo-payment-settlement-kitchen/status)"
    state="$(jq -r '.connector.state // "UNKNOWN"' <<<"${status}")"
    failed="$(jq '[.tasks[]? | select(.state == "FAILED")] | length' <<<"${status}")"
    if [[ "${state}" == "RUNNING" && "${failed}" == "0" ]]; then return 0; fi
    if [[ "${state}" == "FAILED" || "${failed}" != "0" ]]; then echo "${status}" >&2; return 1; fi
    sleep 1
  done
  return 1
}

build_artifacts() {
  chmod +x "${ROOT_DIR}/gradlew"
  "${ROOT_DIR}/gradlew" --no-daemon clean \
    :order-service:bootJar :consumer-service:bootJar :restaurant-service:bootJar \
    :kitchen-service:bootJar :accounting-service:bootJar :e2e-tests:testClasses
}

run_cycle() {
  local run="$1"
  echo "=== Payment settlement E2E run ${run}/${RUNS} ==="
  stop_services
  "${COMPOSE[@]}" --profile relays down --volumes --remove-orphans >/dev/null 2>&1 || true
  "${COMPOSE[@]}" up --detach --wait --wait-timeout 300 mysql zookeeper kafka connect

  start_service "${run}" restaurant-service 8083 ftgo_restaurant
  start_service "${run}" consumer-service 8082 ftgo_consumer
  start_service "${run}" kitchen-service 8084 ftgo_kitchen FTGO_KITCHEN_ACCEPTANCE_TIMEOUT_SCAN_MS=100
  start_service "${run}" accounting-service 8085 ftgo_accounting \
    FTGO_ACCOUNTING_SANDBOX_PROVIDER_ADMIN_ENABLED=true \
    FTGO_ACCOUNTING_PAYMENT_WEBHOOK_SECRETS=sandbox=phase02b-e2e-secret \
    FTGO_ACCOUNTING_RECONCILIATION_INITIAL_DELAY_MS=500 \
    FTGO_ACCOUNTING_RECONCILIATION_FIXED_DELAY_MS=500
  start_service "${run}" order-service 8081 ftgo_order

  "${COMPOSE[@]}" --profile relays up --detach eventuate-cdc
  wait_for_url "Eventuate CDC" "http://localhost:18099/actuator/health"
  wait_for_url "Debezium Connect" "http://localhost:18083/connectors"
  register_kitchen_outbox_connector

  FTGO_E2E_ENABLED=true FTGO_E2E_JDBC_URL=jdbc:mysql://localhost:33306 \
    "${ROOT_DIR}/gradlew" --no-daemon :e2e-tests:test \
    --tests 'net.ftgo.e2e.PaymentSettlementTest' --rerun-tasks --stacktrace

  "${COMPOSE[@]}" --profile relays logs --no-color >"${LOG_ROOT}/run-${run}/compose.log" 2>&1
  stop_services
  "${COMPOSE[@]}" --profile relays down --volumes --remove-orphans
}

build_artifacts
for run in $(seq 1 "${RUNS}"); do run_cycle "${run}"; done
echo "Payment settlement E2E passed ${RUNS} clean-state run(s)"

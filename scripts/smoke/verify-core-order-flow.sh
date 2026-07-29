#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNS=2

while [[ $# -gt 0 ]]; do
  case "$1" in
    --runs)
      RUNS="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if ! [[ "${RUNS}" =~ ^[1-9][0-9]*$ ]]; then
  echo "--runs must be a positive integer" >&2
  exit 2
fi

COMPOSE=(docker compose -f "${ROOT_DIR}/deployment/docker-compose.infra.yml")
LOG_DIR="${ROOT_DIR}/build/phase-02-core-order-flow-e2e-logs"
PID_DIR="${ROOT_DIR}/build/phase-02-core-order-flow-e2e-pids"
SERVICE_NAMES=(restaurant-service consumer-service kitchen-service accounting-service order-service order-history-service api-gateway)
IDENTITY_ISSUER_URI="http://localhost:19000"
IDENTITY_JWK_SET_URI="${IDENTITY_ISSUER_URI}/.well-known/jwks.json"

mkdir -p "${LOG_DIR}" "${PID_DIR}"

stop_services() {
  local service
  for service in "${SERVICE_NAMES[@]}"; do
    local pid_file="${PID_DIR}/${service}.pid"
    if [[ -f "${pid_file}" ]]; then
      local pid
      pid="$(cat "${pid_file}")"
      if kill -0 "${pid}" >/dev/null 2>&1; then
        kill "${pid}" >/dev/null 2>&1 || true
        for _ in $(seq 1 30); do
          if ! kill -0 "${pid}" >/dev/null 2>&1; then
            break
          fi
          sleep 1
        done
        kill -9 "${pid}" >/dev/null 2>&1 || true
      fi
      rm -f "${pid_file}"
    fi
  done
}

cleanup() {
  local status=$?
  stop_services
  "${COMPOSE[@]}" --profile relays down --volumes --remove-orphans >/dev/null 2>&1 || true
  exit "${status}"
}
trap cleanup EXIT INT TERM

start_service() {
  local run="$1"
  local service="$2"
  local port="$3"
  local schema="$4"
  shift 4

  local jar="${ROOT_DIR}/${service}/build/libs/${service}-1.0.0-SNAPSHOT.jar"
  local log="${LOG_DIR}/run-${run}/${service}.log"
  mkdir -p "$(dirname "${log}")"

  env \
    SERVER_PORT="${port}" \
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:33306/${schema}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
    SPRING_DATASOURCE_USERNAME=ftgo_user \
    SPRING_DATASOURCE_PASSWORD=ftgo_password \
    SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:39092 \
    SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI="${IDENTITY_ISSUER_URI}" \
    SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI="${IDENTITY_JWK_SET_URI}" \
    FTGO_SECURITY_PUBLIC_AUDIENCE=ftgo-api \
    FTGO_SECURITY_INTERNAL_AUDIENCE=ftgo-internal \
    FTGO_SECURITY_TRUSTED_PROXY_CIDRS=127.0.0.1/32,::1/128 \
    FTGO_CORS_ALLOWED_ORIGINS= \
    FTGO_CORS_ALLOW_CREDENTIALS=false \
    "$@" \
    java -jar "${jar}" >"${log}" 2>&1 &
  local pid=$!
  echo "${pid}" >"${PID_DIR}/${service}.pid"
  wait_for_url "${service}" "http://localhost:${port}/actuator/health"
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

initialize_scylla() {
  "${COMPOSE[@]}" exec -T scylla cqlsh -e \
    "CREATE KEYSPACE IF NOT EXISTS ftgo_order_history WITH replication = {'class':'SimpleStrategy','replication_factor':1};"
}

register_kitchen_outbox_connector() {
  cat <<'JSON' | curl --silent --show-error --fail-with-body \
    --request PUT \
    --header 'Content-Type: application/json' \
    --data-binary @- \
    http://localhost:18083/connectors/ftgo-core-order-flow-kitchen/config >/dev/null
{
  "connector.class": "io.debezium.connector.mysql.MySqlConnector",
  "database.hostname": "mysql",
  "database.port": "3306",
  "database.user": "root",
  "database.password": "rootpassword",
  "database.server.id": "184021",
  "topic.prefix": "ftgo-core-order-flow-kitchen",
  "database.include.list": "ftgo_kitchen",
  "table.include.list": "ftgo_kitchen.outbox",
  "include.schema.changes": "false",
  "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
  "schema.history.internal.kafka.topic": "schema-history.ftgo-core-order-flow-kitchen",
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
    state="$(curl --silent http://localhost:18083/connectors/ftgo-core-order-flow-kitchen/status \
      | jq -r '.connector.state // "UNKNOWN"')"
    local failed
    failed="$(curl --silent http://localhost:18083/connectors/ftgo-core-order-flow-kitchen/status \
      | jq '[.tasks[]? | select(.state == "FAILED")] | length')"
    if [[ "${state}" == "RUNNING" && "${failed}" == "0" ]]; then
      echo "Kitchen outbox connector is running"
      return 0
    fi
    if [[ "${state}" == "FAILED" || "${failed}" != "0" ]]; then
      curl --silent http://localhost:18083/connectors/ftgo-core-order-flow-kitchen/status | jq . >&2
      return 1
    fi
    sleep 1
  done
  echo "Kitchen outbox connector did not become ready" >&2
  return 1
}

build_artifacts() {
  chmod +x "${ROOT_DIR}/gradlew"
  "${ROOT_DIR}/gradlew" --no-daemon clean \
    :api-gateway:bootJar \
    :order-service:bootJar \
    :consumer-service:bootJar \
    :restaurant-service:bootJar \
    :kitchen-service:bootJar \
    :accounting-service:bootJar \
    :order-history-service:bootJar \
    :e2e-tests:testClasses
}

run_cycle() {
  local run="$1"
  echo "=== Secured core order flow E2E run ${run}/${RUNS} ==="
  stop_services
  "${COMPOSE[@]}" --profile relays down --volumes --remove-orphans >/dev/null 2>&1 || true
  "${COMPOSE[@]}" up --detach --wait --wait-timeout 600 mysql redis scylla zookeeper kafka connect
  initialize_scylla

  start_service "${run}" restaurant-service 8083 ftgo_restaurant
  start_service "${run}" consumer-service 8082 ftgo_consumer
  start_service "${run}" kitchen-service 8084 ftgo_kitchen \
    FTGO_KITCHEN_ACCEPTANCE_TIMEOUT_SCAN_MS=100
  start_service "${run}" accounting-service 8085 ftgo_accounting \
    FTGO_ACCOUNTING_DECLINED_PAYMENT_TOKENS=tok_e2e_decline
  start_service "${run}" order-service 8081 ftgo_order
  start_service "${run}" order-history-service 8087 ftgo_order_history \
    FTGO_ORDER_HISTORY_PAGING_SECRET=phase-02-core-order-flow-e2e-paging-secret \
    SPRING_CASSANDRA_CONTACT_POINTS=localhost \
    SPRING_CASSANDRA_PORT=39042 \
    SPRING_CASSANDRA_KEYSPACE_NAME=ftgo_order_history \
    SPRING_CASSANDRA_LOCAL_DATACENTER=datacenter1 \
    SPRING_CASSANDRA_SCHEMA_ACTION=CREATE_IF_NOT_EXISTS
  start_service "${run}" api-gateway 8080 ftgo_gateway \
    REDIS_HOST=localhost \
    REDIS_PORT=36379 \
    ORDER_SERVICE_URL=http://localhost:8081 \
    CONSUMER_SERVICE_URL=http://localhost:8082 \
    RESTAURANT_SERVICE_URL=http://localhost:8083 \
    KITCHEN_SERVICE_URL=http://localhost:8084 \
    ACCOUNTING_SERVICE_URL=http://localhost:8085 \
    ORDER_HISTORY_SERVICE_URL=http://localhost:8087

  "${COMPOSE[@]}" --profile relays up --detach eventuate-cdc
  wait_for_url "Eventuate CDC" "http://localhost:18099/actuator/health"
  wait_for_url "Debezium Connect" "http://localhost:18083/connectors"
  register_kitchen_outbox_connector

  FTGO_E2E_ENABLED=true \
  FTGO_E2E_JDBC_URL=jdbc:mysql://localhost:33306 \
  "${ROOT_DIR}/gradlew" --no-daemon :e2e-tests:test \
    --tests 'net.ftgo.e2e.CoreOrderFlowTest' \
    --tests 'net.ftgo.e2e.OrderMutationIdempotencyE2ETest' \
    -Dftgo.e2e.gateway-url=http://localhost:8080 \
    -Dftgo.e2e.order-url=http://localhost:8081 \
    -Dftgo.e2e.consumer-url=http://localhost:8082 \
    -Dftgo.e2e.restaurant-url=http://localhost:8083 \
    -Dftgo.e2e.kitchen-url=http://localhost:8084 \
    -Dftgo.e2e.jdbc-url=jdbc:mysql://localhost:33306 \
    --stacktrace
}

build_artifacts
for run in $(seq 1 "${RUNS}"); do
  run_cycle "${run}"
done

echo "Secured core order flow E2E passed ${RUNS} clean-state runs"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/deployment/tests/docker-compose.core-order-flow.yml"
COMPOSE=(docker compose --project-name ftgo-phase03-e2e -f "${COMPOSE_FILE}")
LOG_ROOT="${ROOT_DIR}/distributed-consistency-e2e-logs"
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

service_jar() {
  local module="$1"
  find "${ROOT_DIR}/${module}/build/libs" -maxdepth 1 -type f \
    -name '*.jar' ! -name '*-plain.jar' | sort | head -n 1
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

wait_for_health() {
  local service="$1"
  local url="$2"
  local pid_file="$3"
  local log_file="$4"
  local attempt
  for attempt in $(seq 1 180); do
    if curl --silent --fail "${url}" | grep -q '"status":"UP"'; then
      echo "${service} is healthy"
      return 0
    fi
    if [[ -f "${pid_file}" ]]; then
      local pid
      pid="$(cat "${pid_file}")"
      if ! kill -0 "${pid}" >/dev/null 2>&1; then
        echo "${service} exited before becoming healthy" >&2
        cat "${log_file}" >&2 || true
        return 1
      fi
    fi
    sleep 1
  done
  echo "${service} did not become healthy" >&2
  cat "${log_file}" >&2 || true
  return 1
}

write_restart_script() {
  local script_file="$1"
  local pid_file="$2"
  local log_file="$3"
  shift 3

  {
    echo '#!/usr/bin/env bash'
    echo 'set -euo pipefail'
    printf 'PID_FILE=%q\n' "${pid_file}"
    printf 'LOG_FILE=%q\n' "${log_file}"
    echo 'if [[ -f "${PID_FILE}" ]]; then'
    echo '  old_pid="$(cat "${PID_FILE}")"'
    echo '  if kill -0 "${old_pid}" >/dev/null 2>&1; then kill "${old_pid}"; wait "${old_pid}" 2>/dev/null || true; fi'
    echo 'fi'
    printf 'nohup'
    printf ' %q' "$@"
    echo ' >>"${LOG_FILE}" 2>&1 &'
    echo 'echo $! >"${PID_FILE}"'
  } >"${script_file}"
  chmod +x "${script_file}"
}

start_sql_service() {
  local run="$1"
  local module="$2"
  local port="$3"
  local schema="$4"
  shift 4

  local run_dir="${LOG_ROOT}/run-${run}"
  local control_dir="${run_dir}/control"
  local log_file="${run_dir}/${module}.log"
  local pid_file="${control_dir}/${module}.pid"
  local restart_file="${control_dir}/${module}-restart.sh"
  local jar
  jar="$(service_jar "${module}")"
  mkdir -p "${control_dir}"

  local command=(
    env
    JAVA_TOOL_OPTIONS=-Xms64m\ -Xmx384m
    "SPRING_DATASOURCE_URL=jdbc:mysql://localhost:33306/${schema}?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false"
    SPRING_DATASOURCE_USERNAME=ftgo_user
    SPRING_DATASOURCE_PASSWORD=ftgo_password
    SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:29092
    EVENTUATELOCAL_KAFKA_BOOTSTRAP_SERVERS=localhost:29092
  )
  command+=("$@" java -jar "${jar}")
  write_restart_script "${restart_file}" "${pid_file}" "${log_file}" "${command[@]}"
  "${restart_file}"
  local pid
  pid="$(cat "${pid_file}")"
  SERVICE_PIDS+=("${pid}")
  wait_for_health "${module}" "http://localhost:${port}/actuator/health" "${pid_file}" "${log_file}"
}

start_order_history() {
  local run="$1"
  local run_dir="${LOG_ROOT}/run-${run}"
  local control_dir="${run_dir}/control"
  local module=order-history-service
  local log_file="${run_dir}/${module}.log"
  local pid_file="${control_dir}/${module}.pid"
  local restart_file="${control_dir}/${module}-restart.sh"
  local jar
  jar="$(service_jar "${module}")"
  mkdir -p "${control_dir}"

  local command=(
    env
    JAVA_TOOL_OPTIONS=-Xms64m\ -Xmx512m
    SPRING_CASSANDRA_CONTACT_POINTS=localhost
    SPRING_CASSANDRA_PORT=39042
    SPRING_CASSANDRA_KEYSPACE_NAME=ftgo_order_history
    SPRING_CASSANDRA_LOCAL_DATACENTER=datacenter1
    SPRING_CASSANDRA_SCHEMA_ACTION=CREATE_IF_NOT_EXISTS
    SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:29092
    java -jar "${jar}"
  )
  write_restart_script "${restart_file}" "${pid_file}" "${log_file}" "${command[@]}"
  "${restart_file}"
  local pid
  pid="$(cat "${pid_file}")"
  SERVICE_PIDS+=("${pid}")
  wait_for_health "${module}" "http://localhost:8087/actuator/health" "${pid_file}" "${log_file}"
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
  "${COMPOSE[@]}" exec -T scylla cqlsh scylla 9042 -e \
    "CREATE KEYSPACE IF NOT EXISTS ftgo_order_history WITH replication = {'class':'SimpleStrategy','replication_factor':1};"
}

register_outbox_connector() {
  local connector_name="$1"
  local schema="$2"
  local server_id="$3"
  local topic_prefix="$4"

  jq -n \
    --arg connector "${connector_name}" \
    --arg schema "${schema}" \
    --arg server_id "${server_id}" \
    --arg topic_prefix "${topic_prefix}" \
    '{
      "connector.class": "io.debezium.connector.mysql.MySqlConnector",
      "database.hostname": "mysql",
      "database.port": "3306",
      "database.user": "root",
      "database.password": "rootpassword",
      "database.server.id": $server_id,
      "topic.prefix": $topic_prefix,
      "database.include.list": $schema,
      "table.include.list": ($schema + ".outbox"),
      "include.schema.changes": "false",
      "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
      "schema.history.internal.kafka.topic": ("schema-history." + $connector),
      "key.converter": "org.apache.kafka.connect.storage.StringConverter",
      "value.converter": "org.apache.kafka.connect.json.JsonConverter",
      "value.converter.schemas.enable": "false",
      "transforms": "outbox",
      "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
      "transforms.outbox.table.field.event.id": "event_id",
      "transforms.outbox.table.field.event.key": "aggregate_id",
      "transforms.outbox.table.field.event.type": "event_type",
      "transforms.outbox.table.field.event.payload": "payload",
      "transforms.outbox.route.by.field": "destination",
      "transforms.outbox.route.topic.replacement": "${routedByValue}",
      "transforms.outbox.table.expand.json.payload": "true",
      "transforms.outbox.table.fields.additional.placement": "event_type:header:eventType"
    }' | curl --silent --show-error --fail-with-body \
      --request PUT \
      --header 'Content-Type: application/json' \
      --data-binary @- \
      "http://localhost:18083/connectors/${connector_name}/config" >/dev/null

  local attempt
  for attempt in $(seq 1 90); do
    local state failed
    state="$(curl --silent "http://localhost:18083/connectors/${connector_name}/status" | jq -r '.connector.state // "UNKNOWN"')"
    failed="$(curl --silent "http://localhost:18083/connectors/${connector_name}/status" | jq '[.tasks[]? | select(.state == "FAILED")] | length')"
    if [[ "${state}" == "RUNNING" && "${failed}" == "0" ]]; then
      echo "${connector_name} is running"
      return 0
    fi
    if [[ "${state}" == "FAILED" || "${failed}" != "0" ]]; then
      curl --silent "http://localhost:18083/connectors/${connector_name}/status" | jq . >&2
      return 1
    fi
    sleep 1
  done
  echo "${connector_name} did not become ready" >&2
  return 1
}

provision_failure_topics() {
  local topics=(
    net.ftgo.orderservice.domain.Order.DLT
    net.ftgo.deliveryservice.domain.Delivery.DLT
    net.ftgo.orderhistory.projection.DLT
  )
  local topic
  for topic in "${topics[@]}"; do
    "${COMPOSE[@]}" exec -T kafka kafka-topics \
      --bootstrap-server kafka:9092 \
      --create --if-not-exists \
      --topic "${topic}" \
      --partitions 3 \
      --replication-factor 1 >/dev/null
  done
}

build_artifacts() {
  chmod +x "${ROOT_DIR}/gradlew"
  "${ROOT_DIR}/gradlew" --no-daemon clean \
    :order-service:bootJar \
    :consumer-service:bootJar \
    :restaurant-service:bootJar \
    :kitchen-service:bootJar \
    :accounting-service:bootJar \
    :delivery-service:bootJar \
    :order-history-service:bootJar \
    :e2e-tests:testClasses
}

run_cycle() {
  local run="$1"
  local run_dir="${LOG_ROOT}/run-${run}"
  echo "=== Distributed consistency E2E run ${run}/${RUNS} ==="
  stop_services
  "${COMPOSE[@]}" --profile relays down --volumes --remove-orphans >/dev/null 2>&1 || true
  "${COMPOSE[@]}" up --detach --wait --wait-timeout 600 mysql scylla zookeeper kafka connect
  initialize_scylla

  start_sql_service "${run}" restaurant-service 8083 ftgo_restaurant
  start_sql_service "${run}" consumer-service 8082 ftgo_consumer
  start_sql_service "${run}" kitchen-service 8084 ftgo_kitchen FTGO_KITCHEN_ACCEPTANCE_TIMEOUT_SCAN_MS=100
  start_sql_service "${run}" accounting-service 8085 ftgo_accounting FTGO_ACCOUNTING_DECLINED_PAYMENT_TOKENS=tok_e2e_decline
  start_sql_service "${run}" order-service 8081 ftgo_order
  start_sql_service "${run}" delivery-service 8086 ftgo_delivery
  start_order_history "${run}"

  "${COMPOSE[@]}" --profile relays up --detach eventuate-cdc
  wait_for_url "Eventuate CDC" "http://localhost:18099/actuator/health"
  wait_for_url "Debezium Connect" "http://localhost:18083/connectors"
  register_outbox_connector phase03-order-outbox ftgo_order 184031 phase03-order
  register_outbox_connector phase03-kitchen-outbox ftgo_kitchen 184032 phase03-kitchen
  register_outbox_connector phase03-delivery-outbox ftgo_delivery 184033 phase03-delivery
  provision_failure_topics

  FTGO_PHASE03_E2E_ENABLED=true \
  FTGO_E2E_JDBC_URL=jdbc:mysql://localhost:33306 \
  FTGO_E2E_KAFKA_BOOTSTRAP_SERVERS=localhost:29092 \
  FTGO_E2E_CONNECT_URL=http://localhost:18083 \
  FTGO_E2E_ROOT_DIR="${ROOT_DIR}" \
  FTGO_E2E_RUN_DIR="${run_dir}" \
  "${ROOT_DIR}/gradlew" --no-daemon :e2e-tests:test \
    --tests 'net.ftgo.e2e.DistributedConsistencyTest' \
    --rerun-tasks --stacktrace

  "${COMPOSE[@]}" --profile relays logs --no-color >"${run_dir}/compose.log" 2>&1
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
  echo "Distributed consistency E2E passed ${RUNS} clean-state run(s)"
}

main "$@"

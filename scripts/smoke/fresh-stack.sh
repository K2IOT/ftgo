#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/deployment/tests/docker-compose.fresh-stack.yml"
COMPOSE=(docker compose --project-name ftgo-phase01-smoke -f "${COMPOSE_FILE}")
LOG_ROOT="${ROOT_DIR}/build/fresh-stack-smoke"
RUNS="${FRESH_STACK_RUNS:-2}"
CURRENT_PID=""

usage() {
  echo "Usage: $0 [--runs <count>]" >&2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --runs)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      RUNS="$2"
      shift 2
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if ! [[ "${RUNS}" =~ ^[1-9][0-9]*$ ]]; then
  echo "Run count must be a positive integer: ${RUNS}" >&2
  exit 2
fi

cleanup_process() {
  if [[ -n "${CURRENT_PID}" ]] && kill -0 "${CURRENT_PID}" >/dev/null 2>&1; then
    kill "${CURRENT_PID}" >/dev/null 2>&1 || true
    wait "${CURRENT_PID}" >/dev/null 2>&1 || true
  fi
  CURRENT_PID=""
}

cleanup_stack() {
  cleanup_process
  mkdir -p "${LOG_ROOT}"
  "${COMPOSE[@]}" logs --no-color >"${LOG_ROOT}/compose-last.log" 2>&1 || true
  "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}

trap cleanup_stack EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is missing: $1" >&2
    exit 1
  }
}

build_services() {
  chmod +x "${ROOT_DIR}/gradlew"
  "${ROOT_DIR}/gradlew" --no-daemon clean \
    :api-gateway:bootJar \
    :order-service:bootJar \
    :consumer-service:bootJar \
    :restaurant-service:bootJar \
    :kitchen-service:bootJar \
    :accounting-service:bootJar \
    :delivery-service:bootJar \
    :order-history-service:bootJar
}

service_jar() {
  local module="$1"
  local jar
  jar="$(find "${ROOT_DIR}/${module}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | sort | head -n 1)"
  if [[ -z "${jar}" ]]; then
    echo "No executable JAR found for ${module}" >&2
    return 1
  fi
  printf '%s\n' "${jar}"
}

boot_and_assert() {
  local run_number="$1"
  local module="$2"
  local port="$3"
  shift 3

  local log_dir="${LOG_ROOT}/run-${run_number}"
  local log_file="${log_dir}/${module}.log"
  local jar
  jar="$(service_jar "${module}")"
  mkdir -p "${log_dir}"

  echo "Starting ${module} from ${jar}"
  env \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx384m" \
    SPRING_KAFKA_BOOTSTRAP_SERVERS="localhost:19092" \
    EVENTUATELOCAL_KAFKA_BOOTSTRAP_SERVERS="localhost:19092" \
    "$@" \
    java -jar "${jar}" >"${log_file}" 2>&1 &
  CURRENT_PID=$!

  "${ROOT_DIR}/scripts/smoke/assert-service-health.sh" \
    "${module}" "http://localhost:${port}/actuator/health" "${CURRENT_PID}" "${log_file}"

  cleanup_process
}

wait_for_scylla_cql() {
  local attempt
  for attempt in $(seq 1 90); do
    if "${COMPOSE[@]}" exec -T scylla \
      cqlsh scylla 9042 -e 'SELECT release_version FROM system.local;' \
      >/dev/null 2>&1; then
      echo "Scylla CQL listener is ready"
      return 0
    fi
    echo "Waiting for Scylla CQL listener (${attempt}/90)"
    sleep 2
  done

  echo "Scylla CQL listener did not become ready" >&2
  "${COMPOSE[@]}" logs --no-color scylla >&2 || true
  return 1
}

prepare_dependencies() {
  "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  "${COMPOSE[@]}" config --quiet
  "${COMPOSE[@]}" up --detach --wait --wait-timeout 600
  wait_for_scylla_cql
  "${COMPOSE[@]}" exec -T scylla cqlsh scylla 9042 \
    <"${ROOT_DIR}/deployment/tests/fresh-stack/order-history-schema.cql"
}

run_service_startup_smoke() {
  local run_number="$1"
  prepare_dependencies

  boot_and_assert "${run_number}" order-service 8081 \
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/ftgo_order?createDatabaseIfNotExist=true" \
    SPRING_DATASOURCE_USERNAME=ftgo_user SPRING_DATASOURCE_PASSWORD=ftgo_password
  boot_and_assert "${run_number}" consumer-service 8082 \
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/ftgo_consumer?createDatabaseIfNotExist=true" \
    SPRING_DATASOURCE_USERNAME=ftgo_user SPRING_DATASOURCE_PASSWORD=ftgo_password
  boot_and_assert "${run_number}" restaurant-service 8083 \
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/ftgo_restaurant?createDatabaseIfNotExist=true" \
    SPRING_DATASOURCE_USERNAME=ftgo_user SPRING_DATASOURCE_PASSWORD=ftgo_password
  boot_and_assert "${run_number}" delivery-service 8086 \
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/ftgo_delivery?createDatabaseIfNotExist=true" \
    SPRING_DATASOURCE_USERNAME=ftgo_user SPRING_DATASOURCE_PASSWORD=ftgo_password
  boot_and_assert "${run_number}" kitchen-service 8084 \
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/ftgo_kitchen?createDatabaseIfNotExist=true" \
    SPRING_DATASOURCE_USERNAME=ftgo_user SPRING_DATASOURCE_PASSWORD=ftgo_password
  boot_and_assert "${run_number}" accounting-service 8085 \
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/ftgo_accounting?createDatabaseIfNotExist=true" \
    SPRING_DATASOURCE_USERNAME=ftgo_user SPRING_DATASOURCE_PASSWORD=ftgo_password
  boot_and_assert "${run_number}" order-history-service 8087 \
    SPRING_CASSANDRA_CONTACT_POINTS=localhost \
    SPRING_CASSANDRA_PORT=9042 \
    SPRING_CASSANDRA_LOCAL_DATACENTER=datacenter1 \
    SPRING_CASSANDRA_KEYSPACE_NAME=ftgo_order_history \
    FTGO_ORDER_HISTORY_PAGING_SECRET=test-order-history-paging-secret-32-bytes
  boot_and_assert "${run_number}" api-gateway 8080 \
    SPRING_DATA_REDIS_HOST=localhost \
    SPRING_DATA_REDIS_PORT=6379 \
    SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI="http://127.0.0.1:9/smoke-jwks"

  "${COMPOSE[@]}" logs --no-color >"${LOG_ROOT}/run-${run_number}/compose.log" 2>&1
  "${COMPOSE[@]}" down --volumes --remove-orphans
}

main() {
  require_command curl
  require_command docker
  require_command jq

  build_services
  rm -rf "${LOG_ROOT}"
  mkdir -p "${LOG_ROOT}"

  for run_number in $(seq 1 "${RUNS}"); do
    echo "=== Fresh-stack smoke run ${run_number}/${RUNS} ==="
    run_service_startup_smoke "${run_number}"
    "${ROOT_DIR}/scripts/smoke/assert-order-event.sh"
  done

  echo "Fresh-stack smoke passed ${RUNS} consecutive run(s)"
}

main "$@"

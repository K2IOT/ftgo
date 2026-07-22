#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOYMENT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.debezium-smoke.yml"
COMPOSE=(docker compose --project-name ftgo-debezium-smoke -f "${COMPOSE_FILE}")
TOPIC="net.ftgo.orderservice.domain.Order"
OUTPUT_FILE="${SCRIPT_DIR}/debezium-smoke-record.txt"
RUN_LOG="${SCRIPT_DIR}/debezium-smoke.log"

exec > >(tee "${RUN_LOG}") 2>&1

cleanup() {
  local exit_code=$?
  "${COMPOSE[@]}" logs --no-color >"${SCRIPT_DIR}/debezium-smoke-containers.log" 2>&1 || true
  "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  exit "${exit_code}"
}
trap cleanup EXIT

wait_for_mysql() {
  for attempt in $(seq 1 90); do
    if "${COMPOSE[@]}" exec -T mysql-order \
      mysqladmin ping -h localhost -uroot -prootpassword --silent >/dev/null 2>&1; then
      return 0
    fi
    echo "Waiting for MySQL (${attempt}/90)"
    sleep 2
  done
  echo "MySQL did not become ready" >&2
  return 1
}

prepare_database() {
  echo "Preparing outbox database"
  "${COMPOSE[@]}" exec -T mysql-order mysql -uroot -prootpassword <<'SQL'
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT
  ON *.* TO 'ftgo_user'@'%';
FLUSH PRIVILEGES;

USE ftgo_order;
CREATE TABLE outbox (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  aggregate_type VARCHAR(255) NOT NULL,
  aggregate_id VARCHAR(255) NOT NULL,
  event_type VARCHAR(255) NOT NULL,
  payload JSON NOT NULL,
  destination VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;
SQL
}

register_order_connector() {
  echo "Registering Debezium order connector"
  DEBEZIUM_CONNECT_URL="http://localhost:18083" \
  DEBEZIUM_CONNECTOR_GLOB="order-outbox.json" \
  DEBEZIUM_CONNECT_ATTEMPTS=120 \
  DEBEZIUM_STATUS_ATTEMPTS=90 \
    "${DEPLOYMENT_DIR}/configure-debezium.sh"
}

insert_outbox_event() {
  echo "Inserting deterministic outbox event"
  "${COMPOSE[@]}" exec -T mysql-order mysql -uftgo_user -pftgo_password ftgo_order <<SQL
INSERT INTO outbox
  (aggregate_type, aggregate_id, event_type, payload, destination, published)
VALUES
  ('Order', '42', 'OrderCreated',
   JSON_OBJECT('orderId', 42, 'state', 'APPROVAL_PENDING'),
   '${TOPIC}', FALSE);
SQL
}

wait_for_topic() {
  for attempt in $(seq 1 90); do
    if "${COMPOSE[@]}" exec -T kafka-1 \
      kafka-topics --bootstrap-server kafka-1:9092 --describe --topic "${TOPIC}" \
      >/dev/null 2>&1; then
      echo "Kafka topic is ready: ${TOPIC}"
      return 0
    fi
    echo "Waiting for Kafka topic ${TOPIC} (${attempt}/90)"
    sleep 2
  done
  echo "Kafka topic was not created by Debezium: ${TOPIC}" >&2
  return 1
}

assert_event_contract() {
  grep -q '42' "${OUTPUT_FILE}"
  grep -q 'eventType:OrderCreated' "${OUTPUT_FILE}"
  grep -Eq '"orderId"[[:space:]]*:[[:space:]]*42' "${OUTPUT_FILE}"
  grep -Eq '"state"[[:space:]]*:[[:space:]]*"APPROVAL_PENDING"' "${OUTPUT_FILE}"

  if grep -q 'destination' "${OUTPUT_FILE}"; then
    echo "Destination leaked into Kafka payload envelope" >&2
    return 1
  fi
}

consume_event() {
  wait_for_topic
  rm -f "${OUTPUT_FILE}"

  for attempt in $(seq 1 3); do
    echo "Consuming Debezium event (${attempt}/3)"
    set +e
    timeout 130 "${COMPOSE[@]}" exec -T kafka-1 \
      kafka-console-consumer \
        --bootstrap-server kafka-1:9092 \
        --topic "${TOPIC}" \
        --from-beginning \
        --max-messages 1 \
        --timeout-ms 120000 \
        --property print.key=true \
        --property print.headers=true \
        --property key.separator='|' \
        --property headers.separator=',' \
        >"${OUTPUT_FILE}"
    status=$?
    set -e

    if [[ ${status} -eq 0 ]] && [[ -s "${OUTPUT_FILE}" ]] && assert_event_contract; then
      cat "${OUTPUT_FILE}"
      return 0
    fi

    echo "No valid Debezium event received on attempt ${attempt}" >&2
    cat "${OUTPUT_FILE}" >&2 || true
    sleep 5
  done

  return 1
}

main() {
  command -v docker >/dev/null
  command -v curl >/dev/null
  command -v jq >/dev/null

  rm -f "${OUTPUT_FILE}" "${RUN_LOG}" "${SCRIPT_DIR}/debezium-smoke-containers.log"
  "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  "${COMPOSE[@]}" up --detach --wait --wait-timeout 300
  wait_for_mysql
  prepare_database
  register_order_connector
  insert_outbox_event
  consume_event
  echo "Debezium CDC smoke passed"
}

main "$@"

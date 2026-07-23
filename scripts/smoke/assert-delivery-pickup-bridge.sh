#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/deployment/tests/docker-compose.fresh-stack.yml"
COMPOSE=(docker compose --project-name ftgo-phase01-smoke -f "${COMPOSE_FILE}")
RESTAURANT_ID="424242"
MISSING_RESTAURANT_ID="424243"
BASE_URL="http://localhost:8083/internal/restaurants"

for command in curl jq docker; do
  command -v "${command}" >/dev/null 2>&1 || {
    echo "Required command is missing: ${command}" >&2
    exit 1
  }
done

"${COMPOSE[@]}" exec -T mysql mysql \
  --user=root \
  --password=rootpassword \
  ftgo_restaurant <<SQL
INSERT INTO restaurants (
  id,
  name,
  address_street,
  address_city,
  address_state,
  address_zip_code,
  opening_hours
) VALUES (
  ${RESTAURANT_ID},
  'Phase 01A Kitchen',
  '10 Kitchen Road',
  'Bangkok',
  'Bangkok',
  '10110',
  JSON_OBJECT()
)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  address_street = VALUES(address_street),
  address_city = VALUES(address_city),
  address_state = VALUES(address_state),
  address_zip_code = VALUES(address_zip_code),
  opening_hours = VALUES(opening_hours);
SQL

pickup_response="$(curl --fail --silent --show-error \
  "${BASE_URL}/${RESTAURANT_ID}/pickup-address")"

echo "${pickup_response}" | jq -e \
  --argjson restaurantId "${RESTAURANT_ID}" \
  '.restaurantId == $restaurantId
   and .address.street == "10 Kitchen Road"
   and .address.city == "Bangkok"
   and .address.state == "Bangkok"
   and .address.zipCode == "10110"' \
  >/dev/null

headers_file="$(mktemp)"
body_file="$(mktemp)"
cleanup() {
  rm -f "${headers_file}" "${body_file}"
}
trap cleanup EXIT

status="$(curl --silent --show-error \
  --dump-header "${headers_file}" \
  --output "${body_file}" \
  --write-out '%{http_code}' \
  "${BASE_URL}/${MISSING_RESTAURANT_ID}/pickup-address")"

if [[ "${status}" != "404" ]]; then
  echo "Expected missing restaurant status 404, got ${status}" >&2
  cat "${body_file}" >&2
  exit 1
fi

if ! grep -Eqi '^content-type:[[:space:]]*application/problem\+json' "${headers_file}"; then
  echo "Expected application/problem+json response" >&2
  cat "${headers_file}" >&2
  exit 1
fi

jq -e \
  '.status == 404
   and .title == "Restaurant not found"
   and .code == "RESTAURANT_NOT_FOUND"' \
  "${body_file}" >/dev/null

echo "Delivery pickup bridge real-service contract passed"

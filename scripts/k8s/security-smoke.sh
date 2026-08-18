#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <namespace>" >&2
  exit 2
fi

NAMESPACE="$1"
PROBE=security-probe

cleanup() {
  kubectl -n "${NAMESPACE}" delete pod "${PROBE}" --ignore-not-found --wait=false >/dev/null 2>&1 || true
}
trap cleanup EXIT

command -v kubectl >/dev/null 2>&1 || {
  echo "kubectl is required" >&2
  exit 1
}

for deployment in api-gateway order-service consumer-service restaurant-service; do
  kubectl -n "${NAMESPACE}" rollout status "deployment/${deployment}" --timeout=180s
done

kubectl -n "${NAMESPACE}" run "${PROBE}" \
  --image=curlimages/curl:8.7.1 \
  --labels=app.kubernetes.io/name=security-probe \
  --restart=Never \
  --command -- sleep 600
kubectl -n "${NAMESPACE}" wait --for=condition=Ready "pod/${PROBE}" --timeout=120s

expect_denied() {
  local description="$1"
  shift
  if kubectl -n "${NAMESPACE}" exec "${PROBE}" -- "$@" >/dev/null 2>&1; then
    echo "Expected denied path succeeded: ${description}" >&2
    return 1
  fi
  echo "Denied as expected: ${description}"
}

expect_allowed() {
  local description="$1"
  shift
  if ! "$@" >/dev/null; then
    echo "Expected allowed path failed: ${description}" >&2
    return 1
  fi
  echo "Allowed as expected: ${description}"
}

expect_denied "probe to mysql" \
  curl --connect-timeout 3 --fail http://mysql.ftgo-platform.svc.cluster.local:3306
expect_denied "probe to order-service" \
  curl --connect-timeout 3 --fail http://order-service:8080/actuator/health/liveness

GATEWAY_POD="$(kubectl -n "${NAMESPACE}" get pod \
  -l app.kubernetes.io/name=api-gateway \
  -o jsonpath='{.items[0].metadata.name}')"
expect_allowed "api-gateway to order-service" \
  kubectl -n "${NAMESPACE}" exec "${GATEWAY_POD}" -- \
  curl --connect-timeout 5 --fail --silent \
  http://order-service:8080/actuator/health/liveness

echo "Security smoke passed in namespace ${NAMESPACE}"

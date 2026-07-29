#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
KUBECTL_IMAGE="registry.k8s.io/kubectl:v1.29.2"
KUBECONFORM_IMAGE="ghcr.io/yannh/kubeconform:v0.6.7"
VALID_ENVIRONMENTS=(dev staging production)

usage() {
  echo "Usage: $0 <dev|staging|production|all>" >&2
}

if [[ $# -ne 1 ]]; then
  usage
  exit 2
fi

requested="$1"
case "${requested}" in
  dev|staging|production)
    environments=("${requested}")
    ;;
  all)
    environments=("${VALID_ENVIRONMENTS[@]}")
    ;;
  *)
    usage
    exit 2
    ;;
esac

command -v docker >/dev/null 2>&1 || {
  echo "docker is required for pinned kustomize and kubeconform validation" >&2
  exit 1
}

python -m unittest deployment.tests.test_phase05_kubernetes_contract -v

for environment in "${environments[@]}"; do
  overlay="deployment/kubernetes/overlays/${environment}"
  rendered="$(mktemp)"
  trap 'rm -f "${rendered}"' EXIT

  echo "Rendering ${environment} overlay with kubectl kustomize"
  docker run --rm \
    --volume "${ROOT_DIR}:/workspace:ro" \
    --workdir /workspace \
    "${KUBECTL_IMAGE}" kustomize "${overlay}" >"${rendered}"

  test -s "${rendered}"
  if grep -Eq 'image: .*:latest([[:space:]]|$)' "${rendered}"; then
    echo "Rendered ${environment} overlay contains a latest image tag" >&2
    exit 1
  fi
  if grep -Eq '^(data|stringData):' "${rendered}"; then
    echo "Rendered ${environment} overlay contains inline Secret data" >&2
    exit 1
  fi

  echo "Schema validating ${environment} overlay with kubeconform"
  docker run --rm --interactive \
    "${KUBECONFORM_IMAGE}" \
    -strict \
    -ignore-missing-schemas \
    -summary <"${rendered}"

  rm -f "${rendered}"
  trap - EXIT
done

echo "Validated Kubernetes overlay(s): ${environments[*]}"

#!/usr/bin/env bash
set -euo pipefail

EXPECTED_SHA256="d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd"
DIAGNOSTIC_PATH="build/wrapper-verification.txt"
mkdir -p "$(dirname "${DIAGNOSTIC_PATH}")"

wrapper_path="$(bash scripts/ci/bootstrap-gradle-wrapper.sh)"
actual_sha256="$(sha256sum "${wrapper_path}" | awk '{print $1}')"

{
  echo "wrapper_path=${wrapper_path}"
  echo "expected_sha256=${EXPECTED_SHA256}"
  echo "actual_sha256=${actual_sha256}"
  echo "size_bytes=$(stat -c %s "${wrapper_path}")"
  [[ "${actual_sha256}" == "${EXPECTED_SHA256}" ]] || {
    echo "status=checksum_mismatch"
    exit 1
  }
  echo "status=verified"
} | tee "${DIAGNOSTIC_PATH}"

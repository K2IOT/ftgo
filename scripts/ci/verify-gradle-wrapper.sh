#!/usr/bin/env bash
set -euo pipefail

EXPECTED_SHA256="d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd"
WRAPPER_PATH="gradle/wrapper/gradle-wrapper.jar"
DIAGNOSTIC_PATH="build/wrapper-verification.txt"

mkdir -p "$(dirname "${DIAGNOSTIC_PATH}")"

{
  echo "wrapper_path=${WRAPPER_PATH}"
  if [[ ! -f "${WRAPPER_PATH}" ]]; then
    echo "status=missing"
    exit 1
  fi

  actual_sha256="$(sha256sum "${WRAPPER_PATH}" | awk '{print $1}')"
  echo "expected_sha256=${EXPECTED_SHA256}"
  echo "actual_sha256=${actual_sha256}"
  echo "size_bytes=$(stat -c %s "${WRAPPER_PATH}")"

  if [[ "${actual_sha256}" != "${EXPECTED_SHA256}" ]]; then
    echo "status=checksum_mismatch"
    exit 1
  fi

  echo "status=verified"
} | tee "${DIAGNOSTIC_PATH}"

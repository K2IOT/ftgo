#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="8.5"
EXPECTED_SHA256="d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHECKED_IN_JAR="${ROOT_DIR}/gradle/wrapper/gradle-wrapper.jar"
CACHE_ROOT="${GRADLE_USER_HOME:-${HOME}/.gradle}/wrapper/bootstrap"
CACHE_JAR="${CACHE_ROOT}/gradle-${GRADLE_VERSION}-wrapper.jar"
DOWNLOAD_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-wrapper.jar"

sha256_of() {
  sha256sum "$1" | awk '{print $1}'
}

is_valid() {
  [[ -f "$1" ]] && [[ "$(sha256_of "$1")" == "${EXPECTED_SHA256}" ]]
}

if [[ -f "${CHECKED_IN_JAR}" ]] && ! is_valid "${CHECKED_IN_JAR}"; then
  echo "Refusing invalid checked-in Gradle wrapper JAR: ${CHECKED_IN_JAR}" >&2
  echo "Expected SHA-256: ${EXPECTED_SHA256}" >&2
  echo "Actual SHA-256:   $(sha256_of "${CHECKED_IN_JAR}")" >&2
  exit 1
fi

if is_valid "${CHECKED_IN_JAR}"; then
  printf '%s\n' "${CHECKED_IN_JAR}"
  exit 0
fi

mkdir -p "${CACHE_ROOT}"
if ! is_valid "${CACHE_JAR}"; then
  tmp="${CACHE_JAR}.tmp.$$"
  trap 'rm -f "${tmp:-}"' EXIT
  echo "Downloading verified Gradle ${GRADLE_VERSION} wrapper JAR" >&2
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --silent --show-error "${DOWNLOAD_URL}" --output "${tmp}"
  elif command -v wget >/dev/null 2>&1; then
    wget --quiet "${DOWNLOAD_URL}" --output-document="${tmp}"
  else
    echo "curl or wget is required to bootstrap the Gradle wrapper" >&2
    exit 1
  fi

  actual="$(sha256_of "${tmp}")"
  if [[ "${actual}" != "${EXPECTED_SHA256}" ]]; then
    echo "Downloaded Gradle wrapper checksum mismatch" >&2
    echo "Expected: ${EXPECTED_SHA256}" >&2
    echo "Actual:   ${actual}" >&2
    exit 1
  fi
  mv "${tmp}" "${CACHE_JAR}"
  trap - EXIT
fi

printf '%s\n' "${CACHE_JAR}"

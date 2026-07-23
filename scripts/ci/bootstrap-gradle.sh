#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="8.5"
EXPECTED_SHA256="9d926787066a081739e8200858338b4a69e837c3a821a33aca9db09dd4a41026"
DISTRIBUTION_NAME="gradle-${GRADLE_VERSION}-bin.zip"
DISTRIBUTION_URL="https://services.gradle.org/distributions/${DISTRIBUTION_NAME}"
CACHE_ROOT="${GRADLE_USER_HOME:-${HOME}/.gradle}/wrapper/verified-distributions"
ARCHIVE_PATH="${CACHE_ROOT}/${DISTRIBUTION_NAME}"
INSTALL_ROOT="${CACHE_ROOT}/gradle-${GRADLE_VERSION}-bin"
GRADLE_BIN="${INSTALL_ROOT}/gradle-${GRADLE_VERSION}/bin/gradle"

sha256_of() {
  sha256sum "$1" | awk '{print $1}'
}

archive_is_valid() {
  [[ -f "${ARCHIVE_PATH}" ]] && [[ "$(sha256_of "${ARCHIVE_PATH}")" == "${EXPECTED_SHA256}" ]]
}

mkdir -p "${CACHE_ROOT}"

if ! archive_is_valid; then
  rm -f "${ARCHIVE_PATH}"
  tmp_archive="${ARCHIVE_PATH}.tmp.$$"
  trap 'rm -f "${tmp_archive:-}"' EXIT

  echo "Downloading Gradle ${GRADLE_VERSION} binary distribution" >&2
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --silent --show-error \
      "${DISTRIBUTION_URL}" --output "${tmp_archive}"
  elif command -v wget >/dev/null 2>&1; then
    wget --quiet "${DISTRIBUTION_URL}" --output-document="${tmp_archive}"
  else
    echo "curl or wget is required to bootstrap Gradle" >&2
    exit 1
  fi

  actual_sha256="$(sha256_of "${tmp_archive}")"
  if [[ "${actual_sha256}" != "${EXPECTED_SHA256}" ]]; then
    echo "Gradle distribution checksum mismatch" >&2
    echo "Expected: ${EXPECTED_SHA256}" >&2
    echo "Actual:   ${actual_sha256}" >&2
    exit 1
  fi

  mv "${tmp_archive}" "${ARCHIVE_PATH}"
  trap - EXIT
fi

if [[ ! -x "${GRADLE_BIN}" ]]; then
  command -v unzip >/dev/null 2>&1 || {
    echo "unzip is required to install the verified Gradle distribution" >&2
    exit 1
  }

  tmp_install="${INSTALL_ROOT}.tmp.$$"
  rm -rf "${tmp_install}"
  mkdir -p "${tmp_install}"
  unzip -q "${ARCHIVE_PATH}" -d "${tmp_install}"

  if [[ ! -x "${tmp_install}/gradle-${GRADLE_VERSION}/bin/gradle" ]]; then
    echo "Verified Gradle archive does not contain the expected executable" >&2
    rm -rf "${tmp_install}"
    exit 1
  fi

  rm -rf "${INSTALL_ROOT}"
  mv "${tmp_install}" "${INSTALL_ROOT}"
fi

printf '%s\n' "${GRADLE_BIN}"

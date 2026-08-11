#!/usr/bin/env bash
set -euo pipefail

EXPECTED_SHA256="bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531"
DIAGNOSTIC_PATH="build/gradle-bootstrap-verification.txt"
mkdir -p "$(dirname "${DIAGNOSTIC_PATH}")"

gradle_bin="$(bash scripts/ci/bootstrap-gradle.sh)"
gradle_home="$(cd "$(dirname "${gradle_bin}")/.." && pwd)"
archive="${GRADLE_USER_HOME:-${HOME}/.gradle}/wrapper/verified-distributions/gradle-8.14.3-bin.zip"
actual_sha256="$(sha256sum "${archive}" | awk '{print $1}')"
version_output="$(${gradle_bin} --version)"

{
  echo "distribution=gradle-8.14.3-bin.zip"
  echo "gradle_bin=${gradle_bin}"
  echo "gradle_home=${gradle_home}"
  echo "expected_sha256=${EXPECTED_SHA256}"
  echo "actual_sha256=${actual_sha256}"
  echo "size_bytes=$(stat -c %s "${archive}")"
  if [[ "${actual_sha256}" != "${EXPECTED_SHA256}" ]]; then
    echo "status=checksum_mismatch"
    exit 1
  fi
  if ! grep -q '^Gradle 8\.14\.3$' <<<"${version_output}"; then
    echo "status=version_mismatch"
    printf '%s\n' "${version_output}"
    exit 1
  fi
  echo "status=verified"
} | tee "${DIAGNOSTIC_PATH}"

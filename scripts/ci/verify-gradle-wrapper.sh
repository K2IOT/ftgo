#!/usr/bin/env bash
set -euo pipefail

EXPECTED_SHA256="d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd"

mapfile -t wrappers < <(find . -type f -name gradle-wrapper.jar -print | sort)
if [[ ${#wrappers[@]} -ne 1 ]]; then
  printf 'Expected exactly one gradle-wrapper.jar, found %d:\n' "${#wrappers[@]}" >&2
  printf '  %s\n' "${wrappers[@]:-<none>}" >&2
  exit 1
fi

wrapper="${wrappers[0]}"
actual_sha256="$(sha256sum "${wrapper}" | awk '{print $1}')"
if [[ "${actual_sha256}" != "${EXPECTED_SHA256}" ]]; then
  echo "Unexpected Gradle wrapper checksum for ${wrapper}" >&2
  echo "Expected: ${EXPECTED_SHA256}" >&2
  echo "Actual:   ${actual_sha256}" >&2
  exit 1
fi

echo "Validated Gradle 8.5 wrapper: ${wrapper} (${actual_sha256})"

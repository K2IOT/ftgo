#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

docker run --rm \
  -v "$PWD:/repo" \
  zricethezav/gitleaks:v8.24.3 \
  detect --source /repo --no-banner --redact

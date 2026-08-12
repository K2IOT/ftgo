#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

run_insight() {
  local label="$1"
  local project="$2"
  local configuration="$3"
  local dependency="$4"

  printf '\n===== %s =====\n' "$label"
  ./gradlew "${project}:dependencyInsight" \
    --dependency "$dependency" \
    --configuration "$configuration" \
    --no-daemon
}

run_insight "spring-security" ":api-gateway" "runtimeClasspath" "spring-security"
run_insight "netty" ":api-gateway" "runtimeClasspath" "netty"
run_insight "jackson" ":order-service" "runtimeClasspath" "jackson"
run_insight "kafka" ":delivery-service" "runtimeClasspath" "kafka"
run_insight "mysql" ":order-service" "runtimeClasspath" "mysql"
run_insight "legacy-mysql-driver" ":order-service" "runtimeClasspath" "mysql-connector-java"
run_insight "postgresql-driver" ":order-service" "runtimeClasspath" "postgresql"
run_insight "mssql-driver" ":order-service" "runtimeClasspath" "mssql-jdbc"
run_insight "testcontainers" ":accounting-service" "testRuntimeClasspath" "testcontainers"

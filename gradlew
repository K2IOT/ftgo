#!/bin/sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
GRADLE_BIN=$(bash "${APP_HOME}/scripts/ci/bootstrap-gradle.sh")
exec "${GRADLE_BIN}" "$@"

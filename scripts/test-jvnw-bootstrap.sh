#!/usr/bin/env bash

set -eu

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." >/dev/null 2>&1 && pwd)"
JVNW="$ROOT_DIR/jvnw"

fail() {
  printf 'jvnw bootstrap test failed: %s\n' "$1" >&2
  exit 1
}

bash -n "$JVNW"

help_output="$("$JVNW" --help)"
[[ "$help_output" == *"./jvnw doctor"* ]] || fail "help does not list doctor"

doctor_output="$("$JVNW" doctor)" || {
  printf '%s\n' "$doctor_output" >&2
  fail "doctor rejected the configured CI environment"
}
[[ "$doctor_output" == *"Java runtime"* ]] || fail "doctor omitted Java runtime"
[[ "$doctor_output" == *"Java compiler"* ]] || fail "doctor omitted Java compiler"
[[ "$doctor_output" == *"Gradle wrapper"* ]] || fail "doctor omitted Gradle wrapper"
[[ "$doctor_output" == *"Bash timeout"* ]] || fail "doctor omitted Bash compatibility"

failure_output="$(JVNW_GRADLEW_PATH="$SCRIPT_DIR/test-fixtures/failing-gradle" "$JVNW" editor 2>&1)" &&
  fail "simulated editor failure unexpectedly succeeded"
[[ "$failure_output" == *"Automatic environment check"* ]] || fail "editor failure did not run automatic diagnostics"
[[ "$failure_output" == *"Simulated Gradle startup failure"* ]] || fail "editor failure omitted relevant Gradle output"

printf 'jvnw bootstrap tests passed with %s\n' "$BASH_VERSION"

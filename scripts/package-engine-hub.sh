#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

task=":hub:packageEngineHubRelease"
pattern="jvn-engine-hub-*.jar"
exclude_lite=true
if [[ "${1:-}" == "--lite" ]]; then
  task=":hub:packageEngineHubLiteJar"
  pattern="jvn-engine-hub-lite-*.jar"
  exclude_lite=false
fi

./gradlew "$task"

printf '\nPackaged Engine Hub jar:\n'
for jar in "$ROOT_DIR/build/distributions"/$pattern; do
  if [[ "$exclude_lite" == true && "$(basename "$jar")" == jvn-engine-hub-lite-* ]]; then
    continue
  fi
  printf '  %s\n' "$jar"
done

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

task=":hub:packageEngineHubJar"
pattern="jvn-engine-hub-*.jar"
exclude_cached=true
if [[ "${1:-}" == "--with-cache" || "${1:-}" == "--cached" ]]; then
  task=":hub:packageEngineHubJarWithCache"
  pattern="jvn-engine-hub-cached-*.jar"
  exclude_cached=false
fi

./gradlew "$task"

printf '\nPackaged Engine Hub jar:\n'
for jar in "$ROOT_DIR/build/distributions"/$pattern; do
  if [[ "$exclude_cached" == true && "$(basename "$jar")" == jvn-engine-hub-cached-* ]]; then
    continue
  fi
  printf '  %s\n' "$jar"
done

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew :hub:packageEngineHubJar

printf '\nPackaged Engine Hub jar:\n  %s\n' "$ROOT_DIR/build/distributions/jvn-engine-hub-"*.jar

#!/usr/bin/env bash
set -eu

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$repo_root"

if ! command -v java >/dev/null 2>&1; then
  echo "Java 21 is required. Run ./jvnw doctor for setup guidance." >&2
  exit 1
fi
if ! command -v node >/dev/null 2>&1; then
  echo "Node.js is required for strict documentation validation." >&2
  exit 1
fi

echo "[1/5] Verifying contributor bootstrap"
./scripts/test-jvnw-bootstrap.sh

echo "[2/5] Running full Gradle verification"
./gradlew ci

echo "[3/5] Building public Plugin API documentation"
./gradlew :plugin-api:javadoc

echo "[4/5] Linting documentation"
node scripts/doc-lint.mjs --strict

echo "[5/5] Checking patch whitespace"
git diff --check

echo "Contributor verification passed."

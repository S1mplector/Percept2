#!/usr/bin/env bash
set -euo pipefail

NATIVE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ "$OS" == "darwin" ]]; then
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
      JAVA_HOME="$(/usr/libexec/java_home)"
    fi
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  if command -v javac >/dev/null 2>&1; then
    JAVAC_BIN="$(command -v javac)"
    JAVA_HOME="$(cd "$(dirname "$JAVAC_BIN")/.." && pwd)"
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME is not set and javac not found." >&2
  exit 1
fi

CPP="${CXX:-clang++}"
OUT_NAME="libjvn_math.so"
INCLUDE_OS="linux"
if [[ "$OS" == "darwin" ]]; then
  OUT_NAME="libjvn_math.dylib"
  INCLUDE_OS="darwin"
elif [[ "$OS" == msys* || "$OS" == mingw* || "$OS" == cygwin* ]]; then
  OUT_NAME="jvn_math.dll"
  INCLUDE_OS="win32"
  CPP="${CXX:-g++}"
fi

OUT_PATH="${NATIVE_DIR}/${OUT_NAME}"

"$CPP" -std=c++17 -shared -fPIC \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/$INCLUDE_OS" \
  -o "$OUT_PATH" "$NATIVE_DIR/jvn_math.cpp"

echo "Built $OUT_PATH"

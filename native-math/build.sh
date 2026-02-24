#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"
BUILD_TYPE="Release"
BUILD_TESTS="OFF"
BUILD_JNI="ON"
CLEAN="false"

usage() {
  cat <<'EOF'
Usage: native-math/build.sh [options]

Options:
  --debug             Build Debug configuration
  --release           Build Release configuration (default)
  --clean             Remove build directory before configure/build
  --with-tests        Build native test executables
  --without-jni       Build simjot_native only (skip jvn_native_bridge)
  -h, --help          Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --debug) BUILD_TYPE="Debug"; shift ;;
    --release) BUILD_TYPE="Release"; shift ;;
    --clean) CLEAN="true"; shift ;;
    --with-tests) BUILD_TESTS="ON"; shift ;;
    --without-jni) BUILD_JNI="OFF"; shift ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

OS="$(uname -s)"
if [[ "${OS}" != "Darwin" && "${OS}" != "Linux" ]]; then
  echo "Unsupported OS for this script: ${OS}" >&2
  echo "Use native-math/build.ps1 on Windows." >&2
  exit 1
fi

if [[ "${BUILD_JNI}" == "ON" && -z "${JAVA_HOME:-}" ]]; then
  if [[ "${OS}" == "Darwin" ]]; then
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
      export JAVA_HOME="$("/usr/libexec/java_home" 2>/dev/null || true)"
    fi
  elif command -v javac >/dev/null 2>&1; then
    JAVAC_PATH="$(readlink -f "$(command -v javac)" || true)"
    if [[ -n "${JAVAC_PATH}" ]]; then
      export JAVA_HOME="$(cd "$(dirname "${JAVAC_PATH}")/.." && pwd)"
    fi
  fi
fi

if [[ "${CLEAN}" == "true" ]]; then
  rm -rf "${BUILD_DIR}"
fi

cmake -S "${SCRIPT_DIR}" -B "${BUILD_DIR}" \
  -DCMAKE_BUILD_TYPE="${BUILD_TYPE}" \
  -DSIMJOT_NATIVE_BUILD_TESTS="${BUILD_TESTS}" \
  -DJVN_BUILD_JNI_BRIDGE="${BUILD_JNI}" \
  -DJAVA_HOME="${JAVA_HOME:-}"

cmake --build "${BUILD_DIR}" --config "${BUILD_TYPE}" --parallel

echo
echo "Build complete."
echo "Expected outputs:"
if [[ "${OS}" == "Darwin" ]]; then
  echo "  ${BUILD_DIR}/libsimjot_native.dylib"
  echo "  ${BUILD_DIR}/libjvn_native_bridge.dylib (if JNI enabled)"
else
  echo "  ${BUILD_DIR}/libsimjot_native.so"
  echo "  ${BUILD_DIR}/libjvn_native_bridge.so (if JNI enabled)"
fi

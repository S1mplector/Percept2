#!/usr/bin/env bash
# Cache-first launcher for the JVN Editor, Launcher, and Runtime.
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." >/dev/null 2>&1 && pwd)"
APP="${1:-}"
[[ $# -gt 0 ]] && shift
MODE="${JVN_APP_LAUNCH_MODE:-auto}"
FORCE_REBUILD=0
SAFE_MODE=0
DEVELOPER_MODE=0
declare -a APP_ARGS=()

usage() {
  cat <<'EOF'
Usage: scripts/launch-app.sh editor|launcher|runtime [options] [-- app arguments]

  --direct            Never invoke Gradle; fail if the launch cache is stale.
  --gradle            Always use the original Gradle run task.
  --rebuild           Refresh the compiled launch cache before starting.
  --safe-mode         Forward JVN safe-mode properties.
  --developer-mode    Forward JVN developer-mode properties.

Environment: JVN_APP_LAUNCH_MODE=auto|direct|gradle
             JVN_APP_JAVA_OPTS="<additional JVM options>"
             JVN_DISABLE_GLX_FALLBACK=1 disables Linux Mesa GLX recovery
EOF
}

case "$APP" in
  editor)
    CACHE_NAME="editor"; PREPARE_TASK=":editor:prepareFastLaunch"
    GRADLE_TASK=":editor:run"; MAIN_CLASS="com.jvn.editor.EditorApp" ;;
  launcher)
    CACHE_NAME="editor"; PREPARE_TASK=":editor:prepareFastLaunch"
    GRADLE_TASK=":editor:runLauncher"; MAIN_CLASS="com.jvn.editor.JvnLauncherApp" ;;
  runtime)
    CACHE_NAME="runtime"; PREPARE_TASK=":runtime:prepareFastLaunch"
    GRADLE_TASK=":runtime:run"; MAIN_CLASS="com.jvn.runtime.JvnApp" ;;
  -h|--help|"") usage; exit 0 ;;
  *) echo "[jvn] error: unknown application '$APP'." >&2; usage >&2; exit 2 ;;
esac

while (($#)); do
  case "$1" in
    --direct) MODE=direct ;;
    --gradle) MODE=gradle ;;
    --rebuild) FORCE_REBUILD=1 ;;
    --safe-mode) SAFE_MODE=1 ;;
    --developer-mode) DEVELOPER_MODE=1 ;;
    --) shift; APP_ARGS+=("$@"); break ;;
    *) APP_ARGS+=("$1") ;;
  esac
  shift
done

case "$MODE" in
  auto|direct|gradle) ;;
  *) echo "[jvn] error: invalid JVN_APP_LAUNCH_MODE '$MODE'." >&2; exit 2 ;;
esac

CACHE_DIR="$ROOT_DIR/build/fast-launch/$CACHE_NAME"
CLASSPATH_FILE="$CACHE_DIR/classpath.txt"
MODULE_PATH_FILE="$CACHE_DIR/module-path.txt"
VERSION_FILE="$CACHE_DIR/version.txt"
STAMP_FILE="$CACHE_DIR/launch.stamp"

declare -a MODE_PROPS=()
if [[ "$SAFE_MODE" -eq 1 ]]; then
  MODE_PROPS+=("-Djvn.hub.safeMode=true" "-Djvn.editor.safeMode=true" "-Djvn.launcher.safeMode=true" "-Djvn.help.safeMode=true")
fi
if [[ "$DEVELOPER_MODE" -eq 1 ]]; then
  MODE_PROPS+=("-Djvn.hub.developerMode=true" "-Djvn.editor.developerMode=true" "-Djvn.launcher.developerMode=true" "-Djvn.help.developerMode=true")
fi
# macOS Bash 3.2 treats an expanded empty array as unset under nounset.
set +u

run_gradle() {
  local task="$1"
  local workers=2
  exec "$ROOT_DIR/gradlew" --console=plain --configuration-cache --max-workers="$workers" -p "$ROOT_DIR" \
    "${MODE_PROPS[@]}" "$task"
}

requested_graphics_mode() {
  if [[ -n "${JVN_GRAPHICS_MODE:-}" ]]; then
    printf '%s' "$JVN_GRAPHICS_MODE"
    return
  fi
  local preferences_file="${HOME:-}/.jvn-editor/editor-preferences.properties"
  if [[ -r "$preferences_file" ]]; then
    local stored_mode
    stored_mode="$(sed -n 's/^graphics\\.mode=//p' "$preferences_file" | tail -n 1)"
    if [[ -n "$stored_mode" ]]; then
      printf '%s' "$stored_mode"
      return
    fi
  fi
  printf 'auto'
}

run_glx_probe() {
  if command -v timeout >/dev/null 2>&1; then
    timeout 4s "$@"
  else
    "$@"
  fi
}

configure_linux_glx_fallback() {
  [[ "$(uname -s 2>/dev/null)" == "Linux" ]] || return
  [[ -n "${DISPLAY:-}" ]] || return
  [[ "${JVN_DISABLE_GLX_FALLBACK:-0}" != "1" ]] || return
  [[ -z "${__GLX_VENDOR_LIBRARY_NAME:-}" ]] || return
  command -v glxinfo >/dev/null 2>&1 || return

  local graphics_mode
  graphics_mode="$(requested_graphics_mode)"
  case "${graphics_mode,,}" in
    sw|software|compatibility) return ;;
  esac

  if run_glx_probe glxinfo -B >/dev/null 2>&1; then
    return
  fi
  if ! run_glx_probe env __GLX_VENDOR_LIBRARY_NAME=mesa glxinfo -B >/dev/null 2>&1; then
    return
  fi

  export __GLX_VENDOR_LIBRARY_NAME=mesa
  if [[ "${graphics_mode,,}" == "auto" || -z "$graphics_mode" ]]; then
    # The Mesa probe proved that a hardware OpenGL path exists. Ask JavaFX to bypass
    # its conservative GPU qualifier while retaining the software pipeline fallback.
    export JVN_GRAPHICS_MODE=hardware
  fi
  echo "[jvn] default GLX is unavailable; using the working Mesa GPU provider for this launch." >&2
}

cache_is_stale() {
  [[ "$FORCE_REBUILD" -eq 1 || ! -s "$CLASSPATH_FILE" || ! -f "$MODULE_PATH_FILE" || ! -s "$VERSION_FILE" || ! -f "$STAMP_FILE" ]] && return 0
  find "$ROOT_DIR/modules" "$ROOT_DIR/misc/demo-assets" \
    -path '*/build' -prune -o \( -type f -o -type d \) \
    -newer "$STAMP_FILE" -print -quit 2>/dev/null | grep -q . && return 0
  find "$ROOT_DIR" -maxdepth 2 -type f \
    \( -name '*.gradle' -o -name '*.gradle.kts' -o -name 'gradle.properties' \) \
    -newer "$STAMP_FILE" -print -quit | grep -q .
}

configure_linux_glx_fallback

if [[ "$MODE" == "gradle" ]]; then
  run_gradle "$GRADLE_TASK"
fi

if cache_is_stale; then
  if [[ "$MODE" == "direct" ]]; then
    echo "[jvn] direct $APP launch cache is missing or stale." >&2
    echo "[jvn] refresh once with: scripts/launch-app.sh $APP --rebuild" >&2
    exit 3
  fi
  # Metadata tasks capture resolved classpaths; Gradle configuration-cache cannot
  # serialize those script objects reliably, and this path only runs when stale.
  "$ROOT_DIR/gradlew" --console=plain --no-configuration-cache --max-workers=2 -p "$ROOT_DIR" "$PREPARE_TASK"
  mkdir -p "$CACHE_DIR"
  touch "$STAMP_FILE"
fi

command -v java >/dev/null 2>&1 || { echo "[jvn] error: Java 21 or newer is required." >&2; exit 1; }

join_path_file() {
  local file="$1" result="" line
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -n "$line" ]] || continue
    if [[ -z "$result" ]]; then result="$line"; else result="$result:$line"; fi
  done < "$file"
  printf '%s' "$result"
}

CLASSPATH="$(join_path_file "$CLASSPATH_FILE")"
MODULE_PATH="$(join_path_file "$MODULE_PATH_FILE")"
VERSION="$(sed -n '1p' "$VERSION_FILE")"
declare -a JAVA_ARGS=("-Djvn.version=$VERSION" "${MODE_PROPS[@]}")
GRAPHICS_MODE="$(requested_graphics_mode)"
HARDWARE_PRISM_ORDER="es2,sw"
case "$(uname -s 2>/dev/null)" in
  MINGW*|MSYS*|CYGWIN*) HARDWARE_PRISM_ORDER="d3d,es2,sw" ;;
esac
case "${GRAPHICS_MODE,,}" in
  gpu|hardware|accelerated|prefer-gpu)
    JAVA_ARGS+=("-Djvn.graphics.mode=hardware" "-Dprism.order=$HARDWARE_PRISM_ORDER" "-Dprism.forceGPU=true")
    ;;
  sw|software|compatibility)
    JAVA_ARGS+=("-Djvn.graphics.mode=software" "-Dprism.order=sw")
    ;;
  *)
    JAVA_ARGS+=("-Djvn.graphics.mode=auto")
    ;;
esac
if [[ -n "${JVN_APP_JAVA_OPTS:-}" ]]; then
  read -r -a USER_JAVA_OPTS <<< "$JVN_APP_JAVA_OPTS"
  JAVA_ARGS+=("${USER_JAVA_OPTS[@]}")
fi
if [[ -n "$MODULE_PATH" ]]; then
  JAVA_ARGS+=(--module-path "$MODULE_PATH" --add-modules javafx.controls,javafx.graphics,javafx.base,javafx.media,javafx.swing,javafx.fxml)
fi
exec java "${JAVA_ARGS[@]}" -cp "$CLASSPATH" "$MAIN_CLASS" "${APP_ARGS[@]}"

#!/usr/bin/env bash
# -----------------------------------------------------------------------------
#  install-macos-launcher.sh
#
#  Installs a user-local "JVN Engine Hub" launcher for macOS:
#
#    ~/Applications/JVN Engine Hub.app
#
#  The app bundle runs the in-tree ./jvn script, logs launch output, and keeps a
#  terminal window open if startup fails so errors are visible.
#
#  Uninstall:
#    rm -rf "$HOME/Applications/JVN Engine Hub.app"
#    rm -rf "$HOME/Library/Application Support/JVN Engine Hub"
#    rm -rf "$HOME/Library/Logs/JVN Engine Hub"
# -----------------------------------------------------------------------------

if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi

set -Eeuo pipefail
IFS=$'\n\t'

APP_NAME="JVN Engine Hub"
APP_BUNDLE="$HOME/Applications/$APP_NAME.app"
SUPPORT_DIR="$HOME/Library/Application Support/$APP_NAME"
LOG_DIR="$HOME/Library/Logs/$APP_NAME"
LOG_FILE="$LOG_DIR/launcher.log"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

die() {
  echo "[installer] error: $1" >&2
  exit 1
}

if [[ "${EUID:-$(id -u)}" -eq 0 && -z "${JVN_ALLOW_ROOT_INSTALL:-}" ]]; then
  die "Do not run this installer with sudo. It installs only for the current macOS user."
fi

ensure_executable() {
  local file="$1"
  local label="$2"
  if [[ ! -f "$file" ]]; then
    die "$label was not found at: $file"
  fi
  if [[ ! -x "$file" ]]; then
    echo "[installer] fixing executable bit: $file"
    chmod u+x "$file"
  fi
}

read_project_version() {
  local version=""
  if [[ -f "$SCRIPT_DIR/gradle.properties" ]]; then
    version="$(sed -nE 's/^[[:space:]]*jvnVersion[[:space:]]*=[[:space:]]*([^[:space:]]+).*$/\1/p' "$SCRIPT_DIR/gradle.properties" | head -n 1)"
  fi
  if [[ -z "$version" && -f "$SCRIPT_DIR/build.gradle.kts" ]]; then
    version="$(sed -nE 's/.*val[[:space:]]+jvnVersion[[:space:]]*=.*\?:[[:space:]]*"([^"]+)".*/\1/p' "$SCRIPT_DIR/build.gradle.kts" | head -n 1)"
  fi
  [[ -n "$version" ]] || version="dev"
  printf "%s" "$version"
}

shell_quote() {
  printf "'%s'" "$(printf "%s" "$1" | sed "s/'/'\\\\''/g")"
}

xml_escape() {
  local value="$1"
  value="${value//&/&amp;}"
  value="${value//</&lt;}"
  value="${value//>/&gt;}"
  value="${value//\"/&quot;}"
  printf "%s" "$value"
}

write_svg_icon() {
  local icon_file="$SUPPORT_DIR/jvn-engine-hub.svg"
  local version_label="$1"
  [[ "$version_label" == v* ]] || version_label="v$version_label"
  version_label="$(xml_escape "$version_label")"
  mkdir -p "$SUPPORT_DIR"
  cat > "$icon_file" <<SVG
<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" role="img" aria-label="JVN ${version_label}">
  <defs>
    <linearGradient id="bg" x1="24" y1="24" x2="232" y2="232" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#121826"/>
      <stop offset="0.55" stop-color="#1e2d4c"/>
      <stop offset="1" stop-color="#0b111f"/>
    </linearGradient>
    <linearGradient id="mark" x1="56" y1="42" x2="204" y2="206" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#ffb35c"/>
      <stop offset="0.48" stop-color="#ff6f3c"/>
      <stop offset="1" stop-color="#4fb7ff"/>
    </linearGradient>
  </defs>
  <rect x="18" y="18" width="220" height="220" rx="44" fill="url(#bg)"/>
  <path d="M62 70h36v72c0 28-17 46-45 46-8 0-17-2-25-6l7-29c5 3 10 4 15 4 8 0 12-5 12-15V70z" fill="url(#mark)"/>
  <path d="M102 70h34l24 74 24-74h36l-44 118h-33L102 70z" fill="url(#mark)"/>
  <text x="128" y="126" text-anchor="middle" font-family="Arial, sans-serif" font-size="44" font-weight="800" fill="#ffffff">JVN</text>
  <rect x="54" y="182" width="148" height="32" rx="16" fill="#07101f" opacity="0.74"/>
  <text x="128" y="204" text-anchor="middle" font-family="Arial, sans-serif" font-size="18" font-weight="700" fill="#ffcf91">${version_label}</text>
</svg>
SVG
  echo "$icon_file"
}

ensure_executable "$SCRIPT_DIR/jvn" "JVN launcher script"
ensure_executable "$SCRIPT_DIR/gradlew" "Gradle wrapper"

mkdir -p "$APP_BUNDLE/Contents/MacOS" "$APP_BUNDLE/Contents/Resources" "$LOG_DIR"

version="$(read_project_version)"
svg_icon="$(write_svg_icon "$version")"
project_q="$(shell_quote "$SCRIPT_DIR")"
log_q="$(shell_quote "$LOG_FILE")"

cat > "$APP_BUNDLE/Contents/MacOS/JVN Engine Hub" <<LAUNCHER
#!/usr/bin/env bash
set -u
PROJECT_DIR=$project_q
LOG_FILE=$log_q

mkdir -p "\$(dirname -- "\$LOG_FILE")"
{
  echo "---- \$(date -Is) ----"
  echo "[JVN] Project: \$PROJECT_DIR"
  echo "[JVN] Starting Engine Hub..."
} >> "\$LOG_FILE"

cd "\$PROJECT_DIR" || {
  echo "[JVN] Could not cd to project directory: \$PROJECT_DIR" | tee -a "\$LOG_FILE"
  osascript -e 'display alert "JVN Engine Hub failed" message "Could not enter the project directory. See the launcher log." as critical' >/dev/null 2>&1 || true
  exit 1
}

./jvn 2>&1 | tee -a "\$LOG_FILE"
status="\${PIPESTATUS[0]}"
if [[ "\$status" -ne 0 ]]; then
  osascript -e 'display alert "JVN Engine Hub failed" message "Startup failed. See ~/Library/Logs/JVN Engine Hub/launcher.log." as critical' >/dev/null 2>&1 || true
fi
exit "\$status"
LAUNCHER
chmod 0755 "$APP_BUNDLE/Contents/MacOS/JVN Engine Hub"

cat > "$APP_BUNDLE/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key>
  <string>JVN Engine Hub</string>
  <key>CFBundleDisplayName</key>
  <string>JVN Engine Hub</string>
  <key>CFBundleIdentifier</key>
  <string>com.jvn.enginehub.local</string>
  <key>CFBundleVersion</key>
  <string>$(xml_escape "$version")</string>
  <key>CFBundleShortVersionString</key>
  <string>$(xml_escape "$version")</string>
  <key>CFBundleExecutable</key>
  <string>JVN Engine Hub</string>
  <key>LSMinimumSystemVersion</key>
  <string>10.13</string>
  <key>NSHighResolutionCapable</key>
  <true/>
</dict>
</plist>
PLIST

cp "$svg_icon" "$APP_BUNDLE/Contents/Resources/jvn-engine-hub.svg"

echo "[installer] installed app: $APP_BUNDLE"
echo "[installer] generated SVG icon: $svg_icon"
echo "[installer] launch log: $LOG_FILE"
echo "Open '$APP_NAME' from ~/Applications."

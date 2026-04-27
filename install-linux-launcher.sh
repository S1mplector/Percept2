#!/usr/bin/env bash
# -----------------------------------------------------------------------------
#  install-linux-launcher.sh
#
#  Installs a "JVN Engine Hub" entry into the current user's applications menu
#  (GNOME / KDE / XFCE / etc.) with absolute paths baked in. Use this if your
#  file manager refuses to launch the in-tree jvn.desktop.
#
#  Uninstall: rm ~/.local/share/applications/jvn-engine-hub.desktop
# -----------------------------------------------------------------------------
set -eu

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
TARGET_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
TARGET_FILE="$TARGET_DIR/jvn-engine-hub.desktop"

if [[ ! -x "$SCRIPT_DIR/jvn" ]]; then
  echo "[installer] error: $SCRIPT_DIR/jvn not found or not executable." >&2
  echo "            run: chmod +x jvn" >&2
  exit 1
fi

ICON_PATH=""
for candidate in \
    "$SCRIPT_DIR/docs/assets/images/jvn_logo.png" \
    "$SCRIPT_DIR/editor/src/main/resources/com/jvn/editor/images/jvn_logo.png"; do
  if [[ -f "$candidate" ]]; then
    ICON_PATH="$candidate"
    break
  fi
done

mkdir -p "$TARGET_DIR"

cat > "$TARGET_FILE" <<DESKTOP
[Desktop Entry]
Version=1.0
Type=Application
Name=JVN Engine Hub
GenericName=Game Engine Launcher
Comment=Launch the Java Vector Nexus editor, runtime, build tasks, and updates
Exec=$SCRIPT_DIR/jvn
Path=$SCRIPT_DIR
Icon=$ICON_PATH
Terminal=true
Categories=Development;IDE;
Keywords=JVN;Editor;Engine;Gradle;
StartupNotify=false
DESKTOP

chmod +x "$TARGET_FILE"

# Some file managers require the trusted metadata flag before they'll show
# the entry without a "Suspicious executable" warning. Best-effort only.
if command -v gio >/dev/null 2>&1; then
  gio set "$TARGET_FILE" metadata::trusted true 2>/dev/null || true
fi

# Refresh the desktop database so the new entry shows up immediately.
if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database "$TARGET_DIR" >/dev/null 2>&1 || true
fi

echo "[installer] installed: $TARGET_FILE"
echo "[installer] you can now launch 'JVN Engine Hub' from your applications menu."

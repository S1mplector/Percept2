#!/usr/bin/env bash
# -----------------------------------------------------------------------------
#  JVN.command — double-click launcher for macOS Finder.
#
#  macOS Finder treats files with a `.command` extension and the executable
#  bit set as shell scripts to run in Terminal.app. Double-click this file to
#  open the JVN Engine Hub GUI without having to use the terminal.
#
#  First-time setup (one-off):
#    chmod +x JVN.command
#  Then double-click it in Finder.
# -----------------------------------------------------------------------------
set -eu

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

if [[ ! -x "$SCRIPT_DIR/jvn" ]]; then
  echo "[JVN.command] error: $SCRIPT_DIR/jvn is missing or not executable." >&2
  echo "              run: chmod +x jvn JVN.command" >&2
  read -rp "Press return to close this window..." _
  exit 1
fi

exec "$SCRIPT_DIR/jvn"

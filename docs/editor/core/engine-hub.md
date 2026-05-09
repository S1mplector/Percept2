# JVN Engine Hub

The JVN Engine Hub is the small desktop control panel for the engine workspace. It is meant for the common tasks you do before opening the full editor or dropping into Gradle manually.

Launch it from the repository root:

```bash
# macOS / Linux
./jvn
```

```bat
:: Windows
jvn.bat
```

The hub runs through the Gradle wrapper so the classpath and Java toolchain stay consistent with the rest of the workspace.

## What It Does

The hub exposes the main workspace actions as buttons:

| Action | What it runs |
|--------|--------------|
| Run Editor | Starts the full JVN editor |
| Run Launcher | Starts the standalone JVN launcher |
| Build All | Builds the workspace |
| Run Tests | Runs the test suite |
| Build Shortcuts | Installs native OS shortcuts for this checkout |
| Update Engine | Runs `git pull --rebase` |

The hub shows a compact status strip instead of a terminal-style output panel. Long task output is reduced to simple progress and completion messages.

## Install Desktop Shortcuts

Open the hub and click **Build Shortcuts**. It chooses the right installer for the current OS:

| OS | Installed shortcut | Launcher behavior |
|----|--------------------|-------------------|
| macOS | `~/Applications/JVN Engine Hub.app` | Native app launcher, no Terminal window |
| Linux | `~/.local/share/applications/jvn-engine-hub.desktop` | Desktop entry with `Terminal=false` |
| Windows | Start Menu and Desktop `.lnk` shortcuts | Hidden WSH/PowerShell launcher, no command prompt |

You can also run the installers directly from the repository root.

```bash
# macOS
./install-macos-launcher.sh

# Linux
./install-linux-launcher.sh
```

```powershell
# Windows PowerShell
.\install-windows-launcher.ps1

# Skip the desktop shortcut and only add Start Menu entry
.\install-windows-launcher.ps1 -NoDesktop
```

The installers are user-local. They should not be run with `sudo` or Administrator elevation.

Installed shortcuts use the centered app-icon SVG at `docs/assets/images/jvn_app_icon.svg` as the canonical icon artwork. Linux installs the SVG directly, macOS converts that SVG into the app bundle `.icns`, and Windows stores the SVG alongside the hidden launcher and assigns it to the generated shortcuts when supported by the shell.

## Shortcut Logs

Installed shortcuts launch without opening a terminal. If startup fails, check the launcher log:

| OS | Log path |
|----|----------|
| macOS | `~/Library/Logs/JVN Engine Hub/launcher.log` |
| Linux | `~/.local/state/jvn-engine-hub/launcher.log` |
| Windows | `%LOCALAPPDATA%\JVN Engine Hub\Logs\launcher.log` |

Failure dialogs point at these logs. On Linux, the wrapper uses `notify-send`, `zenity`, `kdialog`, or `xmessage` when available.

## macOS Privacy Notes

If the repository is stored under protected folders such as Desktop, Documents, or Downloads, macOS may ask for folder access when the shortcut first launches. Allow access for **JVN Engine Hub** so the app can run `gradlew` from the checkout.

The macOS installer builds a small native launcher when `swiftc` is available. If `swiftc` is not available, it falls back to a shell launcher inside the `.app` bundle. Both paths keep Terminal closed and write to the same log file.

## Reinstall Or Remove

Run **Build Shortcuts** again after moving the repository or after pulling launcher changes. The shortcuts record the checkout path they were generated from.

Remove installed shortcuts manually:

```bash
# macOS
rm -rf "$HOME/Applications/JVN Engine Hub.app"
rm -rf "$HOME/Library/Application Support/JVN Engine Hub"
rm -rf "$HOME/Library/Logs/JVN Engine Hub"

# Linux
rm -f "$HOME/.local/share/applications/jvn-engine-hub.desktop"
rm -rf "$HOME/.local/share/jvn-engine-hub"
rm -f "$HOME/.local/share/icons/hicolor/scalable/apps/jvn-engine-hub.svg"
rm -rf "$HOME/.local/state/jvn-engine-hub"
```

On Windows, remove the Start Menu/Desktop shortcuts and `%LOCALAPPDATA%\JVN Engine Hub`.

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

## Package A Self-Contained Hub Jar

For distribution, build the packaged Engine Hub jar:

```bash
./scripts/package-engine-hub.sh
```

```powershell
.\scripts\package-engine-hub.ps1
```

Or run the Gradle task directly:

```bash
./gradlew :hub:packageEngineHubJar
```

The artifact is written to `build/distributions/jvn-engine-hub-<version>.jar`.

Run it with:

```bash
java -jar build/distributions/jvn-engine-hub-<version>.jar
```

To build the larger package with a warmed Gradle dependency cache:

```bash
./scripts/package-engine-hub.sh --with-cache
```

```powershell
.\scripts\package-engine-hub.ps1 --with-cache
```

That artifact is written to `build/distributions/jvn-engine-hub-cached-<version>.jar`. It extracts a local `.jvn-gradle-user-home` next to the bundled engine workspace and the hub automatically uses it for Gradle actions. This reduces first-run dependency work on slower machines by carrying the Gradle wrapper, dependency cache, and the host Java 21 toolchain cache, but the jar is larger and the cache is platform-sensitive for the Java toolchain and native JavaFX artifacts.

This jar contains a clean copy of the engine workspace. On first launch it extracts that workspace under `~/.jvn/engine-hub/<version>/<bundle-hash>/engine` and starts the normal hub against it. The hub can then launch the editor, launcher, Gradle builds, shortcut installers, docs, and updates from the extracted workspace.

To override the extraction cache location:

```bash
java -Djvn.packagedEngineRoot=/path/to/cache -jar build/distributions/jvn-engine-hub-<version>.jar
```

For development, you can also point the packaged launcher at an existing checkout:

```bash
java -jar build/distributions/jvn-engine-hub-<version>.jar --project-root /path/to/Java-Vector-Nexus
```

To verify extraction without opening the GUI:

```bash
java -jar build/distributions/jvn-engine-hub-<version>.jar --extract-only
```

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

## Developer Mode

Developer Mode is toggled from the Engine Hub. When enabled, hub-launched editor and launcher windows receive `jvn.editor.developerMode=true` and `jvn.launcher.developerMode=true`.

In Developer Mode, both the editor and launcher add a collapsed **Logs** panel at the top of the window. Expand it to scan recent log-like files from the selected project, workspace `.jvn/logs`, workspace `logs`, Gradle daemon logs, build temp logs, and the user log directory. The panel is intentionally simple: choose a file, refresh, copy the visible tail, or reveal the log folder.

When the launcher opens the editor in Developer Mode, it also forwards Developer Mode to the editor and captures that child process output under the workspace `.jvn/logs` folder.

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

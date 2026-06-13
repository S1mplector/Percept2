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

## High-Resolution Display Scaling

The hub automatically enlarges its window, fonts, icon buttons, and status surfaces on high-DPI or very high-resolution displays. This is meant to keep the compact Swing control panel readable on 2K/3K/4K OLED panels and Linux desktops that report a large physical framebuffer without applying Swing UI scaling.

If your desktop reports unusual DPI data, override the hub scale manually:

```bash
java -Djvn.hub.uiScale=1.35 -jar build/distributions/jvn-engine-hub-<version>.jar
```

For wrapper-driven launches, use the environment variable form:

```bash
JVN_HUB_UI_SCALE=1.35 ./jvn
```

Open **About** from the hub header to see the scale value and detected screen details.

## Distrobox JDK Detection

On Bazzite, the Linux launchers can use a JDK installed in Distrobox. `./jvn` and `./jvnw` inspect available Distrobox containers, prefer common development names such as `jvn`, `java`, `jdk`, and `devbox`, and choose the first container that provides both Java 21+ and `javac`. The selected container is entered before Gradle starts, which lets the Engine Hub and its **Run Editor** button use that container's Java toolchain.

Use an exact container name with:

```bash
JVN_DISTROBOX_CONTAINER=my-devbox ./jvn
```

Force the same detection on a non-Bazzite host with:

```bash
JVN_DISTROBOX=1 ./jvn
```

Disable the automatic handoff with:

```bash
JVN_DISTROBOX=0 ./jvn
```

If `./jvnw editor` fails with `Unable to load glass GTK library`, JavaFX started but the container is missing desktop native libraries. Install the GTK/X11 runtime libraries inside the selected Distrobox container, then retry:

```bash
# Fedora / Bazzite-style container
sudo dnf install gtk3 libXtst libXxf86vm alsa-lib libXrender libXrandr libXinerama libXi

# Debian / Ubuntu-style container
sudo apt install libgtk-3-0 libxtst6 libxxf86vm1 libasound2 libxrender1 libxrandr2 libxinerama1 libxi6
```

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
| Update Engine | Runs the guarded engine update flow (`git pull --rebase` with hub preflight and recovery UI) |

The hub shows a compact status strip instead of a terminal-style output panel. Long task output is reduced to simple progress and completion messages.

## Maintenance And Announcements

The hub reads dynamic workspace state from committed files under `.jvn/`:

| File | Purpose |
|------|---------|
| `.jvn/maintenance.properties` | Feature-level maintenance flags, currently including `launcher.maintenance` and `launcher.message` |
| `.jvn/announcements.md` | Hub announcement cards shown from the bell button |

The running hub re-reads both files after a successful **Update Engine** action. That means a launcher maintenance badge or announcement can appear or disappear after updating the engine without closing and reopening the hub.

When `launcher.maintenance=true`, the **Run Launcher** button stays visible but displays a striped maintenance state. Clicking it shows the configured message instead of launching the standalone launcher. The default committed state is `launcher.maintenance=false`, so the launcher opens normally.

## Update Engine Recovery

**Update Engine** performs a Git preflight before starting the pull/rebase. It detects unfinished Git operations, generated build output, and local changes that need user attention before the update can proceed.

Safe Mode changes the update path from a plain update into a guarded recovery flow:

- tracked local changes are autostashed before update work starts
- stale rebase/merge/cherry-pick state is detected and can be aborted from the recovery dialog
- generated build output can be cleaned before retrying
- after a failed update, the hub shows the Git status/recovery commands instead of only reporting `exit 1`

If Safe Mode recovery also fails, copy the dialog details or run `git status --short --branch` in the engine checkout. The checkout should not be manually reset unless the local changes are known to be disposable.

## Developer Mode

Developer Mode is toggled from the Engine Hub. When enabled, hub-launched editor and launcher windows receive `jvn.editor.developerMode=true` and `jvn.launcher.developerMode=true`.

In Developer Mode, both the editor and launcher add a collapsed **Logs** panel at the top of the window. Expand it to scan recent log-like files from the selected project, workspace `.jvn/logs`, workspace `logs`, Gradle daemon logs, build temp logs, and the user log directory. The panel is intentionally simple: choose a file, refresh, copy the visible tail, or reveal the log folder.

When the launcher opens the editor in Developer Mode, it also forwards Developer Mode to the editor and captures that child process output under the workspace `.jvn/logs` folder.

Developer Mode also adds a **DevTools** menu to the editor and launcher menu bars. It includes runtime/JVM diagnostics, manual GC, log-panel refresh, a developer settings file shortcut, a launcher output-capture toggle, and an editor JVM heap setting. The heap setting is stored in `~/.jvn-editor/devtools.properties` and applies to the next editor launch started from the launcher.

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

If **Run Editor** or **Documentation** fails on Linux while starting the JavaFX toolkit, check that the desktop session exposes a display and that the GTK/X11 native libraries JavaFX loads are installed. On Linux Mint/Ubuntu this usually means installing `libgtk-3-0`, `libxtst6`, `libxxf86vm1`, and the system OpenGL/Mesa packages for the machine's GPU.

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

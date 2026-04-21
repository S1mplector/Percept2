# Deployment & Packaging

Guide to building JVN projects for distribution — creating runnable JARs, bundling assets, platform-specific considerations, and distribution strategies.

For the current automated portable archive tasks, start with [Build System](build-system.md). This page covers broader deployment concepts and native packaging options.

---

## Overview

JVN projects run on the JVM. Distribution involves packaging your compiled engine modules, project assets, and a JDK runtime into a form that end users can launch. This guide covers the build pipeline, asset bundling, and platform targets.

---

## Build Pipeline

### Step 1: Compile the Engine

```bash
./jvnw build
```

This compiles all modules (`core`, `scripting`, `fx`, `runtime`, `audio`, etc.) and runs tests.

### Step 2: Create Release Artifacts

The root build system can now produce three kinds of outputs:

```bash
# Cross-target portable zips (players need Java 21)
./jvnw dist -PjvnGameProject=/path/to/game
./jvnw dist-all -PjvnGameProject=/path/to/game

# Current-host self-contained zip (players do not need Java installed)
./jvnw dist-runtime -PjvnGameProject=/path/to/game

# Current-host native package (players do not need Java installed)
./jvnw native -PjvnGameProject=/path/to/game
```

Outputs are written to `build/distributions/games/`. In the editor, open the game project and use **Build & Publish...** for the same workflow with a popup UI.

The build tasks validate the selected game before assembling: `jvn.project` must be readable, the manifest type must be `vn` or `jes`, the configured entry script must exist, and the selected folder must be a game project rather than the JVN engine workspace.

Bundled-runtime and native packages are host-only. Build mac packages on macOS, Windows packages on Windows, and Linux packages on Linux.

### Low-Level: Create a Runtime JAR

The runtime module produces a runnable application:

```bash
./jvnw jar
```

The output JAR is in `runtime/build/libs/`.

### Targeted module builds for faster iteration:

```bash
./jvnw build

# Optional direct Gradle tasks for focused module work
./gradlew :core:compileJava :runtime:compileJava
```

---

## Asset Bundling

### Classpath Bundling

For production distribution, assets should be accessible on the classpath. Place them in the runtime module's resources directory or include them as a separate JAR/directory on the classpath.

**Option A: Fat JAR** — bundle everything into one JAR:

1. Place assets in `src/main/resources/` following the `game/` prefix convention
2. Build a shadow/fat JAR that includes all dependencies and assets

**Option B: Separate asset directory** — keep assets alongside the JAR:

```text
distribution/
├── jvn-runtime.jar
├── lib/                    # Dependency JARs
├── assets/                 # Project assets
│   ├── backgrounds/
│   ├── characters/
│   └── audio/
├── scripts/
├── config/
└── launch.sh / launch.bat
```

Launch with:

```bash
java -jar jvn-runtime.jar --assets . --script scripts/story/prologue.vns --ui fx --audio auto
```

### Launch Scripts

**Linux/macOS (`launch.sh`):**

```bash
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
java -jar "$DIR/jvn-runtime.jar" --assets "$DIR" --script scripts/story/prologue.vns --ui fx --audio auto
```

**Windows (`launch.bat`):**

```batch
@echo off
set DIR=%~dp0
java -jar "%DIR%jvn-runtime.jar" --assets "%DIR%" --script scripts/story/prologue.vns --ui fx --audio auto
```

---

## Platform Targets

### JDK Requirement

JVN build machines require **JDK 21**. End users only need Java if you ship the portable zip format. Bundled-runtime zips and native packages include their own runtime.

### Using jpackage (Native Installer)

JVN now uses `jpackage` for host-native app images and installers. Under the hood it packages the JVN runtime jars, adds the bundled game folder as app content, and uses a `jlink` runtime image so players do not need a system Java install.

```bash
# macOS .app / .dmg
jpackage --type dmg \
  --name "My Visual Novel" \
  --input dist/ \
  --main-jar jvn-runtime.jar \
  --main-class com.jvn.runtime.JvnApp \
  --java-options "--add-modules javafx.controls,javafx.media" \
  --arguments "--assets . --script scripts/story/prologue.vns --ui fx --audio auto"

# Windows .exe / .msi
jpackage --type msi \
  --name "My Visual Novel" \
  --input dist/ \
  --main-jar jvn-runtime.jar \
  --main-class com.jvn.runtime.JvnApp

# Linux .deb / .rpm
jpackage --type deb \
  --name "my-visual-novel" \
  --input dist/ \
  --main-jar jvn-runtime.jar \
  --main-class com.jvn.runtime.JvnApp
```

### Using jlink (Custom JRE)

JVN now uses `jlink` to create bundled runtime images for self-contained zips and as the runtime base for native packages.

Standalone example:

```bash
jlink --module-path $JAVA_HOME/jmods \
  --add-modules java.base,java.desktop,javafx.controls,javafx.media \
  --output custom-jre \
  --strip-debug \
  --compress=2
```

Then distribute `custom-jre/` alongside your game JAR, or let JVN's `assembleJvnGameBundledRuntimeCurrent` task do the staging automatically.

---

## Distribution Checklist

### Before Distribution

- [ ] All scripts load without parser errors
- [ ] All assets are included (no missing file warnings)
- [ ] Menu navigation works (main → load/save/settings → back)
- [ ] Audio plays correctly (BGM, SFX, voice)
- [ ] Save/load works in the packaged version
- [ ] Settings persist between launches
- [ ] The game starts and reaches the main menu without errors
- [ ] Test on a clean machine (no dev environment)

### Package Contents

- [ ] Runtime JAR with all engine dependencies
- [ ] All project assets (backgrounds, characters, audio, UI)
- [ ] All scripts (`.vns`, `.jes`)
- [ ] All config files (`.layout`, `.style`, `.menu`, `.registry`, `.theme`)
- [ ] Launch script or native installer
- [ ] README with system requirements
- [ ] License information (if distributing publicly)

---

## Distribution Strategies

### Strategy 1: ZIP Archive

The simplest approach — a ZIP containing the JAR, assets, and a launch script.

**Pros:** Simple, cross-platform, can be built for multiple desktop targets from one host
**Cons:** Requires user to have Java 21 installed

### Strategy 2: Native Installer (jpackage)

Platform-specific installer that bundles a JRE.

**Pros:** No Java requirement for users, native look and feel
**Cons:** Must be built on the matching host OS, larger download

### Strategy 3: Distribution Platform (itch.io, Steam)

Upload platform-specific builds. Most platforms support:
- macOS: `.app` bundle or `.dmg`
- Windows: `.exe` installer or portable folder
- Linux: `.AppImage`, `.deb`, or portable folder

---

## Save Data Location

Save files are stored at `~/.jvn/saves/` by default. This is outside the distribution directory, so saves persist across updates.

Settings are at `~/.jvn/settings.properties`.

These paths are user-specific and don't need to be included in the distribution.

---

## Performance Considerations for Distribution

- **Compress audio:** Use OGG/MP3 instead of WAV for smaller download size
- **Optimize images:** Use `pngquant` or `optipng` for PNG assets
- **Remove unused assets:** Don't include demo assets in production builds
- **Remove dev files:** Don't include `.git/`, editor configs, or build artifacts

---

## Troubleshooting Distribution Issues

**"JavaFX runtime components are missing":**
JavaFX must be on the module path. Include JavaFX JARs in your distribution or use `jpackage` which handles this.

**Assets not found in packaged version:**
Ensure `--assets` points to the directory containing `assets/`, `scripts/`, and `config/`. For fat JARs, assets must be on the classpath at the correct prefix paths.

**Audio doesn't play:**
The Simp3 audio module must be on the classpath. Verify the audio JAR is included in your distribution.

**Save permission errors:**
`~/.jvn/` must be writable. On some locked-down systems, the save directory may need configuration.

---

## Related Docs

- [Runtime Guide](../../runtime/core/runtime.md)
- [Build System](build-system.md)
- [Asset Management](../../runtime/systems/asset-management.md)
- [Project Structure Conventions](../onboarding/project-structure.md)
- [Performance](../../architecture/quality/performance.md)
- [Getting Started](../../guides/getting-started.md)

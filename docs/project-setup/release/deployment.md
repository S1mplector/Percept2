# Deployment & Packaging

Guide to building JVN projects for distribution — creating runnable JARs, bundling assets, platform-specific considerations, and distribution strategies.

---

## Overview

JVN projects run on the JVM. Distribution involves packaging your compiled engine modules, project assets, and a JDK runtime into a form that end users can launch. This guide covers the build pipeline, asset bundling, and platform targets.

---

## Build Pipeline

### Step 1: Compile the Engine

```bash
./gradlew build
```

This compiles all modules (`core`, `scripting`, `fx`, `runtime`, `audio`, etc.) and runs tests.

### Step 2: Create a Distribution JAR

The runtime module produces a runnable application:

```bash
./gradlew :runtime:jar
```

The output JAR is in `runtime/build/libs/`.

### Targeted module builds for faster iteration:

```bash
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

JVN requires **JDK 21**. End users need a compatible JVM. Options:

1. **Require users to install JDK 21** — simplest but adds friction
2. **Bundle a JRE** — include a stripped JDK with your distribution using `jlink`
3. **Use a wrapper** — tools like `jpackage` create native installers

### Using jpackage (Native Installer)

JDK 21 includes `jpackage` for creating platform-native packages:

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

Create a minimal JRE containing only the modules your game needs:

```bash
jlink --module-path $JAVA_HOME/jmods \
  --add-modules java.base,java.desktop,javafx.controls,javafx.media \
  --output custom-jre \
  --strip-debug \
  --compress=2
```

Then distribute `custom-jre/` alongside your game JAR.

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

**Pros:** Simple, cross-platform, small distribution size
**Cons:** Requires user to have JDK 21 installed

### Strategy 2: Native Installer (jpackage)

Platform-specific installer that bundles a JRE.

**Pros:** No JDK requirement for users, native look and feel
**Cons:** Separate builds per platform, larger download

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

- [Runtime Guide](../runtime/runtime.md)
- [Asset Management](../runtime/asset-management.md)
- [Project Structure Conventions](project-structure.md)
- [Performance](../architecture/performance.md)
- [Getting Started](../getting-started.md)

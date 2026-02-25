# Java Vector Nexus (JVN)

<div align="left">
  <img src="docs/images/jvn_logo.png" width="512" alt="JVN logo">
</div>

JVN is a modular Visual Novel engine written primarily in Java.

Core capabilities:
- Visual Novel runtime (`.vns`) with branching flow, variables, conditional blocks, choices, transitions, save/load, history, and interop.
- JES runtime (`.jes`) for scene/entity/timeline scripting, minigames, and overlays.
- JavaFX + Swing render backends.
- JavaFX editor with code editors, visual config editors, timeline graph tools, live previews, and in-editor Help Center.

## Requirements

- JDK 21 (toolchain auto-download is enabled, but local JDK 21 is still recommended)
- No global Gradle install required (`./gradlew` is included)
- For team version-control workflows in editor: `git` and `git lfs` installed/configured
- For native acceleration builds: `cmake` + C/C++ compiler toolchain (`clang`/`gcc`/MSVC)

## Quick Start

Build everything:

```bash
./gradlew build
```

`./gradlew build` now auto-attempts a `native-math` CMake build when required native outputs are missing.
If you need to bypass this on a machine without CMake/toolchain:

```bash
./gradlew -PskipNativeMathBuild=true build
```

Run editor:

```bash
./gradlew :editor:run
```

Run runtime:

```bash
./gradlew :runtime:run
```

## Build and Test

Compile main app modules:

```bash
./gradlew :core:compileJava :scripting:compileJava :fx:compileJava :runtime:compileJava :editor:compileJava
```

Run unit tests:

```bash
./gradlew :core:test :scripting:test :swing:test
```

Create runtime distribution:

```bash
./gradlew :runtime:distZip
```

## Native-Math Build (Optional)

JVN now includes a vendored native utility library in `native-math/`.

This is optional; Java fallbacks remain active when native libraries are missing.

Build commands:

```bash
# macOS/Linux
./native-math/build.sh

# macOS-only helper
./native-math/build_mac.sh

# Linux-only helper
./native-math/build_linux.sh
```

On Windows:

```bat
native-math\build_windows.bat
```

Or:

```powershell
.\native-math\build.ps1
```

Current runtime usage:
- `core` save pipeline attempts native atomic writes through `NativeIoBridge`.
- If unavailable, JVN automatically falls back to Java temp-file + atomic move flow.

## Runtime Usage

Entrypoint: `runtime/src/main/java/com/jvn/runtime/JvnApp.java`

Basic run:

```bash
./gradlew :runtime:run
```

Common examples:

```bash
# Run a specific VNS script with FX renderer
./gradlew :runtime:run --args='--script demo.vns --ui fx'

# Run with Swing renderer
./gradlew :runtime:run --args='--script demo.vns --ui swing'

# Load JES directly
./gradlew :runtime:run --args='--jes game/minigames/brickbreaker.jes'

# Overlay filesystem assets onto classpath assets
./gradlew :runtime:run --args='--assets /absolute/path/to/project --script story/prologue.vns'
```

Supported runtime CLI flags:
- `--title <text>`
- `--width <px>`
- `--height <px>`
- `--script <name>` default: `demo.vns`
- `--locale <code>` default: `en`
- `--billiards` (currently logs warning if module entry flow is unavailable)
- `--ui <fx|swing>` default: `fx`
- `--jes <path[,path2...]>`
- `--audio <fx|simp3|auto>` default: `auto`
- `--assets <dir>`

Notes:
- Script loading uses `AssetCatalog` script paths (typically relative to `game/scripts/` on classpath).
- If script loading fails, runtime falls back to built-in demo scenario content.

## Editor

Run:

```bash
./gradlew :editor:run
```

Current editor highlights:
- Startup Welcome dashboard with recent projects + environment health checks.
- Project explorer with root-level run button (runs VN projects through runtime).
- VNS/JES code editors with lint and parser diagnostics.
- VNS quick-fix context actions (undefined labels, missing assets, unreachable blocks).
- Built-in Version Control panel (Git + Git LFS): init repo, status, commit, pull-rebase, push.
- Visual config editors:
  - `config/ui/dialogue.layout`
  - `config/menu/menus/*.menu`
  - `config/menu/layouts/*.layout`
- Timeline graph + DSL editor (`config/timeline/story.timeline`).
- In-editor Help Center (`F1`).

## Simp3 Backend (Default)

JVN now ships with an embedded Simp3-compatible backend by default in `audio`.
No extra Maven install step or `-PuseSimp3` flag is required.

Runtime audio selection:

```bash
./gradlew :runtime:run --args='--audio auto'
./gradlew :runtime:run --args='--audio simp3'
./gradlew :runtime:run --args='--audio fx'
```

`auto` prefers Simp3 and falls back to FX if needed.

## Gradle Lock Troubleshooting (Linux-heavy)

If a machine hits Gradle journal lock errors like:
`Failed to ping owner of lock for journal cache (.../journal-1)`

use this sequence:

```bash
./gradlew --stop
rm -f ~/.gradle/caches/journal-1/*.lock
./gradlew build --no-daemon --no-watch-fs
```

Project defaults already include:
- `org.gradle.vfs.watch=false` in `gradle.properties`

Editor-run tasks also isolate Gradle state in `.jvn-gradle-user-home` to avoid global lock contention.

## Wizard-Generated VN Project Layout

New projects created from the editor wizard are scaffolded in this shape:

```text
<project>/
|-- config/
|   |-- settings/vn.settings
|   |-- timeline/story.timeline
|   |-- ui/dialogue.layout
|   `-- menu/
|       |-- theme/menu.theme
|       |-- registry/menu.registry
|       |-- menus/*.menu
|       |-- layouts/*.layout
|       |-- styles/*.style
|       `-- assets/
|-- scripts/
|   |-- story/prologue.vns
|   |-- common/
|   `-- system/
|-- assets/
|   |-- backgrounds/
|   |-- characters/
|   |-- portraits/
|   |-- cg/
|   |-- ui/
|   |-- fonts/
|   `-- audio/{bgm,sfx,voices}
|-- save/
|-- .gitignore                  (if Git init enabled)
|-- .gitattributes              (if Git LFS defaults enabled)
|-- README.md
`-- jvn.project
```

## Team Version Control (Git + Git LFS)

JVN now ships first-party Git/Git-LFS project tooling:

- **Wizard integration**:
  - initialize Git repository
  - add managed `.gitignore` defaults
  - add managed `.gitattributes` LFS defaults
  - optional initial commit
- **Editor integration**:
  - `Version Control` menu + addable side panel
  - refresh status, initialize repo, commit all, pull (rebase+autostash), push
  - changed-file list with quick open support

Default LFS tracking patterns include common VN binary assets (`png/jpg/webp/gif`, audio/video formats, and fonts).
Only prerequisites are `git` and `git lfs` on PATH.

## Module Overview

- `core`: engine/runtime primitives, VN runtime, menus, save system, 2D/physics.
- `scripting`: JES tokenizer/parser/AST/loader/runtime scene.
- `fx`: JavaFX launcher, VN renderer, menu rendering, FX audio backend.
- `swing`: Swing launcher/backend.
- `runtime`: CLI app (`JvnApp`), runtime interop bridge, scene wiring.
- `editor`: JavaFX authoring environment.
- `audio`: bundled Simp3-compatible audio integration layer.
- `testkit`: shared testing dependencies/helpers.

## Documentation Map

- `docs/Architecture/Overview.md`
- `docs/Architecture/Architecture.md`
- `docs/Architecture/2D-Engine.md`
- `docs/Architecture/Native Library Audit.md`
- `docs/Runtime/Runtime.md`
- `docs/Runtime/Save System.md`
- `docs/Runtime/Interop.md`
- `docs/Editor/Editor.md`
- `docs/Editor/Help Center.md`
- `docs/Editor/Puppeteer.md`
- `docs/Project Setup/New Project Wizard.md`
- `docs/Project Setup/Version Control.md`
- `docs/Project Setup/TitleScreen.md`
- `docs/Project Setup/TextEffects.md`
- `docs/Menu Profiles/Menu Profiles.md`
- `docs/VNS Scripting/VNS Scripting.md`
- `docs/VNS Scripting/VNS Parsing.md`
- `docs/VNS Scripting/Java-JES Cross Development.md`
- `docs/JES Scripting/JES Scripting.md`
- `docs/JES Scripting/JES Parsing.md`
- `docs/JES Scripting/Components.md`
- `docs/Timeline Scripting/Timeline Scripting.md`
- `docs/Performance.md`

## License

This repository is licensed under the MIT License. See `LICENSE.md`.

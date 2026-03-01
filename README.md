# Java Vector Nexus (JVN)

<div align="left">
  <img src="docs/images/jvn_logo.png" width="512" alt="JVN logo">
</div>

JVN is a modular Visual Novel engine written primarily in Java, C and C++.

## Architecture

JVN is designed to be lightweight and predictable under load:
- Modular separation of runtime, scripting, renderer backends, editor tooling, and optional native acceleration.
- Performance-critical paths are accelerated in `native-math` (SIMD text search, pooled batch scanning, math kernels, and atomic save-path I/O).
- Native bridges are isolated in `core/nativebridge` with automatic Java fallbacks, so behavior stays stable even when native binaries are unavailable.
- Hot paths are data-oriented where possible (compact buffers, reduced allocation churn, pooled native buffers for batch workflows).

Typical memory footprint for the core runtime together with the full editor is around **30-60 MB RAM** in normal desktop usage (project/content dependent).

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

`./gradlew build` also auto-attempts a `native-math` CMake build when required native outputs are missing.
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

This is optional; Java fallbacks remain active when native libraries are missing, but using the native library nonetheless is recommended. 

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

Editor currently features:
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

Full documentation index: **`docs/INDEX.md`**

### Start Here
- `docs/getting-started.md` — first-time setup, build, and run
- `docs/cookbook.md` — practical recipes and end-to-end examples

### Architecture
- `docs/architecture/overview.md`
- `docs/architecture/system-architecture.md`
- `docs/architecture/2d-engine.md`
- `docs/architecture/performance.md`
- `docs/architecture/native-library-audit.md`

### VNS Scripting (11 sub-documents)
- `docs/scripting/vns/vns-scripting.md` — overview and quick reference
- `docs/scripting/vns/vns-directives.md` — @scenario, @character, @background, @charimg, @charlayer, @charpreset, @label, @var, @define, @include
- `docs/scripting/vns/vns-dialogue.md` — dialogue forms, text effects, typewriter
- `docs/scripting/vns/vns-choices.md` — choices, branching patterns
- `docs/scripting/vns/vns-commands.md` — complete command catalog
- `docs/scripting/vns/vns-audio.md` — BGM, SFX, voice, crossfade
- `docs/scripting/vns/vns-characters.md` — character system, layered sprites, motion
- `docs/scripting/vns/vns-variables.md` — variables, conditions, if/elif/else
- `docs/scripting/vns/vns-transitions.md` — transitions, shake, flash, UI control
- `docs/scripting/vns/vns-flow-control.md` — labels, jumps, call/return, script switching
- `docs/scripting/vns/vns-interop.md` — JES/Java integration, inline timelines
- `docs/scripting/vns/vns-text-formatting.md` — ICU plurals, select, number formatting
- `docs/scripting/vns/vns-parsing.md` — parser internals
- `docs/scripting/vns/java-jes-cross-development.md` — hybrid architecture

### JES Scripting (12 sub-documents)
- `docs/scripting/jes/jes-scripting.md` — overview, quick start, quick reference
- `docs/scripting/jes/jes-scenes-entities.md` — scene structure, entity declarations, lifecycle, merging
- `docs/scripting/jes/components.md` — all 12 component types with properties
- `docs/scripting/jes/jes-timeline.md` — 22 timeline actions: move, rotate, scale, fade, camera, audio, combat
- `docs/scripting/jes/jes-input.md` — keyboard mappings, continuous movement, custom handlers
- `docs/scripting/jes/jes-camera.md` — position, zoom, shake, follow, dead zones, parallax
- `docs/scripting/jes/jes-physics.md` — rigid bodies, sensors, triggers, restitution, raycasting
- `docs/scripting/jes/jes-tilemaps.md` — tilesets, collision/trigger layers, pathfinding
- `docs/scripting/jes/jes-ai.md` — chase, patrol, guard, flee, line-of-sight, A* pathfinding
- `docs/scripting/jes/jes-rpg.md` — Stats, Inventory, Equipment, Items, damage/heal
- `docs/scripting/jes/jes-ui-widgets.md` — Button2D, Slider2D, HUD patterns
- `docs/scripting/jes/jes-bridge.md` — VNS↔JES bridge, call handlers, Java hooks
- `docs/scripting/jes/jes-parsing.md` — tokenizer, parser, AST, validation

### Timeline (3 sub-documents)
- `docs/scripting/timeline/timeline-scripting.md` — overview, quick start, key concepts
- `docs/scripting/timeline/timeline-story-arcs.md` — arc/link DSL, clusters, validation, story patterns
- `docs/scripting/timeline/timeline-animation.md` — TimelineData, keyframes, audio cues, TimelineRunner, registry

### Runtime
- `docs/runtime/runtime.md`
- `docs/runtime/interop.md`
- `docs/runtime/save-system.md`

### Menu & Layout System (6 sub-documents)
- `docs/menu-profiles/menu-profiles.md` — overview, registry, loader discovery, action types
- `docs/menu-profiles/menu-screens.md` — .menu files, items, actions, bounds, slot previews
- `docs/menu-profiles/menu-layouts.md` — .layout files, list positioning, built-in layouts
- `docs/menu-profiles/menu-styles.md` — .style files, colors, fonts, button skins, backgrounds
- `docs/menu-profiles/menu-button-layouts.md` — per-button positional layouts, Bounds Studio
- `docs/menu-profiles/dialogue-layout.md` — textbox geometry, name box, choices, action buttons

### Editor
- `docs/editor/editor.md`
- `docs/editor/puppeteer.md`
- `docs/editor/action-editor-design.md`
- `docs/editor/puppeteer-audit.md`
- `docs/editor/help-center.md`

### Project Setup
- `docs/project-setup/new-project-wizard.md`
- `docs/project-setup/title-screen.md`
- `docs/project-setup/text-effects.md`
- `docs/project-setup/version-control.md`

## License

This repository is licensed under the MIT License. See `LICENSE.md`.

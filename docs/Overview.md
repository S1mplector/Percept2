# Overview

JVN (Java Vector Nexus) is a Java game engine with a strong focus on visual novel production, scriptable 2D scenes, and editor-driven workflow.

This page is the high-level map. Use the linked docs for deeper implementation details.

## At a Glance

- **Narrative layer**: VNS (`.vns`) with labels, dialogue, branching, conditions, variables, transitions, audio, and interop commands.
- **Scene layer**: JES (`.jes`) for entities/components/input bindings/timelines in 2D scenes.
- **Runtime layer**: `JvnApp` wires engine, assets, interop, menu scenes, and renderer backend.
- **Authoring layer**: JavaFX editor for code + visual configuration editing.

## Main Workflow

1. Create/open a project in the editor.
2. Author story scripts in `scripts/story/*.vns`.
3. Configure dialogue/menu presentation in `config/ui` and `config/menu` (code or visual editors).
4. Preview scripts in editor and run full project via runtime.
5. Iterate with saves, settings, and timeline graph tools.

## Core Capabilities

### Visual Novel Runtime

- Parser: `core/src/main/java/com/jvn/core/vn/script/VnScriptParser.java`
- Runtime scene: `core/src/main/java/com/jvn/core/vn/VnScene.java`
- Interop: `DefaultVnInterop` + runtime extension `RuntimeVnInterop`
- Save/load: schema-based `VnSaveData`, migration layer, autosave slots, atomic writes

### Menu Framework

- Dynamic profile loader from `config/menu/...`
- Configurable screens, layouts, styles, per-item actions, and inheritance
- Main/load/save/settings scenes all consume menu profiles

### JES Scene Runtime

- Tokenize -> parse AST -> load to `JesScene2D`
- Component/property validation in parser for strict diagnostics
- Timeline actions for motion/camera/audio/calls and branching (`label`, `jump`, `loop`, `parallel`)

### Editor Tooling

- Code editors for VNS/JES/Timeline and general text formats
- Visual editors for dialogue layout and menu configs
- VNS lint + quick fixes (undefined labels, missing assets, unreachable blocks)
- Timeline graph with validation and drag/drop script arc creation
- In-editor Help Center (`F1`) for docs search and preview

## Project Requirements

- JDK 21
- Gradle wrapper (`./gradlew`)

## Recommended Docs Next

- Architecture: `docs/Architecture/Architecture.md`
- Runtime usage: `docs/Runtime/Runtime.md`
- Editor tooling: `docs/Editor/Editor.md`
- VNS language: `docs/VNS Scripting/VNS Scripting.md`
- JES language: `docs/JES Scripting/JES Scripting.md`
- Menu profiles: `docs/Menu Profiles/Menu Profiles.md`

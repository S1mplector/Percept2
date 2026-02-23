# Architecture

This document describes the engine structure and the key execution paths across modules.

## Module Boundaries

- `core`
  - Engine loop, scenes, input abstraction, VN runtime state, save/load, menu systems, 2D primitives/physics.
- `scripting`
  - JES tokenizer/parser/AST/loader and `JesScene2D` runtime behavior.
- `fx`
  - JavaFX launcher, VN renderer, menu renderer/theme parsing, FX audio backend.
- `swing`
  - Swing launcher and rendering backend support.
- `runtime`
  - App entrypoint (`JvnApp`) and runtime-only interop (`RuntimeVnInterop`).
- `editor`
  - JavaFX content tooling: file editors, visual editors, project explorer, timeline graph, docs help center.
- `audio`
  - Bundled Simp3-compatible audio integration layer (available by default).

## Runtime Boot Sequence

Entrypoint: `runtime/src/main/java/com/jvn/runtime/JvnApp.java`

1. Parse CLI flags (`--script`, `--ui`, `--jes`, `--audio`, `--assets`, etc.).
2. Initialize localization.
3. Build `AssetManager`:
   - Classpath only, or
   - filesystem+classpath overlay when `--assets` is provided.
4. Start `Engine` and set `VnInteropFactory`.
5. Launch scene path:
   - `--jes`: load JES scene(s) directly.
   - otherwise: push main menu scene.
6. Launch renderer backend (`fx` or `swing`).

## VNS Data Flow

1. `.vns` text is parsed by `VnScriptParser` into `VnScenario`.
2. `VnScene` drives node progression and player state.
3. External commands become `VnExternalCommand` and are handled by `VnInterop`.
4. Runtime interop can push JES scenes, open menu scenes, switch scripts, and call Java utilities.

## JES Data Flow

1. `JesTokenizer` creates tokens with line/column metadata.
2. `JesParser` builds AST with strict property validation.
3. `JesLoader` materializes entities/components and bindings into `JesScene2D`.
4. `JesScene2D` updates physics, input actions, timeline actions, and call handlers.

## Menu System Flow

Menu config loader: `core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`

1. Load `config/menu/registry/menu.registry` (or legacy `config/menu/menu.registry`) if present.
2. Discover screens/layouts/styles from `config/menu/menus`, `config/menu/layouts`, `config/menu/styles`.
3. Resolve `extends` chains.
4. Apply defaults and fallback behavior.
5. Built-in menu scenes (`MainMenuScene`, `LoadMenuScene`, `SaveMenuScene`, `SettingsScene`) consume resolved profile data.

## Save/Load Architecture

- Save model: `VnSaveData` (schema versioned)
- Migration: `VnSaveMigration`
- I/O manager: `VnSaveManager`

Reliability behaviors:
- schema normalization during load/save
- temp file + atomic move writes
- autosave slot rotation
- migration write-back when an old save is upgraded

## Editor Architecture

- `EditorApp` composes project tree, tabbed file editors, inspector/help panels.
- `FileEditorTab` routes file types to matching editor widgets.
- Visual editors keep properties text synchronized for source-control-friendly config files.
- Project run action executes runtime through Gradle with isolated Gradle user home.

## Cross-System Integration Points

- VNS -> JES: `[jes push|replace|call|pop ...]`
- JES -> VNS: `call "return" { ... }` / `call "vns" { ... }`
- VNS -> Java: `[java fully.qualified.Class#method ...]`
- Runtime menus callable from VNS via `[menu ...]`, `[settings]`, `[mainmenu ...]`

## Design Principles Used Here

- **Data-driven config** for menu/layout/style and dialogue UI
- **Strict parser diagnostics** for script quality
- **Fallback defaults** to keep runtime resilient when content is missing
- **Editor-first workflows** with immediate visual feedback and synchronized plain-text assets

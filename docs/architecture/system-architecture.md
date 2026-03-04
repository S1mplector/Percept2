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

## Engine Core

Source: `core/src/main/java/com/jvn/core/engine/Engine.java`

The `Engine` class is the central orchestrator. It owns the scene stack, input state, tween runner, and update loop.

### Update Loop

Each frame, the renderer calls `engine.update(deltaMs)`. The engine applies:

1. **Delta clamping** — caps `deltaMs` to `maxDeltaMs` (default 75ms) to prevent simulation explosions from frame spikes or debugger pauses.
2. **Delta smoothing** — optional exponential moving average (`deltaSmoothing`, default 0.1). Smooths frame-to-frame jitter. Set to 0 to disable.
3. **Fixed timestep** (optional) — if `fixedUpdateMs > 0`, the engine accumulates time and runs `tick()` at fixed intervals (up to `maxFixedSteps` per frame). Prevents accumulator runaway by clamping to one step's worth of time when the cap is hit.
4. **Tick** — updates `TweenRunner`, then calls `update(deltaMs)` on the active scene (top of stack).
5. **Input frame end** — clears `pressed` and `released` sets for next frame.

```java
// Default settings
maxDeltaMs = 75;           // clamp extreme deltas
deltaSmoothing = 0.1;      // exponential smoothing factor [0..1]
fixedUpdateMs = 0;          // 0 = variable timestep (disabled)
maxFixedSteps = 5;          // safety limit per frame
```

### ApplicationConfig

Built via `ApplicationConfig.builder()`:

| Field | Default | Description |
|-------|---------|-------------|
| `title` | `"JVN"` | Window title |
| `width` | `960` | Initial window width |
| `height` | `540` | Initial window height |
| `fixedUpdateMs` | `0` | Fixed update step (0 = variable) |
| `fixedUpdateMaxSteps` | `5` | Max substeps per frame |

### TweenRunner

A lightweight task runner for time-based animations. `Engine.tweens()` returns the shared instance. Add `TweenTask` subclasses (implement `update(deltaMs)` + `isFinished()`); finished tasks are auto-removed.

Source: `core/src/main/java/com/jvn/core/tween/TweenRunner.java`

---

## Scene Stack (SceneManager)

Source: `core/src/main/java/com/jvn/core/scene/SceneManager.java`

Scenes are managed as a **stack** with lifecycle callbacks:

| Operation | Effect |
|-----------|--------|
| `push(scene)` | Pauses current scene (`onPause`), pushes new scene, calls `onEnter` |
| `pop()` | Calls `onExit` on top scene, removes it, calls `onResume` on new top |
| `replace(scene)` | Pops current (`onExit`), pushes new (`onEnter`) |
| `peek()` | Returns current scene without modifying stack |

**Scene lifecycle interface:**

```java
public interface Scene {
    default void onEnter() {}   // scene becomes active
    default void onExit() {}    // scene is removed from stack
    default void onPause() {}   // another scene pushed on top
    default void onResume() {}  // scene becomes active again after pop
    void update(long deltaMs);  // called every frame while active
}
```

This stack model supports VNS → JES minigame → return patterns, menu overlays, and nested scene transitions.

---

## Input System

Source: `core/src/main/java/com/jvn/core/input/Input.java`, `InputCode.java`

Backend-agnostic input that supports keyboard, mouse, and gamepad:

### InputCode

A unified identifier for any input source:

| Device | Factory | Example |
|--------|---------|---------|
| `KEYBOARD` | `InputCode.key("SPACE")` | Key names are uppercased |
| `MOUSE_BUTTON` | `InputCode.mouse(0)` | Button index (0=primary) |
| `GAMEPAD_BUTTON` | `InputCode.gamepadButton(0, "A")` | Pad index + button name |
| `GAMEPAD_AXIS` | `InputCode.gamepadAxis(0, "LEFT_X")` | Pad index + axis name |

InputCodes can be serialized (`encode()`) and deserialized (`decode()`) for persisting bindings.

### Input State

The `Input` class tracks three sets per frame:
- **`down`** — currently held inputs
- **`pressed`** — newly pressed this frame (cleared each frame)
- **`released`** — released this frame (cleared each frame)

Plus mouse position (`mouseX`, `mouseY`), scroll delta (`scrollDeltaY`), and gamepad axis values.

```java
input.isKeyDown("W")        // held right now
input.wasKeyPressed("SPACE") // just pressed this frame
input.wasKeyReleased("E")   // just released this frame
input.isMouseDown(0)         // left mouse held
input.getGamepadAxis(0, "LEFT_X") // -1.0 to 1.0
```

---

## Runtime Boot Sequence

Entrypoint: `runtime/src/main/java/com/jvn/runtime/JvnApp.java`

1. Parse CLI flags (`--script`, `--ui`, `--jes`, `--audio`, `--assets`, etc.).
2. Initialize localization.
3. Build `AssetManager`:
   - Classpath only, or
   - filesystem+classpath overlay when `--assets` is provided.
4. Build `Engine` with `ApplicationConfig`.
5. Set `VnInteropFactory` and `MenuActionHandler`.
6. Launch scene path:
   - `--jes`: load JES scene(s) directly.
   - otherwise: push main menu scene.
7. Launch renderer backend (`fx` or `swing`).
8. Renderer pumps `engine.update(deltaMs)` each frame.

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

Current persisted runtime continuity includes:
- call stack (`CALL`/`RETURN` safe reload)
- character-global-position state
- autoplay timer and UX mode flags
- optional serialized RPG payload

Reliability behaviors:
- schema normalization during load/save
- temp file + atomic move writes
- autosave slot rotation
- migration write-back when an old save is upgraded

## Editor Architecture

- `EditorApp` composes project tree, tabbed file editors, and addable side panels via chooser tabs (`+`).
- `FileEditorTab` routes file types to matching editor widgets.
- Visual editors keep properties text synchronized for source-control-friendly config files.
- Layout studios can run as dedicated external windows for canvas-heavy menu/dialogue editing workflows.
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

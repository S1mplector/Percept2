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

The `Engine` class is the central orchestrator. It owns the scene stack, input state, tween runner, frame statistics, and the multi-phase update loop.

### Update Loop — Frame Pipeline

Each frame, the renderer calls `engine.update(deltaMs)`. The engine runs a seven-stage pipeline:

1. **Record & notify** — record raw delta in `FrameStats`; fire `EngineListener.preUpdate(rawDelta)`.
2. **Delta clamping** — caps `deltaMs` to `maxDeltaMs` (default 75ms) to prevent simulation explosions from frame spikes or debugger pauses.
3. **Delta smoothing** — optional exponential moving average (`deltaSmoothing`, default 0.1). Smooths frame-to-frame jitter. Set to 0 to disable.
4. **Time scaling** — multiplies by `timeScale` (default 1.0). Use for slow-motion (`0.5`), fast-forward (`2.0`), or freeze (`0.0`).
5. **Fixed update phase** — if `fixedUpdateMs > 0`, accumulates scaled time and calls `Scene.fixedUpdate(fixedUpdateMs)` at deterministic intervals (up to `maxFixedSteps` per frame). Computes `interpolationAlpha` from the accumulator remainder.
6. **Variable update phase** — updates `TweenRunner`, then calls `Scene.update(effectiveDelta)` once per frame.
7. **Late update phase** — calls `Scene.lateUpdate(effectiveDelta)` once per frame, after entity positions are finalized. Ideal for camera follow.
8. **Post-frame** — clears input `pressed`/`released` sets; fires `EngineListener.postUpdate(effectiveDelta)`.

If the engine is **paused** or not started, stages 5–7 are skipped — input is still processed and listeners still fire, so pause menus remain responsive.

```java
// Default timing settings
maxDeltaMs = 75;           // clamp extreme deltas
deltaSmoothing = 0.1;      // exponential smoothing factor [0..1]
fixedUpdateMs = 0;          // 0 = variable timestep (disabled)
maxFixedSteps = 5;          // safety limit per frame
timeScale = 1.0;            // global time multiplier [0..10]
```

### Interpolation Alpha

When using a fixed timestep, the accumulator will have a fractional remainder after the last physics tick. `engine.getInterpolationAlpha()` returns this as a value in [0.0, 1.0]. Renderers should use it to interpolate visual state between the previous and current physics snapshots for stutter-free rendering:

```java
double renderX = prevX + (curX - prevX) * engine.getInterpolationAlpha();
```

Returns 0.0 when fixed timestep is disabled.

### Time Scale

`engine.setTimeScale(scale)` multiplies the effective delta after clamping and smoothing. Clamped to [0.0, 10.0].

```java
engine.setTimeScale(0.5);  // slow-motion
engine.setTimeScale(2.0);  // fast-forward
engine.setTimeScale(0.0);  // frozen (game time stops, input still works)
```

### Pause

`engine.setPaused(true)` freezes all game logic (no scene updates, fixed updates, tweens, or late updates) while keeping input responsive and listeners active. This is separate from `timeScale(0)` — pause skips all update phases entirely rather than passing zero delta.

### Frame Statistics

`engine.frameStats()` returns a `FrameStats` instance that tracks timing over a rolling 60-frame window:

| Method | Description |
|--------|-------------|
| `getFps()` | Frames per second (averaged) |
| `getAvgMs()` | Average frame time in ms |
| `getMinMs()` | Minimum frame time in window |
| `getMaxMs()` | Maximum frame time in window |
| `getTotalFrames()` | Total frames since engine start |

Stats are recorded every frame, even when paused.

### Engine Listeners

Register `EngineListener` instances for frame-level hooks without modifying engine internals:

```java
engine.addListener(new EngineListener() {
    @Override public void preUpdate(long rawDeltaMs) { /* profiling start */ }
    @Override public void postUpdate(long effectiveDeltaMs) { /* profiling end */ }
});
```

Listeners fire even when the engine is paused. Both methods have default no-op implementations.

### ApplicationConfig

Built via `ApplicationConfig.builder()`:

| Field | Default | Description |
|-------|---------|-------------|
| `title` | `"JVN"` | Window title |
| `width` | `960` | Initial window width |
| `height` | `540` | Initial window height |
| `fixedUpdateMs` | `0` | Fixed update step (0 = variable) |
| `fixedUpdateMaxSteps` | `5` | Max substeps per frame |
| `timeScale` | `1.0` | Initial time scale multiplier |

### TweenRunner

A lightweight task runner for time-based animations. `Engine.tweens()` returns the shared instance. Add `TweenTask` subclasses (implement `update(deltaMs)` + `isFinished()`); finished tasks are auto-removed. Tweens run during the variable update phase (not the fixed update phase).

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
    default void onEnter() {}           // scene becomes active
    default void onExit() {}            // scene is removed from stack
    default void onPause() {}           // another scene pushed on top
    default void onResume() {}          // scene becomes active again after pop
    default void fixedUpdate(long dt) {} // deterministic rate (physics, gameplay sim)
    void update(long deltaMs);           // once per frame (animation, UI, input)
    default void lateUpdate(long dt) {}  // after update (camera follow, post-corrections)
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

## JES↔VNS Coordination — How JES Orchestrates VNS

While VNS scripts appear to be standalone visual novel files, **JES infrastructure silently coordinates VNS at every layer**. The relationship is not a simple "VNS can call JES scenes" — JES provides the animation engine, the entity model, the scene graph, and the audio pipeline that VNS relies on for all non-trivial presentation.

### The Coordination Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Engine.update(deltaMs)                                     │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  VnScene (active on scene stack)                      │  │
│  │  ├── VnState.updateCharacterAnimations(dt)            │  │
│  │  │     └── CharacterVisual: easing, alpha, offsets    │  │
│  │  ├── VnState.updateTimelineRunners(dt)                │  │
│  │  │     └── TimelineRunner → SceneAccessor → Entity2D  │  │
│  │  ├── VnState.updateScreenEffects(dt)                  │  │
│  │  │     └── shake, flash, fade timers                  │  │
│  │  └── processCurrentNode() loop                        │  │
│  │        └── EXTERNAL → VnInterop.handle()              │  │
│  │              ├── DefaultVnInterop (core providers)     │  │
│  │              │     ├── jes_timeline → TimelineRunner   │  │
│  │              │     ├── inline timeline → TimelineRunner│  │
│  │              │     └── var, audio, screen, etc.        │  │
│  │              └── RuntimeVnInterop (runtime providers)  │  │
│  │                    ├── jes push/replace/pop/call       │  │
│  │                    ├── vns goto/push/replace           │  │
│  │                    └── menu settings/save/load/main    │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  JesScene2D (pushed onto scene stack by VNS or boot)  │  │
│  │  ├── Entity2D scene graph + physics + input actions   │  │
│  │  ├── TimelineRunner (keyframe animation)              │  │
│  │  ├── Call handlers: return, vns, startVns, hud, pop   │  │
│  │  └── JesVnBridge (attaches VNS launch capabilities)   │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Layer 1: JES Animation Infrastructure Inside VNS

VNS doesn't have its own animation engine. Every animated effect in a VN scene is powered by JES infrastructure:

**Character animation** — `VnState.showCharacterAnimated()` uses `Easing.Type` from `com.jvn.core.animation` (the same easing library JES timelines use) to drive slide-in, slide-out, position-move, and expression-fade animations on `CharacterVisual` objects. The animation math is identical to what JES timeline keyframes use.

**Timeline execution** — VNS scripts can embed JES timelines directly via `[jes_timeline name]` or inline timeline blocks. These create `TimelineRunner` instances that are stored in `VnState.activeTimelines` and ticked every frame by `VnState.updateTimelineRunners(deltaMs)`. The timeline runner uses a `SceneAccessor` to resolve entity names to `Entity2D` instances.

**SceneAccessor bridge** — `RuntimeVnInterop` wires a `SceneAccessor` that first checks for a live `JesScene2D` on the stack (for entity lookups via `jes.find(name)`), and falls back to creating `VnCharacterProxyEntity` proxies that map VN character names to lightweight `Entity2D` wrappers. This means JES timelines can animate VN characters by name — the proxy forwards position/scale/rotation/alpha changes to `VnState.CharacterVisual` offsets.

**Audio routing** — The `SceneAccessor.playAudioCue()` and `stopAudio()` hooks route timeline audio cues through the VN scene's `AudioFacade`, mapping JES channel names (`music`, `bgm`, `voice`, `sfx`) to VN audio operations.

**Camera control** — Timeline tracks with `CAMERA_X`, `CAMERA_Y`, `CAMERA_ZOOM` keyframes route through `SceneAccessor.setCameraX/Y/Zoom()`, which the runtime wires to the active `Camera2D`.

### Layer 2: VNS→JES Scene Transitions

VNS scripts push full JES scenes onto the engine's scene stack for minigames, cutscenes, or interactive segments:

| VNS Command | Engine Operation | Return Behavior |
|-------------|-----------------|-----------------|
| `[jes push script.jes]` | `engine.scenes().push(jesScene)` | JES calls `return` → pops JES, resumes VNS |
| `[jes replace script.jes]` | `engine.scenes().replace(jesScene)` | Replaces VNS entirely |
| `[jes pop]` | `engine.scenes().pop()` | Pops current scene |
| `[jes call name k=v ...]` | `jes.invokeCall(name, props)` | Calls handler on top JES scene |
| `[jes push script.jes label after_battle with difficulty=3]` | Push with init props | Init props passed to `init` call handler |

When `RuntimeVnInterop.loadJes()` creates a JES scene from VNS, it automatically wires:

1. **`return` / `vns` call handlers** — pop the JES scene and optionally jump to a VNS label, carrying variables back:
   ```
   // Inside JES scene:
   call "return" { label: "after_minigame", score: 42 }
   // → pops JES, sets $score=42 in VnState, jumps to :after_minigame in VNS
   ```
2. **`hud` call handler** — displays HUD messages on the VN scene's overlay
3. **`pop` call handler** — shortcut to pop the current scene
4. **`JesVnBridge`** — attaches `startVns` / `startVn` / `vns` call handlers that let JES launch VN segments mid-gameplay

### Layer 3: JES→VNS via JesVnBridge

Source: `runtime/src/main/java/com/jvn/runtime/JesVnBridge.java`

The bridge allows JES scenes to start VN segments (dialogue, cutscenes) and resume when they finish:

```
// Inside a JES scene, trigger a VN dialogue segment:
call "startVns" { script: "chapter2.vns", label: "boss_intro", popOnExit: true }
```

**How it works:**

1. `JesVnBridge.attach(jesScene)` registers `startVns`/`startVn`/`vns` call handlers on the JES scene.
2. When called, `startVns()` loads the VNS script via `VnScenarioLoader`, wraps it in a `BridgedVnScene` (a `VnScene` subclass with `onEnter`/`onExit` callbacks), and pushes it onto the scene stack.
3. The JES scene is paused (`jes.setPaused(true)`) — it stays on the stack but doesn't update.
4. When the VN segment ends, `BridgedVnScene.onExit()` fires:
   - Unpauses the JES scene (`jes.setPaused(false)`)
   - Calls `jes.invokeCall("vnsEnded", {script, label})` so the JES scene can react
   - Optionally pops itself off the stack (controlled by `popOnExit`, default `true`)
5. Settings (text speed, volumes, skip behavior, physics params, input profile) are copied from the current VN scene to the new one via `copySettings()`.
6. Audio facade is inherited so BGM continues seamlessly across the transition.

### Layer 4: VnCharacterProxyEntity — The Entity2D Bridge

Source: `runtime/src/main/java/com/jvn/runtime/RuntimeVnInterop.VnCharacterProxyEntity`

When a JES timeline animates a VN character by name (e.g., a track targeting `"alice"`), the `SceneAccessor` creates a `VnCharacterProxyEntity` — a lightweight `Entity2D` subclass that forwards property changes to the VN character's `CharacterVisual`:

- **x/y** → interpreted as pixel **offsets** from the character's natural slot position
- **scale** → applied to character sprite scale
- **rotation** → applied to character rotation
- **alpha** → applied to character opacity

This means JES timeline animations work transparently on VN characters without the VN system needing to know about the entity/component model.

### Layer 5: VnCharacterSceneAccessor (Editor Preview)

Source: `core/src/main/java/com/jvn/core/vn/VnCharacterSceneAccessor.java`

The editor uses a separate `SceneAccessor` implementation that creates virtual `Entity2D` proxies for VN character names during preview. This allows the editor's timeline preview to animate characters without a live runtime. Event cues (like `"expression"` changes) are logged for the diagnostics panel.

### Summary: What VNS Delegates to JES

| Capability | JES Component Used | VNS Entry Point |
|------------|-------------------|-----------------|
| Character slide/fade animation | `Easing` library | `VnState.showCharacterAnimated()` |
| Keyframe animation in VN | `TimelineRunner` + `TimelineData` | `[jes_timeline name]` or inline block |
| Entity lookup for timelines | `SceneAccessor` → `Entity2D` | `RuntimeVnInterop.configureDefaultSceneAccessor()` |
| Character proxy for timelines | `VnCharacterProxyEntity` (extends `Entity2D`) | Automatic via `SceneAccessor.findEntity()` |
| Camera animation | `SceneAccessor.setCameraX/Y/Zoom()` | Timeline tracks with `CAMERA_*` properties |
| Audio cues from timelines | `SceneAccessor.playAudioCue()` | Timeline audio cue entries |
| Scene transitions to gameplay | `JesScene2D` on scene stack | `[jes push script.jes]` |
| VN segments from gameplay | `BridgedVnScene` + `JesVnBridge` | JES `call "startVns" { ... }` |
| Return from JES to VNS | Call handler wiring in `loadJes()` | JES `call "return" { label: "...", k: v }` |
| Event cues (expression, etc.) | `SceneAccessor.onEventCue()` | Timeline event cue entries |

VNS is, in essence, a **high-level dialogue and branching scripting layer** that delegates all animation, entity management, scene graph, and audio integration to the JES infrastructure underneath.

---

## VNS Data Flow

1. `.vns` text is parsed by `VnScriptParser` into `VnScenario` (a list of `VnNode` entries with labels and character/background metadata).
2. `VnScene` implements `Scene` and drives an iterative command loop (`processCurrentNode()`) that chains through instant nodes (SHOW, HIDE, BACKGROUND, AUDIO, JUMP, CALL, RETURN, EXTERNAL) and blocks on interactive nodes (DIALOGUE, CHOICE) and timed nodes (WAIT, TRANSITION).
3. External commands (`[provider payload]`) become `VnExternalCommand` objects routed to `VnInterop.handle()`. The `DefaultVnInterop` handles core providers (var, cond, audio, screen, ui, java, jes_timeline, etc.). `RuntimeVnInterop` extends this with runtime-only providers (jes, menu, vns).
4. `VnState` manages all mutable state: node index, visible characters, variables, history, rollback stack, active transitions, screen effects, timeline runners, settings, and save slot state.

## JES Data Flow

1. `JesTokenizer` creates tokens with line/column metadata.
2. `JesParser` builds AST with strict property validation.
3. `JesLoader` materializes entities/components and bindings into `JesScene2D`.
4. `JesScene2D` updates physics, input actions, timeline actions, AI, HUD, and call handlers each frame. Call handlers provide the extensibility point for VNS integration (`return`, `startVns`, `vnsEnded`, etc.).

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

## Design Principles Used Here

- **Data-driven config** for menu/layout/style and dialogue UI
- **Strict parser diagnostics** for script quality
- **Fallback defaults** to keep runtime resilient when content is missing
- **Editor-first workflows** with immediate visual feedback and synchronized plain-text assets

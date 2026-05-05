# VNS ↔ JES Architecture & Coordination

How JES and VNS coordinate behind the scenes — scene stack management, interop routing, timeline runners, character proxy entities, bridge lifecycle, and data flow through the runtime layer.

Bridge source:
- `modules/runtime/src/main/java/com/jvn/runtime/JesVnBridge.java`
- `modules/runtime/src/main/java/com/jvn/runtime/RuntimeVnInterop.java`
- `modules/runtime/src/main/java/com/jvn/runtime/BridgedVnScene.java`
- `modules/core/src/main/java/com/jvn/core/vn/VnScene.java`
- `modules/core/src/main/java/com/jvn/core/vn/DefaultVnInterop.java`
- `modules/core/src/main/java/com/jvn/core/animation/SceneAccessor.java`
- `modules/core/src/main/java/com/jvn/core/vn/VnCharacterSceneAccessor.java`

---

## Overview

JVN's visual novel system (VNS) and engine scripting system (JES) are designed as independent scene types that share the same `Engine` scene stack. Neither system "owns" the other — instead, they coordinate through well-defined interfaces:

| Mechanism | Purpose |
|-----------|---------|
| **Scene stack** | Push/pop/replace to transfer control between VNS and JES |
| **VnInterop** | VNS dispatches external commands to interop handlers |
| **Call handlers** | JES registers named callbacks; VNS or Java can invoke them |
| **Timeline runners** | JES animation data runs inside VNS scenes via `SceneAccessor` |
| **BridgedVnScene** | Lifecycle hooks (`onEnter`, `onExit`) for cross-scene coordination |

The key insight: **VNS and JES never call each other directly**. All coordination flows through the `Engine` scene stack and the interop/bridge layers in the `runtime` module.

---

## Scene Stack Model

The `Engine` manages a stack of `Scene` objects. Only the top scene receives `update()` calls. VNS and JES scenes coexist on this stack:

### Push (overlay)

`engine.scenes().push(scene)` adds a scene on top. The previous scene stays on the stack but stops receiving updates. Used for:
- VNS launching a JES minigame (`[jes push]`)
- JES launching a VNS dialogue segment (`call "startVns"`)

### Pop (return)

`engine.scenes().pop()` removes the top scene, resuming the one beneath. Used for:
- JES returning to VNS after a minigame (`call "return"`)
- VNS exiting back to JES (`BridgedVnScene.onExit`)

### Replace (transition)

`engine.scenes().replace(scene)` removes the current top and inserts the new scene. Used for permanent transitions where no return is needed.

---

## VNS → JES: Launching a JES Scene

When a VNS script executes `[jes push scene.jes label after with difficulty=hard]`, the following chain occurs:

```text
VNS Script
  │  [jes push scene.jes label after with difficulty=hard]
  │
  ▼
VnScene.processExternalNode()
  │  Dispatches VnExternalCommand { provider: "jes", payload: "push scene.jes ..." }
  │
  ▼
RuntimeVnInterop.handle()
  │  Parses subcommand: "push"
  │  Loads JES scene from "scene.jes"
  │  Registers call handlers on JES scene:
  │    - "return" → pop JES, jump VNS to return label, copy props to VN vars
  │    - "vns"    → alias for "return"
  │    - "hud"    → show HUD message on the VNS scene
  │    - "pop"    → pop JES scene only
  │  Sets return label = "after"
  │  Fires init call with { difficulty: "hard" }
  │
  ▼
Engine.scenes().push(jesScene)
  │  VNS scene pauses (no more update() calls)
  │  JES scene starts receiving update() calls
  │
  ▼
JES Scene active
  │  Receives init call: props = { difficulty: "hard" }
```

### RuntimeVnInterop JES Subcommands

| Subcommand | Behavior |
|------------|----------|
| `push` | Load JES, push onto stack, VNS pauses underneath |
| `replace` | Load JES, replace VNS on stack (no return) |
| `pop` | Pop the current JES scene from the stack |
| `call <name>` | Invoke a registered call handler on the active JES scene |

---

## JES → VNS: Returning from a JES Scene

When a JES scene is done (e.g., minigame over), it calls `return`:

```text
JES Scene
  │  call "return" { label: "after" score: 1200 rank: "A" }
  │
  ▼
RuntimeVnInterop (registered handler)
  │  1. Copies all props (except "label"/"goto") into VNS variables:
  │     state.variables["score"] = 1200
  │     state.variables["rank"]  = "A"
  │  2. Pops JES scene from engine stack
  │  3. VNS scene resumes (receives update() again)
  │  4. Jumps to return label "after" in VNS scenario
  │
  ▼
VNS Script continues at @label after
  │  narrator: You scored ${score} with rank ${rank}!
```

JES return values are copied into VNS variables as scalar values (`String`, `Boolean`, `Integer`, `Double`) depending on what JES sends.

---

## JES → VNS: Starting a VNS Segment from JES

The `JesVnBridge` enables JES scripts to launch VNS segments:

```text
JES Scene
  │  call "startVns" { script: "chapter2.vns" label: "intro" }
  │       (aliases: "startVn", "vns")
  │
  ▼
JesVnBridge.startVns()
  │  1. Loads VnScenario via VnScenarioLoader
  │  2. Creates BridgedVnScene (extends VnScene)
  │  3. Sets onEnter callback: inherit audio facade from parent VN (if any)
  │  4. Sets onExit callback:
  │     - Resume JES (jes.setPaused(false))
  │     - Fire "vnsEnded" call back to JES
  │     - Pop VNS scene from stack (if popOnExit=true)
  │  5. Optionally jumps to label
  │  6. Pushes/replaces VNS scene on engine stack
  │  7. Pauses JES scene
  │
  ▼
VNS Scene active (BridgedVnScene)
  │  Runs VNS script normally
  │  When script hits [end] → onExit fires → JES resumes
```

### BridgedVnScene

`BridgedVnScene` extends `VnScene` with lifecycle hooks:

| Hook | When | Purpose |
|------|------|---------|
| `onEnter` | Scene becomes active | Inherit audio facade, set up state |
| `onExit` | Scene ends or is popped | Resume parent JES, fire completion event |

This enables clean parent-child relationships without tight coupling.

---

## Timeline Runners in VNS

JES timeline animations can run inside a VNS scene. This allows VNS scripts to use JES's keyframe animation system for character choreography, camera movements, and audio cues.

### How It Works

```text
VNS Script
  │  [jes_timeline my_animation]
  │       or
  │  [jes_timeline_inline ...]
  │
  ▼
DefaultVnInterop / RuntimeVnInterop
  │  1. Parses timeline data (from registry or inline)
  │  2. Creates TimelineRunner(data, sceneAccessor)
  │  3. Adds runner to VnState.activeTimelines
  │
  ▼
VnScene.update(deltaMs)
  │  state.updateTimelineRunners(deltaMs)
  │  Each runner:
  │    runner.update(deltaMs) → applyFrame(elapsedMs)
  │    For each track:
  │      entity = sceneAccessor.findEntity(track.entityName)
  │      Apply: position, rotation, scale, alpha
  │      Apply: camera moves, audio cues, event cues
  │  Finished runners are auto-removed
```

### SceneAccessor Interface

`SceneAccessor` is the bridge between timeline animations and the scene's entity model:

```java
public interface SceneAccessor {
    Entity2D findEntity(String name);
    default void setCameraX(double x) {}
    default void setCameraY(double y) {}
    default void setCameraZoom(double zoom) {}
    default void playAudioCue(String trackPath, String channel,
                              double volume, boolean loop, double fadeInMs) {}
    default void stopAudio(String channel) {}
    default void onEventCue(String type, Map<String, String> payload) {}
}
```

In JES scenes, `SceneAccessor` maps to the real entity graph. In VNS scenes, a specialized implementation provides virtual entities.

### VnCharacterSceneAccessor

`VnCharacterSceneAccessor` bridges JES timelines to VNS character visuals:

```java
public class VnCharacterSceneAccessor implements SceneAccessor {
    private final Map<String, Entity2D> proxies = new ConcurrentHashMap<>();

    @Override
    public Entity2D findEntity(String name) {
        // Creates a virtual Entity2D proxy for each character name
        return proxies.computeIfAbsent(name, k -> new Entity2D());
    }

    @Override
    public void onEventCue(String type, Map<String, String> payload) {
        // Captures expression changes triggered by timeline events
        if ("expression".equals(type)) {
            lastExpressionTarget = payload.getOrDefault("target", "");
            lastExpressionValue = payload.getOrDefault("value", "");
        }
    }
}
```

When a timeline animates `x`, `y`, `alpha`, etc. on a character name, the proxy `Entity2D` receives those values. The VNS renderer can then use these coordinates for positioning instead of the default slot-based system.

### Timeline → Character Animation Flow

```text
Timeline track: entity="hero" property="x" keyframes=[0ms→100, 500ms→400]
  │
  ▼
SceneAccessor.findEntity("hero")
  │  Returns proxy Entity2D for "hero"
  │
  ▼
TimelineRunner.applyFrame()
  │  proxy.setX(interpolated_value)
  │
  ▼
VNS Renderer
  │  Reads proxy.getX() for character draw position
  │  (instead of default slot position)
```

---

## Interop Provider Routing

All VNS external commands (`[command]` that map to `EXTERNAL` nodes) are routed through the `VnInterop` interface:

```java
public interface VnInterop {
    VnInteropResult handle(VnExternalCommand command, VnScene scene);
}
```

`VnExternalCommand` has two fields:
- **`provider`** — routing key (e.g., `"jes"`, `"java"`, `"var"`, `"hud"`)
- **`payload`** — free-form argument string

### Provider Routing Table

#### DefaultVnInterop (core module)

Available without the runtime module. Handles:

| Provider | Purpose |
|----------|---------|
| `hud` | Show a temporary HUD message |
| `java` | Invoke a static Java method via reflection |
| `jes` | Placeholder — shows HUD notice |
| `jes_timeline` | Run a named timeline from `TimelineRegistry` |
| `jes_timeline_inline` | Parse and run an inline timeline block |
| `var` | Set/inc/dec/flag/unflag/clear variables |
| `cond` | Conditional jump (`if <expr> goto <label>`) |
| `settings` | Modify VnSettings (textspeed, volumes, etc.) |
| `save` | Quick save / autosave |
| `mode` | Toggle skip/auto modes |
| `ui` | Toggle UI visibility |
| `history` | Toggle history overlay |
| `audio` | Audio control (visualizer, pause, resume) |
| `screen` | Screen effects (shake, flash, clear) |
| `char` | Character control (global mode, move, expression) |

#### RuntimeVnInterop (runtime module)

Extends `DefaultVnInterop` with full JES integration:

| Provider | Purpose |
|----------|---------|
| `jes` | Full JES scene management (push/replace/pop/call) |
| `menu` | Open menu scenes (main, settings, load, save) |
| `vns` | VNS scene transitions (`goto`, `push`, `replace`) |

### VnInteropResult

The return value controls whether the VNS command loop continues to the next node:

| Result | Behavior |
|--------|----------|
| `VnInteropResult.advance()` | Advance to the next node (most commands) |
| `VnInteropResult.stay()` | Stay at current position (conditional jumps that succeeded) |

---

## Preflight and State Restoration

When loading a save or jumping to a label, the engine needs to reconstruct the visual state. `VnScene.preflightState(targetIndex)` replays nodes 0 through `targetIndex - 1` non-interactively.

### Safe Interop Providers During Preflight

Only a subset of interop providers run during preflight to avoid side effects:

| Provider | Allowed in Preflight |
|----------|---------------------|
| `var` | Yes — variables must be correct |
| `ui` | Yes — UI state must match |
| `audio` | Yes — ambient audio should play |
| `char` | Yes — character state must match |
| `settings` | Yes — settings must be restored |
| `mode` | Yes — skip/auto mode state |
| `screen` | Yes — screen effect state |
| `history` | Yes — history overlay state |
| `jes` | **No** — don't launch JES scenes during preflight |
| `java` | **No** — don't call arbitrary Java during preflight |
| `hud` | **No** — don't show transient messages |
| `jes_timeline` | **No** — don't start animations |

---

## Complete Data Flow Diagram

---

## Key Design Decisions

### Why separate interop layers?

`DefaultVnInterop` lives in `core` and handles everything that doesn't need the runtime (variables, conditions, settings, timelines). `RuntimeVnInterop` lives in `runtime` and adds JES scene management, menu navigation, and other runtime-dependent features. This keeps `core` free of runtime dependencies.

### Why proxy entities instead of direct character access?

`VnCharacterSceneAccessor` creates lightweight `Entity2D` proxies instead of exposing VNS character internals. This means:
- Timeline animations don't need to know about VNS character slots
- The same timeline can animate characters in both JES and VNS contexts
- No coupling between the animation system and the VNS renderer

### Why `BridgedVnScene` instead of direct callbacks?

`BridgedVnScene` encapsulates the lifecycle hooks (`onEnter`, `onExit`) that coordinate with parent JES scenes. This keeps `VnScene` itself simple and unaware of JES, while the bridge pattern handles cross-scene coordination cleanly.

### Why scalar-typed cross-boundary data?

Interop payloads are parsed as simple scalar values (`String`, `Boolean`, `Integer`, `Double`). This keeps the protocol lightweight while preserving numeric/boolean behavior for conditions and game state logic.

---

## Related Docs

- [VNS Interop Commands](vns-interop.md) — script-level interop reference
- [Java + JES Cross Development](java-jes-cross-development.md) — hybrid architecture patterns
- [Scene Lifecycle & State](../runtime/vns-scene-lifecycle.md) — VnScene node processing
- [Timeline Animation](../../timeline/animation/timeline-animation.md) — TimelineRunner and keyframes
- [JES Bridge & Java Hooks](../../jes/integration/jes-bridge.md) — JES-side call handlers

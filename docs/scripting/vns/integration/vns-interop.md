# VNS Interop & Integration

Complete reference for integrating VNS scripts with JES scenes, Java code, inline timelines, and menu systems.

Interop classes:
- `core/src/main/java/com/jvn/core/vn/DefaultVnInterop.java`
- `runtime/src/main/java/com/jvn/runtime/RuntimeVnInterop.java`

---

## How Interop Works

When the VNS parser sees `[call <provider> <payload>]` (or shorthand forms like `[jes ...]`, `[java ...]`), it emits a `VnExternalCommand` node.

At runtime:
1. `VnScene` reaches the external command node.
2. The active `VnInterop` implementation receives the provider name and payload.
3. The interop handler returns `advance` (move to next node) or `stay` (flow already changed).

---

## Dialogue Presentation Modes

The built-in `mode` and `char` providers now control alternate dialogue presentation without custom hacks.

### Standard / NVL / Bubble

```vns
[mode dialogue standard]
[mode dialogue nvl]
[mode bubble on]
[mode bubble off]
[mode nvl toggle]
```

Supported forms:

- `[mode dialogue standard|nvl|bubble]`
- `[mode presentation standard|nvl|bubble]`
- `[mode say standard|nvl|bubble]`
- `[mode nvl on|off|toggle]`
- `[mode bubble on|off|toggle]`

### Bubble Placement Overrides

Use the `char` provider to override per-speaker bubble placement:

```vns
[char lavender bubble left]
[char lavender bubble center]
[char lavender bubble right]
[char lavender bubble auto]
[char lavender bubble_offset 12 -8]
[char lavender bubble clear]
```

Supported forms:

- `[char <id> bubble left|center|right|auto]`
- `[char <id> bubble clear|reset]`
- `[char <id> bubble_offset <x> <y>]`

These settings persist through the VN state variable layer, so they behave consistently with saves and runtime mode changes.

---

## JES Scene Integration

### Pushing a JES Scene

Launch a JES scene and return to VNS afterward:

```vns
[jes push game/minigames/puzzle.jes label after_puzzle]
```

With launch properties:

```vns
[jes push game/minigames/arena.jes label after_arena with difficulty=hard round=2]
```

- `label` — the VNS label to jump to when JES returns.
- `with k=v` — properties passed to JES via `call "init" { ... }`.

### Replacing the Current Scene

Replace the VNS scene with a JES scene (no return):

```vns
[jes replace game/scenes/credits.jes]
```

### Popping a JES Scene

Return from JES back to VNS manually:

```vns
[jes pop]
```

### Calling JES Handlers

Invoke registered call handlers on the current JES scene:

```vns
[jes call spawnWave count=5 speed=120]
[jes call resetLevel]
```

### Alternative Shorthand Forms

```vns
[jes_push game/minigames/arena.jes]
[jes_replace game/scenes/boss.jes]
[jes_pop]
[jes_call spawnWave count=5]
```

### JES -> VNS Return

Inside a JES scene, return to VNS with data:

```jes
call "return" { label: "after_game" score: 1200 rank: "A" }
```

Return behavior:
- Pops the JES scene.
- Copies all props (except `label`/`goto`) into VN variables.
- Jumps to the return label.

`call "vns" { ... }` is a supported alias for `call "return"`.

### End-to-End Example

**VNS script:**

```vns
@scenario minigame_demo
@character narrator "Narrator"

@label start
narrator: Time for a challenge!
[jes push game/minigames/aim.jes label after_game with stage=2]

@label after_game
narrator: Your score was ${score}. Rank: ${rank}.
[if score >= 1000 goto great]
[jump okay]

@label great
narrator: Amazing performance!
[end]

@label okay
narrator: Not bad, try again sometime.
[end]
```

**JES scene (when challenge completes):**

```jes
call "return" { label: "after_game" score: 1320 rank: "S" }
```

---

## Java Interop

### Static Method Calls

Call a public static Java method via reflection:

```vns
[java com.example.GameHooks#beginEncounter goblin 3]
[java com.example.Analytics#logEvent chapter_start]
[java com.jvn.core.util.DebugTools#dumpState]
```

### Syntax

```text
[java fully.qualified.Class#methodName arg1 arg2 ...]
```

### Argument Coercion

Arguments are automatically coerced:
- `true` / `false` -> `boolean`
- Numeric tokens -> `int` or `double`
- Everything else -> `String`

The runtime matches the method by name and parameter count, then coerces arguments to match the method signature.

### Security

- Only classes with allowed prefixes can be called (default: `com.jvn.`).
- Prefer thin, stable wrapper methods over exposing deep internals.
- Treat Java interop as trusted-script functionality.

### Example Java Hook

```java
package com.jvn.game;

public class GameHooks {
    public static String beginEncounter(String enemyType, int count) {
        // Start encounter logic
        return "encounter_started";
    }

    public static void logEvent(String eventName) {
        System.out.println("[Event] " + eventName);
    }
}
```

```vns
[java com.jvn.game.GameHooks#beginEncounter dragon 1]
[java com.jvn.game.GameHooks#logEvent boss_defeated]
```

---

## Inline Timelines (Puppeteer-Compatible)

VNS supports inline JES timeline blocks for one-off animations near story text.

### Syntax

```vns
timeline {
  entity "entityName" {
    0ms { x: 640, y: 396 }
    300ms { x: 780, y: 396, easing: ease_out }
  }
  cameraMove 300ms 0 0 0.92
  playAudio "assets/audio/sfx/whoosh.ogg"
}
```

### Timeline Actions

| Action | Description |
|--------|-------------|
| `entity "name" { ... }` | Entity keyframe animation |
| `cameraMove <ms> <x> <y> <zoom>` | Camera position/zoom change |
| `cameraZoom <ms> <zoom>` | Camera zoom only |
| `playAudio "path"` | Play audio cue |

### Entity Keyframes

```vns
timeline {
  entity "hero" {
    0ms { x: 400, y: 300, alpha: 0.0 }
    500ms { x: 640, y: 300, alpha: 1.0, easing: ease_out }
  }
}
```

Supported properties: `x`, `y`, `alpha`, `rotation`, `scaleX`, `scaleY`

### Camera Keyframes

```vns
timeline {
  cameraMove 0ms 0 0 1.0
  cameraMove 400ms 100 0 0.92
}
```

### Examples

**Slide character in with camera zoom:**

```vns
[show hero center neutral]
timeline {
  entity "hero" {
    0ms { x: -200, alpha: 0.0 }
    600ms { x: 640, alpha: 1.0, easing: ease_out }
  }
  cameraMove 0ms 0 0 1.0
  cameraMove 600ms 0 0 0.95
}
hero: I've arrived!
```

**Quick camera shake effect:**

```vns
timeline {
  cameraMove 0ms 0 0 1.0
  cameraMove 50ms 5 3 1.0
  cameraMove 100ms -4 -2 1.0
  cameraMove 150ms 3 1 1.0
  cameraMove 200ms 0 0 1.0
}
```

**Dramatic entrance with audio:**

```vns
timeline {
  entity "villain" {
    0ms { alpha: 0.0, y: 420 }
    800ms { alpha: 1.0, y: 360, easing: ease_out }
  }
  playAudio "assets/audio/sfx/dark_whoosh.ogg"
  cameraMove 800ms 0 -20 0.9
}
```

### Named Registry Timelines

For reusable animation clips, register them in the `TimelineRegistry` (typically via Puppeteer) and play them by name:

```vns
[call jes_timeline hero_entrance]
[call jes_timeline camera_pan_left]
[call jes_timeline dramatic_zoom]
```

Use inline blocks for one-off animations close to story text; use named timelines for reusable clips.

---

## Menu Commands

### Opening Menu Scenes

```vns
[menu settings]     # open settings
[menu save]         # open save screen
[menu load]         # open load screen
[menu main]         # return to main menu
[menu extras]       # open custom menu by ID
```

### Shorthand Commands

```vns
[settings]                          # open settings
[mainmenu]                          # return to main menu
[mainmenu scripts/story/start.vns]  # main menu with script override
```

---

## HUD Messages

```vns
[hud Saved!]
[hud Chapter 2 Complete]
[hud Score: ${score}]
```

Shows a temporary on-screen message (auto-expires after ~2 seconds). Supports `${var}` interpolation.

---

## Provider Reference Summary

### Default Providers (always available)

| Provider | Purpose |
|----------|---------|
| `hud` | Temporary HUD message |
| `java` | Reflection-based static method call |
| `var` | Variable operations (set/inc/dec/flag/unflag/clear) |
| `cond` | Conditional jump logic |
| `settings` | Live settings changes |
| `save` | Quick save/load |
| `mode` | Skip/auto mode control |
| `ui` | UI visibility + visualizer |
| `history` | History overlay control |
| `audio` | Advanced audio control |
| `screen` | Shake/flash effects |
| `char` | Character choreography |
| `jes_timeline` | Named timeline playback |
| `jes_timeline_inline` | Inline timeline playback |

### Runtime-Only Providers (when launched via runtime)

| Provider | Purpose |
|----------|---------|
| `jes` | Push/replace/pop/call JES scenes |
| `menu` | Open menu scenes |
| `vns` | Script flow transitions |

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [VNS ↔ JES Architecture](vns-jes-architecture.md) — runtime bridge internals and scene stack coordination
- [Java + JES Cross Development](java-jes-cross-development.md) — hybrid patterns
- [Commands Reference](../language/vns-commands.md) — full command catalog
- [Runtime Interop Guide](../../../runtime/core/interop.md) — runtime provider details
- [Puppeteer](../../../editor/puppeteer/puppeteer.md) — visual timeline editor

# Puppeteer Animation Timelines

Complete reference for keyframe-based animation timelines created with the Puppeteer editor — the `TimelineData` model, `TimelineRunner` playback, audio cues, and VNS/JES integration.

Data model: `modules/core/src/main/java/com/jvn/core/animation/TimelineData.java`
Parser: `modules/core/src/main/java/com/jvn/core/animation/TimelineDataParser.java`
Runner: `modules/core/src/main/java/com/jvn/core/animation/TimelineRunner.java`
Registry: `modules/core/src/main/java/com/jvn/core/animation/TimelineRegistry.java`

---

## Overview

Puppeteer animation timelines are **keyframe-based** animations that interpolate entity properties over time. They are distinct from the JES timeline block (which is action-based). Puppeteer timelines:

- Are created visually in the Puppeteer editor or written as inline JES blocks
- Stored as named `TimelineData` objects in the `TimelineRegistry`
- Played back by `TimelineRunner` which applies property values each frame
- Support looping, audio cues, event cues, easing per keyframe, camera control, and advanced custom numeric channels

---

## TimelineData Model

### Properties

Each runtime track can animate these built-in `TimelineData.Property` values:

| Property | Default | Description |
|----------|---------|-------------|
| `X` | 0.0 | Entity X position |
| `Y` | 0.0 | Entity Y position |
| `Z` | 0.0 | Entity Z/layer order |
| `PIVOT_X` | 0.0 | Origin X for rotation/scale (0–1) |
| `PIVOT_Y` | 0.0 | Origin Y for rotation/scale (0–1) |
| `ROTATION` | 0.0 | Rotation in degrees |
| `SCALE_X` | 1.0 | Horizontal scale |
| `SCALE_Y` | 1.0 | Vertical scale |
| `ALPHA` | 1.0 | Opacity (0–1) |
| `VISIBILITY` | 1.0 | Runtime visibility threshold |
| `CAMERA_X` | 0.0 | Camera X position |
| `CAMERA_Y` | 0.0 | Camera Y position |
| `CAMERA_ZOOM` | 1.0 | Camera zoom factor |

Puppeteer also writes advanced numeric channels onto each track's `customKeyframes` map:

| Key Family | Description |
|------------|-------------|
| `matrix.mxx`, `matrix.mxy`, `matrix.myx`, `matrix.myy`, `matrix.tx`, `matrix.ty` | Supplemental affine matrix channels |
| `effect.blur` | Entity blur radius |
| `color.m00` through `color.m34` | Full RGBA color matrix |
| `dof.focus`, `dof.strength`, `dof.maxBlur` | Runtime camera depth-of-field channels on the `__camera__` track |
| any freeform key | Registered or custom numeric property consumed through the custom-property path |

### Structure

```text
TimelineData
├── name: String
├── durationMs: double
├── looping: boolean
├── tracks: List<Track>
│   └── Track
│       ├── entityName: String
│       ├── keyframes: Map<Property, List<Keyframe>>
│       └── customKeyframes: Map<String, List<Keyframe>>
│           └── Keyframe
│               ├── timeMs: double
│               ├── value: double
│               └── easing: Easing.Type / EasingSpec
├── audioCues: List<AudioCue>
│   └── AudioCue
│       ├── timeMs: double
│       ├── trackPath: String
│       ├── channel: String ("sound" | "music")
│       ├── volume: double (0–1)
│       ├── loop: boolean
│       └── fadeInMs: double
└── eventCues: List<EventCue>
    └── EventCue
        ├── timeMs: double
        ├── type: String
        └── payload: Map<String, String>
```

---

## Keyframe Interpolation

Between any two keyframes, the value is interpolated using the **destination keyframe's** easing function:

1. Find the two surrounding keyframes (`a` and `b`) for the current time
2. Calculate `t = (currentTime - a.time) / (b.time - a.time)`
3. Apply easing: `eased = Easing.apply(b.easing, t)`
4. Lerp: `result = a.value + (b.value - a.value) * eased`

**Before the first keyframe:** the first keyframe's value is used (clamped hold).
**After the last keyframe:** the last keyframe's value is used (clamped hold).
**Single keyframe:** that value is constant for the entire duration.

### Interpolation Visual

```text
Value
  │         ╭── ease_out_cubic ──╮
  │        ╱                      ╲── ease_in_quad ──╮
  │       ╱                                           ╲
  │──────╱                                             ╲──────
  │  hold                                                hold
  └──────┬──────────┬────────────────┬──────────────┬──────── Time
       kf_a       kf_b            kf_c           kf_d

  Before kf_a: returns kf_a.value (hold)
  Between kf_a–kf_b: eased interpolation using kf_b.easing
  Between kf_b–kf_c: eased interpolation using kf_c.easing
  After kf_d: returns kf_d.value (hold)
```

### Easing Assignment Rule

The easing curve is stored on each keyframe but **applied on the incoming segment** (from the previous keyframe to this one). This means:

- The **first** keyframe's easing is never used for interpolation (there's no preceding segment)
- The **last** keyframe's easing defines how the animation arrives at its final value
- Each segment uses the **destination** keyframe's easing

This is important when hand-coding: place the easing on the keyframe you're animating **to**, not from.

### Edge Cases

| Scenario | Behavior |
|----------|----------|
| Two keyframes at the same time | Second keyframe's value wins (zero-span → snap) |
| Span < 0.001ms | Returns destination value directly (avoids precision issues) |
| No keyframes for a property | Returns default: 0.0 for most, 1.0 for SCALE_X/Y, ALPHA, CAMERA_ZOOM |

---

## Inline JES Block Syntax

Puppeteer timelines can be written as inline JES timeline blocks. The `TimelineDataParser` converts these into `TimelineData`:

```jes
timeline {
  move "hero" {
    x: 640
    y: 468
    dur: 340
    easing: ease_in_out
  }
  wait 200
  fade "hero" {
    alpha: 0
    dur: 500
  }
}
```

### Supported Actions

| Action | Properties | Description |
|--------|-----------|-------------|
| `move "entity"` | `x`, `y`, `dur`, `easing`, `interp` | Position keyframes |
| `depth "entity"` | `z`, `dur`, `easing`, `interp` | Z-order / layer depth keyframe |
| `pivot "entity"` | `ox`, `oy`, `dur`, `easing`, `interp` | Origin/pivot keyframes |
| `rotate "entity"` | `angle`/`rotation`/`deg`, `dur`, `easing`, `interp` | Rotation keyframe |
| `scale "entity"` | `x`/`sx`/`scale_x`, `y`/`sy`/`scale_y`, `dur`, `easing`, `interp` | Scale keyframes |
| `fade "entity"` | `alpha`, `dur`, `easing`, `interp` | Alpha keyframe |
| `visible "entity"` | `value`/`visible` | Instant visibility toggle (no dur/easing) |
| `property "entity"` | `key`, `value`, `dur`, `easing`, `interp` | Generic numeric channel keyframe |
| `cameraMove` | `x`, `y`, `dur`, `easing`, `interp` | Camera position keyframes |
| `cameraZoom` | `zoom`, `dur`, `easing`, `interp` | Camera zoom keyframe |
| `playAudio "path"` | `volume`, `loop`, `bgm`, `channel`, `fadein` | Audio cue (instant) |
| `event "type"` | arbitrary key-value payload | Event cue (instant callback) |
| `expression "entity"` | `value`, `path`, `position` | Sprite expression swap (instant) |
| `show "entity"` | `target`, `expression`, `path`, `position`, `layer` | Show entity (instant) |
| `hide "entity"` | `target` | Hide entity (instant) |
| `replace "entity"` | `target`, `expression`, `path` | Replace sprite (instant) |
| `scene` | `target`, `id`, `path`, `value` | Background/scene change (instant) |
| `wait <ms>` | — | Advances the time cursor |

### Time Cursor

The parser maintains a **time cursor** that starts at 0. Each action's keyframes are placed at `cursor + dur`. `wait` advances the cursor without creating keyframes.

**Example:**

```jes
timeline {
  move "a" { x: 100 dur: 200 }          // keyframe at 200ms
  wait 100                                // cursor now at 200+100=300ms (wait does NOT add dur)
  fade "a" { alpha: 0 dur: 300 }         // keyframe at 300+300=600ms
}
```

Note: `wait` advances the cursor by its value. Actions place keyframes at `cursor + dur` but do NOT advance the cursor themselves. This means sequential actions without `wait` between them start from the same cursor position.

---

## Audio Cues

Audio cues trigger at specific times during playback:

```jes
timeline {
  playAudio "assets/audio/sfx/whoosh.ogg" {
    volume: 0.8
    channel: sound
  }
  wait 500
  playAudio "assets/audio/bgm/battle.ogg" {
    volume: 0.7
    loop: true
    bgm: true
    fadein: 200
  }
}
```

| Property | Default | Description |
|----------|---------|-------------|
| `volume` | 1.0 | Playback volume (0–1) |
| `loop` | `bgm` value | Loop playback |
| `bgm` | false | Treat as background music |
| `channel` | auto | `"sound"` or `"music"` (auto-detected from `bgm`) |
| `fadein` / `fadein_ms` / `fade_in` | 0 | Fade-in duration in ms |

Audio cues are triggered when the runner's elapsed time crosses the cue's timestamp. In looping timelines, cues re-trigger each cycle.

---

## TimelineRunner

`TimelineRunner` plays back a `TimelineData` on a live scene. Create one runner per playback, call `update()` each frame, and query `isFinished()` to detect completion.

### Basic Usage

```java
TimelineData data = TimelineRegistry.get("intro_animation");
TimelineRunner runner = new TimelineRunner(data, sceneAccessor);

// In update loop:
runner.update(deltaMs);
if (runner.isFinished()) {
    // Animation complete — runner can be discarded
}
```

### Per-Frame Update Cycle

Each call to `update(deltaMs)` performs these steps in order:

```text
1. Guard — if already finished, return immediately
2. Advance elapsed time: nextElapsed = elapsedMs + deltaMs
3. Handle duration:
   - Non-looping: clamp to duration, mark finished if exceeded
   - Looping: wrap with modulo (elapsedMs = nextElapsed % duration)
4. Trigger audio cues in the [prev, next] time window
5. Apply frame — interpolate all properties and write to entities
```

### Property Write Semantics

The runner **only writes properties that have keyframes**. Unkeyed properties are left untouched:

| What's Keyed | What Gets Written | What's Preserved |
|--------------|-------------------|------------------|
| X only | `entity.setPosition(interpolated_x, current_y)` | Y unchanged |
| SCALE_X only | `entity.setScale(interpolated_sx, current_sy)` | SCALE_Y unchanged |
| ALPHA | `sprite.setAlpha(value)` | Position, scale, rotation unchanged |
| CAMERA_X + CAMERA_ZOOM | Camera X and zoom | Camera Y unchanged |

This means you can safely compose multiple timelines that animate different properties on the same entity — they won't interfere as long as they don't animate overlapping properties.

### Alpha Application by Entity Type

| Entity Type | Method Called | Notes |
|-------------|-------------|-------|
| `Sprite2D` | `setAlpha(value)` | Direct alpha property |
| `CharacterEntity2D` | `setAlpha(value)` | Inherits from Sprite2D |
| `Label2D` | `setColor(r, g, b, alpha)` | Preserves existing RGB, replaces alpha channel |
| `Panel2D` | `setFill(r, g, b, alpha)` | Preserves existing fill RGB, replaces alpha channel |
| Other `Entity2D` | **No effect** | Alpha is silently ignored |

### Pivot (Origin) Application

Pivot keyframes (`PIVOT_X`, `PIVOT_Y`) only apply to `Sprite2D` and `CharacterEntity2D`. Values are clamped to 0.0–1.0. Non-finite values (NaN, Infinity) fall back to 0.5 (center origin).

### Camera Track

Camera properties live on a special internal track named `"__camera__"`. The runner checks for `CAMERA_X`, `CAMERA_Y`, `CAMERA_ZOOM` keyframes on every track and routes them to `SceneAccessor` methods instead of entity properties:

```java
scene.setCameraX(track.getValueAt(CAMERA_X, timeMs));
scene.setCameraY(track.getValueAt(CAMERA_Y, timeMs));
scene.setCameraZoom(track.getValueAt(CAMERA_ZOOM, timeMs));
```

In editor-authored timelines, you should treat the dedicated **Runtime Camera / Frame** lane as the canonical source for these keys. Puppeteer now warns during runtime registration if camera keys are spread across multiple tracks or mixed into normal entity tracks, because runtime playback then depends on track write order instead of one clean camera source.

### Looping Behavior

When `TimelineData.isLooping()` is true:

- Elapsed time wraps: `elapsedMs = nextElapsed % duration`
- The runner **never finishes** — `isFinished()` always returns false
- Audio cues re-trigger at the start of each loop cycle
- Loop boundary handling: if the playhead wraps from 980ms→20ms (in a 1000ms loop), cues in both the 980–1000ms AND 0–20ms windows are triggered

### Audio Cue Timing

Audio cues are sorted by time at construction. During each `update()`, the runner checks which cues fall within the time window `[prevElapsed, nextElapsed]`:

| Scenario | Behavior |
|----------|----------|
| Cue at t=0 | Fires on the first update (inclusive start) |
| Cue at t=500, update spans 480→520 | Fires (cue is within window) |
| Cue at t=500, update spans 520→600 | Does **not** fire (already passed) |
| Looping, cue at t=0, loop wraps | Re-triggers at each cycle start |
| Zero-duration timeline | All cues fire once, then finished (or loops) |

### Finished State

- Non-looping: `isFinished()` returns true when `elapsedMs >= duration`
- Looping: `isFinished()` always returns false
- Once finished, subsequent `update()` calls are no-ops
- `getElapsedMs()` returns the current playback position

### SceneAccessor Interface

The runner communicates with the scene through `SceneAccessor`, which decouples it from any specific scene implementation:

```java
public interface SceneAccessor {
    // Required: find entity by name (return null if not found)
    Entity2D findEntity(String name);

    // Optional camera hooks (default: no-op)
    default void setCameraX(double x) {}
    default void setCameraY(double y) {}
    default void setCameraZoom(double zoom) {}

    // Optional audio hooks (default: no-op)
    default void playAudioCue(String path, String channel, double volume, boolean loop, double fadeInMs) {}
    default void stopAudio(String channel) {}

    // Optional event cue hook (default: no-op)
    default void onEventCue(String type, java.util.Map<String, String> payload) {}
}
```

**`findEntity`** is the only required method. Camera, audio, and event cue hooks are optional — if the scene doesn't implement them, those keyframes and cues are silently ignored. This allows the same `TimelineData` to work across different scene types (JES scenes, VN scenes, test harnesses).

---

## Event Cues

Event cues fire instant callbacks at specific timestamps during playback. Unlike audio cues (which trigger audio playback), event cues deliver structured data to the scene for expression changes, show/hide beats, scene swaps, or arbitrary game logic.

```jes
timeline {
  # Expression change at 200ms
  event "expression" {
    target: hero
    value: angry
  }

  wait 500

  # Show/hide beat at 500ms
  event "show" {
    target: villain
    position: right
    value: smug
  }

  wait 300

  # Custom game event at 800ms
  event "script_call" {
    handler: spawnParticles
    count: 12
  }
}
```

### EventCue Structure

| Field | Type | Description |
|-------|------|-------------|
| `timeMs` | double | When the cue fires (milliseconds) |
| `type` | String | Event category (e.g. `"expression"`, `"show"`, `"hide"`, `"replace"`, `"scene"`, custom) |
| `payload` | Map\<String, String\> | Arbitrary key-value pairs describing the event |

### Predefined Event Types

| Type | Common Payload Keys | Description |
|------|-------------------|-------------|
| `expression` | `target`, `value` | Change a character's expression mid-animation |
| `show` | `target`, `position`, `value` | Show a character at a position with expression |
| `hide` | `target` | Hide a character |
| `replace` | `target`, `value`, `path` | Replace a sprite's image source |
| `scene` | `path`, `position` | Change background or cutaway |
| (custom) | any | Forwarded to `SceneAccessor.onEventCue()` for game-specific logic |

### Runtime Behavior

- Event cues are triggered by `TimelineRunner` when elapsed time crosses the cue's timestamp (same window logic as audio cues).
- In looping timelines, event cues re-fire each cycle.
- The runner calls `sceneAccessor.onEventCue(type, payload)` for each triggered cue.
- `VnCharacterSceneAccessor` provides a VN-native implementation that interprets expression/show/hide/replace cues and maintains an event log.
- Scenes that don't override `onEventCue` silently ignore all event cues.

### DSL Syntax

```text
event "<type>" {
  key1: value1
  key2: value2
}
```

The type is a quoted string. The body contains `key: value` pairs (one per line). Values are always stored as strings in the payload map.

---

## TimelineRegistry

Named timelines are stored in a global registry for cross-system access.

```java
// Register a timeline (done by Puppeteer or code)
TimelineRegistry.register(timelineData);

// Look up from VNS or JES
TimelineData data = TimelineRegistry.get("intro_animation");

// Check existence
boolean exists = TimelineRegistry.has("intro_animation");

// List all names
Set<String> names = TimelineRegistry.names();

// Remove
TimelineRegistry.remove("intro_animation");

// Clear all
TimelineRegistry.clear();
```

### VNS Integration

VNS scripts can play registered timelines via the `jes_timeline` interop provider:

```vns
[external jes_timeline intro_animation]
```

This looks up `"intro_animation"` in the `TimelineRegistry` and creates a `TimelineRunner` to play it on the current scene.

### Inline Timelines in VNS

VNS also supports inline timeline blocks:

```vns
[timeline_start hero_entrance]
move "hero" {
  x: 640
  y: 468
  dur: 400
  easing: ease_out_back
}
wait 200
fade "hero" {
  alpha: 1.0
  dur: 300
}
[timeline_end]
```

This creates a `TimelineData` from the inline block via `TimelineDataParser`, registers it, and plays it immediately.

---

## Easing Types

All keyframe easings use the `Easing.Type` enum:

| Category | Types |
|----------|-------|
| **Linear** | `LINEAR` |
| **Quadratic** | `EASE_IN_QUAD`, `EASE_OUT_QUAD`, `EASE_IN_OUT_QUAD` |
| **Cubic** | `EASE_IN_CUBIC`, `EASE_OUT_CUBIC`, `EASE_IN_OUT_CUBIC` |
| **Quartic** | `EASE_IN_QUART`, `EASE_OUT_QUART`, `EASE_IN_OUT_QUART` |
| **Exponential** | `EASE_IN_EXPO`, `EASE_OUT_EXPO`, `EASE_IN_OUT_EXPO` |
| **Sine** | `EASE_IN_SINE`, `EASE_OUT_SINE`, `EASE_IN_OUT_SINE` |
| **Elastic** | `EASE_IN_ELASTIC`, `EASE_OUT_ELASTIC`, `EASE_IN_OUT_ELASTIC` |
| **Back** | `EASE_IN_BACK`, `EASE_OUT_BACK`, `EASE_IN_OUT_BACK` |
| **Bounce** | `EASE_IN_BOUNCE`, `EASE_OUT_BOUNCE`, `EASE_IN_OUT_BOUNCE` |

In inline blocks, use lowercase with underscores: `ease_in_out_quad`.

In addition to the enum-backed names, the timeline parser also accepts:

- `spring(stiffness, damping, mass, velocity)`
- `damped_spring(frequency, damping_ratio, response, velocity)`
- named reusable curves: `hero_pop`, `ui_soft_in`, `camera_glide`

---

## Complete Example

### Inline JES Block

```jes
timeline {
  # Camera establishes the scene
  cameraMove { x: 0 y: 0 dur: 0 }
  cameraZoom { zoom: 0.8 dur: 0 }

  # Hero slides in from the left
  move "hero" { x: -100 y: 300 dur: 0 }
  fade "hero" { alpha: 0 dur: 0 }
  wait 200

  move "hero" { x: 200 y: 300 dur: 600 easing: ease_out_back }
  fade "hero" { alpha: 1.0 dur: 400 easing: ease_out_quad }

  # Camera zooms in
  wait 400
  cameraZoom { zoom: 1.2 dur: 500 easing: ease_in_out_quad }

  # Title fades in
  wait 200
  fade "title" { alpha: 1.0 dur: 300 easing: ease_out_quad }

  # Sound effect
  playAudio "assets/audio/sfx/reveal.ogg" { volume: 0.8 }

  # Background music starts
  wait 300
  playAudio "assets/audio/bgm/theme.ogg" {
    volume: 0.6
    loop: true
    bgm: true
    fadein: 500
  }
}
```

### Java Registration

```java
// Parse and register
TimelineData intro = TimelineDataParser.parse("intro_animation",
    Files.readString(Path.of("config/timelines/intro.jes")));
intro.setLooping(false);
TimelineRegistry.register(intro);

// Play later
TimelineRunner runner = new TimelineRunner(
    TimelineRegistry.get("intro_animation"),
    sceneAccessor
);
```

---

## VnState Lifecycle Management

In VNS scenes, `VnState` manages the lifecycle of active `TimelineRunner` instances:

### Adding Runners

```java
// From DefaultVnInterop (registered timeline)
TimelineData data = TimelineRegistry.get(name);
TimelineRunner runner = new TimelineRunner(data, sceneAccessor);
scene.getState().addTimelineRunner(runner);

// From DefaultVnInterop (inline timeline)
String name = "_inline_timeline_" + (++counter);
TimelineData data = TimelineDataParser.parse(name, blockContent);
TimelineRunner runner = new TimelineRunner(data, sceneAccessor);
scene.getState().addTimelineRunner(runner);
```

### Per-Frame Tick

```java
// Called every frame in the game loop
vnState.updateTimelineRunners(deltaMs);
```

Internally this does:

```java
activeTimelines.removeIf(r -> {
    r.update(deltaMs);
    return r.isFinished();
});
```

Finished runners are **automatically removed**. Looping runners persist until the scene changes or they are explicitly removed.

### Querying State

```java
// Check if any animations are still playing
if (vnState.hasActiveTimelines()) {
    // Animations in progress — you might want to wait
}

// Access the live runner list (e.g., for debugging)
List<TimelineRunner> runners = vnState.getActiveTimelines();
```

---

## Concurrent Timeline Patterns

Multiple timelines can run simultaneously. Understanding how they interact is important for complex scenes.

### Independent Timelines (Safe)

Different entities, no conflicts:

```vns
[call jes_timeline hero_entrance]
[call jes_timeline villain_entrance]
[call jes_timeline camera_pan]
```

Each runner has its own elapsed time and animates its own entities. No interference.

### Same Entity, Different Properties (Safe)

One timeline animates position, another animates alpha:

```vns
// Timeline A: moves hero
[call jes_timeline hero_walk]

// Timeline B: fades hero's alpha
[call jes_timeline hero_glow]
```

Safe **only if** the timelines don't animate overlapping properties. The runner only writes properties with keyframes, so `hero_walk` (X, Y) and `hero_glow` (ALPHA) won't conflict.

### Same Entity, Same Property (Conflict!)

If two concurrent timelines animate the same property on the same entity, the **last one to write wins** each frame. The result depends on the order runners are stored in `VnState.activeTimelines`:

```vns
// ❌ CONFLICT — both animate hero's X position
[call jes_timeline hero_walk_right]
[call jes_timeline hero_walk_left]
// Result: unpredictable jitter
```

**Avoid this.** If you need to change an animation mid-flight, let the first timeline finish (or remove it) before starting the second.

---

## TimelineDataParser Internals

`TimelineDataParser` converts inline JES blocks into `TimelineData`. Understanding its internals helps when hand-coding timelines.

### Time Cursor

The parser maintains a `cursor` variable starting at 0:

- **`wait N`** → `cursor += N`
- **Actions** → keyframes placed at `[cursor, cursor + dur]`; cursor is **not** advanced

This means:

```jes
// Two actions without wait = parallel (both start at cursor=0)
move "a" { x: 100 dur: 300 }   // keyframes: 0→300
fade "a" { alpha: 1 dur: 200 } // keyframes: 0→200
```

### Implicit Start Keyframes

When `addTweenKeyframe` creates a keyframe at `endTime`, it checks if a "start" keyframe exists at `startTime` (the cursor). If not, it implicitly creates one using the track's current interpolated value at that time with `LINEAR` easing. This ensures smooth transitions from whatever value the entity currently has.

```text
// If hero.x has no keyframe at t=500:
move "hero" { x: 800 dur: 300 easing: ease_out_cubic }
// → Parser adds implicit keyframe at t=500 with hero's current x value (LINEAR)
// → Parser adds target keyframe at t=800 with x=800 (ease_out_cubic)
```

### Action Parsing Details

| Action | Regex Pattern | Track Name | Properties Mapped |
|--------|--------------|------------|-------------------|
| `move "name"` | `move\s+"([^"]+)"\s*\{` | `name` | x→X, y→Y |
| `pivot "name"` | `pivot\s+"([^"]+)"\s*\{` | `name` | ox→PIVOT_X, oy→PIVOT_Y |
| `rotate "name"` | `rotate\s+"([^"]+)"\s*\{` | `name` | angle/rotation→ROTATION |
| `scale "name"` | `scale\s+"([^"]+)"\s*\{` | `name` | x/scale_x→SCALE_X, y/scale_y→SCALE_Y |
| `fade "name"` | `fade\s+"([^"]+)"\s*\{` | `name` | alpha→ALPHA |
| `cameraMove` | `cameraMove\s*\{` | `__camera__` | x→CAMERA_X, y→CAMERA_Y |
| `cameraZoom` | `cameraZoom\s*\{` | `__camera__` | zoom→CAMERA_ZOOM |
| `playAudio "path"` | `playAudio\s+"([^"]+)"\s*\{` | — | Creates AudioCue at cursor time |
| `wait N` | `wait\s+(\d+(?:\.\d+)?)` | — | Advances cursor by N |

### Block Parsing

Action bodies are read line-by-line until the closing `}`. Properties are extracted with:

```text
key: value
key: "string value"
```

Comments (`//`, `#`) and empty lines within blocks are skipped. The parser is case-insensitive for action names and easing values.

### Duration Aliases

The `dur` property accepts aliases: `dur`, `duration`. Both map to the same value.

### Easing Resolution

The parser maps lowercase easing strings to `Easing.Type` values:

```text
"linear"           → LINEAR
"ease_in_quad"     → EASE_IN_QUAD
"ease_out_cubic"   → EASE_OUT_CUBIC
"ease_in_out_sine" → EASE_IN_OUT_SINE
(etc.)
```

Unknown easing strings fall back to `LINEAR`.

---

## Building TimelineData Programmatically

For dynamic animations or procedural content, you can construct `TimelineData` in Java:

```java
TimelineData data = new TimelineData("procedural_shake", 400);

TimelineData.Track hero = new TimelineData.Track("hero");
double[] offsets = {-12, 12, -8, 8, -4, 0};
double timeStep = 400.0 / offsets.length;
for (int i = 0; i < offsets.length; i++) {
    hero.addKeyframe(
        TimelineData.Property.X,
        new TimelineData.Keyframe(
            i * timeStep,
            baseX + offsets[i],
            Easing.Type.EASE_OUT_QUAD
        )
    );
}
data.addTrack(hero);

// Optionally add audio
data.addAudioCue(new TimelineData.AudioCue(
    0, "assets/audio/sfx/hit.ogg", "sound", 0.8, false, 0
));

// Play immediately
TimelineRunner runner = new TimelineRunner(data, sceneAccessor);
vnState.addTimelineRunner(runner);
```

This is useful for:
- **Damage shake** where amplitude varies by damage amount
- **Procedural particle paths** based on game state
- **Dynamic UI animations** where positions depend on screen layout

---

## Performance Notes

- **Interpolation** is O(k) per property per frame (linear scan for surrounding keyframe pair). For typical timelines (< 50 keyframes per property), this is negligible.
- **Entity lookup** via `findEntity()` is called every frame for every track. In VNS scenes this is a linear scan. Fast for < 20 entities.
- **Audio cues** use sorted-list window comparison — no overhead between cue points.
- **Looping** uses modulo arithmetic, no allocation per cycle.
- **Multiple concurrent runners** are fine for typical use (< 10). Each is independent with its own elapsed time.
- **Memory** per runner: reference to `TimelineData` (shared, not copied) + sorted audio cue list + two doubles (elapsed, finished flag). Minimal.

---

## Puppeteer Editor

The Puppeteer is a visual keyframe animation editor that produces `TimelineData`:

- **Entity tracks** — one per animated entity
- **Property lanes** — X, Y, ROTATION, SCALE_X, SCALE_Y, ALPHA, etc.
- **Keyframe editing** — click to place, drag to move, right-click for easing
- **Preview** — real-time playback with camera and alpha support
- **Code export** — generates inline JES timeline blocks
- **Layer order** — entity/group `layerOrder` metadata with raise/lower controls

See [Puppeteer Animation Editor](../../../editor/puppeteer/puppeteer.md) for the full editor guide.

---

## Related Docs

- [Timeline Overview](../overview/timeline-scripting.md)
- [Story Arcs & Links](../story/timeline-story-arcs.md)
- [Hand-Coding Timelines](timeline-hand-coding.md) — write animations by hand with 18 examples and templates
- [JES Timeline & Actions](../../jes/timeline/jes-timeline.md) — JES runtime timeline actions
- [VNS Interop](../../vns/integration/vns-interop.md) — `jes_timeline` provider
- [Puppeteer Overview & Architecture](../../../editor/puppeteer/puppeteer.md) — system architecture, workflow patterns, troubleshooting
- [Puppeteer Editor Guide](../../../editor/puppeteer/puppeteer-editor-guide.md) — visual editor UI usage
- [Puppeteer JES DSL Reference](../../../editor/puppeteer/puppeteer-jes-dsl.md) — exported timeline code syntax

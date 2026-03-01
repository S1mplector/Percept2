# Puppeteer Animation Timelines

Complete reference for keyframe-based animation timelines created with the Puppeteer editor — the `TimelineData` model, `TimelineRunner` playback, audio cues, and VNS/JES integration.

Data model: `core/src/main/java/com/jvn/core/animation/TimelineData.java`
Parser: `core/src/main/java/com/jvn/core/animation/TimelineDataParser.java`
Runner: `core/src/main/java/com/jvn/core/animation/TimelineRunner.java`
Registry: `core/src/main/java/com/jvn/core/animation/TimelineRegistry.java`

---

## Overview

Puppeteer animation timelines are **keyframe-based** animations that interpolate entity properties over time. They are distinct from the JES timeline block (which is action-based). Puppeteer timelines:

- Are created visually in the Puppeteer editor or written as inline JES blocks
- Stored as named `TimelineData` objects in the `TimelineRegistry`
- Played back by `TimelineRunner` which applies property values each frame
- Support looping, audio cues, easing per keyframe, and camera control

---

## TimelineData Model

### Properties

Each entity track can animate these properties:

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
| `CAMERA_X` | 0.0 | Camera X position |
| `CAMERA_Y` | 0.0 | Camera Y position |
| `CAMERA_ZOOM` | 1.0 | Camera zoom factor |

### Structure

```text
TimelineData
├── name: String
├── durationMs: double
├── looping: boolean
├── tracks: List<Track>
│   └── Track
│       ├── entityName: String
│       └── keyframes: Map<Property, List<Keyframe>>
│           └── Keyframe
│               ├── timeMs: double
│               ├── value: double
│               └── easing: Easing.Type
└── audioCues: List<AudioCue>
    └── AudioCue
        ├── timeMs: double
        ├── trackPath: String
        ├── channel: String ("sound" | "music")
        ├── volume: double (0–1)
        ├── loop: boolean
        └── fadeInMs: double
```

---

## Keyframe Interpolation

Between any two keyframes, the value is interpolated using the **destination keyframe's** easing function:

1. Find the two surrounding keyframes (`a` and `b`) for the current time
2. Calculate `t = (currentTime - a.time) / (b.time - a.time)`
3. Apply easing: `eased = Easing.apply(b.easing, t)`
4. Lerp: `result = a.value + (b.value - a.value) * eased`

**Before the first keyframe:** the first keyframe's value is used.
**After the last keyframe:** the last keyframe's value is used.
**Single keyframe:** that value is constant.

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
| `move "entity"` | `x`, `y`, `dur`, `easing` | Position keyframes |
| `pivot "entity"` | `ox`, `oy`, `dur`, `easing` | Origin/pivot keyframes |
| `rotate "entity"` | `angle`/`rotation`, `dur`, `easing` | Rotation keyframe |
| `scale "entity"` | `x`/`scale_x`, `y`/`scale_y`, `dur`, `easing` | Scale keyframes |
| `fade "entity"` | `alpha`, `dur`, `easing` | Alpha keyframe |
| `cameraMove` | `x`, `y`, `dur`, `easing` | Camera position keyframes |
| `cameraZoom` | `zoom`, `dur`, `easing` | Camera zoom keyframe |
| `playAudio "path"` | `volume`, `loop`, `bgm`, `channel`, `fadein` | Audio cue |
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

`TimelineRunner` plays back a `TimelineData` on a live scene.

### Usage

```java
TimelineData data = TimelineRegistry.get("intro_animation");
TimelineRunner runner = new TimelineRunner(data, sceneAccessor);

// In update loop:
runner.update(deltaMs);
if (runner.isFinished()) {
    // Animation complete
}
```

### Behavior

- **Each frame**, `update(deltaMs)` advances elapsed time and applies all property values
- **Entity lookup** — finds entities by name via `SceneAccessor.findEntity()`
- **Camera** — applies `CAMERA_X`, `CAMERA_Y`, `CAMERA_ZOOM` to the scene camera
- **Alpha** — applies to `Sprite2D`, `Label2D`, `Panel2D` (type-aware)
- **Pivot** — applies to `Sprite2D` and `CharacterEntity2D`
- **Looping** — when `looping=true`, elapsed time wraps with `% duration`
- **Audio** — triggers audio cues in the correct time window, including across loop boundaries

### SceneAccessor Interface

The runner communicates with the scene through `SceneAccessor`:

```java
public interface SceneAccessor {
    Entity2D findEntity(String name);
    void setCameraX(double x);
    void setCameraY(double y);
    void setCameraZoom(double zoom);
    void playAudioCue(String path, String channel, double volume, boolean loop, double fadeInMs);
}
```

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

## Puppeteer Editor

The Puppeteer is a visual keyframe animation editor that produces `TimelineData`:

- **Entity tracks** — one per animated entity
- **Property lanes** — X, Y, ROTATION, SCALE_X, SCALE_Y, ALPHA, etc.
- **Keyframe editing** — click to place, drag to move, right-click for easing
- **Preview** — real-time playback with camera and alpha support
- **Code export** — generates inline JES timeline blocks
- **Layer order** — entity/group `layerOrder` metadata with raise/lower controls

See [Puppeteer Animation Editor](../../editor/puppeteer.md) for the full editor guide.

---

## Related Docs

- [Timeline Overview](timeline-scripting.md)
- [Story Arcs & Links](timeline-story-arcs.md)
- [JES Timeline & Actions](../jes/jes-timeline.md) — JES runtime timeline actions
- [VNS Interop](../vns/vns-interop.md) — `jes_timeline` provider
- [Puppeteer Editor](../../editor/puppeteer.md)

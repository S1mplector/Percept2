# Puppeteer — JES Timeline DSL Reference

Complete reference for the JES timeline code that Puppeteer generates and exports. Covers the `timeline { }` block syntax, all action types, generic property channels, event cues, easing values, parallel blocks, wait commands, audio cues, camera actions, editor metadata comments, and how to use exported code in VNS scripts and JES scenes.

Exporter: `modules/editor/src/main/java/com/jvn/editor/ui/actioneditor/CodeExporter.java`
Runtime: `modules/core/src/main/java/com/jvn/core/animation/TimelineRunner.java`

---

## Overview

Puppeteer exports animations as JES **timeline blocks** — a structured DSL that describes keyframe-interpolated property changes over time. The exported code can be:

1. **Copied** to clipboard and pasted into `.jes` scene files
2. **Registered** to the `TimelineRegistry` for VNS interop via `[call jes_timeline <name>]`
3. **Embedded inline** in VNS scripts via `timeline { ... }` blocks

### JES Parser Compatibility

The full JES scene parser accepts Puppeteer timeline exports directly. `duration` is normalized to `dur`; `angle`/`rotation` normalize to `deg`; `x`/`y` and `scale_x`/`scale_y` normalize to `sx`/`sy`; event shortcuts such as `expression`, `show`, `hide`, `replace`, and `scene` become timeline event cues; and `depth`, `property`, custom easing literals, and `interp` are all shared with runtime timeline parsing.

The editor diagnostics, JES parser, and timeline parser share the same action schema for generated timeline blocks, so hand-authored JES and Puppeteer output stay aligned.

---

## Top-Level Structure

Every Puppeteer export wraps actions in a `timeline` block:

```jes
timeline {
  <action or control statement>
  <action or control statement>
  ...
}
```

Actions are emitted in chronological order. When multiple actions start at the same time, they are wrapped in a `parallel` block.

### Named Export

When using `exportNamed()`, a header comment is prepended:

```jes
// Timeline: hero_entrance
// Usage in VNS: @external jes_timeline hero_entrance

timeline {
  ...
}
```

Named export can also include Puppeteer-only metadata comments. Runtime parsers ignore these comments; the editor uses them when reopening a registered animation.

```jes
// Puppeteer stage metadata. Runtime parsers ignore these comments.
// @jvn-puppeteer-stage id=sunset_park source=config%2Fstage%2Fsunset_park.stagepreset bg=park_day subject=hero lights=3 occluders=1 zones=4
// @jvn-puppeteer-eye-focus character=john expression=neutral source=eyes sourceX=0.5 sourceY=0.26 deadZone=0.12 maxNudge=3 strength=1 layer1=eyes_01 layer2=eyes_02 layer3=eyes_03 layer4=eyes_04 layer5=eyes_05 layer6=eyes_06 layer7=eyes_07 layer8=eyes_08 layer9=eyes_09
```

---

## Action Types

### `move` — Position Animation

Animates an entity's X and/or Y position.

```jes
move "hero" {
  x: 320.00
  y: 396.00
  dur: 500
  easing: ease_out_cubic
}
```

| Property | Type | Description |
|----------|------|-------------|
| `x` | number | Target X position (pixels) |
| `y` | number | Target Y position (pixels) |
| `dur` | number | Duration in milliseconds |
| `easing` | string | Easing curve name (see [Easing Reference](#easing-reference)) |
| `interp` | string | Interpolation mode (see [Interpolation Modes](#interpolation-modes)) |

Either `x` or `y` or both may be present. The entity interpolates from its current position to the target over the specified duration.

### `rotate` — Rotation Animation

Animates an entity's rotation in degrees.

```jes
rotate "logo" {
  deg: 360
  dur: 600
  easing: ease_in_out_cubic
}
```

| Property | Type | Description |
|----------|------|-------------|
| `deg` | number | Target rotation in degrees |
| `dur` | number | Duration in milliseconds |
| `easing` | string | Easing curve name |
| `interp` | string | Interpolation mode (see [Interpolation Modes](#interpolation-modes)) |

### `scale` — Scale Animation

Animates an entity's X and/or Y scale factors.

```jes
scale "button" {
  sx: 1.15
  sy: 1.15
  dur: 250
  easing: ease_in_out_quad
}
```

| Property | Type | Description |
|----------|------|-------------|
| `sx` | number | Target horizontal scale (1.0 = normal) |
| `sy` | number | Target vertical scale (1.0 = normal) |
| `dur` | number | Duration in milliseconds |
| `easing` | string | Easing curve name |
| `interp` | string | Interpolation mode (see [Interpolation Modes](#interpolation-modes)) |

### `fade` — Opacity Animation

Animates an entity's alpha (transparency).

```jes
fade "ghost" {
  alpha: 0
  dur: 500
  easing: ease_in_quad
}
```

| Property | Type | Description |
|----------|------|-------------|
| `alpha` | number | Target opacity (0 = invisible, 1 = opaque) |
| `dur` | number | Duration in milliseconds |
| `easing` | string | Easing curve name |
| `interp` | string | Interpolation mode (see [Interpolation Modes](#interpolation-modes)) |

### `pivot` — Origin Point Animation

Animates an entity's pivot (origin) point. The pivot controls the center of rotation and scale.

```jes
pivot "spinner" {
  ox: 0.5
  oy: 0.5
  dur: 200
}
```

| Property | Type | Description |
|----------|------|-------------|
| `ox` | number | Horizontal origin (0 = left edge, 1 = right edge) |
| `oy` | number | Vertical origin (0 = top edge, 1 = bottom edge) |
| `dur` | number | Duration in milliseconds |
| `easing` | string | Easing curve name |
| `interp` | string | Interpolation mode (see [Interpolation Modes](#interpolation-modes)) |

### `depth` — Z-Order Animation

Animates an entity's rendering depth (layer order).

```jes
depth "overlay" {
  z: 20
  dur: 0
}
```

| Property | Type | Description |
|----------|------|-------------|
| `z` | number | Target Z depth (higher values render on top) |
| `dur` | number | Duration in milliseconds (usually `0` for instant layer changes) |
| `easing` | string | Easing curve name |
| `interp` | string | Interpolation mode (see [Interpolation Modes](#interpolation-modes)) |

### `visible` — Visibility Toggle

Sets an entity's visibility state as a binary flag. This is an **instant** action — it has no duration or easing.

```jes
visible "hint_arrow" {
  value: 0
}
```

| Property | Type | Description |
|----------|------|-------------|
| `value` | boolean/number | `true`/`1` = visible, `false`/`0` = hidden |

Unlike `fade` (which controls opacity over time), `visible` is a binary toggle — the entity is either rendered or not. A value of `0.5` is treated as visible (threshold > 0). Use `fade` for smooth opacity transitions. The `visible` key is also accepted as an alias for `value`.

### `cameraMove` — Camera Pan

Animates the scene camera position. No target entity name is specified.

```jes
cameraMove {
  x: 200
  y: 100
  dur: 800
  easing: ease_in_out_quad
}
```

| Property | Type | Description |
|----------|------|-------------|
| `x` | number | Target camera X position |
| `y` | number | Target camera Y position |
| `dur` | number | Duration in milliseconds |
| `easing` | string | Easing curve name |
| `interp` | string | Interpolation mode (see [Interpolation Modes](#interpolation-modes)) |

### `cameraZoom` — Camera Zoom

Animates the scene camera zoom level. No target entity name is specified.

```jes
cameraZoom {
  zoom: 1.5
  dur: 600
  easing: ease_out_quad
}
```

| Property | Type | Description |
|----------|------|-------------|
| `zoom` | number | Target zoom level (1.0 = normal, >1 = closer) |
| `dur` | number | Duration in milliseconds |
| `easing` | string | Easing curve name |
| `interp` | string | Interpolation mode (see [Interpolation Modes](#interpolation-modes)) |

### `playAudio` — Audio Cue

Triggers an audio event at a specific point in the timeline.

```jes
playAudio "assets/audio/sfx/whoosh.wav" {
  volume: 0.8
  loop: false
  bgm: false
}
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `volume` | number | 1.0 | Playback volume (0–1) |
| `loop` | boolean | `bgm` value | Whether to loop playback |
| `bgm` | boolean | false | Whether this is background music |
| `channel` | string | auto | Audio channel: `"sound"`, `"music"`, or `"voice"` (auto-detected from `bgm` if omitted) |
| `fadein` | number | 0 | Fade-in duration in milliseconds (aliases: `fadein_ms`, `fade_in`) |

Audio actions have no `dur` or `easing` — they fire instantly at their timestamp. Puppeteer's exporter writes `volume`, `loop`, and `bgm`; `channel` and `fadein` are recognized by the parser for hand-authored timelines.

### `event` — Event Cue

Fires an instant event cue at the current timeline position. Events carry a type string and an arbitrary key-value payload, delivered to `SceneAccessor.onEventCue(type, payload)`.

```jes
event "expression" {
  target: hero
  value: angry
}
```

| Property | Type | Description |
|----------|------|-------------|
| (any key) | string | Arbitrary payload entries — all values are strings |

Event actions have no `dur` or `easing` — they fire instantly at their timestamp, like `playAudio`. The Puppeteer editor emits these from `EditorEventCue` entries. At runtime they are delivered through `SceneAccessor.onEventCue(...)`, and the default runtime interop maps built-in cue types onto JES scene sprites or VN scene character/background state.

### Supported Built-In Event Cue Payloads

Puppeteer recognizes several event types specially when exporting and previewing:

| Event Type | Typical Payload Keys | Meaning |
|------------|----------------------|---------|
| `expression` | `target`, `value` or `expression`, optional `path`, optional `position` | Swap a sprite/character expression immediately |
| `show` | `target`, optional `expression`, optional `path`, optional `position` | Show an entity or VN character instantly |
| `hide` | `target` | Hide an entity or VN character instantly |
| `replace` | `target`, `expression` or `value`, optional `path` | Replace the current sprite/image mid-sequence |
| `scene` | optional `target`, `id` or `value`, optional `path` | Change the current background/cutaway state |
| any other type | arbitrary keys | Delivered through `SceneAccessor.onEventCue(type, payload)` unchanged |

On the runtime side:

- JES scene playback uses `path` to swap `Sprite2D` images directly for `expression`, `replace`, and `scene`
- VN playback uses `target`, `expression`/`value`, `position`, and `id` to call the VN character/background state APIs
- unknown event types are still emitted and delivered, but only do something if the active `SceneAccessor` interprets them

---

## Control Statements

### `wait` — Time Gap

Inserts a pause between action groups. Emitted when there is a gap of more than 0.5ms between consecutive action start times.

```jes
timeline {
  move "hero" { x: 100, dur: 300 }
  wait 500
  fade "hero" { alpha: 0, dur: 200 }
}
```

The `wait` value is in milliseconds.

### `parallel` — Simultaneous Actions

When multiple actions start at the same time, they are wrapped in a `parallel` block:

```jes
timeline {
  parallel {
    move "hero" { x: 500, y: 300, dur: 400, easing: ease_out_cubic }
    fade "hero" { alpha: 1, dur: 200, easing: ease_out_quad }
  }
}
```

All actions inside `parallel` begin at the same time.

---

## Easing Reference

All runtime easing values plus custom and parameterized curve functions:

| Easing Name | DSL Value | Description |
|-------------|-----------|-------------|
| Linear | `linear` | Constant speed (default, omitted in output) |
| Quad In | `ease_in_quad` | Accelerating (t²) |
| Quad Out | `ease_out_quad` | Decelerating |
| Quad In-Out | `ease_in_out_quad` | Smooth start and end |
| Cubic In | `ease_in_cubic` | Stronger acceleration (t³) |
| Cubic Out | `ease_out_cubic` | Stronger deceleration |
| Cubic In-Out | `ease_in_out_cubic` | Smooth cubic |
| Quart In | `ease_in_quart` | Very strong acceleration (t⁴) |
| Quart Out | `ease_out_quart` | Very strong deceleration |
| Quart In-Out | `ease_in_out_quart` | Smooth quartic |
| Expo In | `ease_in_expo` | Exponential acceleration |
| Expo Out | `ease_out_expo` | Exponential deceleration |
| Expo In-Out | `ease_in_out_expo` | Smooth exponential |
| Sine In | `ease_in_sine` | Gentle sinusoidal acceleration |
| Sine Out | `ease_out_sine` | Gentle sinusoidal deceleration |
| Sine In-Out | `ease_in_out_sine` | Smooth sinusoidal |
| Elastic In | `ease_in_elastic` | Spring-like overshoot on entry |
| Elastic Out | `ease_out_elastic` | Spring-like overshoot on exit |
| Elastic In-Out | `ease_in_out_elastic` | Symmetric elastic |
| Back In | `ease_in_back` | Slight pullback before acceleration |
| Back Out | `ease_out_back` | Slight overshoot after deceleration |
| Back In-Out | `ease_in_out_back` | Symmetric back |
| Bounce In | `ease_in_bounce` | Bouncing on entry |
| Bounce Out | `ease_out_bounce` | Bouncing on exit |
| Bounce In-Out | `ease_in_out_bounce` | Symmetric bounce |
| Spring | `spring(stiffness, damping, mass, velocity)` | Physical spring with overshoot/settle |
| Damped Spring | `damped_spring(frequency, damping_ratio, response, velocity)` | Motion-design spring tuning with response scaling |
| Hero Pop | `hero_pop` | Named energetic pop preset |
| UI Soft In | `ui_soft_in` | Named gentle UI preset |
| Camera Glide | `camera_glide` | Named camera settle preset |
| Custom | `cubic_bezier(cx1, cy1, cx2, cy2)` | CSS-style cubic Bézier |

### Custom Cubic Bézier

```jes
move "hero" {
  x: 500
  dur: 400
  easing: cubic_bezier(0.25, 0.10, 0.25, 1.00)
}
```

The four parameters define the control points of a cubic Bézier curve from (0,0) to (1,1), matching the CSS `cubic-bezier()` function.

### Spring Functions

```jes
move "hero" {
  x: 500
  dur: 420
  easing: spring(220, 24, 1.0, 0)
}

cameraMove {
  x: 140
  y: -20
  dur: 800
  easing: damped_spring(1.25, 1.10, 0.92, 0)
}
```

`spring(...)` uses physical parameters: `stiffness`, `damping`, `mass`, `velocity`.

`damped_spring(...)` uses motion-oriented parameters: `frequency`, `damping_ratio`, `response`, `velocity`.

Named reusable curves are exported as bare tokens:

```jes
scale "hero" {
  x: 1.08
  y: 1.08
  dur: 220
  easing: hero_pop
}
```

### Easing Omission Rule

`linear` easing is the default and is **not** written to the output. Only non-linear easing values appear in exported code.

---

## Interpolation Modes

All timed actions support an optional `interp` key that controls how the value transitions between keyframes:

| Mode | DSL Value | Description |
|------|-----------|-------------|
| Tween | `tween` | Smooth interpolation using the easing curve (default, omitted in output) |
| Hold | `hold` | Holds the previous keyframe value until the destination time, then snaps |
| Step | `step` | Alias for `hold` — instant snap at the destination keyframe |

Additional accepted aliases: `step_start`, `instant`, `jump` (all treated as step-start behavior); `step_end`, `constant` (step-end behavior).

### Interpolation Omission Rule

`tween` interpolation is the default and is **not** written to the output. Only non-default interpolation values appear in exported code:

```jes
move "hero" {
  x: 400
  dur: 300
  interp: hold
}
```

When `interp` is `hold` or `step`, the easing value has no visible effect since there is no gradual transition — the value snaps at the target time.

---

## Export Modes

### Standard Export

`CodeExporter.export(project)` — full timeline with all events, sorted chronologically. Simultaneous events are wrapped in `parallel` blocks, gaps emit `wait` statements.

### Named Export

`CodeExporter.exportNamed(project, name)` — same as standard but with a comment header and editor metadata when the project has reusable context such as scene snapshots, groups, rigging, eye-focus profiles, or a Scene Lighting Studio stage preset:

```jes
// Timeline: hero_entrance
// Usage in VNS: @external jes_timeline hero_entrance
// Puppeteer stage metadata. Runtime parsers ignore these comments.
// @jvn-puppeteer-stage id=sunset_park source=config%2Fstage%2Fsunset_park.stagepreset bg=park_day subject=hero lights=3 occluders=1 zones=4
// @jvn-puppeteer-eye-focus character=john expression=neutral source=eyes sourceX=0.5 sourceY=0.26 deadZone=0.12 maxNudge=3 strength=1 layer1=eyes_01 layer2=eyes_02 layer3=eyes_03 layer4=eyes_04 layer5=eyes_05 layer6=eyes_06 layer7=eyes_07 layer8=eyes_08 layer9=eyes_09

timeline {
  ...
}
```

The metadata is URL-encoded key/value text so paths, tags, group state, anchors, constraints, original local keyframes, and eye-focus mappings survive round-trip import safely. It is not part of the runtime timeline DSL.

### Group-Annotated Export

`CodeExporter.exportWithGroups(project)` — same as standard but includes `// Group: <name>` comments for entity group structure.

Puppeteer group tracks are editor metadata, not separate JES runtime nodes. During standard export and runtime registration, group X/Y, pivot, rotation, scale, depth, and alpha are baked into the affected child entity tracks. Child-part keyframes remain local before the parent group transform is applied, so layered character presets can move as one rig while individual parts still animate independently.

For VN character layers, exports also seed snapshot pivots when a layer has pivot-sensitive transforms such as mirror, scale, or rotation. This keeps pasted VNS timelines visually aligned with the Puppeteer preview. Orbit-anchored rotation exports the orbit anchor as a `pivot` command instead of exporting the preview arc as X/Y movement.

### Incremental Export

`CodeExporter.exportIncremental(project)` — only emits events where property values have *changed* from their initial snapshot. Useful for animations that build on an existing scene state (e.g., launched from a VNS cursor position).

### Audio-Only Export

`CodeExporter.exportAudioCues(project)` — exports only audio cue events in a descriptive format:

```text
at 0ms: play music "assets/audio/bgm/theme.ogg" volume 0.8
at 1500ms: play sound "assets/audio/sfx/impact.wav"
```

### Compact Export

`CodeExporter.exportCompact(project)` — single-line-per-action format for minimal file size. Ideal for embedding short timelines:

```jes
timeline {
  move "hero" { x:-150 y:350 dur:0 }
  fade "hero" { alpha:0 dur:0 }
  wait 100
  move "hero" { x:400 y:350 dur:600 easing:ease_out_cubic }
  fade "hero" { alpha:1 dur:400 easing:ease_out_quad }
}
```

Key differences from standard export:
- All properties on a single line inside `{ }`
- No spaces after colons
- No `parallel` grouping — each action on its own line
- `linear` easing still omitted (same as standard)

The parser handles compact format identically to multi-line format.

---

## Generic `property` Actions

Puppeteer now exports advanced channels that do not fit the legacy `move` / `rotate` / `scale` / `fade` buckets through a generic `property` action:

```jes
property "hero" {
  key: "matrix.mxy"
  value: 0.25
  dur: 300
  easing: ease_in_out_quad
}
```

```jes
property "__camera__" {
  key: "dof.strength"
  value: 6
  dur: 400
}
```

| Property | Type | Description |
|----------|------|-------------|
| `key` | string | Registry key or freeform custom numeric channel name |
| `value` | number | Target numeric value |
| `dur` | number | Duration in milliseconds |
| `easing` | string | Easing curve name |

`property` actions are how Puppeteer exports:

- entity depth and visibility (`z`, `visible`)
- matrix channels such as `matrix.mxx` / `matrix.tx`
- entity blur via `effect.blur`
- color matrix channels `color.m00` through `color.m34`
- camera DOF channels `dof.focus`, `dof.strength`, `dof.maxBlur`
- arbitrary registered or freeform numeric channels

The parser and runtime treat these keys uniformly through the custom-property path, with built-in engine registries intercepting known keys.

---

## Property Code Mapping

The exporter maps Puppeteer's `PropertyType` enum to JES action types and property keys:

| PropertyType | Action | Key | Default |
|-------------|--------|-----|---------|
| `X` | `move` | `x` | 0 |
| `Y` | `move` | `y` | 0 |
| `PIVOT_X` | `pivot` | `ox` | 0.5 |
| `PIVOT_Y` | `pivot` | `oy` | 0.5 |
| `ROTATION` | `rotate` | `deg` | 0 |
| `SCALE_X` | `scale` | `sx` | 1.0 |
| `SCALE_Y` | `scale` | `sy` | 1.0 |
| `ALPHA` | `fade` | `alpha` | 1.0 |
| `Z` | `depth` | `z` | 0 |
| `VISIBILITY` | `visible` | `value` | 1.0 |
| `MATRIX_MXX` | `property` | `matrix.mxx` | 1.0 |
| `MATRIX_MXY` | `property` | `matrix.mxy` | 0 |
| `MATRIX_MYX` | `property` | `matrix.myx` | 0 |
| `MATRIX_MYY` | `property` | `matrix.myy` | 1.0 |
| `MATRIX_TX` | `property` | `matrix.tx` | 0 |
| `MATRIX_TY` | `property` | `matrix.ty` | 0 |
| `BLUR` | `property` | `effect.blur` | 0 |
| `CAMERA_X` | `cameraMove` | `x` | 0 |
| `CAMERA_Y` | `cameraMove` | `y` | 0 |
| `CAMERA_ZOOM` | `cameraZoom` | `zoom` | 1.0 |
| `CAMERA_DOF_FOCUS` | `property` | `dof.focus` | 0 |
| `CAMERA_DOF_STRENGTH` | `property` | `dof.strength` | 0 |
| `CAMERA_DOF_MAX_BLUR` | `property` | `dof.maxBlur` | 0 |

The full color matrix and any custom numeric channels are exported as `property` actions keyed by their string property names rather than by `PropertyType` enum constants.

---

## Using Exported Code

### In VNS Scripts — Registry

After registering a timeline in Puppeteer:

- Puppeteer verifies the timeline against runtime registration rules first
- blocking issues stop registration
- warnings can be reviewed and continued intentionally
- successful registration writes `scripts/timelines/<name>.jes` and registers the timeline in-editor

```vns
# Call a registered timeline by name
[call jes_timeline hero_entrance]

# The timeline runs asynchronously — VNS continues on the next node
# Use [wait] if you need to block until the animation completes
[wait 600]
hero: Did you see that?
```

### In VNS Scripts — Inline

Paste the exported code directly into a VNS script:

```vns
narrator: Watch this!

timeline {
  parallel {
    move "hero" {
      x: 500
      y: 300
      dur: 400
      easing: ease_out_cubic
    }
    fade "hero" {
      alpha: 1
      dur: 200
      easing: ease_out_quad
    }
  }
}

hero: I'm here!
```

Inline `timeline { }` blocks are parsed and executed by the VNS interop system (`jes_timeline_inline`).
The inline parser accepts Puppeteer-generated aliases such as `deg`, `sx`, `sy`, `duration`, `angle`, `rotation`, `scale_x`, and `scale_y`, so current exports can be pasted without hand-converting rotate or scale keys.

### In JES Scene Files

Paste the exported code into a `.jes` file as a named timeline:

```jes
scene "CutsceneIntro" {
  entity "hero" {
    component Sprite2D {
      image: "sprites/hero.png"
      x: 200
      y: 400
    }
  }

  timeline "hero_entrance" {
    move "hero" {
      x: 640
      dur: 500
      easing: ease_out_cubic
    }
    wait 200
    parallel {
      scale "hero" {
        sx: 1.1
        sy: 1.1
        dur: 300
        easing: ease_in_out_quad
      }
      fade "hero" {
        alpha: 1
        dur: 300
      }
    }
  }
}
```

---

## Runtime Execution

### TimelineData

Puppeteer converts its `AnimationProject` to a `TimelineData` object via `toTimelineData(name)`. This is the runtime representation:

| Component | Description |
|-----------|-------------|
| `Track` | Per-entity keyframe data |
| `Property` | X, Y, PIVOT_X, PIVOT_Y, ROTATION, SCALE_X, SCALE_Y, ALPHA, CAMERA_X, CAMERA_Y, CAMERA_ZOOM, Z, VISIBILITY |
| `Keyframe` | (timeMs, value, easing) tuples |
| `customKeyframes` | Named numeric channels such as matrix, blur, color matrix, DOF, or freeform keys |
| `AudioCue` | (timeMs, file, channel, volume, loop, fadeDuration) |

### TimelineRunner

The `TimelineRunner` applies `TimelineData` keyframes to entities via a `SceneAccessor`:

1. Each frame, `update(deltaMs)` advances the internal clock
2. For each track and property, the runner interpolates between the surrounding keyframes
3. The interpolated value is written to the entity via `SceneAccessor.setProperty(entityName, property, value)` or `SceneAccessor.applyCustomProperty(target, key, value)`
4. Audio cues are triggered when the playhead passes their timestamp
5. Event cues are delivered via `SceneAccessor.onEventCue(type, payload)`
6. When the timeline ends, the runner marks itself as finished and is removed from `VnState.activeTimelines`

### SceneAccessor

The `SceneAccessor` interface decouples the runner from the scene implementation:

```java
public interface SceneAccessor {
    double getProperty(String entityName, TimelineData.Property property);
    void setProperty(String entityName, TimelineData.Property property, double value);
    void applyCustomProperty(String target, String propertyKey, double value);
    void onEventCue(String type, Map<String, String> payload);
}
```

This allows the same `TimelineData` to animate entities in both JES scenes (`JesScene2D`) and VN scenes (character sprites, background).

### TimelineRegistry

Global name → `TimelineData` map:

```java
// Registration (from Puppeteer)
TimelineRegistry.register("hero_entrance", timelineData);

// Lookup (from VNS interop)
TimelineData data = TimelineRegistry.get("hero_entrance");

// List all registered timelines
Set<String> names = TimelineRegistry.names();

// Remove
TimelineRegistry.unregister("hero_entrance");
```

---

## Complete Examples

### Character Entrance with Sound

```jes
// Timeline: hero_dramatic_entrance
// Usage in VNS: @external jes_timeline hero_dramatic_entrance

timeline {
  parallel {
    move "hero" {
      x: 640
      y: 396
      dur: 600
      easing: ease_out_cubic
    }
    fade "hero" {
      alpha: 1
      dur: 400
      easing: ease_out_quad
    }
  }
  playAudio "assets/audio/sfx/whoosh.wav" {
    volume: 0.8
    loop: false
    bgm: false
  }
  wait 200
  scale "hero" {
    sx: 1.05
    sy: 1.05
    dur: 150
    easing: ease_out_quad
  }
  scale "hero" {
    sx: 1
    sy: 1
    dur: 150
    easing: ease_in_quad
  }
}
```

### Camera Zoom with BGM

```jes
timeline {
  playAudio "assets/audio/bgm/dramatic.ogg" {
    volume: 0.7
    loop: true
    bgm: true
  }
  parallel {
    cameraMove {
      x: 400
      y: 300
      dur: 1000
      easing: ease_in_out_quad
    }
    cameraZoom {
      zoom: 1.5
      dur: 1000
      easing: ease_in_out_quad
    }
  }
}
```

### Multi-Entity Choreography

```jes
timeline {
  parallel {
    move "hero" {
      x: 400
      dur: 500
      easing: ease_out_cubic
    }
    move "villain" {
      x: 880
      dur: 500
      easing: ease_out_cubic
    }
  }
  wait 300
  parallel {
    rotate "hero" {
      deg: -15
      dur: 200
      easing: ease_in_out_quad
    }
    rotate "villain" {
      deg: 15
      dur: 200
      easing: ease_in_out_quad
    }
  }
  wait 100
  parallel {
    rotate "hero" {
      deg: 0
      dur: 200
      easing: ease_out_back
    }
    rotate "villain" {
      deg: 0
      dur: 200
      easing: ease_out_back
    }
  }
}
```

---

## Number Formatting

The exporter formats numbers as:
- Integers when the value has no fractional part: `500`, `0`, `360`
- Two decimal places otherwise, trailing zeros stripped: `320.5`, `1.15`, `0.25`

---

## Related Docs

- [Puppeteer Editor Guide](puppeteer-editor-guide.md)
- [Puppeteer Architecture](puppeteer.md)
- [Hand-Coding Timelines](../../scripting/timeline/animation/timeline-hand-coding.md) — write animations by hand with 18 examples, time cursor model, and reusable templates
- [Timeline Animation (Core)](../../scripting/timeline/animation/timeline-animation.md)
- [JES Timeline Actions](../../scripting/jes/timeline/jes-timeline.md)
- [VNS Interop](../../scripting/vns/integration/vns-interop.md)

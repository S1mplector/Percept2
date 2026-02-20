# Puppeteer — Animation Timeline Editor

Puppeteer is JVN's visual keyframe animation editor. It allows authors to create entity animations by placing keyframes on a timeline, previewing them in real-time, and exporting the result as JES timeline code that can be embedded in both JES scenes and VNS scripts.

This document covers the system architecture, the JES/VNS relationship, the data pipeline, and a usability guide.

---

## 1. JES and VNS — The Two Scripting Layers

JVN has two scripting systems that operate at different abstraction levels. Understanding their relationship is essential to understanding how Puppeteer fits in.

### JES — The Low-Level Engine Layer

**JES (JVN Engine Script)** is a scene-description DSL. It directly controls 2D entities, their properties, physics, input bindings, and timelines. A JES file describes *what exists* in a scene and *how it behaves* frame-by-frame.

```jes
scene "BattleIntro" {
  entity "hero" {
    component Sprite2D {
      image: "sprites/hero.png"
      x: 200
      y: 400
      width: 128
      height: 256
    }
  }

  timeline "hero_entrance" {
    target: "hero"
    0ms   { x: -200, alpha: 0.0 }
    500ms { x: 200,  alpha: 1.0, easing: ease_out }
  }
}
```

Key characteristics:

- JES is **entity-centric** in the sense that everything is an `Entity2D` with typed properties (x, y, rotation, scaleX, scaleY, alpha)
- It allows for **frame-level control** — timelines interpolate properties between keyframes with configurable easing
- **Imperative** — JES scenes update every frame, running physics, input, and timeline ticks
- **Scene graph** — `JesScene2D` manages named entities, a camera, and an input system

Core files:
| File | Role |
|------|------|
| `scripting/.../jes/JesTokenizer.java` | Lexer |
| `scripting/.../jes/JesParser.java` | AST builder |
| `scripting/.../jes/JesLoader.java` | Materializes AST into `JesScene2D` |
| `scripting/.../jes/runtime/JesScene2D.java` | Runtime scene with named entity registry |

### VNS — The High-Level Narrative Layer

**VNS (Visual Novel Script)** is a dialogue-and-direction DSL. It controls *story flow*: who speaks, what background is shown, where characters stand, which choices the player sees. VNS does not manage frame-by-frame animation — it issues commands and lets lower-level systems handle the rendering.

```vns
@scenario tutorial_dialogue
@character codel "Codel"
@background field_day demo-assets/demo_bg_field/field.jpg
@charimg codel neutral demo-assets/demo_sprite_codel/Codel1.png
@charimg codel talking demo-assets/demo_sprite_codel/Codel3.png

@label start
[bg field_day]
[show codel center neutral]

Codel: Welcome to the tutorial!
[show codel center talking]
Codel: Let me show you how things work.
```

Key characteristics:
- **Narrative-centric** — commands are about story beats (show character, change background, present choice)
- **Declarative positioning** — characters are placed at named slots (`left`, `center`, `right`, `far_left`, `far_right`) rather than pixel coordinates
- **State-driven** — `VnState` tracks visible characters, current background, variables, and dialogue progress
- **Asset declarations** — `@background` and `@charimg` map IDs to file paths in the script header

Core files:
| File | Role |
|------|------|
| `core/.../vn/script/VnScriptParser.java` | Parses `.vns` text into `VnScenario` |
| `core/.../vn/VnScene.java` | Drives node progression |
| `core/.../vn/VnState.java` | Mutable narrative state (characters, bg, vars) |
| `fx/.../vn/VnRenderer.java` | JavaFX Canvas renderer for VN scenes |

### How They Relate: JES Coordinates VNS

The relationship is hierarchical:

```
┌─────────────────────────────────────────┐
│                  VNS                     │
│  (story flow, dialogue, character        │
│   placement, choices, transitions)       │
│                                          │
│  Delegates animation to:                 │
│  ┌─────────────────────────────────┐     │
│  │              JES                │     │
│  │  (entity properties, timelines, │     │
│  │   keyframes, easing, physics)   │     │
│  └─────────────────────────────────┘     │
└─────────────────────────────────────────┘
```

**VNS can invoke JES** for complex animations that go beyond simple show/hide/move:

```vns
@external jes_timeline hero_entrance
```

This loads a named JES timeline from the `TimelineRegistry` and runs it against the current VN scene's entities using `TimelineRunner` and `SceneAccessor`.

**JES can return to VNS** via `call "return"` or `call "vns"` commands.

In practice:
- **VNS** handles the 95% case: dialogue, character placement, transitions
- **JES** handles the 5%: custom animations, particle effects, minigames, complex camera work
- **Puppeteer** is the visual tool that generates the JES timeline code, making it accessible to authors who don't want to hand-write keyframe data

---

## 2. System Architecture

### Data Pipeline Overview

```
VNS Script (.vns)          JES Scene (.jes)
      │                          │
      ▼                          ▼
PuppeteerLauncherPanel     FileEditorTab.getJesScene()
      │                          │
      ▼                          │
 resolveSnapshot()               │
 (parse @background,             │
  @charimg, [show],              │
  [bg] commands up               │
  to cursor line)                │
      │                          │
      ▼                          │
 SceneSnapshot                   │
 {backgroundId,                  │
  characters[],                  │
  backgroundPaths{},             │
  characterImagePaths{}}         │
      │                          │
      ▼                          │
EditorApp.buildSceneFromSnapshot()
      │                          │
      ▼                          ▼
           JesScene2D
           (named Sprite2D entities
            at VN slot positions)
                │
                ▼
        PuppeteerWindow
        {AnimationProject,
         AnimationPreview,
         EntitySelector,
         TimelinePanel,
         KeyframeEditor,
         CodePreviewPane}
                │
                ▼
        User edits keyframes
                │
                ▼
        CodeExporter.export()
                │
                ▼
        JES timeline code
        (clipboard or register
         to TimelineRegistry)
                │
                ▼
        VNS: @external jes_timeline <name>
        (TimelineRunner applies keyframes
         to VnState entities via SceneAccessor)
```

### Key Classes

| Class | Module | Role |
|-------|--------|------|
| `PuppeteerLauncherPanel` | editor | Right-panel widget; parses VNS source to build scene snapshots; provides "Launch Puppeteer Here" button |
| `PuppeteerWindow` | editor | `Stage` subclass; main Puppeteer UI; assembles all sub-panels |
| `AnimationProject` | editor | Data model: entity tracks, groups, keyframes, audio cues, loop region, playback state |
| `AnimationPreview` | editor | Canvas-based scene renderer with entity selection, drag-to-move, onion skinning |
| `EntitySelector` | editor | TreeView of named entities and groups |
| `TimelinePanel` | editor | Horizontal timeline with keyframe diamonds, playhead, entity rows |
| `KeyframeEditor` | editor | Property editor for selected keyframe (time, value, easing, sliders) |
| `CodePreviewPane` | editor | Live-updating JES code output |
| `CodeExporter` | editor | Converts `AnimationProject` to JES timeline syntax |
| `AnimationPreset` | editor | 12 built-in animation templates (fade, slide, bounce, shake, etc.) |
| `PuppeteerCommand` | editor | Undo/redo command stack |
| `FxBlitter2D` | fx | Canvas renderer for `Entity2D` — handles image loading from classpath and filesystem |
| `TimelineData` | core | Serializable timeline representation for registry transport |
| `TimelineRegistry` | core | Static registry mapping names to `TimelineData` for VNS interop |
| `TimelineRunner` | core | Applies `TimelineData` keyframes to entities via `SceneAccessor` |
| `SceneAccessor` | core | Interface decoupling `TimelineRunner` from `JesScene2D` — allows VN scenes to be animated too |

### Image Loading Pipeline

When Puppeteer renders entities from a VNS snapshot, images must be loaded from the filesystem (not just the classpath). The loading chain:

```
Sprite2D.render(blitter)
    └─▶ FxBlitter2D.drawImage(path, ...)
           └─▶ loadImage(path)
                  1. ClassLoader.getResource(path)     ← classpath
                  2. new File(path).exists()            ← absolute path
                  3. new File(projectRoot, path).exists() ← project-relative
```

`projectRoot` is set by `EditorApp` → `PuppeteerWindow.setProjectRoot()` → `AnimationPreview.setProjectRoot()` → `FxBlitter2D.setProjectRoot()`.

### VNS Snapshot Resolution

`PuppeteerLauncherPanel.resolveSnapshot(source, cursorLine)` performs a lightweight parse of VNS text without building a full `VnScenario`. It scans lines 0..cursorLine for:

| Pattern | What it captures |
|---------|-----------------|
| `@label <name>` | Current label context |
| `@background <id> <path>` | Background ID → file path mapping |
| `@charimg <charId> <expr> <path>` | Character expression → file path mapping |
| `[bg <id>]` / `[background <id>]` | Active background ID |
| `[show <charId> <position> <expr?>]` | Visible character with slot + expression |
| `[hide <charId>]` | Character removal |
| `@external character <id> show/hide/move/expr` | External character commands |

The result is a `SceneSnapshot` containing:
- `backgroundId` — which background is currently active
- `characters` — list of `{characterId, position, expression}` tuples
- `backgroundPaths` — map of background IDs to their declared file paths
- `characterImagePaths` — map of `"charId/expression"` keys to file paths
- `resolveBackgroundPath()` — looks up the active background's file path
- `resolveCharacterPath(id, expr)` — resolves the best matching sprite path with fallback chain: exact expression → neutral → any expression for that character

### Scene Construction from Snapshot

`EditorApp.buildSceneFromSnapshot()` converts a `SceneSnapshot` into a `JesScene2D`:

1. Creates background `Sprite2D` at center (1280×720, origin 0.5/0.5)
2. Creates character `Sprite2D` entities at VN slot positions:
   - `far_left` → 10% of scene width
   - `left` → 25%
   - `center` → 50%
   - `right` → 75%
   - `far_right` → 90%
   - Y position: 55% of scene height, origin at bottom-center (0.5, 1.0)
3. Registers each entity by name in the `JesScene2D` (e.g. `"codel"`, `"bg_field_day"`)

---

## 3. Timeline Registry — Bridging Puppeteer to VNS Runtime

The `TimelineRegistry` is the bridge between the editor (Puppeteer) and the runtime (VNS playback).

### Registration Flow

1. Author creates animation in Puppeteer
2. Types a name in the "Name" field (e.g. `hero_entrance`)
3. Clicks **Register** button
4. `AnimationProject.toTimelineData(name)` serializes keyframes into `TimelineData`
5. `TimelineRegistry.register(name, data)` stores it globally

### Consumption Flow

1. VNS script contains: `@external jes_timeline hero_entrance`
2. `DefaultVnInterop` handles this command
3. Looks up `TimelineRegistry.get("hero_entrance")`
4. Creates a `TimelineRunner` with the `TimelineData`
5. `TimelineRunner` uses `SceneAccessor` to read/write entity properties
6. `VnState.updateTimelineRunners(deltaMs)` ticks all active runners each frame

The `SceneAccessor` interface decouples the runner from `JesScene2D`, allowing it to work with VN scene entities that aren't managed by JES.

---

## 4. Usability Guide

### Launching Puppeteer

There are two ways to open Puppeteer:

**From a VNS file** (recommended for VN authors):
1. Open a `.vns` file in the editor
2. Place cursor on a line where characters are visible
3. Open the **Puppeteer Launcher** panel (right sidebar)
4. Review the snapshot preview (background, characters, positions)
5. Click **Launch Puppeteer Here**
6. Puppeteer opens with the VN scene pre-populated

**From a JES file**:
1. Open a `.jes` file in the editor
2. Use the menu or the **Puppeteer Launcher** panel
3. Puppeteer opens with the JES scene's entities

### Creating an Animation

1. **Select an entity** — click it in the preview viewport or in the Entities tree
2. **Move the playhead** — click on the timeline ruler to set the current time
3. **Add a keyframe** — press `K` or click in the timeline at the desired time
4. **Edit keyframe values** — use the Keyframe Editor panel:
   - Time (ms) — when this keyframe occurs
   - Value — the property value at this time
   - Easing — interpolation curve (LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BOUNCE, ELASTIC)
5. **Drag entities** — click and drag in the viewport to reposition; this auto-creates X/Y keyframes at the playhead
6. **Apply presets** — use the Presets dropdown for common animations (Fade In, Slide From Left, Shake, etc.)

### Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `Space` | Toggle play/pause |
| `Home` | Rewind to start |
| `K` | Add keyframe at playhead |
| `Delete` | Delete selected keyframe(s) |
| `Shift+Click` | Multi-select keyframes |
| `Cmd+C` | Copy generated code to clipboard |
| `Cmd+Z` | Undo |
| `Cmd+Shift+Z` | Redo |
| `Cmd+O` | Toggle onion skinning |
| Middle-drag / Right-drag | Pan viewport |
| Scroll wheel | Zoom viewport |

### Using Animations in VNS Scripts

After creating an animation in Puppeteer:

1. Type a name in the **Name** field (e.g. `codel_wave`)
2. Click **Register** — this adds the timeline to the global registry
3. In your VNS script, add:

```vns
@external jes_timeline codel_wave
```

Alternatively, click **Copy Code** to get the raw JES timeline code for embedding directly in a `.jes` file.

### Understanding the Generated Code

Puppeteer generates JES timeline blocks:

```jes
timeline {
  entity "codel" {
    0ms   { x: 640.00, y: 396.00 }
    300ms { x: 500.00, y: 396.00, easing: ease_out }
    600ms { x: 640.00, y: 396.00, easing: ease_in_out }
  }
}
```

Each `entity` block targets a named entity. Each line specifies a timestamp and the property values at that time. The runtime interpolates between keyframes using the specified easing curve.

### Tips

- **Start with presets** — Apply a preset first, then tweak individual keyframes
- **Use onion skinning** (`Cmd+O`) to visualize motion paths and timing
- **Name your timelines** descriptively — they appear in VNS scripts and should be self-documenting
- **Fit duration** — click the "Fit" button to auto-size the timeline to your content
- **Loop preview** — check "Loop" to continuously preview cyclic animations
- **Drag to reposition** — dragging entities in the viewport is the fastest way to author spatial animations
- **Check the code preview** — the right panel updates live; use it to verify your animation before exporting

---

## 5. File Map

### Editor Module (`editor/`)

```
editor/src/main/java/com/jvn/editor/
├── EditorApp.java                          # Main editor app; wires Puppeteer launcher
├── ui/
│   ├── PuppeteerLauncherPanel.java         # VNS snapshot resolver + launch button
│   ├── FileEditorTab.java                  # Tab routing; exposes JES scene + VNS caret
│   ├── VnsCodeEditor.java                  # RichTextFX editor with caret tracking
│   └── actioneditor/
│       ├── PuppeteerWindow.java            # Main Puppeteer Stage
│       ├── AnimationProject.java           # Data model (tracks, groups, keyframes)
│       ├── AnimationPreview.java           # Canvas viewport with entity interaction
│       ├── EntitySelector.java             # Entity tree panel
│       ├── TimelinePanel.java              # Keyframe timeline panel
│       ├── KeyframeEditor.java             # Keyframe property editor
│       ├── CodePreviewPane.java            # Live code output panel
│       ├── CodeExporter.java               # Project → JES code converter
│       ├── AnimationPreset.java            # Built-in animation templates
│       ├── PuppeteerCommand.java           # Undo/redo command stack
│       ├── SplinePath.java                 # Catmull-Rom spline for motion paths
│       ├── AudioCue.java                   # Audio cue model
│       ├── EntityTrack.java                # Per-entity keyframe storage
│       ├── EntityGroup.java                # Hierarchical entity grouping
│       ├── Keyframe.java                   # Single keyframe (time, value, easing)
│       ├── PropertyType.java               # Animatable property enum
│       └── Easing.java                     # Easing curve implementations
```

### Core Module (`core/`)

```
core/src/main/java/com/jvn/core/
├── animation/
│   ├── TimelineData.java                   # Serializable timeline for registry
│   ├── TimelineRunner.java                 # Applies timeline to entities at runtime
│   ├── TimelineRegistry.java              # Global name → TimelineData map
│   └── SceneAccessor.java                 # Interface for entity property access
├── scene2d/
│   ├── Entity2D.java                       # Base entity (x, y, rotation, scale, alpha)
│   ├── Sprite2D.java                       # Image entity (width, height, origin)
│   ├── Scene2DBase.java                    # Entity list + camera + input
│   └── Blitter2D.java                      # Rendering interface
└── vn/
    ├── VnState.java                        # Mutable VN state (characters, bg, vars)
    ├── VnScene.java                        # VN scene driver
    ├── CharacterPosition.java              # LEFT/CENTER/RIGHT/FAR_LEFT/FAR_RIGHT
    └── script/VnScriptParser.java          # .vns text → VnScenario
```

### FX Module (`fx/`)

```
fx/src/main/java/com/jvn/fx/
├── scene2d/FxBlitter2D.java               # JavaFX Canvas Blitter2D impl
│                                            (classpath + filesystem image loading)
└── vn/VnRenderer.java                      # VN scene Canvas renderer
```

---

## 6. Recent Additions

### Asset Picker Panel (`AssetPickerPanel.java`)
The **Assets** tab in the left sidebar scans the project directory for image files (png, jpg, gif, bmp, webp) and displays them with thumbnails. Selecting an image and clicking **+ Add to Scene** creates a new `Sprite2D` entity at center-screen with the image's actual dimensions, registers it in the scene and project, and refreshes the entity selector and timeline.

### Easing Curve Editor (`EasingCurveEditor.java`)
The **Keyframe Editor** now includes a visual curve preview below the easing dropdown. It draws the selected easing function as a curve from (0,0) to (1,1) with:
- Grid lines at 0.25 intervals
- Dashed diagonal linear reference line
- Blue curve showing the actual easing output
- Orange start/end point markers
- Easing name label

The curve updates live when the easing type is changed.

### CharacterEntity2D Bounds
`CharacterEntity2D` now exposes `getDrawWidth()`, `getDrawHeight()`, `getOriginX()`, and `getOriginY()` getters. `AnimationPreview` uses these for accurate bounding box hit detection and selection highlights on animated sprite-sheet characters.

---

## 7. Known Limitations

- **Single-scene scope** — Puppeteer operates on one scene at a time; cross-scene transitions must be authored separately
- **Snapshot is static** — the VNS snapshot at launch time captures the state up to the cursor line; it does not update if the VNS script changes while Puppeteer is open
- **No custom Bezier handles** — the curve editor visualizes built-in easing types but does not yet support user-defined Bezier control points
- **No drag-from-asset-picker** — assets are added via button click; drag-and-drop onto the preview is not yet implemented

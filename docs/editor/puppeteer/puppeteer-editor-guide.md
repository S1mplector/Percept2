# Puppeteer — Editor Guide

Comprehensive guide to using the Puppeteer animation editor — launching, UI panels, entity management, keyframe editing, animation presets, audio cues, camera animation, groups, layer ordering, preview controls, and export workflows.

Source: `editor/src/main/java/com/jvn/editor/ui/actioneditor/PuppeteerWindow.java`

---

## Overview

Puppeteer is JVN's visual keyframe animation editor. It lets you:

- Animate entity properties (position, rotation, scale, opacity) on a timeline
- Animate the scene camera (pan, zoom)
- Place audio cues at precise timestamps
- Group entities and animate them together
- Preview animations in real-time with onion skinning
- Export animations as JES timeline code for use in VNS scripts and JES scenes
- Register animations to the `TimelineRegistry` for runtime playback

---

## Launching Puppeteer

### From a VNS File (Recommended)

1. Open a `.vns` file in the editor
2. Place the cursor on a line where characters are visible (after `[show]` commands)
3. Open the **Puppeteer Launcher** panel in the right sidebar (click the **+** tab → "Puppeteer Launcher")
4. Review the snapshot preview — it shows the background and visible characters at the cursor position
5. Click **Launch Puppeteer Here**

### Puppeteer Launcher Panel — In Detail

Source: `editor/src/main/java/com/jvn/editor/ui/PuppeteerLauncherPanel.java`

The Puppeteer Launcher is a sidebar panel that provides live VNS scene state tracking and one-click Puppeteer launch. It updates automatically as you move the cursor within a `.vns` file.

#### Panel Display

| Element | Description |
|---------|-------------|
| **Line indicator** | Current cursor line number and trimmed line text (max 80 chars) |
| **Scene Snapshot at Cursor** | Section header |
| **Label** | The most recent `@label` / `label` before the cursor |
| **Background** | The active background from the most recent `[bg]` / `[background]` command |
| **Visible Characters** | List of character entries: `charId @ position [expression]` |
| **Launch Puppeteer Here** | Blue button — disabled until a VNS file is active |

#### Scene Snapshot Resolution

The launcher parses every line from line 1 through the cursor position, tracking cumulative scene state. It recognizes these VNS commands:

| Command | Pattern | What It Captures |
|---------|---------|-----------------|
| **Label** | `@label start` / `label start` | Sets current label name |
| **Background command** | `[bg park]` / `[background park]` | Sets active background ID |
| **Background declaration** | `@background park assets/bg/park.png` | Maps background ID → asset path |
| **Character image** | `@charimg hero neutral assets/char/hero_neutral.png` | Maps `charId/expression` → asset path |
| **Character layer** | `@charlayer hero eyes assets/char/hero_eyes.png` | Maps `charId/layerId` → asset path |
| **Character preset** | `@charpreset hero happy $eyes=happy $mouth=smile` | Resolves layer references into a composite asset path |
| **Show character** | `[show hero center happy]` | Adds character with position and expression |
| **Hide character** | `[hide hero]` | Removes character from visible set |
| **External show** | `@external character hero show center happy` | Adds character via external command |
| **External hide** | `@external character hero hide` | Removes character |
| **External move** | `@external character hero move left` | Updates character position (preserves expression) |
| **External expression** | `@external character hero expr angry` | Updates character expression (preserves position) |

#### Scene Snapshot Data Model

The snapshot passed to Puppeteer contains:

```java
SceneSnapshot {
    String currentLabel;              // e.g., "battle_start"
    String backgroundId;              // e.g., "park"
    List<CharacterEntry> characters;  // each: characterId, position, expression
    Map<String, String> bgPaths;      // background ID → asset path
    Map<String, String> charImgPaths; // "charId/expression" → asset path
    Map<String, Map<String, String>> charLayerPaths; // charId → (layerId → path)
}
```

Puppeteer uses this to construct a `JesScene2D` with:
- Background entity from the resolved `bgPaths` mapping
- Character `Sprite2D` entities positioned at VN slot locations (left, center, right, etc.)
- Correct expression images resolved from `charImgPaths` or composite `charLayerPaths`

This means the Puppeteer animation viewport matches exactly what the player would see at that point in the script.

### From a JES File

1. Open a `.jes` file in the editor
2. Use the Puppeteer Launcher panel or menu
3. Puppeteer opens with all entities from the JES scene

### Adding Entities Manually

Use the **Assets** tab in the left sidebar:
1. Browse project images (png, jpg, gif, bmp, webp)
2. Select an image
3. Click **+ Add to Scene** — creates a `Sprite2D` entity at center-screen

---

## UI Layout

Overview snapshot:

![Puppeteer Overview](../../assets/images/puppeteer/puppeteer_ui_full.png)

```text
┌──────────────────────────────────────────────────────────────┐
│  Toolbar: [Play] [Stop] [Rewind] [K] [Snap] [Fit] [Presets] │
│  [Name: ________] [Register] [Copy Code] [+ Cue]            │
├──────────┬───────────────────────────────┬───────────────────┤
│          │                               │                   │
│ Entity   │     Animation Preview         │   Keyframe        │
│ Selector │     (Canvas viewport)         │   Editor          │
│          │                               │                   │
│ - hero   │   [drag entities, select,     │   Entity: hero    │
│ - bg     │    orbit anchors, onion skin] │   Property: X     │
│ - 📁 grp │                               │   Time: 500       │
│          │                               │   Value: 320.00   │
│          │                               │   Easing: [▼]     │
│          │                               │   [Curve Preview] │
│          │                               │   [Delete] [Reset]│
├──────────┴───────────────────────────────┴───────────────────┤
│                    Timeline Panel                             │
│  Ruler: |0ms    |500ms    |1s      |1.5s    |2s              │
│  hero ▸ ──────◆───────────◆──────────────────                │
│   └ X   ──────◆───────────◆──────────────────                │
│   └ Y   ──────────────────────────────────────               │
│  bg   ▸ ──────────────────────────────────────               │
│         ▲ playhead                                           │
├──────────────────────────────────────────────────────────────┤
│  Code Preview (live JES export)                              │
│  timeline {                                                   │
│    move "hero" { x: 320.00, y: 396.00, dur: 500, ... }      │
│  }                                                            │
└──────────────────────────────────────────────────────────────┘
```

For raw generated captures and contact sheet, see:

- [Generated Puppeteer Screenshots](generated-puppeteer-screenshots.md)

---

## Complete UI Reference (Exhaustive)

This section is intentionally exhaustive and mirrors the current implementation in:

- `editor/src/main/java/com/jvn/editor/ui/actioneditor/PuppeteerWindow.java`
- `editor/src/main/java/com/jvn/editor/ui/actioneditor/EntitySelector.java`
- `editor/src/main/java/com/jvn/editor/ui/actioneditor/AssetPickerPanel.java`
- `editor/src/main/java/com/jvn/editor/ui/actioneditor/KeyframeEditor.java`
- `editor/src/main/java/com/jvn/editor/ui/actioneditor/TimelinePanel.java`
- `editor/src/main/java/com/jvn/editor/ui/actioneditor/AnimationPreview.java`
- `editor/src/main/java/com/jvn/editor/ui/actioneditor/CodePreviewPane.java`

### Window Regions

| Region | UI Element | Notes |
|-------|------------|-------|
| Top | Toolbar | Transport, duration, presets, property track target, keyframe ops, snapping, preview modes, orbit tools, audio cues, registration, help |
| Left (top tab pane) | `Entities` tab | Entity/group tree with Z badges and context menu actions |
| Left (top tab pane) | `Assets` tab | Image browser + add-to-scene pipeline |
| Left (bottom) | Keyframe Editor | Fine-grained keyframe editing, easing controls, pivot presets, camera readout |
| Center (top) | Preview canvas | World-overview rendering plus runtime frame, camera HUD, selection handles |
| Center (bottom) | Timeline canvas | Time ruler, tracks, keyframes, playhead, loop region, audio cues |
| Right | Timeline Code panel | Live JES source, diagnostics, preview-stage controls |
| Bottom | Status bar | Undo/redo state, auto-key status, playback speed |

### Top Toolbar (Left to Right)

![Top Toolbar](../../assets/images/puppeteer/puppeteer_ui_toolbar.png)

#### Transport Group

| Control | Type | Default | Action |
|--------|------|---------|--------|
| Rewind | icon button | enabled | Set playhead to `0` (`Home`) |
| Play | icon button | enabled | Start playback (`Space`) |
| Pause | icon button | disabled until play | Pause playback (`Space`) |
| Stop | icon button | enabled | Pause + reset playhead to `0` |
| Time readout (`0 ms`) | label | current playhead | Live playhead text |

#### Duration + Loop Group

| Control | Type | Default | Action |
|--------|------|---------|--------|
| Duration field | text field | project total duration | Sets timeline duration in ms |
| Fit duration | icon button | enabled | `fitDurationToContent()` |
| Loop playback | icon toggle | mirrors project | Toggle loop mode |
| `In` | compact text button | enabled | Set loop start at playhead |
| `Out` | compact text button | enabled | Set loop end at playhead |
| Clear loop | icon button | enabled | Remove loop region |

#### Presets Group

| Control | Type | Default | Action |
|--------|------|---------|--------|
| Presets | icon menu button | enabled | Apply preset to selected entity at playhead |
| Preset entries | menu items | dynamic | Items from `AnimationPreset.ALL` grouped by category |

#### Property Target Group

| Control | Type | Default | Action |
|--------|------|---------|--------|
| Property dropdown | combo box | `X` | Sets active property track for add-keyframe + nudging |
| Values | enum list | all `PropertyType` | `X`, `Y`, `PIVOT_X`, `PIVOT_Y`, `ROTATION`, `SCALE_X`, `SCALE_Y`, `ALPHA`, `CAMERA_X`, `CAMERA_Y`, `CAMERA_ZOOM` |

#### Keyframe Ops Group

| Control | Type | Default | Action |
|--------|------|---------|--------|
| Copy selected keyframes | icon button | enabled | Copy selected keyframes (`Ctrl/Cmd+Alt+C`) |
| Paste keyframes | icon button | enabled | Paste at playhead (`Ctrl/Cmd+Alt+V`) |
| Duplicate keyframes | icon button | enabled | Duplicate by snap step (`Ctrl/Cmd+Alt+D`) |
| Batch keyframe | icon button | enabled | Add current property keyframe for all entities |
| Save clip | icon button | enabled | Save selected track segment to `config/puppeteer/clips/*.clip` |
| Load clip | icon button | enabled | Load clip file and apply at playhead |
| Slot menu | text menu button | `Slot` | Place selected entity at VN slot positions |
| Slot menu entries | menu items | fixed | `FAR_LEFT`, `LEFT`, `CENTER`, `RIGHT`, `FAR_RIGHT` |
| Timeline zoom fit | icon button | enabled | Fit timeline zoom to full duration |
| Compact export | icon toggle | off | Switch code preview to compact export |

#### Snap Group

| Control | Type | Default | Action |
|--------|------|---------|--------|
| Snap enabled | icon toggle | on | Toggle timeline snapping |
| Snap step | text field | `50` | Snap interval (ms), clamped to `>=1` |

#### Auto-Key Group

| Control | Type | Default | Action |
|--------|------|---------|--------|
| Auto-key | icon toggle | off | Mark auto-key mode flag (`Auto-Key ON` indicator in status bar) |
| `Auto` | label | static | Visual label next to toggle |

#### Preview Behavior Group

| Control | Type | Default | Action |
|--------|------|---------|--------|
| Snap to grid | icon toggle | off | Grid-snaps dragged entities in preview |
| Snap to entity | icon toggle | off | Snaps near other entity positions |
| Speed | combo box | `1x` | Playback multiplier: `0.25x`, `0.5x`, `1x`, `2x`, `4x` |
| Wheel mode | combo box | `Wheel: View` | Mouse wheel controls `View Zoom` or `Camera Zoom` |

#### Orbit Group

| Control | Type | Default | Action |
|--------|------|---------|--------|
| Orbit tool | icon toggle | off | Enables orbit-anchor workflows |
| Align rotation | icon toggle | on | Rotate entity to outward angle while orbiting |
| Clear orbit anchor | icon button | enabled | Remove selected entity orbit anchor |

#### Audio Cue Group

| Control | Type | Default | Action |
|--------|------|---------|--------|
| Add cue | icon button | enabled | Open Add Audio Cue dialog at current playhead |
| Clear cues | icon button | enabled | Confirmation dialog, then remove all cues |

#### Timeline Naming + Registration Group

| Control | Type | Default | Action |
|--------|------|---------|--------|
| Timeline name field | text field | `my_animation` | Name for exported/registered timeline |
| Register | success icon button | enabled | Registers timeline + writes `scripts/timelines/<name>.jes` |

#### Help Group

| Control | Type | Default | Action |
|--------|------|---------|--------|
| Shortcuts help | icon button | enabled | Opens keyboard shortcuts overlay |

### Left Sidebar: Entities Tab

![Entity + Keyframe Side Panel](../../assets/images/puppeteer/puppeteer_ui_entities_panel.png)

| Element | Type | Behavior |
|--------|------|----------|
| `Entities` title | label | Section header |
| `Filter entities...` | text field | Filters tree by visible name |
| Empty hint | label | Shows when no entities exist |
| Entity/group tree | tree view | Select entity/group target for timeline + preview |
| Tree row icon | canvas icon | Type-specific icon (sprite, label, panel, animation, group) |
| Tree row name | label | Entity name or `📁 <group>` |
| Z badge (`Z +10`) | label badge | Shows effective layer order |
| `+ Group` | button | Opens create-group input dialog |

#### Entities Tab Context Menu (right-click on tree)

| Menu item | Behavior |
|----------|----------|
| Add to Group → `<group name>` | Add selected entity/group into target group |
| Remove from Group | Detach selected item from parent group |
| Layer Order → Raise (+10) | Increase layer order |
| Layer Order → Lower (-10) | Decrease layer order |
| Delete | Remove selected group or entity track |

### Left Sidebar: Assets Tab

| Element | Type | Behavior |
|--------|------|----------|
| `Assets` title | label | Section header |
| `Filter images...` | text field | Filters discovered asset paths |
| `Refresh` | button | Rescans project for images |
| Empty hint | label | Missing project root / no assets found message |
| Asset list | list view | Thumbnail + base name + relative path |
| `+ Add to Scene` | button | Adds selected image as new scene sprite |
| Status line | label | Shows scan counts and add status |

### Left Bottom: Keyframe Editor Panel

| Element | Type | Behavior |
|--------|------|----------|
| `Keyframe Editor` | label | Panel header |
| Empty hint | label | Visible when no keyframe selected |
| `Entity` value | label | Current selected target |
| `Property` value | label | Current property track |
| `Time (ms)` field | text field | Direct time edit (validation + error border) |
| Time slider | slider | Drag changes keyframe time |
| `Value` field | text field | Direct value edit (validation + error border) |
| Value slider | slider | Drag changes property value |
| `Interp` | combo box | `TWEEN`, `HOLD`, `STEP` |
| `Easing` | combo box | all `Easing.Type` values |
| Easing curve editor | custom canvas widget | Curve preview; drag bezier handles when easing=`CUSTOM` |
| `Pivot Presets` label | label | Visible only for `PIVOT_X`/`PIVOT_Y` |
| Pivot preset grid | 3x3 buttons | `TL`, `TC`, `TR`, `ML`, `C`, `MR`, `BL`, `BC`, `BR` |
| `Delete` | button | Deletes current keyframe |
| `Reset` | button | Resets value to property default |
| `Camera` readout | label | Shows preview camera `X`, `Y`, `Z` state |

### Center Top: Preview Pane

![Preview Canvas](../../assets/images/puppeteer/puppeteer_ui_preview.png)

| Element | Type | Behavior |
|--------|------|----------|
| Viewport info label | label above canvas | Shows project runtime resolution and red-frame explanation |
| Preview canvas | interactive canvas | Scene authoring surface |
| Runtime frame | red rectangle overlay | Runtime-visible camera viewport |
| Camera HUD | top-left overlay | Wheel mode, camera position/zoom, view zoom |
| Grid | canvas overlay | World grid in overview space |
| Selection highlight | canvas overlay | Outline, pivot handle, orbit anchor visuals |
| Motion paths | canvas overlay | Spline visualization for animated movement |
| Onion skins | canvas overlay | Ghost transforms around playhead |
| Background overflow reference | canvas overlay | Shows full source background when source exceeds sprite dimensions |

#### Preview Gestures (Exact)

| Gesture | Condition | Result |
|--------|-----------|--------|
| Mouse wheel | cursor inside preview | Zooms `View` or `Camera` based on Wheel mode |
| Middle-drag / Right-drag | anywhere in preview | Pans view or camera (mode-dependent) |
| Left click on entity | normal mode | Select entity |
| Left drag selected entity | normal mode | Move entity (X/Y updates) |
| Left click + drag pivot handle | pivot-capable entity | Move pivot and entity anchor together |
| Shift while dragging pivot | pivot drag active | Axis-lock pivot drag to horizontal or vertical |
| Shift+Left click | orbit tool ON | Set orbit anchor at cursor |
| Alt+Shift+Left click | orbit tool ON + selected entity | Link selected entity anchor to clicked source entity (joint/nail) |
| Left drag near orbit anchor handle | orbit tool ON | Reposition orbit anchor |
| Left drag entity with orbit anchor | orbit tool ON | Orbit around anchor; optional outward rotation alignment |
| Left click outside viewport | normal mode | Clear selection |

### Center Bottom: Timeline Panel

![Timeline Panel](../../assets/images/puppeteer/puppeteer_ui_timeline.png)

| Element | Type | Behavior |
|--------|------|----------|
| Time ruler | canvas row | Adaptive time ticks (`ms` / `s`) |
| Entity rows | canvas rows | Track headers |
| Property rows | canvas rows | Color-coded per property type |
| Keyframe diamonds | canvas glyphs | Select/drag keyframes |
| Playhead | red line + triangle | Current time |
| Loop region | green overlay | Active loop segment |
| Audio cue markers | orange dots + waveform | Cue timing and channel indicator |

#### Timeline Interactions (Exact)

| Gesture | Result |
|--------|--------|
| Click/drag near ruler top | Move playhead |
| Click keyframe | Select keyframe |
| Shift+click keyframe | Toggle keyframe in multi-selection |
| Drag selected keyframe(s) | Move keyframes in time (snapped if snap enabled) |
| Double-click property lane | Add keyframe at clicked time |
| Scroll | Pan timeline |
| Ctrl+Scroll | Horizontal zoom timeline centered at cursor |

### Right Panel: Timeline Code

![Live Code Export Panel](../../assets/images/puppeteer/puppeteer_ui_code_panel.png)

| Element | Type | Behavior |
|--------|------|----------|
| `Timeline Code` | label | Header |
| Status line | label | `Auto-generated`, `Manually edited`, preview staged/committed states |
| JES editor | code editor widget | Editable export code |
| Copy button | icon button | Copy current code |
| Regenerate button | icon button | Rebuild code from current model |
| Preview Parse button | success icon button | Parse code and stage model preview |
| Commit button | icon button | Commit staged preview |
| Discard button | icon button | Drop staged preview and restore previous model |
| Diagnostics area | multiline label | Parse + timeline diagnostics |

### Bottom Status Bar

| Element | Type | Behavior |
|--------|------|----------|
| Status line | label | Shows undo/redo labels, auto-key state, playback speed |

### Dialogs and Popups

| Dialog | Trigger | Fields / Buttons |
|-------|---------|------------------|
| Keyboard Shortcuts | Help button | Informational shortcut list |
| Add Audio Cue | Add cue button | Path field, channel dropdown (`music/sound/voice`), volume slider, `Add Cue` |
| Clear Audio Cues confirmation | Clear cues button | Confirm / cancel |
| Create Group | `+ Group` button | Group name input |
| Load Clip | Load clip button | Clip selector list |
| Unsaved close confirmation | close window with dirty or preview state | `Save & Register`, `Discard`, `Cancel` |
| Save / register error dialogs | save failures, parse failures | Error details and dismiss |

---

## Animatable Properties

Each entity track supports 11 animatable properties:

### Entity Properties

| Property | Code | Display Name | Default | Slider Range | Description |
|----------|------|-------------|---------|-------------|-------------|
| `X` | `x` | Position X | 0 | -2000 – 2000 | Horizontal position (pixels) |
| `Y` | `y` | Position Y | 0 | -2000 – 2000 | Vertical position (pixels) |
| `PIVOT_X` | `pivotX` | Pivot X | 0.5 | 0 – 1 | Horizontal origin (0 = left, 1 = right) |
| `PIVOT_Y` | `pivotY` | Pivot Y | 0.5 | 0 – 1 | Vertical origin (0 = top, 1 = bottom) |
| `ROTATION` | `rotation` | Rotation | 0 | -360 – 360 | Rotation in degrees |
| `SCALE_X` | `scaleX` | Scale X | 1.0 | 0.01 – 5.0 | Horizontal scale factor |
| `SCALE_Y` | `scaleY` | Scale Y | 1.0 | 0.01 – 5.0 | Vertical scale factor |
| `ALPHA` | `alpha` | Opacity | 1.0 | 0 – 1 | Transparency (0 = invisible, 1 = opaque) |

### Camera Properties

| Property | Code | Display Name | Default | Description |
|----------|------|-------------|---------|-------------|
| `CAMERA_X` | `cameraX` | Camera X | 0 | Camera horizontal position |
| `CAMERA_Y` | `cameraY` | Camera Y | 0 | Camera vertical position |
| `CAMERA_ZOOM` | `cameraZoom` | Camera Zoom | 1.0 | Camera zoom level (>1 = closer) |

Select the active property from the toolbar dropdown or click a property sub-track in the timeline.

---

## Keyframes

### What Is a Keyframe

A keyframe defines the value of a property at a specific point in time. The engine interpolates between keyframes using the easing curve set on the destination keyframe.

### Keyframe Data

| Field | Type | Description |
|-------|------|-------------|
| `timeMs` | double | Time position in milliseconds (≥ 0) |
| `value` | double | Property value at this time |
| `easing` | Easing.Type | Interpolation curve from previous keyframe to this one |
| `cx1, cy1, cx2, cy2` | double | Custom cubic Bézier control points (only when easing = CUSTOM) |

### Adding Keyframes

- **Press `K`** — adds a keyframe at the playhead for the selected entity/property
- **Double-click** on a property track in the timeline
- **Drag an entity** in the preview viewport — auto-creates X/Y keyframes at the playhead
- **Apply a preset** — inserts multiple keyframes from a template

### Editing Keyframes

Select a keyframe by clicking its diamond in the timeline. The **Keyframe Editor** panel shows:

- **Entity** — which entity this keyframe belongs to
- **Property** — which property track (X, Y, Rotation, etc.)
- **Time (ms)** — editable text field + slider
- **Value** — editable text field + slider (range adapts to property type)
- **Easing** — dropdown with all 32 easing options (`Easing.Type`)
- **Curve Preview** — visual easing curve editor (interactive for CUSTOM type)
- **Delete** — remove this keyframe
- **Reset** — reset value to the property's default

### Multi-Selection

- **Shift+Click** — toggle keyframes in/out of multi-selection
- **Alt+Left/Right** — nudge all selected keyframes by the snap step
- **Delete** — removes all selected keyframes

### Keyframe Interpolation

Between two keyframes, the value is interpolated:

```text
value = keyA.value + (keyB.value - keyA.value) * easing(t)

where t = (currentTime - keyA.time) / (keyB.time - keyA.time)
```

Before the first keyframe: holds first keyframe value.
After the last keyframe: holds last keyframe value.

---

## Easing Types

32 easing options are available in the UI (`Easing.Type`):

- 31 built-in curves (`LINEAR` + easing families)
- 1 custom curve (`CUSTOM`) with editable cubic Bézier handles

### Standard

| Easing | Description |
|--------|-------------|
| `LINEAR` | Constant speed |
| `EASE_IN_QUAD` | Slow start, accelerating (t²) |
| `EASE_OUT_QUAD` | Fast start, decelerating |
| `EASE_IN_OUT_QUAD` | Slow start and end |

### Power Families

| Family | In | Out | In-Out |
|--------|-----|------|--------|
| **Cubic** | `EASE_IN_CUBIC` | `EASE_OUT_CUBIC` | `EASE_IN_OUT_CUBIC` |
| **Quartic** | `EASE_IN_QUART` | `EASE_OUT_QUART` | `EASE_IN_OUT_QUART` |
| **Quintic** | `EASE_IN_QUINT` | `EASE_OUT_QUINT` | `EASE_IN_OUT_QUINT` |

### Geometry / Exponential

| Family | In | Out | In-Out |
|--------|-----|------|--------|
| **Circular** | `EASE_IN_CIRC` | `EASE_OUT_CIRC` | `EASE_IN_OUT_CIRC` |
| **Exponential** | `EASE_IN_EXPO` | `EASE_OUT_EXPO` | `EASE_IN_OUT_EXPO` |

### Smooth / Organic

| Family | In | Out | In-Out |
|--------|-----|------|--------|
| **Sine** | `EASE_IN_SINE` | `EASE_OUT_SINE` | `EASE_IN_OUT_SINE` |
| **Elastic** | `EASE_IN_ELASTIC` | `EASE_OUT_ELASTIC` | `EASE_IN_OUT_ELASTIC` |
| **Back** | `EASE_IN_BACK` | `EASE_OUT_BACK` | `EASE_IN_OUT_BACK` |
| **Bounce** | `EASE_IN_BOUNCE` | `EASE_OUT_BOUNCE` | `EASE_IN_OUT_BOUNCE` |

### Custom Cubic Bézier

Select `CUSTOM` easing to define a CSS-style `cubic-bezier(cx1, cy1, cx2, cy2)` curve. The Keyframe Editor shows an interactive curve editor where you can drag control points.

Uses Newton-Raphson iteration for accurate evaluation.

---

## Animation Presets

12 built-in animation templates organized by category. Apply via the **Presets** dropdown in the toolbar.

### Entrance

| Preset | Properties | Duration | Description |
|--------|-----------|----------|-------------|
| **Fade In** | alpha: 0→1 | 500ms | Smooth opacity fade-in (ease_out_quad) |
| **Slide From Left** | x: -300→0, alpha: 0→1 | 400ms | Slide in from left edge (ease_out_cubic) |
| **Slide From Right** | x: 300→0, alpha: 0→1 | 400ms | Slide in from right edge |
| **Slide From Bottom** | y: 200→0, alpha: 0→1 | 400ms | Slide up from bottom |
| **Bounce In** | scaleX/Y: 0.3→1.1→0.9→1.0, alpha: 0→1 | 500ms | Bouncy scale entrance |

### Exit

| Preset | Properties | Duration | Description |
|--------|-----------|----------|-------------|
| **Fade Out** | alpha: 1→0 | 500ms | Smooth opacity fade-out (ease_in_quad) |
| **Zoom Out** | scaleX/Y: 1→0, alpha: 1→0 | 400ms | Shrink and disappear |

### Emphasis

| Preset | Properties | Duration | Description |
|--------|-----------|----------|-------------|
| **Shake** | x: 0→-15→15→-10→10→-5→5→0 | 550ms | Horizontal shaking |
| **Pulse** | scaleX/Y: 1→1.15→1 | 500ms | Scale pulse (ease_in_out_quad) |
| **Spin** | rotation: 0→360 | 600ms | Full rotation (ease_in_out_cubic) |

### Loop

| Preset | Properties | Duration | Description |
|--------|-----------|----------|-------------|
| **Float** | y: 0→-15→0 | 2000ms | Gentle vertical bob (ease_in_out_sine) |
| **Breathe** | scaleX/Y: 1→1.05→1 | 3000ms | Subtle breathing scale (ease_in_out_sine) |

Presets are applied starting at the current playhead position. You can layer multiple presets on the same entity.

---

## Entity Groups

Groups let you organize entities hierarchically and animate them as a unit.

### Creating Groups

- Click **+ Group** in the Entity Selector
- Enter a group name in the dialog

### Managing Groups

- **Add to Group** — right-click an entity/group → "Add to Group" → select target group
- **Remove from Group** — right-click → "Remove from Group"
- **Delete Group** — right-click → "Delete" (removes group, entities remain)
- Groups can contain other groups (nested hierarchy)

### Group Animation

When a group is selected, its own `EntityTrack` is editable with **X** and **Y** properties. Group keyframes animate all child entities as a unit — offsets are additive to individual entity animations.

### Layer Ordering

Entities and groups have a `layerOrder` value controlling render order:
- Higher values render on top
- **Right-click → Layer Order → Raise (+10)** or **Lower (-10)**
- Layer order is exported to JES and used by the runtime renderer

---

## Audio Cues

Audio events can be placed at specific times on the timeline.

### Adding an Audio Cue

1. Move the playhead to the desired time
2. Click **+ Cue** in the toolbar
3. Fill in the dialog:
   - **Path** — audio asset path (e.g., `assets/audio/music/theme.mp3`)
   - **Channel** — `music`, `sound`, or `voice`
   - **Volume** — 0.0 to 1.0

### Audio Cue Properties

| Property | Type | Description |
|----------|------|-------------|
| `timeMs` | double | Trigger time on the timeline |
| `audioFile` | String | Asset path to the audio file |
| `channel` | String | `music` (loops, BGM), `sound` (one-shot SFX), `voice` |
| `volume` | double | Playback volume (0–1) |
| `fadeIn` | boolean | Whether to fade in |
| `fadeDurationMs` | double | Fade-in duration |

Audio cues appear as orange dots at the bottom of the timeline.

---

## Camera Animation

Puppeteer supports animating the scene camera alongside entities.

### Camera Properties

| Property | Exported As | Description |
|----------|------------|-------------|
| Camera X | `cameraMove` | Horizontal camera pan |
| Camera Y | `cameraMove` | Vertical camera pan |
| Camera Zoom | `cameraZoom` | Camera zoom level |

Camera keyframes are placed on entity tracks that have camera properties. The exporter collects camera keyframes from the first track that has them and emits `cameraMove` and `cameraZoom` actions.

### Camera in VNS

Exported camera actions are applied via `TimelineRunner` and `SceneAccessor`:

```vns
# Camera pan + zoom during dialogue
[call jes_timeline dramatic_zoom]
hero: Something is coming...
```

---

## Preview Controls

### Playback

| Control | Description |
|---------|-------------|
| **Play/Pause** (Space) | Toggle animation playback |
| **Stop** | Stop and return to start |
| **Rewind** (Home) | Jump playhead to 0ms |
| **Loop** | Toggle loop playback (green region on timeline) |
| **Fit** | Auto-size timeline to content duration |

### Viewport

| Control | Description |
|---------|-------------|
| **Click entity** | Select entity |
| **Drag entity** | Move entity (auto-creates X/Y keyframes) |
| **Middle-drag / Right-drag** | Pan viewport |
| **Scroll wheel** | Zoom viewport |
| **Ctrl+Scroll** on timeline | Zoom timeline horizontally |

### Onion Skinning

Toggle with **Cmd+O** (or **Ctrl+O**). Shows ghost frames at surrounding timestamps to visualize motion paths and timing.

### Orbit Tool

Toggle with **A**. When enabled, clicking on the preview sets an orbit anchor point for the selected entity. **Shift+A** clears the anchor.

---

## Timeline Panel

### Ruler

The top ruler shows time markers. Grid lines adapt to zoom level (50ms, 100ms, 200ms, 500ms, 1s, 2s, 5s steps).

### Tracks

Each entity has a header row and sub-rows for animated properties. Click a header to select the entity; click a property row to select that property for editing.

### Playhead

The red vertical line with a triangle handle. Click the ruler to position it. Drag to scrub through time.

### Snap

Enable **Snap** in the toolbar and set a step size (default: 50ms). All keyframe placement and movement snaps to the grid.

### Loop Region

When **Loop** is enabled, a green-tinted region is drawn between loop start and end points. Playback wraps within this region.

### Zoom

- **Ctrl+Scroll** on timeline — zoom horizontally (`0.01` to `5.0` pixels/ms)
- **Scroll** — pan vertically and horizontally

---

## Undo/Redo

Puppeteer uses a command stack for full undo/redo:

| Shortcut | Action |
|----------|--------|
| **Ctrl/Cmd+Alt+Z** | Undo |
| **Ctrl/Cmd+Alt+Shift+Z** or **Ctrl/Cmd+Alt+Y** | Redo |

Undoable operations include:
- Add/delete/move keyframes
- Apply presets
- Entity property changes
- Group operations

---

## Export & Registration

### Register Timeline

1. Enter a name in the **Name** field (e.g., `hero_entrance`)
2. Click **Register**
3. The animation is:
   - Converted to `TimelineData` and stored in `TimelineRegistry`
   - Exported as JES code to `scripts/timelines/<name>.jes`
   - Marked as saved (title shows "saved & registered")

### Copy to Clipboard

Click **Copy Code** (button in the right code panel) or use **Ctrl/Cmd+Shift+C** to copy generated JES timeline code.

### Export Modes

| Mode | Method | Description |
|------|--------|-------------|
| **Standard** | `CodeExporter.export()` | Full timeline with all events |
| **With Groups** | `CodeExporter.exportWithGroups()` | Includes group comment annotations |
| **Incremental** | `CodeExporter.exportIncremental()` | Only changed properties (compared to initial snapshot) |
| **Named** | `CodeExporter.exportNamed()` | Adds header comments with timeline name and VNS usage hint |

### Unsaved Changes

Closing Puppeteer with unsaved changes prompts: **Save & Register**, **Discard**, or **Cancel**.

---

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `Space` | Toggle play/pause |
| `Home` | Rewind to start |
| `K` | Add keyframe at playhead |
| `Delete` | Delete selected keyframe(s) |
| `Shift+Click` | Multi-select keyframes |
| `Ctrl/Cmd+Shift+C` | Copy generated code to clipboard |
| `Ctrl/Cmd+Alt+C` | Copy selected keyframes |
| `Ctrl/Cmd+Alt+V` | Paste keyframes at playhead |
| `Ctrl/Cmd+Alt+D` | Duplicate keyframes by snap step |
| `Ctrl/Cmd+Alt+Z` | Undo |
| `Ctrl/Cmd+Alt+Shift+Z` or `Ctrl/Cmd+Alt+Y` | Redo |
| `Ctrl/Cmd+O` | Toggle onion skinning |
| `Alt+Left` | Nudge selected keyframe(s) backward by snap step |
| `Alt+Right` | Nudge selected keyframe(s) forward by snap step |
| `Alt+Shift+Left` | Nudge selected keyframe(s) backward by 1ms |
| `Alt+Shift+Right` | Nudge selected keyframe(s) forward by 1ms |
| `A` | Toggle orbit tool |
| `Shift+A` | Clear orbit anchor for selected entity |
| Middle-drag / Right-drag | Pan viewport |
| Scroll wheel | Zoom viewport |
| Ctrl+Scroll (on timeline) | Zoom timeline horizontally |

---

## Workflow Tips

- **Start from VNS** — launch Puppeteer from a VNS file to get characters pre-positioned at their story locations
- **Start with presets** — apply a preset first, then fine-tune individual keyframes
- **Use onion skinning** to visualize motion paths and timing overlap
- **Name timelines descriptively** — they appear in VNS scripts as `[call jes_timeline <name>]`
- **Use snap** for consistent timing (50ms or 100ms steps)
- **Fit duration** — click Fit to auto-size the timeline after adding keyframes
- **Loop preview** for cyclic animations (float, breathe)
- **Check code preview** — the right panel updates live, verify before exporting
- **Group related entities** — animate a character and their props as a unit
- **Layer order** — use Raise/Lower to control which entities render on top during overlaps

---

## Known Limitations

- **Single-scene scope** — Puppeteer operates on one scene at a time; cross-scene transitions must be authored separately
- **Snapshot is static** — the VNS snapshot captures state at launch time and does not update if the script changes while Puppeteer is open
- **Group properties** — groups only support X and Y animation (not rotation, scale, or alpha)
- **No drag-from-asset-picker** — assets are added via button click; drag-and-drop is not yet implemented

---

## Related Docs

- [Puppeteer Architecture](puppeteer.md)
- [Puppeteer JES DSL Reference](puppeteer-jes-dsl.md)
- [Puppeteer Audit & Roadmap](puppeteer-audit.md)
- [Timeline Animation (Core)](../../scripting/timeline/animation/timeline-animation.md)
- [VNS Interop](../../scripting/vns/integration/vns-interop.md)

# JVN Action Editor - Design Document

A visual animation choreography tool for JVN, similar to Ren'Py's Action Editor. Allows users to visually position sprites, create keyframe animations, and export as JES timeline code.

## Overview

The Action Editor provides a **visual timeline interface** for choreographing entity animations without writing code. Users can:
- Select entities from the current scene
- Create keyframes for position, rotation, scale, alpha, etc.
- Preview animations in real-time
- Export the result as copy-pasteable JES timeline code

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                      ActionEditorWindow                              │
├─────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                        Toolbar                               │    │
│  │  [Play] [Pause] [Stop] [Rewind] | Duration: [____] | [Copy] │    │
│  └─────────────────────────────────────────────────────────────┘    │
├──────────────────────┬──────────────────────────────────────────────┤
│   EntitySelector     │              AnimationPreview                 │
│  ┌────────────────┐  │  ┌────────────────────────────────────────┐  │
│  │ ☑ hero         │  │  │                                        │  │
│  │ ☐ enemy        │  │  │          Live Scene Preview            │  │
│  │ ☑ background   │  │  │                                        │  │
│  │ ☐ particle_fx  │  │  │                                        │  │
│  └────────────────┘  │  └────────────────────────────────────────┘  │
├──────────────────────┴──────────────────────────────────────────────┤
│                         TimelinePanel                                │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ Time:  0ms    500ms    1000ms   1500ms   2000ms   2500ms     │   │
│  │        ▼───────────────────────────────────────────────────  │   │
│  ├──────────────────────────────────────────────────────────────┤   │
│  │ hero                                                          │   │
│  │  ├─ x        ●───────────────●─────────────●                 │   │
│  │  ├─ y        ●───────────────●─────────────●                 │   │
│  │  ├─ rotation ●───────────────────────────────────●           │   │
│  │  └─ alpha    ●───────────────────────────────────────────●   │   │
│  ├──────────────────────────────────────────────────────────────┤   │
│  │ background                                                    │   │
│  │  └─ alpha    ●─────────────────────●                         │   │
│  └──────────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────┤
│                       KeyframeEditor                                 │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ Entity: hero  |  Property: x  |  Time: 500ms                 │   │
│  │ Value: [320.0]  Easing: [EASE_OUT_QUAD ▼]  [Delete Keyframe] │   │
│  └──────────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────┤
│                         CodePreview                                  │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ timeline {                                                    │   │
│  │   move "hero" { x: 320, y: 200, dur: 500, easing: ease_out } │   │
│  │   rotate "hero" { deg: 45, dur: 1000, easing: linear }       │   │
│  │ }                                                             │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

## Component Breakdown

### 1. ActionEditorWindow
**File:** `editor/src/main/java/com/jvn/editor/ui/actioneditor/ActionEditorWindow.java`

Main window container. Opens as a separate Stage from EditorApp.

**Responsibilities:**
- Layout management (SplitPanes for resizable sections)
- Toolbar with playback controls
- Coordination between child components
- Scene loading/saving

### 2. EntitySelector
**File:** `editor/src/main/java/com/jvn/editor/ui/actioneditor/EntitySelector.java`

Left panel listing all named entities in the scene.

**Features:**
- Checkbox selection for which entities to animate
- Search/filter by name
- Entity type badges (Sprite, Label, Panel, etc.)
- Drag to reorder track display

### 3. AnimationPreview
**File:** `editor/src/main/java/com/jvn/editor/ui/actioneditor/AnimationPreview.java`

Embedded viewport showing the scene with current animation state.

**Features:**
- Real-time preview at current playhead position
- Play/pause/scrub controls synced with timeline
- Click-to-select entity (syncs with EntitySelector)
- Gizmo overlays for dragging position (creates keyframes)

### 4. TimelinePanel
**File:** `editor/src/main/java/com/jvn/editor/ui/actioneditor/TimelinePanel.java`

Main timeline view with tracks and keyframes.

**Features:**
- Horizontal time ruler (ms or frames)
- Playhead (draggable red line)
- Zoom in/out (mouse wheel)
- Scroll horizontally/vertically

### 5. PropertyTrackView
**File:** `editor/src/main/java/com/jvn/editor/ui/actioneditor/PropertyTrackView.java`

Individual track row for a single property (e.g., "hero.x").

**Features:**
- Track label (entity + property name)
- Keyframe diamonds on the track
- Drag keyframes horizontally to change time
- Double-click to edit keyframe value
- Right-click context menu (delete, duplicate, copy)

### 6. KeyframeEditor
**File:** `editor/src/main/java/com/jvn/editor/ui/actioneditor/KeyframeEditor.java`

Bottom panel for editing selected keyframe properties.

**Fields:**
- Time (ms)
- Value (numeric or color picker depending on property)
- Easing curve dropdown (all types from `Easing.Type`)
- Delete button

### 7. CodeExporter
**File:** `editor/src/main/java/com/jvn/editor/ui/actioneditor/CodeExporter.java`

Generates JES timeline code from animation data.

**Output format:**
```jes
timeline {
  // Parallel group - actions at same time
  parallel {
    move "hero" { x: 320, y: 200, dur: 500, easing: ease_out_quad }
    fade "hero" { alpha: 0.5, dur: 500, easing: linear }
  }
  wait 200
  rotate "hero" { deg: 45, dur: 300, easing: ease_in_out_cubic }
}
```

### 8. CodePreviewPane
**File:** `editor/src/main/java/com/jvn/editor/ui/actioneditor/CodePreviewPane.java`

Read-only code view showing generated JES.

**Features:**
- Syntax highlighting
- Copy to clipboard button
- Auto-updates as keyframes change

## Data Model

### AnimationProject
```java
public class AnimationProject {
    private String sceneName;
    private double totalDurationMs;
    private List<EntityTrack> tracks;
}
```

### EntityTrack
```java
public class EntityTrack {
    private String entityName;
    private Map<PropertyType, List<Keyframe>> propertyKeyframes;
}
```

### Keyframe
```java
public class Keyframe {
    private double timeMs;
    private double value;
    private Easing.Type easing;
}
```

### PropertyType
```java
public enum PropertyType {
    X, Y, ROTATION, SCALE_X, SCALE_Y, ALPHA,
    CAMERA_X, CAMERA_Y, CAMERA_ZOOM
}
```

## Supported Timeline Actions (from JesScene2D)

| Action Type   | Properties                          | Notes                    |
|---------------|-------------------------------------|--------------------------|
| `move`        | x, y, dur, easing                   | Entity position          |
| `rotate`      | deg, dur, easing                    | Entity rotation          |
| `scale`       | sx, sy, dur, easing                 | Entity scale             |
| `fade`        | alpha, dur, easing                  | Entity opacity           |
| `visible`     | value (bool)                        | Instant show/hide        |
| `cameraMove`  | x, y, dur, easing                   | Camera pan               |
| `cameraZoom`  | zoom, dur, easing                   | Camera zoom              |
| `cameraShake` | ampX, ampY, dur                     | Screen shake             |
| `wait`        | ms                                  | Delay between actions    |
| `call`        | target (handler name)               | Trigger custom handler   |

## Easing Curves (from Easing.java)

- LINEAR
- EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD
- EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC
- EASE_IN_QUART, EASE_OUT_QUART, EASE_IN_OUT_QUART
- EASE_IN_EXPO, EASE_OUT_EXPO, EASE_IN_OUT_EXPO
- EASE_IN_SINE, EASE_OUT_SINE, EASE_IN_OUT_SINE
- EASE_IN_ELASTIC, EASE_OUT_ELASTIC, EASE_IN_OUT_ELASTIC
- EASE_IN_BACK, EASE_OUT_BACK, EASE_IN_OUT_BACK
- EASE_IN_BOUNCE, EASE_OUT_BOUNCE, EASE_IN_OUT_BOUNCE

## Integration Points

### Opening the Action Editor
- Menu: `Tools > Action Editor`
- Toolbar button in EditorApp
- Right-click on JES file > "Open in Action Editor"

### Scene Loading
1. Parse current JES file to get entities
2. Build JesScene2D in memory
3. Pass to ActionEditorWindow

### Code Export Flow
1. User creates keyframes visually
2. CodeExporter groups keyframes by time
3. Generates optimized JES timeline block
4. User clicks "Copy Code" → clipboard
5. Paste into JES file in code editor

## Keyboard Shortcuts

| Key           | Action                        |
|---------------|-------------------------------|
| Space         | Play/Pause                    |
| Home          | Go to start                   |
| End           | Go to end                     |
| Delete        | Delete selected keyframe      |
| Ctrl+C        | Copy keyframe                 |
| Ctrl+V        | Paste keyframe                |
| Ctrl+D        | Duplicate keyframe            |
| K             | Add keyframe at playhead      |
| +/-           | Zoom timeline in/out          |
| Arrow L/R     | Nudge playhead                |

## Implementation Order

1. **ActionEditorWindow** - basic layout shell
2. **AnimationProject data model** - Keyframe, EntityTrack, etc.
3. **TimelinePanel** - time ruler, playhead, basic rendering
4. **PropertyTrackView** - keyframe display and selection
5. **EntitySelector** - list entities, toggle tracks
6. **KeyframeEditor** - edit selected keyframe
7. **AnimationPreview** - integrate ViewportView
8. **CodeExporter** - generate JES code
9. **CodePreviewPane** - display and copy
10. **EditorApp integration** - menu, toolbar
11. **Polish** - icons, dark theme, drag handles

## Files to Create

```
editor/src/main/java/com/jvn/editor/ui/actioneditor/
├── ActionEditorWindow.java
├── AnimationPreview.java
├── AnimationProject.java
├── CodeExporter.java
├── CodePreviewPane.java
├── EntitySelector.java
├── EntityTrack.java
├── Keyframe.java
├── KeyframeEditor.java
├── PropertyTrackView.java
├── PropertyType.java
└── TimelinePanel.java
```

## Hierarchical Entity Groups

The Action Editor supports **parent-child entity relationships** for layered animation:

```
character_group/          ← Group track: animating this moves all children
  ├── body_sprite         ← Child entity: has local offset animations
  └── head_sprite         ← Child entity: can nod/turn independently
```

### How It Works

1. **EntityGroup** contains child entities and child groups (nested hierarchy)
2. **Local transforms** - each entity's keyframes define position relative to parent
3. **World transforms** - computed by summing parent chain transforms
4. **Timeline UI** - collapsible tree showing group → children structure

### Creating Groups

1. Select entities in EntitySelector
2. Click "+ Group" button
3. Drag entities into the group
4. Animate the group track to move all children together
5. Animate individual children for local motion (head turning, etc.)

### Code Export

Groups export as separate timeline actions that play in parallel:

```jes
timeline {
  parallel {
    move "character_group" { x: 100, y: 0, dur: 500 }
    move "head_sprite" { x: 5, y: -3, dur: 200, easing: ease_out_quad }
  }
}
```

## Future Enhancements

- **Curve editor** - visual bezier curve editing for custom easing
- **Onion skinning** - ghost frames for previous/next positions
- **Multi-select keyframes** - shift+click, box select
- **Copy/paste between entities**
- **Animation templates/presets** - "shake", "bounce in", etc.
- **Import from existing timeline** - parse JES timeline back to keyframes

# JES Camera System

Complete reference for camera positioning, zoom, shake, follow, parallax, and dead zones in JES scenes.

Runtime: `scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`

---

## Camera Basics

Every `JesScene2D` has a built-in camera that controls the viewport offset and zoom. The camera is managed through timeline actions, the `cameraFollow` system, or direct Java API calls.

Default state:
- Position: `(0, 0)`
- Zoom: `1.0`
- No follow target

---

## Camera Position

### Via Timeline

```jes
// Pan to position over 500ms
cameraMove { x: 200 y: 100 dur: 500 easing: ease_out_quad }

// Instant snap
cameraMove { x: 0 y: 0 dur: 0 }
```

| Property | Default | Description |
|----------|---------|-------------|
| `x` | current | Target X position |
| `y` | current | Target Y position |
| `dur` | 0 | Duration in milliseconds |
| `easing` | `linear` | Easing function |

---

## Camera Zoom

### Via Timeline

```jes
// Zoom in
cameraZoom { zoom: 1.5 dur: 600 easing: ease_in_out_quad }

// Zoom out
cameraZoom { zoom: 0.8 dur: 400 easing: ease_out_quad }

// Reset zoom
cameraZoom { zoom: 1.0 dur: 300 easing: ease_out_quad }
```

| Value | Effect |
|-------|--------|
| `1.0` | Normal view |
| `> 1.0` | Zoom in (closer, less visible area) |
| `< 1.0` | Zoom out (wider, more visible area) |

---

## Camera Shake

Adds randomized offset to the camera for a duration to simulate impact or tremor.

```jes
cameraShake { ampX: 8 ampY: 8 dur: 400 }
```

| Property | Default | Description |
|----------|---------|-------------|
| `ampX` | 0 | Horizontal shake amplitude (pixels) |
| `ampY` | 0 | Vertical shake amplitude (pixels) |
| `dur` | 0 | Duration in milliseconds |

**Examples:**

```jes
// Explosion impact
cameraShake { ampX: 12 ampY: 12 dur: 500 }

// Subtle ground tremor
cameraShake { ampX: 2 ampY: 4 dur: 300 }

// Horizontal-only shake (side impact)
cameraShake { ampX: 8 ampY: 0 dur: 250 }
```

---

## Camera Follow

Makes the camera smoothly track a target entity each frame.

### Via Timeline

```jes
// Simple follow
cameraFollow "hero"

// Follow with configuration
cameraFollow "hero" {
  lerp: 0.15
  offsetX: 0
  offsetY: -40
  deadZoneW: 100
  deadZoneH: 60
}
```

### Via Java API

```java
scene.setCameraFollow("hero", 0.15);
scene.setCameraDeadZone(100, 60);
```

### Properties

| Property | Default | Description |
|----------|---------|-------------|
| `target` | — | Entity name to follow |
| `lerp` | 0.2 | Smoothing factor (0.0–1.0). Lower = smoother/slower |
| `offsetX` | 0 | Fixed offset from target center |
| `offsetY` | 0 | Fixed offset from target center |
| `deadZoneW` | 0 | Width of dead zone rectangle |
| `deadZoneH` | 0 | Height of dead zone rectangle |

### How Lerp Works

Each frame, the camera position moves toward the target by `lerp * distance`:

- `lerp: 1.0` — instant tracking (snaps to target)
- `lerp: 0.2` — smooth tracking (default)
- `lerp: 0.05` — very smooth, cinematic feel
- `lerp: 0.0` — camera doesn't move (effectively disabled)

### Dead Zone

The dead zone is a rectangle centered on the camera. The camera only moves when the target exits this rectangle. This prevents jittery camera movement during small motions.

```text
┌──────────────────────┐
│                      │
│    ┌──────────┐      │  Viewport
│    │ Dead Zone│      │
│    │   Hero   │      │
│    └──────────┘      │
│                      │
└──────────────────────┘
```

When the hero moves within the dead zone, the camera stays still. When the hero exits the dead zone, the camera starts following with the configured lerp.

**Example: Platformer-style camera:**

```jes
cameraFollow "hero" {
  lerp: 0.1
  offsetY: -30
  deadZoneW: 120
  deadZoneH: 80
}
```

**Example: Top-down RPG camera:**

```jes
cameraFollow "hero" {
  lerp: 0.2
  deadZoneW: 60
  deadZoneH: 60
}
```

---

## Parallax Scrolling

Parallax makes background layers scroll at different speeds for depth illusion.

```jes
setParallax "entityName" { px: <factorX> py: <factorY> }
```

| Factor | Effect |
|--------|--------|
| `1.0` | Moves with camera (default) |
| `0.0` | Fixed to screen (HUD) |
| `0.5` | Half-speed scrolling (distant layer) |
| `2.0` | Double-speed (very close foreground) |

**Example: Layered scrolling background:**

```jes
scene "SideScroller" {
  entity "sky" {
    component Sprite2D {
      image: "assets/backgrounds/sky.png"
      x: 0
      y: 0
      w: 1920
      h: 1080
    }
  }
  entity "mountains" {
    component Sprite2D {
      image: "assets/backgrounds/mountains.png"
      x: 0
      y: 200
      w: 1920
      h: 400
    }
  }
  entity "trees" {
    component Sprite2D {
      image: "assets/backgrounds/trees.png"
      x: 0
      y: 400
      w: 1920
      h: 300
    }
  }
  entity "hero" {
    component Sprite2D {
      image: "assets/characters/hero.png"
      x: 200
      y: 500
      w: 64
      h: 64
    }
  }

  timeline {
    // Set parallax layers
    setParallax "sky" { px: 0.1 py: 0.1 }
    setParallax "mountains" { px: 0.3 py: 0.3 }
    setParallax "trees" { px: 0.7 py: 0.7 }
    // hero is default 1.0 (moves with camera)

    cameraFollow "hero" { lerp: 0.2 }
  }
}
```

---

## Cinematic Camera Sequences

Combine camera actions for cutscene-style sequences:

```jes
timeline {
  // Start wide
  cameraZoom { zoom: 0.8 dur: 0 }
  cameraMove { x: 0 y: 0 dur: 0 }
  wait 500

  // Slow pan to the right
  cameraMove { x: 400 y: 0 dur: 2000 easing: ease_in_out_sine }

  // Zoom in on a point of interest
  cameraZoom { zoom: 1.3 dur: 800 easing: ease_in_out_quad }
  wait 1000

  // Quick shake for impact
  cameraShake { ampX: 6 ampY: 6 dur: 300 }
  wait 400

  // Zoom out and follow the hero
  parallel {
    cameraZoom { zoom: 1.0 dur: 600 easing: ease_out_quad }
    cameraMove { x: 200 y: 150 dur: 600 easing: ease_out_quad }
  }
  wait 200

  // Switch to follow mode
  cameraFollow "hero" { lerp: 0.15 offsetY: -20 }
}
```

---

## Related Docs

- [JES Overview](../overview/jes-scripting.md)
- [Timeline & Actions](../timeline/jes-timeline.md)
- [Scenes & Entities](../scene/jes-scenes-entities.md)

# JES By Example — Parallel Animation and Camera

Orchestrate multiple simultaneous actions and control the scene camera for cinematic sequences.

**Difficulty:** Intermediate
**Time:** 15 minutes
**Concepts:** `parallel`, `cameraMove`, `cameraZoom`, `cameraShake`, `cameraFollow`, cinematic sequencing

---

## The Scene

```jes
scene "CinematicIntro" {
  entity "bg" {
    component Sprite2D {
      image: "assets/backgrounds/sunset_field.png"
      x: 0
      y: 0
      w: 1280
      h: 720
    }
  }

  entity "hero" {
    component Sprite2D {
      image: "assets/characters/hero.png"
      x: -100
      y: 400
      w: 80
      h: 120
    }
  }

  entity "title" {
    component Label2D {
      text: "The Journey Begins"
      x: 400
      y: 100
      size: 36
      bold: true
      color: rgba(1, 0.95, 0.8, 0)
    }
  }

  timeline {
    parallel {
      move "hero" { x: 300 y: 400 dur: 1500 easing: ease_out_cubic }
      fade "title" { alpha: 1 dur: 1200 easing: ease_in_quad }
    }

    wait 800

    parallel {
      cameraMove { x: 300 y: 400 dur: 1000 easing: ease_in_out_quad }
      cameraZoom { zoom: 1.4 dur: 1000 easing: ease_in_out_quad }
      fade "title" { alpha: 0 dur: 600 easing: ease_out_quad }
    }

    wait 500

    cameraShake { ampX: 8 ampY: 8 dur: 400 }

    cameraZoom { zoom: 1.0 dur: 800 easing: ease_out_quad }
    cameraMove { x: 0 y: 0 dur: 800 easing: ease_out_quad }
  }
}
```

---

## `parallel` Blocks

Normally, timeline actions run one after another. `parallel { }` runs all contained actions **simultaneously**:

```jes
// Sequential: move THEN fade (total time = 800ms)
move "hero" { x: 500 dur: 500 }
fade "hero" { alpha: 0 dur: 300 }

// Parallel: move AND fade at the same time (total time = 500ms)
parallel {
  move "hero" { x: 500 dur: 500 }
  fade "hero" { alpha: 0 dur: 300 }
}
```

### Nesting Rules

- All actions inside `parallel` start at the same time
- The `parallel` block completes when the **longest** action finishes
- You **cannot** nest `parallel` inside `parallel` — use multiple `parallel` blocks separated by `wait`
- `parallel` is ideal for coordinating multi-entity choreography

### Common Patterns

**Entrance with fade:**
```jes
parallel {
  move "char" { x: 400 dur: 600 easing: ease_out_cubic }
  fade "char" { alpha: 1 dur: 400 easing: ease_out_quad }
}
```

**Exit with fade:**
```jes
parallel {
  move "char" { x: 900 dur: 500 easing: ease_in_cubic }
  fade "char" { alpha: 0 dur: 300 easing: ease_in_quad }
}
```

**Multi-entity simultaneously:**
```jes
parallel {
  move "hero" { x: 300 dur: 600 }
  move "villain" { x: 500 dur: 600 }
  fade "title" { alpha: 0 dur: 400 }
}
```

---

## Camera Actions

Camera actions control the scene camera. They do **not** take an entity target name.

### `cameraMove` — Pan the Camera

```jes
cameraMove { x: 300 y: 200 dur: 1000 easing: ease_in_out_quad }
```

| Property | Description |
|----------|-------------|
| `x` | Target camera X position |
| `y` | Target camera Y position |
| `dur` | Duration in milliseconds |
| `easing` | Easing curve |

### `cameraZoom` — Zoom In/Out

```jes
cameraZoom { zoom: 1.5 dur: 600 easing: ease_out_quad }
```

| Property | Description |
|----------|-------------|
| `zoom` | Target zoom level (`1.0` = normal, `>1` = closer, `<1` = wider) |
| `dur` | Duration in milliseconds |
| `easing` | Easing curve |

### `cameraShake` — Screen Shake

```jes
cameraShake { ampX: 12 ampY: 12 dur: 400 }
```

| Property | Default | Description |
|----------|---------|-------------|
| `ampX` | `16` | Horizontal shake amplitude (pixels) |
| `ampY` | `16` | Vertical shake amplitude (pixels) |
| `dur` | `300` | Duration in milliseconds |

Shake intensity fades linearly to zero. The camera returns to its original position when done.

### `cameraFollow` — Follow an Entity

```jes
cameraFollow "hero" { lerp: 0.15 offsetY: -20 }
```

| Property | Default | Description |
|----------|---------|-------------|
| `lerp` | `0.2` | Smoothing factor (0 = no follow, 1 = instant snap) |
| `offsetX` | `0` | Horizontal offset from target |
| `offsetY` | `0` | Vertical offset from target |
| `deadZoneW` | `0` | Horizontal dead zone width (camera won't move within this range) |
| `deadZoneH` | `0` | Vertical dead zone height |

`cameraFollow` is an **instant** action — it sets up continuous tracking that persists until changed or the scene ends.

---

## Cinematic Sequencing Patterns

### Classic Entrance

```jes
timeline {
  // Start: hero off-screen, title invisible, camera at default
  parallel {
    move "hero" { x: 400 dur: 1200 easing: ease_out_cubic }
    fade "title" { alpha: 1 dur: 800 easing: ease_in_quad }
  }
  wait 1000
  fade "title" { alpha: 0 dur: 400 }
}
```

### Dramatic Zoom

```jes
timeline {
  parallel {
    cameraMove { x: 400 y: 300 dur: 1500 easing: ease_in_out_quad }
    cameraZoom { zoom: 2.0 dur: 1500 easing: ease_in_out_quad }
  }
  wait 500
  cameraShake { ampX: 6 ampY: 6 dur: 300 }
}
```

### Impact Sequence

```jes
timeline {
  // Build-up: slow zoom
  cameraZoom { zoom: 1.2 dur: 800 easing: ease_in_quad }

  // Impact: shake + flash
  parallel {
    cameraShake { ampX: 16 ampY: 16 dur: 500 }
    fade "flash_overlay" { alpha: 0.8 dur: 100 }
  }
  fade "flash_overlay" { alpha: 0 dur: 400 easing: ease_out_quad }

  // Settle: zoom back
  cameraZoom { zoom: 1.0 dur: 600 easing: ease_out_quad }
}
```

### Multi-Character Duel

```jes
timeline {
  // Characters face each other
  parallel {
    move "hero" { x: 300 dur: 600 easing: ease_out_cubic }
    move "villain" { x: 500 dur: 600 easing: ease_out_cubic }
  }
  wait 400

  // Both lean in
  parallel {
    rotate "hero" { deg: -10 dur: 200 }
    rotate "villain" { deg: 10 dur: 200 }
  }
  wait 150

  // Spring back
  parallel {
    rotate "hero" { deg: 0 dur: 200 easing: ease_out_back }
    rotate "villain" { deg: 0 dur: 200 easing: ease_out_back }
  }
}
```

---

## Key Takeaways

1. `parallel { }` runs all contained actions simultaneously
2. Camera actions (`cameraMove`, `cameraZoom`, `cameraShake`) have no entity target
3. `cameraFollow` enables continuous entity tracking with smoothing
4. Cinematic sequences alternate between `parallel` blocks and `wait` pauses
5. Camera shake auto-decays and restores the original camera position
6. Zoom values: `1.0` = normal, `>1` = closer, `<1` = wider

---

## Next

- [Animated Characters](06-animated-characters.md) — spritesheet animation with `Character2D`
- [Back to Index](../jes-by-example.md)

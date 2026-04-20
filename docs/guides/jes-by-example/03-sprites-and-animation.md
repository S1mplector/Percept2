# JES By Example — Sprites and Animation

Load images with `Sprite2D` and bring them to life with timeline actions: `move`, `fade`, `scale`, and `wait`.

**Difficulty:** Beginner
**Time:** 15 minutes
**Concepts:** `Sprite2D`, timeline blocks, `move`, `fade`, `scale`, `rotate`, `wait`, easing curves

---

## The Scene

```jes
scene "SpriteDemo" {
  entity "bg" {
    component Panel2D {
      x: 0
      y: 0
      w: 800
      h: 600
      fill: rgb(0.05, 0.1, 0.2, 1)
    }
  }

  entity "hero" {
    component Sprite2D {
      image: "assets/characters/hero_idle.png"
      x: 100
      y: 300
      w: 64
      h: 64
    }
  }

  entity "destination" {
    component Label2D {
      text: "X"
      x: 600
      y: 310
      size: 32
      color: rgb(1, 0.3, 0.3, 1)
    }
  }

  timeline {
    move "hero" { x: 600 y: 300 dur: 1200 easing: ease_out_cubic }
    wait 300
    fade "destination" { alpha: 0 dur: 400 easing: ease_in_quad }
    scale "hero" { sx: 1.2 sy: 1.2 dur: 200 easing: ease_out_back }
    scale "hero" { sx: 1.0 sy: 1.0 dur: 200 easing: ease_in_quad }
  }
}
```

---

## `Sprite2D` Property Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `image` | string | required | Path to the image file (relative to project root) |
| `x` | number | `0` | Horizontal position (pixels) |
| `y` | number | `0` | Vertical position (pixels) |
| `w` | number | image width | Display width (pixels) |
| `h` | number | image height | Display height (pixels) |

Supported image formats: PNG, JPG, GIF, BMP, WebP.

---

## Timeline Basics

The `timeline { }` block contains a sequence of actions that execute **top to bottom**, one after the other. Each action completes before the next starts (unless wrapped in `parallel`).

```jes
timeline {
  action1    // runs first
  action2    // runs after action1 finishes
  action3    // runs after action2 finishes
}
```

### `move` — Position Animation

Moves an entity from its current position to a target position over a duration.

```jes
move "hero" { x: 600 y: 300 dur: 1200 easing: ease_out_cubic }
```

| Property | Type | Description |
|----------|------|-------------|
| `x` | number | Target X position |
| `y` | number | Target Y position |
| `dur` | number | Duration in milliseconds |
| `easing` | string | Easing curve name |

You can omit `x` or `y` to only animate one axis:

```jes
move "hero" { x: 600 dur: 500 }           // horizontal only
move "hero" { y: 100 dur: 500 }           // vertical only
move "hero" { x: 600 y: 100 dur: 500 }    // both axes
```

### `fade` — Opacity Animation

Animates an entity's transparency.

```jes
fade "hero" { alpha: 0 dur: 400 easing: ease_in_quad }
```

| Property | Type | Description |
|----------|------|-------------|
| `alpha` | number | Target opacity: `0` = invisible, `1` = opaque |
| `dur` | number | Duration in milliseconds |
| `easing` | string | Easing curve name |

### `scale` — Scale Animation

Animates an entity's size multiplier.

```jes
scale "hero" { sx: 1.5 sy: 1.5 dur: 300 easing: ease_out_back }
```

| Property | Type | Description |
|----------|------|-------------|
| `sx` | number | Target horizontal scale (`1.0` = normal) |
| `sy` | number | Target vertical scale (`1.0` = normal) |
| `dur` | number | Duration in milliseconds |
| `easing` | string | Easing curve name |

### `rotate` — Rotation Animation

Animates an entity's rotation in degrees.

```jes
rotate "logo" { deg: 360 dur: 800 easing: ease_in_out_cubic }
```

| Property | Type | Description |
|----------|------|-------------|
| `deg` | number | Target rotation in degrees |
| `dur` | number | Duration in milliseconds |
| `easing` | string | Easing curve name |

### `wait` — Timed Pause

Inserts a delay between actions.

```jes
wait 500    // pause for 500ms
```

---

## Easing Curves

Easing controls the *feel* of motion — whether it starts fast, ends slow, bounces, etc.

| Category | Options |
|----------|---------|
| **Linear** | `linear` (default — constant speed) |
| **Quadratic** | `ease_in_quad`, `ease_out_quad`, `ease_in_out_quad` |
| **Cubic** | `ease_in_cubic`, `ease_out_cubic`, `ease_in_out_cubic` |
| **Quartic** | `ease_in_quart`, `ease_out_quart`, `ease_in_out_quart` |
| **Exponential** | `ease_in_expo`, `ease_out_expo`, `ease_in_out_expo` |
| **Sine** | `ease_in_sine`, `ease_out_sine`, `ease_in_out_sine` |
| **Elastic** | `ease_in_elastic`, `ease_out_elastic`, `ease_in_out_elastic` |
| **Back** | `ease_in_back`, `ease_out_back`, `ease_in_out_back` |
| **Bounce** | `ease_in_bounce`, `ease_out_bounce`, `ease_in_out_bounce` |

**Common choices:**

- `ease_out_cubic` — smooth deceleration (great for character movement)
- `ease_in_out_quad` — gentle start and end (great for camera pans)
- `ease_out_back` — slight overshoot (great for UI pop-in effects)
- `ease_out_elastic` — springy arrival (great for playful animations)
- `ease_out_bounce` — bouncing arrival (great for impact effects)

If you omit `easing`, the default is `linear` (constant speed).

---

## Animation Recipes

### Fade In from Invisible

Start an entity invisible, then fade it in:

```jes
entity "title" {
  component Label2D {
    text: "Chapter 1"
    x: 300
    y: 200
    size: 36
    color: rgba(1, 1, 1, 0)       // start invisible (alpha = 0)
  }
}

timeline {
  wait 500
  fade "title" { alpha: 1 dur: 800 easing: ease_in_quad }
}
```

### Pulse Effect

Scale up then back down for a "pulse" or "pop":

```jes
timeline {
  scale "button" { sx: 1.15 sy: 1.15 dur: 150 easing: ease_out_quad }
  scale "button" { sx: 1.0 sy: 1.0 dur: 150 easing: ease_in_quad }
}
```

### Slide In From Off-Screen

Start an entity off-screen, then slide it in:

```jes
entity "hero" {
  component Sprite2D {
    image: "assets/characters/hero.png"
    x: -100     // start off-screen left
    y: 300
    w: 64
    h: 64
  }
}

timeline {
  move "hero" { x: 200 dur: 800 easing: ease_out_cubic }
}
```

### Spin and Grow

Combine rotation and scale:

```jes
timeline {
  rotate "star" { deg: 720 dur: 1000 easing: ease_out_expo }
  scale "star" { sx: 2.0 sy: 2.0 dur: 600 easing: ease_out_back }
}
```

### Sequential Multi-Entity

Animate entities one after another:

```jes
timeline {
  fade "line1" { alpha: 1 dur: 400 }
  wait 200
  fade "line2" { alpha: 1 dur: 400 }
  wait 200
  fade "line3" { alpha: 1 dur: 400 }
}
```

---

## Instant Actions (dur: 0)

Setting `dur: 0` makes an action complete instantly — useful for teleporting or snapping:

```jes
timeline {
  move "hero" { x: 400 y: 300 dur: 0 }     // instant teleport
  fade "hero" { alpha: 1 dur: 0 }            // instant appear
  wait 500
  move "hero" { x: 600 y: 300 dur: 800 easing: ease_out_cubic }
}
```

---

## Key Takeaways

1. `Sprite2D` renders images at a position with configurable display size
2. `timeline { }` runs actions sequentially, top to bottom
3. Core actions: `move`, `fade`, `scale`, `rotate`, `wait`
4. Every action takes `dur` (milliseconds) and optional `easing`
5. Easing curves control motion feel — `ease_out_*` for natural deceleration, `ease_in_out_*` for smooth arcs
6. `dur: 0` creates instant snaps

---

## Next

- [Input Bindings and Call Handlers](04-input-and-call-handlers.md) — keyboard interaction and Java hooks
- [Back to Index](../jes-by-example.md)

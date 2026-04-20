# JES By Example — Shapes and Layout

Build layered visual compositions using `Panel2D` rectangles and `Label2D` text — the foundation for HUDs, dashboards, title screens, and any scene that needs structure.

**Difficulty:** Beginner
**Time:** 10 minutes
**Concepts:** `Panel2D`, entity layering, z-order, building UI compositions in JES

---

## The Scene

```jes
scene "Dashboard" {
  entity "bg" {
    component Panel2D {
      x: 0
      y: 0
      w: 800
      h: 600
      fill: rgb(0.08, 0.08, 0.15, 1)
    }
  }

  entity "header" {
    component Panel2D {
      x: 0
      y: 0
      w: 800
      h: 60
      fill: rgb(0.15, 0.15, 0.25, 1)
    }
  }

  entity "header_text" {
    component Label2D {
      text: "Mission Control"
      x: 20
      y: 20
      size: 22
      bold: true
      color: rgb(0.6, 0.85, 1.0, 1)
    }
  }

  entity "status_box" {
    component Panel2D {
      x: 50
      y: 100
      w: 300
      h: 200
      fill: rgb(0.12, 0.12, 0.2, 1)
    }
  }

  entity "status_label" {
    component Label2D {
      text: "Systems: Online"
      x: 70
      y: 140
      size: 16
      color: rgb(0.3, 1.0, 0.4, 1)
    }
  }
}
```

---

## `Panel2D` Property Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `x` | number | `0` | Left edge position (pixels) |
| `y` | number | `0` | Top edge position (pixels) |
| `w` | number | `0` | Width (pixels) |
| `h` | number | `0` | Height (pixels) |
| `fill` | color | `rgb(0,0,0,1)` | Fill color as `rgb(r,g,b,a)` |

`Panel2D` is the simplest shape component — a solid colored rectangle. It's useful for:

- Full-screen backgrounds
- Header/footer bars
- Card containers and bordered regions
- HUD frames
- Debug visualization

---

## Entity Layering (Z-Order)

Entities render in **declaration order**. The first entity declared is drawn first (furthest back), and the last entity is drawn on top.

```jes
// This renders behind everything
entity "bg" { ... }

// This renders on top of bg
entity "header" { ... }

// This renders on top of header
entity "header_text" { ... }
```

Think of it like layers in a drawing program — earlier declarations go further back.

### Layering Rules

1. Tilemaps render first (behind all entities)
2. Entities render in declaration order
3. There is no explicit z-index property in JES declarations — order matters
4. At runtime, entity Z can be changed via timeline `depth` actions or `property` channels

---

## Design Patterns

### Full-Screen Background

Always start with a full-screen `Panel2D` to avoid transparent areas:

```jes
entity "bg" {
  component Panel2D {
    x: 0
    y: 0
    w: 800
    h: 600
    fill: rgb(0.05, 0.05, 0.1, 1)
  }
}
```

### Card Layout

Group related information into visual cards:

```jes
entity "card_bg" {
  component Panel2D {
    x: 40
    y: 80
    w: 320
    h: 180
    fill: rgb(0.15, 0.15, 0.22, 1)
  }
}

entity "card_title" {
  component Label2D {
    text: "Player Stats"
    x: 60
    y: 100
    size: 18
    bold: true
    color: rgb(0.8, 0.85, 1.0, 1)
  }
}

entity "card_hp" {
  component Label2D {
    text: "HP: 100/100"
    x: 60
    y: 135
    size: 14
    color: rgb(0.9, 0.3, 0.3, 1)
  }
}

entity "card_mp" {
  component Label2D {
    text: "MP: 50/50"
    x: 60
    y: 160
    size: 14
    color: rgb(0.3, 0.5, 1.0, 1)
  }
}
```

### Side-by-Side Panels

```jes
entity "left_panel" {
  component Panel2D {
    x: 20
    y: 80
    w: 370
    h: 480
    fill: rgb(0.1, 0.1, 0.18, 1)
  }
}

entity "right_panel" {
  component Panel2D {
    x: 410
    y: 80
    w: 370
    h: 480
    fill: rgb(0.1, 0.1, 0.18, 1)
  }
}
```

### Status Bar

```jes
entity "status_bar" {
  component Panel2D {
    x: 0
    y: 570
    w: 800
    h: 30
    fill: rgb(0.1, 0.1, 0.15, 1)
  }
}

entity "status_text" {
  component Label2D {
    text: "Ready"
    x: 10
    y: 576
    size: 12
    color: rgb(0.5, 0.5, 0.5, 1)
  }
}
```

---

## Full Example: Title Screen

```jes
scene "TitleScreen" {
  entity "bg" {
    component Panel2D {
      x: 0
      y: 0
      w: 800
      h: 600
      fill: rgb(0.02, 0.02, 0.06, 1)
    }
  }

  entity "accent_bar" {
    component Panel2D {
      x: 0
      y: 240
      w: 800
      h: 3
      fill: rgb(0.3, 0.5, 1.0, 0.6)
    }
  }

  entity "game_title" {
    component Label2D {
      text: "VECTOR NEXUS"
      x: 230
      y: 180
      size: 48
      bold: true
      color: rgb(0.9, 0.95, 1.0, 1)
    }
  }

  entity "subtitle" {
    component Label2D {
      text: "A story of code and consequence"
      x: 260
      y: 260
      size: 16
      color: rgb(0.5, 0.55, 0.65, 1)
    }
  }

  entity "prompt" {
    component Label2D {
      text: "Press ENTER to begin"
      x: 310
      y: 450
      size: 14
      color: rgb(0.35, 0.35, 0.45, 1)
    }
  }

  entity "version" {
    component Label2D {
      text: "v0.1.0"
      x: 740
      y: 580
      size: 10
      color: rgb(0.25, 0.25, 0.3, 1)
    }
  }
}
```

---

## Key Takeaways

1. `Panel2D` renders filled rectangles — the building block for backgrounds, cards, and bars
2. Entities render in **declaration order** (first = back, last = front)
3. Layer entities intentionally — background → frame → content → overlay
4. Combine `Panel2D` and `Label2D` to build rich static compositions
5. There's no explicit z-index — use ordering (runtime can change this via timeline)

---

## Next

- [Sprites and Animation](03-sprites-and-animation.md) — images, timeline basics, easing curves
- [Back to Index](../jes-by-example.md)

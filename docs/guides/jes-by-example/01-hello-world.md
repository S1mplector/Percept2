# JES By Example — Hello World

The simplest possible JES scene: a single text label rendered on screen. This is where every JES journey begins.

**Difficulty:** Beginner
**Time:** 5 minutes
**Concepts:** Scene block, entity declaration, `Label2D` component, running a JES scene

---

## The Scene

```jes
scene "HelloWorld" {
  entity "title" {
    component Label2D {
      text: "Hello from JES!"
      x: 300
      y: 250
      size: 28
      bold: true
      color: rgb(1, 1, 1, 1)
    }
  }
}
```

## Run It

```bash
./gradlew :runtime:run --args='--jes game/scenes/hello.jes'
```

You should see white text reading "Hello from JES!" on a black background.

---

## Anatomy of a JES Scene

### The `scene` Block

Every JES file must contain at least one `scene` declaration. The first scene is loaded by default.

```jes
scene "Name" {
  // everything goes here
}
```

- The name is a **quoted string** — it identifies the scene in logs and debugging
- A file can technically contain multiple scenes, but the first one loads automatically
- The scene block is the **root container** for entities, input bindings, tilemaps, items, and timelines

### The `entity` Block

Entities are named objects that exist in the scene. They do nothing on their own — they need **components** to define their behavior and appearance.

```jes
entity "title" {
  // components go here
}
```

- Entity names must be **unique** within a scene
- Names are used to reference entities from timelines, input handlers, and Java code
- Entities render in **declaration order** (first declared = drawn first = furthest back)

### The `component` Block

Components are typed data blocks attached to an entity. Each component type has its own set of properties.

```jes
component Label2D {
  text: "Hello from JES!"
  x: 300
  y: 250
  size: 28
  bold: true
  color: rgb(1, 1, 1, 1)
}
```

`Label2D` is the simplest visual component — it renders text at a position.

---

## `Label2D` Property Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `text` | string | `""` | Text to display |
| `x` | number | `0` | Horizontal position (pixels from left) |
| `y` | number | `0` | Vertical position (pixels from top) |
| `size` | number | `14` | Font size in points |
| `bold` | boolean | `false` | Bold rendering |
| `color` | color | `rgb(1,1,1,1)` | Text color as `rgb(r,g,b,a)` with 0–1 floats |

---

## Color Values

JES uses float-based RGBA colors:

```jes
rgb(1, 0, 0, 1)       // solid red
rgb(0, 0.5, 1, 1)     // sky blue
rgb(1, 1, 1, 0.5)     // semi-transparent white
rgba(0, 0, 0, 0)      // fully transparent (rgba is also accepted)
```

All channels range from `0.0` to `1.0`. The fourth value is alpha (opacity).

---

## Variations to Try

### Multiple labels

```jes
scene "MultiLabel" {
  entity "greeting" {
    component Label2D {
      text: "Welcome to JVN"
      x: 250
      y: 200
      size: 32
      bold: true
      color: rgb(0.4, 0.8, 1, 1)
    }
  }

  entity "subtitle" {
    component Label2D {
      text: "A visual novel + game engine"
      x: 230
      y: 260
      size: 18
      color: rgb(0.7, 0.7, 0.7, 1)
    }
  }

  entity "footer" {
    component Label2D {
      text: "Press any key to continue..."
      x: 270
      y: 500
      size: 14
      color: rgb(0.4, 0.4, 0.4, 1)
    }
  }
}
```

### Colored text

```jes
scene "Colors" {
  entity "red" {
    component Label2D { text: "Red" x: 100 y: 100 size: 24 color: rgb(1, 0.2, 0.2, 1) }
  }
  entity "green" {
    component Label2D { text: "Green" x: 100 y: 140 size: 24 color: rgb(0.2, 1, 0.3, 1) }
  }
  entity "blue" {
    component Label2D { text: "Blue" x: 100 y: 180 size: 24 color: rgb(0.3, 0.5, 1, 1) }
  }
}
```

---

## Key Takeaways

1. Every JES file starts with `scene "Name" { ... }`
2. Entities are named containers for components
3. Components define rendering and behavior — `Label2D` renders text
4. Properties use `key: value` syntax with types: string, number, boolean, color
5. `rgb(r, g, b, a)` uses 0–1 float values

---

## Next

- [Shapes and Layout](02-shapes-and-layout.md) — `Panel2D` rectangles and layered entity composition
- [Back to Index](../jes-by-example.md)

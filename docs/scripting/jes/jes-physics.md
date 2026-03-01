# JES Physics & Collision

Complete reference for physics bodies, collision detection, sensors, triggers, and the physics simulation in JES.

Physics engine: `core/src/main/java/com/jvn/core/physics/PhysicsWorld2D.java`
Runtime: `scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`

---

## Physics World

Every `JesScene2D` has a built-in `PhysicsWorld2D` that simulates rigid body dynamics. The world runs `step()` each frame, resolving collisions, applying velocities, and updating body positions.

---

## PhysicsBody2D Component

### Syntax

```jes
entity "objectName" {
  component PhysicsBody2D {
    shape: circle     // or "box"
    x: 200
    y: 100
    // shape-specific size
    r: 15             // radius (circle only)
    w: 40             // width (box only)
    h: 40             // height (box only)
    mass: 1.0
    restitution: 0.5
    static: false
    sensor: false
    vx: 0
    vy: 0
    color: rgb(0.2, 0.8, 0.3, 1)
    onTrigger: "handleCollision"
  }
}
```

### Properties

| Property | Default | Description |
|----------|---------|-------------|
| `shape` | `circle` | `"circle"` or `"box"` |
| `x`, `y` | 0 | Initial position |
| `r` | 0.5 | Radius (circle shape only) |
| `w`, `h` | 1 | Width/height (box shape only) |
| `mass` | 1.0 | Mass for dynamics (0 = infinite/static) |
| `restitution` | 0.2 | Bounciness (0 = no bounce, 1 = full bounce) |
| `static` | false | If true, body does not move |
| `sensor` | false | If true, detects overlap without physical response |
| `vx`, `vy` | 0 | Initial velocity |
| `color` | — | Debug visualization color |
| `onTrigger` | — | Call handler name invoked on collision |

---

## Circle Bodies

```jes
entity "ball" {
  component PhysicsBody2D {
    shape: circle
    x: 400
    y: 100
    r: 20
    mass: 1.0
    restitution: 0.8
    vy: 50
    color: rgb(1, 0.3, 0.3, 1)
  }
}
```

Circle bodies are ideal for:
- Projectiles
- Balls
- Rounded obstacles
- Character collision proxies

---

## Box Bodies

```jes
entity "crate" {
  component PhysicsBody2D {
    shape: box
    x: 300
    y: 200
    w: 50
    h: 50
    mass: 2.0
    restitution: 0.3
    color: rgb(0.6, 0.4, 0.2, 1)
  }
}
```

Box bodies are ideal for:
- Walls and platforms
- Crates and obstacles
- Rectangular collision zones

---

## Static Bodies

Static bodies never move but participate in collision response. Other bodies bounce off them.

```jes
// Floor
entity "floor" {
  component PhysicsBody2D {
    shape: box
    x: 400
    y: 580
    w: 800
    h: 20
    static: true
    restitution: 0.3
    color: rgb(0.4, 0.4, 0.4, 1)
  }
}

// Wall
entity "left_wall" {
  component PhysicsBody2D {
    shape: box
    x: 0
    y: 300
    w: 20
    h: 600
    static: true
    restitution: 0.5
  }
}
```

---

## Sensors

Sensors detect overlaps without applying physical force. They trigger callbacks but don't bounce or deflect other bodies.

```jes
entity "goal_zone" {
  component PhysicsBody2D {
    shape: box
    x: 700
    y: 300
    w: 60
    h: 60
    sensor: true
    onTrigger: "goalReached"
  }
}
```

Use sensors for:
- Goal/finish zones
- Pickup areas
- Damage zones
- Proximity detection

---

## Collision Callbacks

When `onTrigger` is set, the handler is invoked with collision info:

```jes
entity "pocket" {
  component PhysicsBody2D {
    shape: circle
    x: 50
    y: 50
    r: 25
    static: true
    sensor: true
    onTrigger: "handlePocket"
  }
}
```

The callback receives props including:
- `other` — name of the other entity involved
- `thisBody` / `otherBody` — physics body references

**Java handler:**

```java
scene.registerCall("handlePocket", props -> {
    String other = (String) props.get("other");
    if (other != null) {
        scene.removeEntity(other);
        // Score tracking, effects, etc.
    }
});
```

---

## Restitution Guide

| Value | Behavior |
|-------|----------|
| `0.0` | No bounce (dead stop on contact) |
| `0.2` | Slight bounce (default, realistic) |
| `0.5` | Moderate bounce |
| `0.8` | High bounce |
| `1.0` | Full elastic bounce (no energy loss) |

Effective restitution between two bodies = `min(body1.restitution, body2.restitution)`.

---

## Spawning Bodies at Runtime

Use input bindings or call handlers to spawn physics bodies dynamically.

### Via Input Binding

```jes
on key "C" do spawnCircle { r: 12 mass: 1 restitution: 0.7 }
on key "B" do spawnBox { w: 30 h: 30 mass: 1.5 restitution: 0.4 }
```

Bodies are spawned at the current mouse position.

### Via Java API

```java
RigidBody2D body = RigidBody2D.circle(x, y, radius);
body.setMass(1.0);
body.setRestitution(0.5);
body.setVelocity(vx, vy);
scene.getWorld().addBody(body);
```

---

## Raycasting

Cast a ray between two points and check for collision:

```java
PhysicsWorld2D.RaycastHit hit = scene.raycast(x1, y1, x2, y2);
if (hit != null) {
    // hit.body, hit.point, hit.normal, hit.distance
}
```

Used internally by AI line-of-sight checks and can be used by custom Java hooks.

---

## Tile-Based Collision

For tile map collision (Character2D movement), see [Tilemaps & Maps](jes-tilemaps.md). Tile collision and physics body collision are separate systems:

- **Tile collision** — grid-based, used by `Character2D` movement, `moveHero`, and AI pathfinding
- **Physics collision** — continuous rigid body simulation with `PhysicsBody2D` components

---

## Debug Visualization

Toggle the physics debug overlay with:

```jes
on key "D" do toggleDebug
```

The overlay draws:
- Circle/box outlines for all physics bodies
- Body colors as specified in the component
- Collision contact points
- Grid overlay (when tilemaps are present)

---

## Example: Pool/Billiards Table

```jes
scene "PoolTable" {
  // Table surface
  entity "table" {
    component Panel2D {
      x: 50
      y: 50
      w: 700
      h: 400
      fill: rgb(0.1, 0.5, 0.2, 1)
    }
  }

  // Table borders (static walls)
  entity "top_wall" {
    component PhysicsBody2D { shape: box x: 400 y: 50 w: 700 h: 10 static: true restitution: 0.7 }
  }
  entity "bottom_wall" {
    component PhysicsBody2D { shape: box x: 400 y: 450 w: 700 h: 10 static: true restitution: 0.7 }
  }
  entity "left_wall" {
    component PhysicsBody2D { shape: box x: 50 y: 250 w: 10 h: 400 static: true restitution: 0.7 }
  }
  entity "right_wall" {
    component PhysicsBody2D { shape: box x: 750 y: 250 w: 10 h: 400 static: true restitution: 0.7 }
  }

  // Corner pockets (sensors)
  entity "pocket_tl" {
    component PhysicsBody2D { shape: circle x: 60 y: 60 r: 20 static: true sensor: true onTrigger: "handlePocket" }
  }
  entity "pocket_tr" {
    component PhysicsBody2D { shape: circle x: 740 y: 60 r: 20 static: true sensor: true onTrigger: "handlePocket" }
  }
  entity "pocket_bl" {
    component PhysicsBody2D { shape: circle x: 60 y: 440 r: 20 static: true sensor: true onTrigger: "handlePocket" }
  }
  entity "pocket_br" {
    component PhysicsBody2D { shape: circle x: 740 y: 440 r: 20 static: true sensor: true onTrigger: "handlePocket" }
  }

  // Cue ball
  entity "cue_ball" {
    component PhysicsBody2D {
      shape: circle
      x: 250
      y: 250
      r: 10
      mass: 1.0
      restitution: 0.9
      color: rgb(1, 1, 1, 1)
    }
  }

  // Target balls
  entity "ball_1" {
    component PhysicsBody2D { shape: circle x: 500 y: 230 r: 10 mass: 1 restitution: 0.9 color: rgb(1, 0, 0, 1) }
  }
  entity "ball_2" {
    component PhysicsBody2D { shape: circle x: 520 y: 250 r: 10 mass: 1 restitution: 0.9 color: rgb(0, 0, 1, 1) }
  }
  entity "ball_3" {
    component PhysicsBody2D { shape: circle x: 500 y: 270 r: 10 mass: 1 restitution: 0.9 color: rgb(1, 1, 0, 1) }
  }

  // Score display
  entity "score_label" {
    component Label2D { text: "Score: 0" x: 10 y: 10 size: 16 bold: true color: rgb(1, 1, 1, 1) }
  }

  on key "SPACE" do strike
  on key "R" do resetBalls
  on key "D" do toggleDebug
}
```

---

## Related Docs

- [JES Overview](jes-scripting.md)
- [Component Reference](components.md) — `PhysicsBody2D`
- [Tilemaps & Maps](jes-tilemaps.md) — tile-based collision
- [Scenes & Entities](jes-scenes-entities.md)

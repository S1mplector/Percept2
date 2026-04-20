# JES By Example — Physics Bodies

Add rigid body physics to entities for collision-based gameplay — bouncing balls, solid platforms, and sensor trigger zones.

**Difficulty:** Intermediate
**Time:** 20 minutes
**Concepts:** `PhysicsBody2D`, circle/box bodies, static bodies, sensors, `onTrigger`, `PhysicsWorld2D`, gravity, restitution, friction

---

## The Scene

```jes
scene "PhysicsDemo" {
  entity "ball" {
    component Sprite2D {
      image: "assets/sprites/ball.png"
      x: 400
      y: 100
      w: 32
      h: 32
    }
    component PhysicsBody2D {
      type: circle
      radius: 16
      restitution: 0.8
      friction: 0.3
      mass: 1.0
    }
  }

  entity "platform" {
    component Panel2D {
      x: 200
      y: 500
      w: 400
      h: 20
      fill: rgb(0.6, 0.6, 0.6, 1)
    }
    component PhysicsBody2D {
      type: box
      w: 400
      h: 20
      static: true
    }
  }

  entity "sensor_zone" {
    component Panel2D {
      x: 350
      y: 550
      w: 100
      h: 20
      fill: rgb(1, 0.3, 0.3, 0.3)
    }
    component PhysicsBody2D {
      type: box
      w: 100
      h: 20
      sensor: true
      onTrigger: "goalReached"
    }
  }

  on key "D" do toggleDebug
}
```

---

## `PhysicsBody2D` Property Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `type` | `circle` or `box` | required | Body shape |
| `radius` | number | `0` | Circle radius (only for `circle` type) |
| `w` | number | `0` | Box width (only for `box` type) |
| `h` | number | `0` | Box height (only for `box` type) |
| `mass` | number | `1.0` | Body mass (affects collision response) |
| `restitution` | number | `0.0` | Bounciness: `0` = no bounce, `1` = perfect bounce |
| `friction` | number | `0.3` | Surface friction: `0` = ice, `1` = rough |
| `static` | boolean | `false` | If `true`, body never moves (walls, platforms) |
| `sensor` | boolean | `false` | If `true`, detects overlap but doesn't collide physically |
| `onTrigger` | string | `""` | Call handler name invoked when a sensor is entered |

---

## Body Types

### Dynamic Bodies (default)

Regular physics objects affected by gravity and collisions:

```jes
entity "ball" {
  component Sprite2D { ... }
  component PhysicsBody2D {
    type: circle
    radius: 16
    mass: 1.0
    restitution: 0.7
  }
}
```

Dynamic bodies:
- Fall under gravity
- Collide with other bodies
- Bounce based on `restitution`
- Slide based on `friction`

### Static Bodies

Immovable objects that other bodies collide against:

```jes
entity "wall" {
  component Panel2D { x: 0 y: 580 w: 800 h: 20 fill: rgb(0.4, 0.4, 0.4, 1) }
  component PhysicsBody2D {
    type: box
    w: 800
    h: 20
    static: true
  }
}
```

Static bodies:
- Never move, regardless of forces
- Other bodies bounce off them
- Zero computational cost for movement
- Use for floors, walls, platforms, and boundaries

### Sensor Bodies

Detect overlap without physical collision:

```jes
entity "goal_zone" {
  component Panel2D { x: 350 y: 550 w: 100 h: 30 fill: rgb(0, 1, 0, 0.2) }
  component PhysicsBody2D {
    type: box
    w: 100
    h: 30
    sensor: true
    onTrigger: "goalReached"
  }
}
```

Sensors:
- Don't cause physical collision (objects pass through)
- Fire the `onTrigger` call handler when another body enters
- Perfect for goal zones, damage zones, pickup areas, area triggers

---

## Shapes

### Circle

Best for balls, projectiles, and round objects:

```jes
component PhysicsBody2D {
  type: circle
  radius: 16
}
```

The circle is centered on the entity's position. `radius` is in pixels.

### Box

Best for platforms, walls, and rectangular objects:

```jes
component PhysicsBody2D {
  type: box
  w: 100
  h: 20
}
```

The box is positioned at the entity's top-left corner, matching the visual component's `x`/`y`/`w`/`h`.

---

## Physics Properties Explained

### Restitution (Bounciness)

Controls how much energy is preserved on collision:

| Value | Behavior |
|-------|----------|
| `0.0` | No bounce — stops dead on impact |
| `0.5` | Medium bounce — loses half its energy |
| `0.8` | High bounce — retains most energy |
| `1.0` | Perfect bounce — never loses energy |

### Friction

Controls surface resistance:

| Value | Behavior |
|-------|----------|
| `0.0` | No friction (ice) — slides freely |
| `0.3` | Normal friction — moderate sliding |
| `0.8` | High friction — resists sliding |
| `1.0` | Maximum friction — almost no sliding |

### Mass

Affects collision response between dynamic bodies:

- Heavier bodies push lighter ones more
- Equal masses result in symmetric bounces
- Mass doesn't affect gravity speed (all objects fall at the same rate)

---

## Sensor Events

When a body enters a sensor, the `onTrigger` call handler receives:

```java
// Automatically called with these props:
{
  "sensor": "goal_zone",   // name of the sensor entity
  "other": "ball"          // name of the entity that entered
}
```

### Default Sensor Handler

If `onTrigger` is not specified, the engine calls a default `sensorHit` handler:

```java
scene.registerCall("sensorHit", props -> {
    String sensor = (String) props.get("sensor");
    String other = (String) props.get("other");
    System.out.println(other + " entered " + sensor);
});
```

### Custom Sensor Handlers

```java
scene.registerCall("goalReached", props -> {
    String other = (String) props.get("other");
    if ("ball".equals(other)) {
        // Ball reached the goal!
        score[0]++;
        scene.invokeCall("setLabelText", Map.of(
            "target", "score_label",
            "text", "Score: " + score[0]
        ));
    }
});

scene.registerCall("damageZone", props -> {
    String other = (String) props.get("other");
    Stats stats = scene.getStats(other);
    if (stats != null) {
        stats.setHp(stats.getHp() - 10);
    }
});
```

---

## Physics World

The `PhysicsWorld2D` steps automatically every frame. The default configuration includes gravity pulling objects downward.

### Accessing from Java

```java
PhysicsWorld2D world = scene.getWorld();
```

### Physics + Visual Sync

Physics bodies automatically sync their position to the entity's visual position each frame. You don't need to manually update entity positions — the physics engine handles it.

---

## Spawning Bodies from Timeline

Use `spawnCircle` and `spawnBox` timeline actions to create physics bodies dynamically:

```jes
timeline {
  spawnCircle { name: "bullet" x: 400 y: 300 radius: 8 vx: 200 vy: -100 restitution: 0.9 }
  spawnBox { name: "crate" x: 200 y: 100 w: 32 h: 32 mass: 2.0 }
}
```

---

## Full Example: Pinball Board

```jes
scene "Pinball" {
  entity "bg" {
    component Panel2D {
      x: 0
      y: 0
      w: 800
      h: 600
      fill: rgb(0.05, 0.05, 0.1, 1)
    }
  }

  entity "ball" {
    component Sprite2D {
      image: "assets/sprites/ball.png"
      x: 400
      y: 50
      w: 24
      h: 24
    }
    component PhysicsBody2D {
      type: circle
      radius: 12
      restitution: 0.85
      friction: 0.2
      mass: 0.5
    }
  }

  entity "left_wall" {
    component Panel2D { x: 0 y: 0 w: 10 h: 600 fill: rgb(0.3, 0.3, 0.3, 1) }
    component PhysicsBody2D { type: box w: 10 h: 600 static: true }
  }

  entity "right_wall" {
    component Panel2D { x: 790 y: 0 w: 10 h: 600 fill: rgb(0.3, 0.3, 0.3, 1) }
    component PhysicsBody2D { type: box w: 10 h: 600 static: true }
  }

  entity "bumper_a" {
    component Sprite2D {
      image: "assets/sprites/bumper.png"
      x: 250
      y: 250
      w: 48
      h: 48
    }
    component PhysicsBody2D {
      type: circle
      radius: 24
      static: true
      restitution: 1.2
    }
  }

  entity "bumper_b" {
    component Sprite2D {
      image: "assets/sprites/bumper.png"
      x: 550
      y: 200
      w: 48
      h: 48
    }
    component PhysicsBody2D {
      type: circle
      radius: 24
      static: true
      restitution: 1.2
    }
  }

  entity "floor_sensor" {
    component Panel2D { x: 0 y: 590 w: 800 h: 10 fill: rgb(1, 0, 0, 0.2) }
    component PhysicsBody2D {
      type: box
      w: 800
      h: 10
      sensor: true
      onTrigger: "ballLost"
    }
  }

  entity "score_label" {
    component Label2D {
      text: "Score: 0"
      x: 20
      y: 15
      size: 18
      bold: true
      color: rgb(1, 1, 1, 1)
    }
  }

  on key "D" do toggleDebug
  on key "R" do resetBalls
}
```

```java
int[] score = {0};

scene.registerCall("ballLost", props -> {
    // Reset the ball to the top
    Entity2D ball = scene.find("ball");
    if (ball != null) {
        ball.setPosition(400, 50);
    }
});
```

---

## Key Takeaways

1. `PhysicsBody2D` adds rigid body physics to any entity
2. Two shapes: `circle` (radius) and `box` (w/h)
3. `static: true` creates immovable objects (walls, platforms, bumpers)
4. `sensor: true` detects overlap without physical collision
5. `onTrigger` fires a named call handler with `sensor` and `other` entity names
6. `restitution` controls bounciness, `friction` controls sliding
7. Physics steps automatically each frame — no manual position syncing needed
8. `spawnCircle`/`spawnBox` create bodies dynamically from timelines

---

## Next

- [VNS Bridge Integration](10-vns-bridge.md) — launching JES from VNS with data exchange
- [Back to Index](../jes-by-example.md)

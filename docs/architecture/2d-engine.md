# 2D Engine

This document covers the JES + Scene2D stack used for gameplay overlays, minigames, and the underlying 2D primitives.

---

## Core 2D Stack

| Class | Role | Source |
|-------|------|--------|
| `Scene2DBase` | Base scene with entity list, camera, rendering | `core/.../scene2d/Scene2DBase.java` |
| `Entity2D` | Base entity with transform, parallax, z-order | `core/.../scene2d/Entity2D.java` |
| `Camera2D` | Camera with smoothing, bounds, zoom, world↔screen transforms | `core/.../graphics/Camera2D.java` |
| `PhysicsWorld2D` | Physics simulation: gravity, collisions, raycasts, broadphase | `core/.../physics/PhysicsWorld2D.java` |
| `RigidBody2D` | Physics body: AABB or circle, velocity, mass, friction | `core/.../physics/RigidBody2D.java` |
| `JesScene2D` | JES-specific scene with entity wiring, timelines, input bindings | `scripting/.../JesScene2D.java` |
| `Input` | Backend-agnostic input: keyboard, mouse, gamepad | `core/.../input/Input.java` |

---

## Entity2D

The base class for all renderable objects. Every entity has:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `x`, `y` | `double` | `0.0` | World position |
| `z` | `double` | `0.0` | Depth for render ordering (higher = drawn later / on top) |
| `rotationDeg` | `double` | `0.0` | Rotation in degrees |
| `scaleX`, `scaleY` | `double` | `1.0` | Scale factors |
| `visible` | `boolean` | `true` | Whether the entity is rendered |
| `parallaxX`, `parallaxY` | `double` | `1.0` | Parallax scrolling factors (1.0 = moves with camera, 0.0 = fixed to screen, 0.5 = half-speed) |
| `originX`, `originY` | `double` | `0.0` | Transform origin point |

Entity lifecycle: `update(deltaMs)` is called every frame. `render(Blitter2D)` draws the entity.

### Entity Specializations

| Type | Adds |
|------|------|
| `Sprite2D` | Image rendering, sprite sheets, animation frames |
| `Label2D` | Text rendering with font, size, color |
| `Panel2D` | Colored rectangle, background panel |
| `TileMap2D` | Tile-based map rendering with layers |
| `ParticleEmitter2D` | Particle effects (emission rate, lifetime, spread) |
| `Character2D` | RPG character with stats, inventory, equipment |
| `Button2D` | Interactive UI button with hover/press states |
| `Slider2D` | Draggable slider UI widget |

See [Component Reference](../scripting/jes/components.md) for per-property documentation of all 12 JES component types.

---

## Scene2DBase — Render Pipeline

Source: `core/src/main/java/com/jvn/core/scene2d/Scene2DBase.java`

The base 2D scene manages a list of `Entity2D` children and renders them with camera transforms.

### Update cycle

1. Update camera (`camera.update(deltaMs)` — applies smoothing toward target)
2. Update all children (`entity.update(deltaMs)`)

### Render pipeline

1. **Z-sort** children by `entity.getZ()` (ascending — lower z renders first / behind)
2. **Push camera transform** — translate by `(-camera.x, -camera.y)` and scale by `camera.zoom`
3. **For each visible entity:**
   - Apply **parallax offset**: `camera.x * (1 - entity.parallaxX)` (entities with parallax < 1.0 scroll slower than the camera, creating depth)
   - Translate to entity position
   - Apply rotation and scale
   - Call `entity.render(blitter)`
4. **Pop camera transform**

### Parallax scrolling

Parallax values control how entities respond to camera movement:

| `parallaxX` | Effect |
|-------------|--------|
| `1.0` | Normal — moves with the world (default) |
| `0.0` | Fixed to screen — HUD elements, UI overlays |
| `0.5` | Half-speed — distant background layer |
| `2.0` | Double-speed — close foreground layer |

```jes
entity "far_mountains" {
    Sprite2D { image: "assets/bg/mountains.png" }
    parallaxX: 0.3
    parallaxY: 0.3
}

entity "hud_score" {
    Label2D { text: "Score: 0", fontSize: 24 }
    parallaxX: 0.0
    parallaxY: 0.0
}
```

---

## Camera2D

Source: `core/src/main/java/com/jvn/core/graphics/Camera2D.java`

### Properties

| Property | Default | Description |
|----------|---------|-------------|
| `x`, `y` | `0.0` | Current camera position |
| `zoom` | `1.0` | Zoom level (> 1.0 zooms in, < 1.0 zooms out) |
| `targetX`, `targetY` | `0.0` | Target position for smooth following |
| `smoothingMs` | `0.0` | Smoothing time constant (0 = instant snap, higher = smoother / laggier) |
| `bounds` | none | Optional rectangular clamp region |

### Smoothing

Camera smoothing uses an **exponential decay** model:

```
alpha = 1 - exp(-deltaMs / smoothingMs)
position += (target - position) * alpha
```

This produces smooth, framerate-independent camera following. Larger `smoothingMs` values create lazier cameras.

```jes
cameraMove 0ms 400 300 1.0     # snap to position
cameraFollow "player" 150      # follow with 150ms smoothing
```

### Bounds clamping

`setBounds(left, top, right, bottom)` constrains the camera position within a rectangle. Useful for preventing the camera from showing empty space beyond map edges.

```java
camera.setBounds(0, 0, mapWidth, mapHeight);
camera.clearBounds(); // remove constraint
```

### Coordinate transforms

```java
// World → screen (for HUD positioning, hit detection)
double screenX = camera.worldToScreenX(worldX, viewportWidth, originX);

// Screen → world (for mouse clicks, touch input)
double worldX = camera.screenToWorldX(screenX, viewportWidth, originX);
```

---

## Physics System

Source: `core/src/main/java/com/jvn/core/physics/PhysicsWorld2D.java`, `RigidBody2D.java`

### RigidBody2D

Two shape types with configurable physical properties:

| Property | Default | Description |
|----------|---------|-------------|
| `shapeType` | `AABB` | `AABB` (axis-aligned box) or `CIRCLE` |
| `mass` | `1.0` | Mass (affects collision response) |
| `isStatic` | `false` | Static bodies don't move but participate in collision |
| `restitution` | `0.2` | Bounciness [0..1] (0 = no bounce, 1 = perfect bounce) |
| `friction` | `0.2` | Kinetic friction [0..1] |
| `linearDamping` | `0.0` | Velocity damping per second (air resistance) |
| `sensor` | `false` | Sensors detect overlap but don't produce collision response |
| `vx`, `vy` | `0.0` | Current velocity |

Factory methods:

```java
RigidBody2D box = RigidBody2D.box(x, y, width, height);
RigidBody2D circle = RigidBody2D.circle(x, y, radius);
```

### PhysicsWorld2D

| Feature | Description |
|---------|-------------|
| **Gravity** | `setGravity(gx, gy)` — applied to all dynamic bodies each step |
| **World bounds** | Optional `Rect` — bodies bounce off edges, fires `onBoundsCollide` |
| **Static rects** | `addStaticRect(Rect)` — immovable colliders (tilemap tiles, walls) |
| **Fixed timestep** | `setFixedTimeStepMs(stepMs, maxSubSteps)` — deterministic simulation |
| **Max step clamp** | `setMaxStepMs(ms)` — prevents simulation explosion from large deltas |
| **Broadphase** | Spatial hash grid (`setBroadphaseCellSize(px)`) — reduces pairwise checks for large body counts |
| **Raycasts** | `raycast(x1, y1, x2, y2)` → `RaycastHit` with body, position, normal, distance |

### Collision callbacks

```java
// Body-body collisions (dynamic bodies)
world.setCollisionListener(new CollisionListener() {
    void onBodiesCollide(RigidBody2D a, RigidBody2D b, double nx, double ny) { ... }
    void onBoundsCollide(RigidBody2D b, String side) { ... }  // "left"/"right"/"top"/"bottom"
    void onStaticCollide(RigidBody2D b, Rect tile, double nx, double ny) { ... }
});

// Sensor overlaps (trigger zones)
world.setSensorListener((sensor, other) -> { ... });
```

### Simulation step

Each `step(deltaMs)`:

1. Clamp to `maxStepMs` (default 50ms)
2. If fixed timestep enabled: accumulate time, run `stepOnce` at fixed intervals
3. For each dynamic body: apply gravity → apply damping → integrate velocity → resolve world bounds → resolve static colliders
4. Gather collision pairs (broadphase spatial hash) → detect collisions → handle sensors or apply impulse response

### Broadphase

The spatial hash grid (`broadphaseCellSize`, default 128px) divides world space into cells. Only bodies in the same or adjacent cells are tested for collision, reducing O(n²) to roughly O(n) for evenly distributed bodies.

```java
world.setBroadphaseCellSize(64);  // smaller cells = more precise but more overhead
```

---

## JES Scene Assembly

`JesLoader` converts JES AST to a runtime scene and wires:

- entities/components with validated properties
- tilemaps/collision layers → static rect colliders
- input bindings → action handlers
- timeline actions → sequenced animation/logic
- optional RPG-style components (`Stats`, `Inventory`, `Equipment`, `Ai2D`)

## Timeline Actions in Runtime

Parser/runtime support includes:

| Category | Actions |
|----------|---------|
| **Timing** | `wait`, `waitForCall`, `call` |
| **Transform** | `move`, `walkToTile`, `rotate`, `scale`, `fade`, `visible` |
| **Camera** | `cameraMove`, `cameraZoom`, `cameraShake`, `cameraFollow` |
| **Audio** | `playAudio`, `stopAudio` |
| **Gameplay** | `damage`, `heal`, `emitParticles`, `setParallax` |
| **Flow** | `label`, `jump`, `parallel`, `loop` |

See [Timeline & Actions](../scripting/jes/jes-timeline.md) for full syntax and examples.

---

## Asset Resolution

Assets are loaded through `AssetCatalog` with manager selection from runtime startup:

- **`ClasspathAssetManager`** — default, loads from JAR/classpath
- **`FilesystemAssetManager`** — overlays filesystem paths with classpath fallback (when `--assets` is provided)

`AssetCatalog` provides typed listing helpers:

```java
catalog.listImages()    // assets/images/
catalog.listAudio()     // assets/audio/
catalog.listScripts()   // scripts/
catalog.listFonts()     // assets/fonts/
catalog.listUI()        // assets/ui/
catalog.listVideo()     // assets/video/
catalog.listConfig()    // config/
```

Recommended JES asset conventions:
- images/sprites in `assets/` (project side) or `game/images/` (classpath side)
- scripts in `scripts/` (project) or `game/scripts/` (classpath)

---

## VN + JES Coexistence

JVN lets narrative scenes and 2D scripted scenes cooperate via the scene stack:

1. VNS pushes a JES scene for a minigame (`[jes push game/puzzle.jes]`).
2. VN scene receives `onPause` — audio/animation pauses.
3. JES scene runs independently with its own input, physics, timelines.
4. JES returns values back to VN variables (`call "return" { score: 42 }`).
5. JES scene is popped, VN scene receives `onResume`.
6. VN flow resumes from the return label.

This enables "story → gameplay → story" loops without leaving the engine runtime.

---

## Current Practical Limits

- JES timeline is intentionally declarative; heavy per-frame custom logic should stay in Java scene code.
- Parser validates known properties but intentionally allows unknown component types for extension paths.
- Very dense body scenes benefit from tuning `broadphaseCellSize` and using static colliders for map geometry.

## Short-Term Extension Opportunities

- Richer UI component set for JES overlays
- More timeline easing/curve controls
- Dedicated in-editor collision/trigger visualization
- Scene profiling overlays (entity count, physics steps, timeline action timings)

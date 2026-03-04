# JES Component Reference

Complete reference for all 12 JES component types — every property with type, default value, description, and annotated examples.

Parser validation source: `scripting/src/main/java/com/jvn/scripting/jes/JesParser.java`
Runtime loader: `scripting/src/main/java/com/jvn/scripting/jes/JesLoader.java`

The parser enforces **strict property validation**: unknown properties on known component types produce a parse error with line and column. This catches typos before runtime.

---

## Panel2D

A solid-color rectangle. Use for backgrounds, UI panels, and debug overlays.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `x` | number | `0` | X position |
| `y` | number | `0` | Y position |
| `w` | number | `1` | Width in pixels |
| `h` | number | `1` | Height in pixels |
| `fill` | `rgb(r,g,b,a)` | — | Fill color (0.0–1.0 per channel) |

### Example

```jes
entity "background" {
  component Panel2D {
    x: 0
    y: 0
    w: 800
    h: 600
    fill: rgb(0.05, 0.07, 0.12, 1)
  }
}

entity "health_bar_bg" {
  component Panel2D {
    x: 20
    y: 20
    w: 200
    h: 16
    fill: rgb(0.2, 0.2, 0.2, 0.8)
  }
}
```

---

## Sprite2D

An image sprite with optional source-region cropping. Core building block for characters, items, and scene decoration.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `image` | string | `null` | Asset path to the image file |
| `x` | number | `0` | X position |
| `y` | number | `0` | Y position |
| `w` | number | `64` | Display width |
| `h` | number | `64` | Display height |
| `alpha` | number | `1.0` | Opacity (0.0 = invisible, 1.0 = opaque) |
| `originX` | number | `0.0` | Origin X (fraction of width, for rotation/scale pivot) |
| `originY` | number | `0.0` | Origin Y (fraction of height) |
| `sx` | number | `0` | Source region X (pixels in source image) |
| `sy` | number | `0` | Source region Y |
| `sw` | number | `w` | Source region width |
| `sh` | number | `h` | Source region height |
| `dw` | number | `w` | Destination draw width (when using region) |
| `dh` | number | `h` | Destination draw height (when using region) |

Region properties (`sx`, `sy`, `sw`, `sh`, `dw`, `dh`) are **optional**. If any region property is set, the sprite uses region rendering mode — drawing a sub-rectangle of the source image.

### Examples

**Simple sprite:**

```jes
entity "logo" {
  component Sprite2D {
    image: "assets/ui/logo.png"
    x: 300
    y: 50
    w: 200
    h: 80
  }
}
```

**Semi-transparent sprite with centered origin:**

```jes
entity "ghost" {
  component Sprite2D {
    image: "assets/characters/ghost.png"
    x: 400
    y: 300
    w: 96
    h: 96
    alpha: 0.6
    originX: 0.5
    originY: 0.5
  }
}
```

**Sprite with source region (sprite sheet crop):**

```jes
entity "icon" {
  component Sprite2D {
    image: "assets/ui/icons_sheet.png"
    x: 10
    y: 10
    sx: 64
    sy: 0
    sw: 32
    sh: 32
    dw: 48
    dh: 48
  }
}
```

---

## Label2D

A text display element. Use for HUD text, score counters, NPC names, and debug info.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `text` | string | `""` | Display text |
| `x` | number | `0` | X position |
| `y` | number | `0` | Y position |
| `size` | number | `16` | Font size in pixels |
| `bold` | boolean | `false` | Bold weight |
| `color` | `rgb(r,g,b,a)` | — | Text color (0.0–1.0 per channel) |
| `align` | string | `"left"` | Text alignment: `left`, `center`, or `right` |

### Examples

```jes
entity "title" {
  component Label2D {
    text: "Level 1"
    x: 400
    y: 30
    size: 36
    bold: true
    color: rgb(1, 0.85, 0, 1)
    align: center
  }
}

entity "score" {
  component Label2D {
    text: "Score: 0"
    x: 10
    y: 560
    size: 20
    bold: false
    color: rgb(0.9, 0.9, 0.9, 1)
  }
}
```

---

## ParticleEmitter2D

A particle effect system. Use for fire, smoke, sparks, rain, magic effects, explosions, and ambient atmosphere.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `x` | number | `0` | Emitter X position |
| `y` | number | `0` | Emitter Y position |
| `emissionRate` | number | `10` | Particles spawned per second |
| `minLife` | number | `1.0` | Minimum particle lifetime (seconds) |
| `maxLife` | number | `3.0` | Maximum particle lifetime (seconds) |
| `minSize` | number | `2.0` | Minimum particle size (pixels) |
| `maxSize` | number | `8.0` | Maximum particle size (pixels) |
| `endSizeScale` | number | `0.1` | Size multiplier at end of life (0.1 = shrink to 10%) |
| `minSpeed` | number | `50` | Minimum emit speed (pixels/sec) |
| `maxSpeed` | number | `150` | Maximum emit speed (pixels/sec) |
| `minAngle` | number | `0` | Minimum emit angle (degrees, 0 = right) |
| `maxAngle` | number | `360` | Maximum emit angle (degrees) |
| `gravityY` | number | `100` | Downward gravity (pixels/sec²) |
| `texture` | string | `null` | Optional particle texture asset path |
| `additive` | boolean | `true` | Additive blending (glow effect) |
| `startColor` | `rgb(r,g,b,a)` | — | Particle color at spawn |
| `endColor` | `rgb(r,g,b,a)` | — | Particle color at death |

### Examples

**Campfire:**

```jes
entity "fire" {
  component ParticleEmitter2D {
    x: 400
    y: 500
    emissionRate: 30
    minLife: 0.3
    maxLife: 0.8
    minSize: 4
    maxSize: 12
    endSizeScale: 0.0
    minSpeed: 30
    maxSpeed: 80
    minAngle: 250
    maxAngle: 290
    gravityY: -50
    additive: true
    startColor: rgb(1, 0.6, 0, 1)
    endColor: rgb(1, 0.1, 0, 0)
  }
}
```

**Snow:**

```jes
entity "snow" {
  component ParticleEmitter2D {
    x: 400
    y: -10
    emissionRate: 15
    minLife: 3
    maxLife: 6
    minSize: 2
    maxSize: 5
    endSizeScale: 0.8
    minSpeed: 20
    maxSpeed: 40
    minAngle: 80
    maxAngle: 100
    gravityY: 30
    additive: false
    startColor: rgb(1, 1, 1, 0.9)
    endColor: rgb(1, 1, 1, 0.2)
  }
}
```

---

## PhysicsBody2D

A rigid body in the physics world — circles or axis-aligned boxes with mass, restitution, velocity, sensor mode, and trigger callbacks.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `shape` | string | `"circle"` | Body shape: `circle` or `box` |
| `x` | number | `0` | X position |
| `y` | number | `0` | Y position |
| `r` | number | `0.5` | Radius (circle only) |
| `w` | number | `1` | Width (box only) |
| `h` | number | `1` | Height (box only) |
| `mass` | number | `1` | Body mass |
| `restitution` | number | `0.2` | Bounciness (0 = no bounce, 1 = full bounce) |
| `static` | boolean | `false` | If true, body doesn't move |
| `sensor` | boolean | `false` | If true, detects overlaps but doesn't collide |
| `vx` | number | `0` | Initial X velocity |
| `vy` | number | `0` | Initial Y velocity |
| `color` | `rgb(r,g,b,a)` | — | Debug visualization color |
| `onTrigger` | string | `null` | Call handler name when sensor triggers |

### Examples

**Bouncing ball:**

```jes
entity "ball" {
  component PhysicsBody2D {
    shape: circle
    x: 400
    y: 100
    r: 16
    mass: 1
    restitution: 0.8
    vy: 100
    color: rgb(0.2, 0.6, 1, 1)
  }
}
```

**Static wall:**

```jes
entity "floor" {
  component PhysicsBody2D {
    shape: box
    x: 0
    y: 580
    w: 800
    h: 20
    static: true
    color: rgb(0.3, 0.3, 0.3, 1)
  }
}
```

**Sensor trigger zone:**

```jes
entity "exit_zone" {
  component PhysicsBody2D {
    shape: box
    x: 750
    y: 200
    w: 50
    h: 200
    sensor: true
    onTrigger: "exitReached"
    color: rgb(0, 1, 0, 0.3)
  }
}
```

---

## Character2D

An animated sprite-sheet character with tile-based movement, directional animations, and dialogue support. The primary component for top-down RPG characters.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `spriteSheet` | string | `null` | Asset path to the sprite sheet image |
| `frameW` | int | `16` | Frame width in source pixels |
| `frameH` | int | `16` | Frame height in source pixels |
| `cols` | int | `8` | Columns in the sprite sheet |
| `drawW` | number | `frameW` | Display width |
| `drawH` | number | `frameH` | Display height |
| `x` | number | `0` | X position (pixels, or overridden by `startTileX`) |
| `y` | number | `0` | Y position (pixels, or overridden by `startTileY`) |
| `startTileX` | int | — | Starting tile column (if a map is defined) |
| `startTileY` | int | — | Starting tile row (if a map is defined) |
| `speed` | number | `80.0` | Movement speed in pixels/second |
| `originX` | number | `0.5` | Origin X (fraction, default center-bottom) |
| `originY` | number | `1.0` | Origin Y (fraction, default bottom) |
| `animations` | string | `null` | Animation spec: `"down:0-3,up:4-7,left:8-11,right:12-15"` |
| `startAnim` | string | `null` | Initial animation name |
| `dialogueId` | string | `null` | NPC dialogue call handler ID |
| `z` | number | `0.0` | Draw order (higher = on top) |
| `controllable` | boolean | `false` | If true, this entity receives player input |

### Animation Format

The `animations` property uses a compact format:

```text
"name:startFrame-endFrame,name2:start-end,..."
```

Example: `"down:0-3,up:4-7,left:8-11,right:12-15"` defines 4 directional animations, each 4 frames.

### Examples

**Player character:**

```jes
entity "hero" {
  component Character2D {
    spriteSheet: "assets/characters/knight.png"
    frameW: 16
    frameH: 16
    cols: 4
    drawW: 32
    drawH: 32
    startTileX: 5
    startTileY: 10
    speed: 90
    controllable: true
    animations: "down:0-3,up:4-7,left:8-11,right:12-15"
    startAnim: "down"
  }
}
```

**NPC with dialogue and patrol AI:**

```jes
entity "shopkeeper" {
  component Character2D {
    spriteSheet: "assets/characters/shopkeeper.png"
    frameW: 16
    frameH: 16
    cols: 4
    drawW: 32
    drawH: 32
    startTileX: 8
    startTileY: 3
    dialogueId: "shop_talk"
    animations: "down:0-3,up:4-7,left:8-11,right:12-15"
    startAnim: "down"
  }
  component Ai2D { type: "patrol" patrolRadius: 40 patrolIntervalMs: 2000 moveSpeed: 25 }
}
```

---

## Stats

RPG stat block for HP, MP, attack, defense, speed. Attach to any entity for combat interactions.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `maxHp` | number | `0` | Maximum hit points |
| `hp` | number | `maxHp` | Current hit points |
| `maxMp` | number | `0` | Maximum magic/mana points |
| `mp` | number | `maxMp` | Current magic/mana points |
| `atk` | number | `0` | Attack power |
| `def` | number | `0` | Defense power |
| `speed` | number | `0` | Speed stat (used by AI and turn order) |
| `onDeathCall` | string | `null` | Call handler name when HP reaches 0 |
| `removeOnDeath` | boolean | `false` | Auto-remove entity on death |

### Example

```jes
entity "hero" {
  component Character2D { ... }
  component Stats {
    maxHp: 100
    hp: 100
    maxMp: 30
    mp: 30
    atk: 12
    def: 6
    speed: 8
  }
}

entity "slime" {
  component Character2D { ... }
  component Stats {
    maxHp: 20
    hp: 20
    atk: 4
    def: 2
    onDeathCall: "slimeDefeated"
    removeOnDeath: true
  }
}
```

---

## Inventory

Item storage with bounded slots. Items are referenced by ID from `item` declarations.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `slots` | int | `0` | Maximum inventory slots |
| `items` | string | `null` | Initial items as CSV: `"potion*3,herb,sword"` |

The `items` format supports `id*count` for stacking. Each item's `maxStack` (from the item declaration) bounds the count.

### Example

```jes
item "potion" { name: "Health Potion" type: "consumable" maxStack: 10 healAmount: 50 }
item "key" { name: "Dungeon Key" type: "quest" maxStack: 1 }

entity "hero" {
  component Character2D { ... }
  component Inventory {
    slots: 20
    items: "potion*3,key"
  }
}
```

---

## Equipment

Equipment slot system with free-form slot names mapped to item IDs. Unlike other components, Equipment uses **free-form properties** — any `key: value` pair is a valid slot assignment.

### Properties

All properties are free-form: `slotName: itemId`

### Example

```jes
item "iron_sword" { name: "Iron Sword" type: "equipment" atkBonus: 8 equipSlot: "weapon" }
item "leather_armor" { name: "Leather Armor" type: "equipment" defBonus: 4 equipSlot: "armor" }
item "speed_ring" { name: "Speed Ring" type: "equipment" speedBonus: 3 equipSlot: "accessory" }

entity "hero" {
  component Character2D { ... }
  component Stats { maxHp: 100 hp: 100 atk: 10 def: 5 }
  component Equipment {
    weapon: "iron_sword"
    armor: "leather_armor"
    accessory: "speed_ring"
  }
}
```

---

## Ai2D

AI behavior controller. Attach to an entity with `Character2D` for automated NPC behavior.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `type` | string | `"chase"` | Behavior type: `chase`, `patrol`, `guard`, `flee` |
| `target` | string | `null` | Entity name to target (usually the player) |
| `aggroRange` | number | `0` | Distance at which AI activates |
| `attackRange` | number | `0` | Distance at which AI attacks |
| `attackIntervalMs` | number | `1000` | Milliseconds between attacks |
| `attackAmount` | number | `0` | Damage per attack |
| `moveSpeed` | number | `0` | Movement speed |
| `attackCooldownMs` | number | — | Alias for `attackIntervalMs` |
| `patrolRadius` | number | `0` | Radius of patrol area (patrol type) |
| `patrolIntervalMs` | number | `1500` | Pause between patrol waypoints |
| `requiresLineOfSight` | boolean | `false` | Only act when target is visible |
| `guardRadius` | number | `0` | Distance before returning to guard post |
| `fleeDistance` | number | `0` | Distance to maintain when fleeing |

### AI Types

- **`chase`** — moves toward target when in aggro range, attacks when in attack range
- **`patrol`** — wanders within `patrolRadius`, pauses at waypoints
- **`guard`** — stays near a point, chases intruders but returns within `guardRadius`
- **`flee`** — runs away from target, maintaining `fleeDistance`

### Examples

**Aggressive enemy:**

```jes
entity "goblin" {
  component Character2D { ... }
  component Stats { maxHp: 30 hp: 30 atk: 6 onDeathCall: "goblinDead" removeOnDeath: true }
  component Ai2D {
    type: "chase"
    target: "hero"
    aggroRange: 120
    attackRange: 24
    attackIntervalMs: 800
    attackAmount: 6
    moveSpeed: 50
    requiresLineOfSight: true
  }
}
```

**Patrolling guard:**

```jes
entity "guard_npc" {
  component Character2D { ... }
  component Ai2D {
    type: "guard"
    target: "hero"
    guardRadius: 80
    aggroRange: 60
    attackRange: 20
    attackAmount: 8
    moveSpeed: 40
  }
}
```

**Fleeing creature:**

```jes
entity "rabbit" {
  component Character2D { ... }
  component Ai2D {
    type: "flee"
    target: "hero"
    aggroRange: 80
    fleeDistance: 120
    moveSpeed: 70
  }
}
```

---

## Button2D

A clickable UI button with normal/hover/pressed color states and a call handler.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `x` | number | `0` | X position |
| `y` | number | `0` | Y position |
| `w` | number | `100` | Width |
| `h` | number | `32` | Height |
| `text` | string | `""` | Button label text |
| `call` | string | `null` | Call handler name on click |
| `normal` | `rgb(r,g,b,a)` | — | Background color (normal state) |
| `hover` | `rgb(r,g,b,a)` | — | Background color (hover state) |
| `pressed` | `rgb(r,g,b,a)` | — | Background color (pressed state) |
| `textColor` | `rgb(r,g,b,a)` | — | Text color |
| `fontSize` | number | `14` | Font size |

### Example

```jes
entity "start_btn" {
  component Button2D {
    x: 300
    y: 400
    w: 200
    h: 50
    text: "Start Game"
    call: "startGame"
    normal: rgb(0.2, 0.4, 0.8, 1)
    hover: rgb(0.3, 0.5, 0.9, 1)
    pressed: rgb(0.1, 0.3, 0.7, 1)
    textColor: rgb(1, 1, 1, 1)
    fontSize: 20
  }
}

entity "quit_btn" {
  component Button2D {
    x: 300
    y: 470
    w: 200
    h: 40
    text: "Quit"
    call: "quitGame"
    normal: rgb(0.6, 0.1, 0.1, 1)
    hover: rgb(0.8, 0.2, 0.2, 1)
    pressed: rgb(0.5, 0.0, 0.0, 1)
    textColor: rgb(1, 1, 1, 1)
    fontSize: 16
  }
}
```

---

## Slider2D

A draggable slider for volume controls, difficulty settings, or any continuous value.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `x` | number | `0` | X position |
| `y` | number | `0` | Y position |
| `w` | number | `120` | Width |
| `h` | number | `20` | Height |
| `min` | number | `0` | Minimum value |
| `max` | number | `1` | Maximum value |
| `value` | number | `0` | Initial value |
| `call` | string | `null` | Call handler name on value change |
| `trackColor` | `rgb(r,g,b,a)` | — | Track background color |
| `fillColor` | `rgb(r,g,b,a)` | — | Filled portion color |
| `knobColor` | `rgb(r,g,b,a)` | — | Knob/handle color |

### Example

```jes
entity "volume_slider" {
  component Slider2D {
    x: 300
    y: 350
    w: 200
    h: 24
    min: 0
    max: 1
    value: 0.7
    call: "setVolume"
    trackColor: rgb(0.2, 0.2, 0.2, 1)
    fillColor: rgb(0.3, 0.6, 1, 1)
    knobColor: rgb(1, 1, 1, 1)
  }
}

entity "difficulty_slider" {
  component Slider2D {
    x: 300
    y: 400
    w: 200
    h: 24
    min: 1
    max: 10
    value: 5
    call: "setDifficulty"
    trackColor: rgb(0.15, 0.15, 0.15, 1)
    fillColor: rgb(1, 0.5, 0, 1)
    knobColor: rgb(1, 0.8, 0.2, 1)
  }
}
```

---

## Component Combination Patterns

### RPG Character (all systems)

```jes
entity "hero" {
  component Character2D {
    spriteSheet: "assets/characters/knight.png"
    frameW: 16
    frameH: 16
    cols: 4
    drawW: 32
    drawH: 32
    startTileX: 5
    startTileY: 10
    speed: 90
    controllable: true
    animations: "down:0-3,up:4-7,left:8-11,right:12-15"
    startAnim: "down"
  }
  component Stats { maxHp: 120 hp: 120 maxMp: 40 mp: 40 atk: 14 def: 8 speed: 10 }
  component Inventory { slots: 24 items: "potion*5,antidote*2" }
  component Equipment { weapon: "iron_sword" armor: "chain_mail" }
}
```

### HUD Overlay

```jes
entity "hud_bg" {
  component Panel2D { x: 0 y: 0 w: 800 h: 40 fill: rgb(0, 0, 0, 0.6) }
}
entity "hp_text" {
  component Label2D { text: "HP: 100/100" x: 10 y: 10 size: 18 bold: true color: rgb(0.2, 1, 0.3, 1) }
}
entity "score_text" {
  component Label2D { text: "Score: 0" x: 700 y: 10 size: 18 color: rgb(1, 0.85, 0, 1) align: right }
}
```

### Physics Playground

```jes
entity "floor" {
  component PhysicsBody2D { shape: box x: 0 y: 560 w: 800 h: 40 static: true color: rgb(0.4, 0.4, 0.4, 1) }
}
entity "left_wall" {
  component PhysicsBody2D { shape: box x: -20 y: 0 w: 20 h: 600 static: true }
}
entity "ball_1" {
  component PhysicsBody2D { shape: circle x: 200 y: 50 r: 20 mass: 1.5 restitution: 0.9 color: rgb(1, 0.3, 0.3, 1) }
}
entity "ball_2" {
  component PhysicsBody2D { shape: circle x: 350 y: 100 r: 12 mass: 0.8 restitution: 0.7 vx: -50 color: rgb(0.3, 0.6, 1, 1) }
}
```

---

## Quick Reference Table

| Component | Purpose | Key Properties |
|-----------|---------|---------------|
| `Panel2D` | Colored rectangle | `x`, `y`, `w`, `h`, `fill` |
| `Sprite2D` | Image/sprite | `image`, `x`, `y`, `w`, `h`, `alpha` |
| `Label2D` | Text display | `text`, `x`, `y`, `size`, `bold`, `color`, `align` |
| `ParticleEmitter2D` | Particle effects | `emissionRate`, `minLife`/`maxLife`, `minSize`/`maxSize` |
| `PhysicsBody2D` | Rigid body | `shape`, `mass`, `restitution`, `static`, `sensor` |
| `Character2D` | Animated character | `spriteSheet`, `animations`, `speed`, `controllable` |
| `Stats` | RPG stats | `maxHp`, `hp`, `atk`, `def`, `onDeathCall` |
| `Inventory` | Item storage | `slots`, `items` |
| `Equipment` | Gear slots | Free-form `slot: itemId` |
| `Ai2D` | AI behavior | `type`, `target`, `aggroRange`, `moveSpeed` |
| `Button2D` | Clickable button | `text`, `call`, `normal`/`hover`/`pressed` |
| `Slider2D` | Value slider | `min`, `max`, `value`, `call` |

---

## Notes for Script Authors

- **Unknown property on a known component type is a parse error.** This catches typos at parse time.
- Unknown component **types** are tolerated for extension flexibility.
- `Equipment` and timeline `call` use free-form properties (any key is allowed).
- Prefer explicit numeric values for dimensions/positions.
- Keep entity names stable if timelines or call handlers reference them.
- Colors use `rgb(r, g, b, a)` format with values from 0.0 to 1.0 (not 0–255).

---

## Related Docs

- [JES Overview](../overview/jes-scripting.md)
- [Timeline & Actions](../timeline/jes-timeline.md)
- [Physics & Collision](../systems/jes-physics.md)
- [AI System](../gameplay/jes-ai.md)
- [RPG Stats & Combat](../gameplay/jes-rpg.md)
- [UI Widgets](../gameplay/jes-ui-widgets.md)
- [Input Bindings](../systems/jes-input.md)

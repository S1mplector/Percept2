# JES Scripting

JES (JVN Engine Script) is a DSL for authoring 2D scenes with entities, components, tile maps, physics, AI, input bindings, timelines, RPG systems, and UI widgets. It integrates with VNS for hybrid visual-novel + gameplay projects.

Core files:
- `scripting/src/main/java/com/jvn/scripting/jes/JesTokenizer.java`
- `scripting/src/main/java/com/jvn/scripting/jes/JesParser.java`
- `scripting/src/main/java/com/jvn/scripting/jes/JesLoader.java`
- `scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`

---

## Quick Start

```jes
scene "Demo" {
  entity "background" {
    component Panel2D {
      x: 0
      y: 0
      w: 800
      h: 600
      fill: rgb(0.1, 0.12, 0.18, 1)
    }
  }

  entity "hero" {
    component Sprite2D {
      image: "assets/characters/hero.png"
      x: 200
      y: 300
      w: 64
      h: 64
    }
  }

  entity "title" {
    component Label2D {
      text: "Hello JES"
      x: 300
      y: 80
      size: 32
      bold: true
      color: rgb(1, 0.85, 0, 1)
    }
  }

  on key "D" do toggleDebug

  timeline {
    move "hero" { x: 400 y: 300 dur: 800 easing: ease_out_back }
    wait 500
    fade "title" { alpha: 0.0 dur: 600 easing: ease_in_quad }
  }
}
```

Run directly:

```bash
./gradlew :runtime:run --args='--jes game/scenes/demo.jes'
```

---

## Language Basics

### Scene Block

```jes
scene "SceneName" {
  // tilesets, items, maps, entities, bindings, timeline, props
}
```

### Entity Block

```jes
entity "entityName" {
  component ComponentType { property: value }
}
```

### Value Types

| Type | Examples |
|------|---------|
| Number | `1`, `-2`, `0.5` |
| String | `"text"` |
| Boolean | `true`, `false` |
| Color | `rgb(1, 0.5, 0)`, `rgba(1, 0.5, 0, 0.8)` |
| Bare ident | `circle`, `left` (treated as strings) |

---

## Sub-Document Reference

Each JES feature area has its own detailed documentation with examples:

### Core

- **[Scenes & Entities](jes-scenes-entities.md)** — scene structure, entity declarations, lifecycle, merging, save/load
- **[Component Reference](components.md)** — all 12 component types with full property tables
- **[Timeline & Actions](jes-timeline.md)** — 22 timeline actions: move, rotate, scale, fade, camera, audio, combat, parallel, loop, labels, jumps

### Systems

- **[Input Bindings](jes-input.md)** — keyboard mappings, built-in actions, continuous movement, custom handlers
- **[Camera System](jes-camera.md)** — position, zoom, shake, follow with dead zones, parallax scrolling
- **[Physics & Collision](jes-physics.md)** — rigid bodies, circles, boxes, sensors, triggers, restitution, raycasting
- **[Tilemaps & Maps](jes-tilemaps.md)** — tilesets, tile layers, collision layers, trigger layers, pathfinding
- **[AI System](jes-ai.md)** — chase, patrol, guard, flee, line-of-sight, A* pathfinding
- **[RPG Stats & Combat](jes-rpg.md)** — Stats, Inventory, Equipment, Items, damage/heal, death callbacks
- **[UI Widgets](jes-ui-widgets.md)** — Button2D, Slider2D, HUD patterns

### Integration

- **[VN Bridge & Java Hooks](jes-bridge.md)** — call handlers, VNS↔JES scene stack, return data, launch properties
- **[Parsing Internals](jes-parsing.md)** — tokenizer, parser, AST, validation

---

## Timeline Action Quick Reference

| Category | Actions |
|----------|---------|
| **Movement** | `move`, `walkToTile`, `pivot` |
| **Transform** | `rotate`, `scale`, `fade`, `visible` |
| **Camera** | `cameraMove`, `cameraZoom`, `cameraShake`, `cameraFollow` |
| **Audio** | `playAudio`, `stopAudio` |
| **Combat** | `damage`, `heal` |
| **Particles** | `emitParticles` |
| **Appearance** | `setParallax` |
| **Timing** | `wait`, `waitForCall` |
| **Flow** | `label`, `jump`, `call` |
| **Composite** | `parallel`, `loop` |

---

## Component Quick Reference

| Component | Purpose |
|-----------|---------|
| `Panel2D` | Colored rectangle |
| `Sprite2D` | Image/sprite with region support |
| `Label2D` | Text display |
| `ParticleEmitter2D` | Particle effects |
| `PhysicsBody2D` | Physics rigid body (circle/box) |
| `Character2D` | Animated sprite-sheet character |
| `Stats` | RPG stats (HP, MP, ATK, DEF) |
| `Inventory` | Item storage with slots |
| `Equipment` | Equipment slot management |
| `Ai2D` | AI behavior (chase, patrol, guard) |
| `Button2D` | Clickable button |
| `Slider2D` | Draggable slider |

---

## Easing Types

| Category | Easings |
|----------|---------|
| **Linear** | `linear` |
| **Quadratic** | `ease_in_quad`, `ease_out_quad`, `ease_in_out_quad` |
| **Cubic** | `ease_in_cubic`, `ease_out_cubic`, `ease_in_out_cubic` |
| **Quartic** | `ease_in_quart`, `ease_out_quart`, `ease_in_out_quart` |
| **Exponential** | `ease_in_expo`, `ease_out_expo`, `ease_in_out_expo` |
| **Sine** | `ease_in_sine`, `ease_out_sine`, `ease_in_out_sine` |
| **Elastic** | `ease_in_elastic`, `ease_out_elastic`, `ease_in_out_elastic` |
| **Back** | `ease_in_back`, `ease_out_back`, `ease_in_out_back` |
| **Bounce** | `ease_in_bounce`, `ease_out_bounce`, `ease_in_out_bounce` |

---

## Full Example

```jes
scene "RPGDemo" {
  item "potion" { name: "Health Potion" type: "consumable" maxStack: 10 healAmount: 50 }
  item "sword" { name: "Iron Sword" type: "equipment" atkBonus: 8 equipSlot: "weapon" }

  tileset "overworld" { image: "assets/tilesets/overworld.png" tileW: 16 tileH: 16 cols: 16 }

  map "town" {
    tileset: "overworld"
    width: 20
    height: 15
    tileW: 32
    tileH: 32
    layer "ground" { data: "maps/town_ground.csv" }
    layer "walls" { data: "maps/town_walls.csv" collision: true }
  }

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
    component Stats { maxHp: 100 hp: 100 atk: 12 def: 6 }
    component Inventory { slots: 20 items: "potion*3" }
    component Equipment { weapon: "sword" }
  }

  entity "npc" {
    component Character2D {
      spriteSheet: "assets/characters/villager.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 32
      drawH: 32
      startTileX: 10
      startTileY: 5
      dialogueId: "elder_chat"
    }
    component Ai2D { type: "patrol" patrolRadius: 60 patrolIntervalMs: 3000 moveSpeed: 30 }
  }

  on key "SPACE" do interact
  on key "D" do toggleDebug

  timeline {
    cameraFollow "hero" { lerp: 0.15 offsetY: -10 }
    playAudio "assets/audio/bgm/town.ogg" { volume: 0.6 loop: true bgm: true }
  }
}
```

---

## Related Docs

- [Documentation Index](../../INDEX.md)
- [VNS Scripting](../vns/vns-scripting.md)
- [Timeline Scripting](../timeline/timeline-scripting.md)
- [Runtime Guide](../../runtime/runtime.md)
- [Runtime Interop](../../runtime/interop.md)

# JES Scenes & Entities

Complete reference for JES scene structure, entity declarations, and the scene lifecycle.

Parser: `scripting/src/main/java/com/jvn/scripting/jes/JesParser.java`
Loader: `scripting/src/main/java/com/jvn/scripting/jes/JesLoader.java`
Runtime: `scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`

---

## Scene Block

Every JES file contains one or more scene declarations. The first scene is loaded by default.

```jes
scene "SceneName" {
  // scene contents
}
```

A scene can contain:
- **Tileset declarations** — sprite sheet definitions for tile maps
- **Item declarations** — item database entries
- **Map declarations** — tile map layers with collision and triggers
- **Entity declarations** — visual and logical objects
- **Input bindings** — keyboard/input mappings
- **Timeline blocks** — scripted animation sequences
- **Scene-level properties** — key-value metadata

### Minimal Scene

```jes
scene "Hello" {
  entity "title" {
    component Label2D {
      text: "Hello JES"
      x: 100
      y: 80
      size: 24
      color: rgb(1, 1, 1, 1)
    }
  }
}
```

### Scene with Everything

```jes
scene "FullDemo" {
  // Item database
  item "health_potion" {
    name: "Health Potion"
    type: "consumable"
    maxStack: 10
    healAmount: 50
  }

  // Tileset for maps
  tileset "dungeon" {
    image: "assets/tilesets/dungeon.png"
    tileW: 16
    tileH: 16
    cols: 8
  }

  // Tile map
  map "level1" {
    tileset: "dungeon"
    width: 20
    height: 15
    tileW: 32
    tileH: 32

    layer "ground" {
      data: "maps/level1_ground.csv"
    }
    layer "walls" {
      data: "maps/level1_walls.csv"
      collision: true
    }
    layer "triggers" {
      data: "maps/level1_triggers.csv"
      triggerCall: "warpMap"
      toTileX: 5
      toTileY: 3
    }
  }

  // Entities
  entity "hero" {
    component Character2D {
      spriteSheet: "assets/characters/hero.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 32
      drawH: 32
      startTileX: 3
      startTileY: 5
      speed: 100
      controllable: true
      animations: "idle_down:0-0,walk_down:0-3,walk_up:4-7,walk_left:8-11,walk_right:12-15"
      startAnim: "idle_down"
    }
    component Stats {
      maxHp: 100
      hp: 100
      atk: 15
      def: 8
      speed: 100
    }
    component Inventory {
      slots: 20
      items: "health_potion*3"
    }
  }

  entity "hud" {
    component Label2D {
      text: "HP: 100"
      x: 10
      y: 10
      size: 14
      bold: true
      color: rgb(1, 1, 1, 1)
    }
  }

  // Input bindings
  on key "D" do toggleDebug
  on key "I" do openInventory
  on key "SPACE" do interact

  // Timeline
  timeline {
    move "hud" { x: 10 y: 6 dur: 300 easing: ease_out_back }
    wait 200
    move "hud" { x: 10 y: 10 dur: 250 easing: ease_in_out_sine }
  }
}
```

---

## Entity Declarations

Entities are named objects composed of one or more components.

```jes
entity "entityName" {
  component ComponentType {
    property: value
    // ...
  }
  component AnotherComponent {
    // ...
  }
}
```

### Entity Naming

- Entity names must be **unique** within a scene.
- Names are used by timeline actions, input handlers, call handlers, and the VN bridge.
- Keep names stable if external code references them.

### Multi-Component Entities

An entity can have multiple components of different types:

```jes
entity "warrior" {
  component Character2D {
    spriteSheet: "assets/characters/warrior.png"
    frameW: 16
    frameH: 16
    cols: 4
    drawW: 32
    drawH: 32
    x: 100
    y: 200
    speed: 80
    controllable: true
  }
  component Stats {
    maxHp: 120
    hp: 120
    atk: 20
    def: 12
  }
  component Inventory {
    slots: 10
    items: "sword_iron*1,shield_wood*1"
  }
  component Equipment {
    weapon: "sword_iron"
    shield: "shield_wood"
  }
  component Ai2D {
    type: "patrol"
    patrolRadius: 100
    patrolIntervalMs: 2000
    moveSpeed: 60
  }
}
```

---

## Scene Lifecycle

### Loading

1. `JesTokenizer` tokenizes the source into a `JesToken` stream.
2. `JesParser` builds an AST (`JesAst.Program` with `SceneDecl` nodes).
3. `JesLoader.buildScene()` creates a `JesScene2D` from the AST:
   - Builds the item database
   - Creates tileset lookup and tile maps
   - Instantiates entities with components
   - Registers input bindings
   - Sets the timeline

### Update Loop

Each frame, `JesScene2D.update(deltaMs)` runs:

1. `super.update()` — base scene update (particle emitters, etc.)
2. `world.step()` — physics simulation
3. **Input processing** — checks bindings for key presses
4. **Continuous movement** — handles WASD/arrow character movement
5. **Button/slider interaction** — handles mouse clicks on UI widgets
6. **AI update** — runs AI behavior for all AI-enabled entities
7. **Timeline update** — advances scripted animation actions
8. **Async actions** — updates background/parallel actions
9. **Camera follow** — smoothly tracks the follow target

### Render

`render(Blitter2D, width, height)` draws all entities in scene order (tilemaps first, entities on top, HUD last).

---

## Scene Merging

Multiple JES files can be merged into a single scene:

```java
JesScene2D scene = JesLoader.loadMerged(List.of(
    stream1,  // base scene
    stream2   // overlay: entities, bindings, timeline appended
));
```

Merge behavior:
- Items, tilesets, maps, entities, bindings, and timeline actions are **appended**.
- The base scene's name is kept.
- Useful for layering shared content (common HUD, shared items) with level-specific content.

---

## Entity Management at Runtime

### Finding Entities

```java
Entity2D hero = scene.find("hero");
Set<String> allNames = scene.names();
Map<String, Entity2D> snapshot = scene.exportNamed();
```

### Removing Entities

```java
scene.removeEntity("enemy_1");
```

Also removes associated physics bodies and cleans up references.

### Renaming Entities

```java
scene.rename("old_name", "new_name");
```

### Spawn Positions

Every entity's initial position is recorded as its spawn position. The `resetBalls` / `resetToSpawn` call restores all entities to their spawn positions.

---

## Scene State Save/Load

`JesScene2D` supports state snapshots for save/load:

```java
// Save
JesSceneState state = scene.saveState();

// Load
scene.loadState(state);
```

State captures:
- Entity positions
- Player name and facing direction
- Stats (HP, MP, ATK, DEF, bonuses)
- Inventories (item counts, slot limits)
- Equipment (slot assignments)

---

## Running a JES Scene

### From CLI

```bash
./gradlew :runtime:run --args='--jes game/scenes/demo.jes'
```

### From VNS

```vns
[jes push game/scenes/demo.jes label after_game]
```

### From Java

```java
InputStream in = getClass().getClassLoader().getResourceAsStream("game/scenes/demo.jes");
JesScene2D scene = JesLoader.load(in);
```

---

## Related Docs

- [JES Overview](jes-scripting.md)
- [Component Reference](components.md)
- [Timeline & Actions](jes-timeline.md)
- [Input Bindings](jes-input.md)
- [Tilemaps & Maps](jes-tilemaps.md)

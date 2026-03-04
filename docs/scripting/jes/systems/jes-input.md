# JES Input Bindings

Complete reference for keyboard input bindings, continuous movement, and interaction systems in JES.

Runtime: `scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`

---

## Input Binding Syntax

Input bindings map keyboard keys to named actions.

```jes
on key "<KeyName>" do <actionName>
on key "<KeyName>" do <actionName> { prop1: value1 prop2: value2 }
```

**Examples:**

```jes
on key "D" do toggleDebug
on key "SPACE" do interact
on key "I" do openInventory
on key "R" do resetBalls
on key "F" do strike { power: 0.8 range: 50 }
```

---

## Key Names

Key names are string identifiers matching the backend's key mapping. Common names:

| Key | Name |
|-----|------|
| Letters | `"A"` through `"Z"` |
| Numbers | `"0"` through `"9"` |
| Arrow keys | `"UP"`, `"DOWN"`, `"LEFT"`, `"RIGHT"` |
| Space | `"SPACE"` |
| Enter | `"ENTER"` |
| Escape | `"ESCAPE"` |
| Tab | `"TAB"` |
| Shift | `"SHIFT"` |
| Control | `"CONTROL"` |
| Backspace | `"BACK_SPACE"` |
| Delete | `"DELETE"` |
| Function keys | `"F1"` through `"F12"` |

---

## Built-in Actions

These actions are handled directly by `JesScene2D`:

### `toggleDebug`

Toggles the physics debug overlay (shows collision shapes, bodies, grid).

```jes
on key "D" do toggleDebug
```

### `interact`

Triggers NPC interaction. Checks the tile in front of the player character (based on facing direction) for a `CharacterEntity2D` with a `dialogueId`. Invokes the `interactNpc` call with:
- `npc` — entity name
- `dialogueId` — the NPC's dialogue ID (if set)
- `facing` — player's current facing direction
- `heroX`, `heroY` — player position

```jes
on key "SPACE" do interact
on key "E" do interact
```

### `spawnCircle`

Spawns a physics circle at the mouse position or specified coordinates.

```jes
on key "C" do spawnCircle { r: 15 mass: 1.5 restitution: 0.6 }
```

Properties: `x`, `y`, `r`, `mass`, `restitution`

### `spawnBox`

Spawns a physics box at the mouse position or specified coordinates.

```jes
on key "B" do spawnBox { w: 30 h: 30 mass: 2 restitution: 0.3 }
```

Properties: `x`, `y`, `w`, `h`, `mass`, `restitution`

### `moveHero`

Moves the player character one tile in a direction.

```jes
on key "UP" do moveHero { dir: "up" }
on key "DOWN" do moveHero { dir: "down" }
on key "LEFT" do moveHero { dir: "left" }
on key "RIGHT" do moveHero { dir: "right" }
```

This is tile-based discrete movement (one grid cell per key press). For smooth continuous movement, use the `controllable` property on `Character2D` instead.

### `resetBalls` / `resetToSpawn`

Resets all entities to their initial spawn positions and zeros velocities. Also resets the `score` variable.

```jes
on key "R" do resetBalls
```

---

## Custom Action Handlers

Actions not handled by built-in handlers are forwarded to the scene's registered action handler.

### Registering from Java

```java
scene.setActionHandler((action, props) -> {
    switch (action) {
        case "openInventory" -> showInventoryUI(props);
        case "strike" -> performStrike(props);
        default -> System.out.println("Unknown action: " + action);
    }
});
```

### Registering from Call Handlers

```java
scene.registerCall("openInventory", props -> {
    // Custom inventory UI logic
});
```

---

## Continuous Character Movement

When a `Character2D` component has `controllable: true`, the engine automatically handles WASD/Arrow key movement every frame (no input binding required).

```jes
entity "hero" {
  component Character2D {
    spriteSheet: "assets/characters/hero.png"
    frameW: 16
    frameH: 16
    cols: 4
    drawW: 32
    drawH: 32
    speed: 100
    controllable: true
    animations: "down:0-3,up:4-7,left:8-11,right:12-15"
    startAnim: "down"
  }
}
```

### Movement Keys (always active for controllable characters)

| Key | Direction |
|-----|-----------|
| `W` / `UP` | Up |
| `S` / `DOWN` | Down |
| `A` / `LEFT` | Left |
| `D` / `RIGHT` | Right |

### Movement Physics

Continuous movement uses acceleration/drag smoothing:
- **Acceleration** — ramps up to max speed
- **Drag** — slows down when no input
- **Max speed** — capped by `Character2D.speed` or `Stats.speed`
- **Collision** — axis-slide against blocked tiles (player slides along walls instead of stopping)
- **Facing** — automatically updates `playerFacing` and plays directional animation

### Collision Detection

Movement is blocked by tiles marked with `collision: true` in map layers. The engine checks the target position against all collision tilemaps and prevents movement into solid tiles.

---

## Interaction Flow Example

Complete example of a scene with NPCs and interaction:

```jes
scene "Village" {
  tileset "overworld" {
    image: "assets/tilesets/overworld.png"
    tileW: 16
    tileH: 16
    cols: 16
  }

  map "village" {
    tileset: "overworld"
    width: 20
    height: 15
    tileW: 32
    tileH: 32

    layer "ground" {
      data: "maps/village_ground.csv"
    }
    layer "walls" {
      data: "maps/village_walls.csv"
      collision: true
    }
  }

  entity "hero" {
    component Character2D {
      spriteSheet: "assets/characters/hero.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 32
      drawH: 32
      startTileX: 5
      startTileY: 8
      speed: 90
      controllable: true
      animations: "down:0-3,up:4-7,left:8-11,right:12-15"
      startAnim: "down"
    }
  }

  entity "shopkeeper" {
    component Character2D {
      spriteSheet: "assets/characters/shopkeeper.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 32
      drawH: 32
      startTileX: 10
      startTileY: 5
      speed: 0
      dialogueId: "shop_dialogue"
      animations: "down:0-0"
      startAnim: "down"
    }
  }

  on key "SPACE" do interact
  on key "D" do toggleDebug

  // Camera follows the hero
  timeline {
    cameraFollow "hero" { lerp: 0.15 offsetY: -20 }
  }
}
```

When the player presses SPACE facing the shopkeeper, the `interactNpc` call is invoked with `{ npc: "shopkeeper", dialogueId: "shop_dialogue", ... }`. A Java hook can then open a shop UI or trigger a VNS dialogue.

---

## Related Docs

- [JES Overview](jes-scripting.md)
- [Scenes & Entities](jes-scenes-entities.md)
- [Component Reference](components.md) — `Character2D`, `controllable`
- [Tilemaps & Maps](jes-tilemaps.md) — collision layers
- [VN Bridge & Java Hooks](jes-bridge.md)

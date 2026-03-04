# JES Tilemaps & Maps

Complete reference for tilesets, tile maps, collision layers, trigger layers, and grid-based world building in JES.

Loader: `scripting/src/main/java/com/jvn/scripting/jes/JesLoader.java`
Runtime: `scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`
Tilemap: `core/src/main/java/com/jvn/core/scene2d/TileMap2D.java`

---

## Overview

JES supports tile-based worlds through three cooperating declarations:

1. **Tileset** — defines the sprite sheet used for tiles
2. **Map** — defines the grid dimensions and tile draw size
3. **Layer** — individual tile data layers within a map (ground, walls, triggers)

---

## Tileset Declaration

```jes
tileset "tilesetName" {
  image: "path/to/tileset.png"
  tileW: 16
  tileH: 16
  cols: 8
}
```

| Property | Description |
|----------|-------------|
| `image` | Path to the tileset sprite sheet image |
| `tileW` | Width of each tile in the source image (pixels) |
| `tileH` | Height of each tile in the source image (pixels) |
| `cols` | Number of tile columns in the sprite sheet |

Tile IDs are assigned left-to-right, top-to-bottom starting at 0. A tile ID of `-1` means empty (no tile drawn).

**Example tileset image layout (cols=8):**

```text
 0  1  2  3  4  5  6  7
 8  9 10 11 12 13 14 15
16 17 18 19 20 21 22 23
...
```

---

## Map Declaration

```jes
map "mapName" {
  tileset: "tilesetName"
  width: 20
  height: 15
  tileW: 32
  tileH: 32

  layer "layerName" {
    data: "path/to/layer.csv"
    collision: false
    triggerCall: "handlerName"
  }
}
```

### Map Properties

| Property | Default | Description |
|----------|---------|-------------|
| `tileset` | — | Name of the tileset to use (must be declared) |
| `width` | 10 | Number of tile columns in the map |
| `height` | 10 | Number of tile rows in the map |
| `tileW` | tileset `tileW` | Draw width of each tile on screen (pixels) |
| `tileH` | tileset `tileH` | Draw height of each tile on screen (pixels) |

Note: `tileW`/`tileH` on the map can differ from the tileset's source dimensions, allowing scaled rendering.

### Layer Properties

| Property | Default | Description |
|----------|---------|-------------|
| `data` | — | Path to CSV file containing tile IDs |
| `collision` | false | If true, non-empty tiles block movement |
| `triggerCall` | — | Call handler invoked when player steps on non-empty tiles |
| `call` | — | Alias for `triggerCall` |

Additional properties on a trigger layer are passed to the call handler as props (e.g., `toTileX`, `toTileY` for warp destinations).

---

## Layer Data Format (CSV)

Layer data files are simple CSV grids of tile IDs:

```csv
0,0,0,1,1,1,0,0,0,0
0,2,2,1,1,1,2,2,0,0
0,2,3,3,3,3,3,2,0,0
0,2,3,3,3,3,3,2,0,0
0,2,2,2,2,2,2,2,0,0
0,0,0,0,0,0,0,0,0,0
```

- Each number is a tile ID from the tileset
- `-1` = empty tile (nothing drawn, not collidable)
- Rows correspond to Y coordinates (top-to-bottom)
- Columns correspond to X coordinates (left-to-right)
- Grid size must match the map's `width` × `height`

---

## Collision Layers

Mark a layer with `collision: true` to make its non-empty tiles block movement:

```jes
layer "walls" {
  data: "maps/dungeon_walls.csv"
  collision: true
}
```

Collision behavior:
- `Character2D` with `controllable: true` checks collision before moving
- AI pathfinding respects collision tiles
- `moveHero` action checks collision before stepping
- Wall-sliding: when moving diagonally into a wall, the engine tries each axis independently

### Collision detection helpers

```java
// Check if a world position is blocked
boolean blocked = scene.isWorldBlocked(worldX, worldY);

// Check if a tile coordinate is blocked
boolean blocked = scene.isTileBlocked(tileX, tileY);
```

---

## Trigger Layers

Trigger layers invoke a call handler when the player steps on a non-empty tile:

```jes
layer "warps" {
  data: "maps/dungeon_warps.csv"
  triggerCall: "warpMap"
  toTileX: 5
  toTileY: 3
}
```

When the player moves onto a non-empty tile in this layer, the handler receives:

| Prop | Description |
|------|-------------|
| `tileX` | Tile X coordinate the player is on |
| `tileY` | Tile Y coordinate the player is on |
| `tile` | Tile ID at that position |
| `map` | Map name |
| Any extra layer props | e.g., `toTileX`, `toTileY` |

### Built-in: `warpMap`

The built-in `warpMap` handler teleports the player to a target position:

```jes
layer "warps" {
  data: "maps/warps.csv"
  triggerCall: "warpMap"
  toTileX: 10
  toTileY: 5
}
```

`warpMap` supports these target formats:
- `toTileX` / `toTileY` — target tile coordinates (converted to world position)
- `toX` / `toY` — direct world position coordinates
- Falls back to `tileX` / `tileY` from the trigger event

### Custom Trigger Handlers

```java
scene.registerCall("treasureFound", props -> {
    int tile = ((Number) props.get("tile")).intValue();
    int tx = ((Number) props.get("tileX")).intValue();
    int ty = ((Number) props.get("tileY")).intValue();
    System.out.println("Found treasure at tile " + tx + "," + ty + " (ID: " + tile + ")");
    // Award items, update quest state, etc.
});
```

---

## Grid Size

The first valid map/tileset combination sets the scene's grid size, which is used for:

- Character tile-based movement (`moveHero`)
- `Character2D` `startTileX`/`startTileY` positioning
- AI pathfinding
- Trigger checking
- Collision detection

```java
scene.setGridSize(tileDrawWidth, tileDrawHeight);
```

---

## Pathfinding

`JesScene2D` includes built-in A* pathfinding for tile-based movement:

```java
List<int[]> path = scene.findPathTiles(startX, startY, targetX, targetY, maxNodes);
```

- Returns a list of `[tileX, tileY]` waypoints
- Respects collision tiles
- Used internally by AI chase behavior
- `maxNodes` limits search depth (default 512 for AI)

---

## Full Example: Dungeon Map

### JES Scene

```jes
scene "Dungeon" {
  tileset "dungeon_tiles" {
    image: "assets/tilesets/dungeon.png"
    tileW: 16
    tileH: 16
    cols: 16
  }

  map "floor1" {
    tileset: "dungeon_tiles"
    width: 20
    height: 15
    tileW: 32
    tileH: 32

    layer "ground" {
      data: "maps/dungeon/floor1_ground.csv"
    }

    layer "walls" {
      data: "maps/dungeon/floor1_walls.csv"
      collision: true
    }

    layer "decorations" {
      data: "maps/dungeon/floor1_decor.csv"
    }

    layer "warps" {
      data: "maps/dungeon/floor1_warps.csv"
      triggerCall: "warpMap"
      toTileX: 3
      toTileY: 12
    }

    layer "events" {
      data: "maps/dungeon/floor1_events.csv"
      triggerCall: "dungeonEvent"
    }
  }

  entity "hero" {
    component Character2D {
      spriteSheet: "assets/characters/knight.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 32
      drawH: 32
      startTileX: 3
      startTileY: 12
      speed: 90
      controllable: true
      animations: "down:0-3,up:4-7,left:8-11,right:12-15"
      startAnim: "down"
    }
    component Stats {
      maxHp: 100
      hp: 100
      atk: 15
      def: 8
    }
  }

  entity "skeleton" {
    component Character2D {
      spriteSheet: "assets/characters/skeleton.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 32
      drawH: 32
      startTileX: 15
      startTileY: 5
      speed: 60
      animations: "down:0-3,up:4-7,left:8-11,right:12-15"
      startAnim: "down"
    }
    component Stats {
      maxHp: 40
      hp: 40
      atk: 8
      def: 3
      onDeathCall: "enemyDied"
      removeOnDeath: true
    }
    component Ai2D {
      type: "chase_and_attack"
      aggroRange: 160
      attackRange: 35
      attackIntervalMs: 1200
      moveSpeed: 50
      requiresLineOfSight: true
    }
  }

  on key "SPACE" do interact
  on key "D" do toggleDebug

  timeline {
    cameraFollow "hero" { lerp: 0.15 offsetY: -10 }
  }
}
```

### Layer CSV: `floor1_ground.csv` (excerpt)

```csv
0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
0,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,0
0,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,0
0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0
0,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,0
0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
```

### Layer CSV: `floor1_walls.csv` (excerpt)

```csv
2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2
2,-1,-1,-1,-1,-1,-1,-1,2,2,2,-1,-1,-1,-1,-1,-1,-1,-1,2
2,-1,-1,-1,-1,-1,-1,-1,2,2,2,-1,-1,-1,-1,-1,-1,-1,-1,2
2,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,2
2,-1,-1,-1,-1,-1,-1,-1,2,2,2,-1,-1,-1,-1,-1,-1,-1,-1,2
2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2
```

`-1` = passable, `2` = wall tile (solid).

---

## Related Docs

- [JES Overview](../overview/jes-scripting.md)
- [Scenes & Entities](../scene/jes-scenes-entities.md)
- [Component Reference](../scene/components.md) — `Character2D`
- [AI System](../gameplay/jes-ai.md) — pathfinding, chase, patrol
- [Input Bindings](jes-input.md) — `moveHero`, `interact`

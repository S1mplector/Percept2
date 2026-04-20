# JES By Example — Tilemap World

Create tile-based game worlds with visual layers, collision walls, trigger zones, and map warps.

**Difficulty:** Intermediate
**Time:** 20 minutes
**Concepts:** `tileset`, `map`, map layers, collision, triggers, CSV tile data, `warpMap`, `playAudio`

---

## The Scene

```jes
scene "TownMap" {
  tileset "overworld" {
    image: "assets/tilesets/overworld.png"
    tileW: 16
    tileH: 16
    cols: 16
  }

  map "town" {
    tileset: "overworld"
    width: 20
    height: 15
    tileW: 32
    tileH: 32

    layer "ground" {
      data: "maps/town_ground.csv"
    }
    layer "walls" {
      data: "maps/town_walls.csv"
      collision: true
    }
    layer "triggers" {
      data: "maps/town_triggers.csv"
      triggerCall: "warpMap"
      toTileX: 5
      toTileY: 3
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
      startTileX: 10
      startTileY: 7
      speed: 90
      controllable: true
      animations: "down:0-3,up:4-7,left:8-11,right:12-15"
      startAnim: "down"
    }
  }

  on key "D" do toggleDebug
  on key "SPACE" do interact

  timeline {
    cameraFollow "hero" { lerp: 0.15 }
    playAudio "assets/audio/bgm/town.ogg" { volume: 0.6 loop: true bgm: true }
  }
}
```

---

## Tilesets

A **tileset** defines a source image containing a grid of tile graphics.

```jes
tileset "overworld" {
  image: "assets/tilesets/overworld.png"
  tileW: 16
  tileH: 16
  cols: 16
}
```

| Property | Type | Description |
|----------|------|-------------|
| `image` | string | Path to the tileset image file |
| `tileW` | number | Width of each tile in the image (pixels) |
| `tileH` | number | Height of each tile in the image (pixels) |
| `cols` | number | Number of tile columns in the image |

### Tileset Image Layout

```text
Tile indices (reading left-to-right, top-to-bottom):
┌──┬──┬──┬──┬──┬──┬──┬──┐
│0 │1 │2 │3 │4 │5 │6 │7 │  ← Each cell is tileW × tileH
├──┼──┼──┼──┼──┼──┼──┼──┤
│8 │9 │10│11│12│13│14│15│
├──┼──┼──┼──┼──┼──┼──┼──┤
│16│17│18│19│20│21│22│23│
└──┴──┴──┴──┴──┴──┴──┴──┘
```

A single tileset image can contain hundreds of tiles. Each tile is referenced by its index in the CSV data files.

---

## Maps

A **map** is a rectangular grid of tiles that uses a tileset and has one or more layers.

```jes
map "town" {
  tileset: "overworld"
  width: 20
  height: 15
  tileW: 32
  tileH: 32
  ...
}
```

| Property | Type | Description |
|----------|------|-------------|
| `tileset` | string | Name of the tileset to use (must match a `tileset` declaration) |
| `width` | number | Number of tile columns |
| `height` | number | Number of tile rows |
| `tileW` | number | Display width of each tile on screen (pixels) |
| `tileH` | number | Display height of each tile on screen (pixels) |

### Size Relationship

- Tileset `tileW`/`tileH` — the **source size** in the tileset image (what to cut)
- Map `tileW`/`tileH` — the **display size** on screen (how big to draw)
- Example: 16×16 source tiles rendered at 32×32 = 2x upscale

### World Dimensions

The map's total pixel size is:
```
worldWidth  = width × tileW   (e.g., 20 × 32 = 640 pixels)
worldHeight = height × tileH  (e.g., 15 × 32 = 480 pixels)
```

---

## Map Layers

Layers stack visual and behavioral data on top of each other. There are three types:

### Visual Layers

The simplest layer — renders tile graphics with no special behavior:

```jes
layer "ground" {
  data: "maps/town_ground.csv"
}
```

### Collision Layers

Tiles in a collision layer block entity movement:

```jes
layer "walls" {
  data: "maps/town_walls.csv"
  collision: true
}
```

Any tile index ≥ 0 in the collision layer blocks the corresponding tile position. Index `-1` means empty (passable).

### Trigger Layers

Trigger layers fire a call handler when the player steps on a non-empty tile:

```jes
layer "triggers" {
  data: "maps/town_triggers.csv"
  triggerCall: "warpMap"
  toTileX: 5
  toTileY: 3
}
```

| Property | Type | Description |
|----------|------|-------------|
| `triggerCall` | string | Call handler name to invoke |
| `toTileX` | number | X tile coordinate passed to the handler |
| `toTileY` | number | Y tile coordinate passed to the handler |

The built-in `warpMap` handler teleports the player to the specified tile position. You can also use custom call handler names.

---

## CSV Data Format

Tile data is stored in CSV files — one file per layer, one number per tile.

```csv
-1,-1,-1,-1,-1,-1,-1,-1,-1,-1
-1,0,1,1,1,1,1,1,0,-1
-1,2,3,3,3,3,3,3,2,-1
-1,2,3,3,3,3,3,3,2,-1
-1,0,1,1,1,1,1,1,0,-1
-1,-1,-1,-1,-1,-1,-1,-1,-1,-1
```

- Each number is a tile index from the tileset
- `-1` means **empty** (no tile drawn, no collision)
- The grid dimensions must match the map's `width` × `height`
- Rows correspond to Y positions, columns to X positions

### Editing Tile Data

Options for creating CSV tile data:

1. **By hand** — small maps only, practical for collision/trigger overlays
2. **External tool** — use [Tiled](https://www.mapeditor.org/) and export as CSV
3. **Generated** — write a script that outputs CSV data

---

## Multiple Maps in One Scene

A scene can contain multiple maps for different areas or overlapping layers:

```jes
scene "Overworld" {
  tileset "terrain" { ... }
  tileset "buildings" { ... }

  map "ground_layer" {
    tileset: "terrain"
    width: 40
    height: 30
    tileW: 32
    tileH: 32
    layer "floor" { data: "maps/overworld_floor.csv" }
    layer "walls" { data: "maps/overworld_walls.csv" collision: true }
  }

  map "building_overlay" {
    tileset: "buildings"
    width: 40
    height: 30
    tileW: 32
    tileH: 32
    layer "roofs" { data: "maps/overworld_roofs.csv" }
  }

  ...
}
```

---

## Map Warping

The built-in `warpMap` handler teleports the player. It can be triggered from:

1. **Trigger layers** — automatically when the player walks onto a trigger tile
2. **Call handlers** — programmatically from Java
3. **Timeline** — via a `call` action

### Multi-Map Warping

For warping between different JES scenes (different `.jes` files), use the VNS bridge or Java scene management instead of `warpMap`. The `warpMap` built-in only repositions within the current scene.

To warp to a different area within the same scene:

```java
scene.registerCall("enterDungeon", props -> {
    // Reposition hero
    Entity2D hero = scene.find("hero");
    hero.setPosition(5 * 32, 3 * 32);  // tile (5,3) at 32px grid

    // Pan camera instantly
    scene.getCamera().setPosition(5 * 32, 3 * 32);
});
```

---

## Audio in Timelines

`playAudio` starts audio playback:

```jes
playAudio "assets/audio/bgm/town.ogg" { volume: 0.6 loop: true bgm: true }
```

| Property | Default | Description |
|----------|---------|-------------|
| `volume` | `1.0` | Volume level (0.0–1.0) |
| `loop` | `false` | Loop the audio |
| `bgm` | `false` | Mark as background music (only one BGM plays at a time) |

`stopAudio` stops audio:

```jes
stopAudio { path: "assets/audio/bgm/town.ogg" }
```

---

## Full Example: Two-Room House

```jes
scene "House" {
  tileset "interior" {
    image: "assets/tilesets/interior.png"
    tileW: 16
    tileH: 16
    cols: 8
  }

  map "main_room" {
    tileset: "interior"
    width: 10
    height: 8
    tileW: 32
    tileH: 32

    layer "floor" {
      data: "maps/house_floor.csv"
    }
    layer "walls" {
      data: "maps/house_walls.csv"
      collision: true
    }
    layer "door_to_bedroom" {
      data: "maps/house_door.csv"
      triggerCall: "warpMap"
      toTileX: 2
      toTileY: 6
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
      startTileX: 5
      startTileY: 4
      speed: 80
      controllable: true
      animations: "down:0-3,up:4-7,left:8-11,right:12-15"
      startAnim: "down"
    }
  }

  entity "cat" {
    component Character2D {
      spriteSheet: "assets/characters/cat.png"
      frameW: 16
      frameH: 16
      cols: 2
      drawW: 24
      drawH: 24
      startTileX: 7
      startTileY: 3
      animations: "down:0-1"
      startAnim: "down"
    }
    component Ai2D {
      type: "patrol"
      patrolRadius: 64
      patrolIntervalMs: 2000
      moveSpeed: 20
    }
  }

  on key "SPACE" do interact
  on key "D" do toggleDebug

  timeline {
    cameraFollow "hero" { lerp: 0.15 }
  }
}
```

---

## Key Takeaways

1. `tileset` defines a sprite sheet of tile graphics
2. `map` creates a tile grid with configurable display size
3. Three layer types: **visual**, **collision** (`collision: true`), **trigger** (`triggerCall`)
4. CSV files store tile indices; `-1` = empty
5. Collision layers block character movement automatically
6. Trigger layers fire call handlers when the player enters the tile
7. `warpMap` is a built-in handler for in-scene teleportation
8. `playAudio` with `loop: true bgm: true` starts background music

---

## Next

- [RPG Systems](08-rpg-systems.md) — stats, inventory, equipment, and combat
- [Back to Index](../jes-by-example.md)

# JES By Example — Animated Characters

Use `Character2D` for spritesheet-animated, grid-aware characters with built-in movement controls.

**Difficulty:** Intermediate
**Time:** 15 minutes
**Concepts:** `Character2D`, spritesheet layout, animation definitions, `controllable`, `cameraFollow`, grid movement

---

## The Scene

```jes
scene "RPGField" {
  entity "hero" {
    component Character2D {
      spriteSheet: "assets/characters/knight.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 48
      drawH: 48
      startTileX: 5
      startTileY: 5
      speed: 100
      controllable: true
      animations: "idle_down:0-0,walk_down:0-3,walk_up:4-7,walk_left:8-11,walk_right:12-15"
      startAnim: "idle_down"
    }
  }

  entity "hud" {
    component Label2D {
      text: "Use arrow keys to move"
      x: 10
      y: 10
      size: 14
      color: rgb(1, 1, 1, 1)
    }
  }

  on key "D" do toggleDebug

  timeline {
    cameraFollow "hero" { lerp: 0.12 offsetY: -20 }
  }
}
```

---

## `Character2D` Property Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `spriteSheet` | string | required | Path to the spritesheet image |
| `frameW` | number | required | Width of each frame in the sheet (pixels) |
| `frameH` | number | required | Height of each frame in the sheet (pixels) |
| `cols` | number | required | Number of columns in the spritesheet |
| `drawW` | number | `frameW` | Display width on screen (pixels) |
| `drawH` | number | `frameH` | Display height on screen (pixels) |
| `x` | number | `0` | Pixel position X (overrides `startTileX` if both set) |
| `y` | number | `0` | Pixel position Y (overrides `startTileY` if both set) |
| `startTileX` | number | `0` | Initial tile column position |
| `startTileY` | number | `0` | Initial tile row position |
| `speed` | number | `80` | Movement speed in pixels per second |
| `controllable` | boolean | `false` | Responds to arrow key / WASD input |
| `animations` | string | `""` | Animation definition string (see below) |
| `startAnim` | string | `""` | Initial animation name |
| `dialogueId` | string | `""` | VNS scenario ID triggered by `interact` action |

---

## Spritesheet Layout

A spritesheet is a single image containing all animation frames arranged in a grid.

```text
┌────┬────┬────┬────┐
│ 0  │ 1  │ 2  │ 3  │  ← Row 0: walk_down frames
├────┼────┼────┼────┤
│ 4  │ 5  │ 6  │ 7  │  ← Row 1: walk_up frames
├────┼────┼────┼────┤
│ 8  │ 9  │ 10 │ 11 │  ← Row 2: walk_left frames
├────┼────┼────┼────┤
│ 12 │ 13 │ 14 │ 15 │  ← Row 3: walk_right frames
└────┴────┴────┴────┘
```

- Each cell has size `frameW` × `frameH`
- Frames are numbered left-to-right, top-to-bottom starting at 0
- `cols` tells the engine how many columns exist per row

### Size Relationship

- `frameW` / `frameH` — the size of each frame **in the image file** (the source rectangle)
- `drawW` / `drawH` — the size **on screen** (can be different for scaling)
- Example: 16×16 pixel art frames drawn at 48×48 = 3x upscale

---

## Animation Definitions

The `animations` property is a comma-separated list of `name:startFrame-endFrame` ranges:

```
animations: "idle_down:0-0,walk_down:0-3,walk_up:4-7,walk_left:8-11,walk_right:12-15"
```

| Definition | Meaning |
|-----------|---------|
| `idle_down:0-0` | Single frame (frame 0), used when standing still |
| `walk_down:0-3` | Frames 0, 1, 2, 3 cycle when walking down |
| `walk_up:4-7` | Frames 4, 5, 6, 7 cycle when walking up |
| `walk_left:8-11` | Frames 8–11 cycle when walking left |
| `walk_right:12-15` | Frames 12–15 cycle when walking right |

### Naming Convention

The runtime uses these **exact names** for automatic directional animation:

| Name | When Used |
|------|-----------|
| `idle_down`, `idle_up`, `idle_left`, `idle_right` | Standing still, facing that direction |
| `walk_down`, `walk_up`, `walk_left`, `walk_right` | Moving in that direction |
| `down`, `up`, `left`, `right` | Shorthand (used as both idle and walk if the `idle_*` / `walk_*` variants aren't defined) |

### Minimal Setup

If your spritesheet has only 4 walking animations (one per direction), you can use the shorthand:

```
animations: "down:0-3,up:4-7,left:8-11,right:12-15"
```

The engine will use frame 0 of each direction as the idle pose.

---

## Grid Positioning

Characters can be positioned by **tile coordinates** instead of pixel coordinates:

```jes
startTileX: 5
startTileY: 5
```

The pixel position is calculated as:
```
x = startTileX * gridWidth
y = startTileY * gridHeight
```

The default grid size is 16×16. When using tilemaps, the grid size is set by the map's `tileW` / `tileH`.

---

## Controllable Characters

When `controllable: true`, the character responds to:

| Keys | Direction |
|------|-----------|
| `UP` / `W` | Move up |
| `DOWN` / `S` | Move down |
| `LEFT` / `A` | Move left |
| `RIGHT` / `D` | Move right |

Movement is **continuous** — holding a key moves the character each frame at the configured `speed`. The character's animation switches automatically based on movement direction.

### Multiple Characters

Only **one** character should be `controllable: true`. Non-controllable characters can be moved by:

- AI components (`Ai2D`)
- Timeline actions (`move`, `walkToTile`)
- Java call handlers

---

## NPC Characters

Characters without `controllable: true` are NPCs. Add `dialogueId` to make them interactive:

```jes
entity "elder" {
  component Character2D {
    spriteSheet: "assets/characters/elder.png"
    frameW: 16
    frameH: 16
    cols: 4
    drawW: 32
    drawH: 32
    startTileX: 8
    startTileY: 3
    dialogueId: "elder_intro"
    animations: "down:0-3,up:4-7,left:8-11,right:12-15"
    startAnim: "down"
  }
}
```

When the player presses `SPACE` (the `interact` action) near this NPC, the engine triggers a VNS dialogue with scenario ID `elder_intro`.

---

## Camera Follow with Dead Zones

For top-down games, use `cameraFollow` with dead zones to avoid the camera jittering on small movements:

```jes
timeline {
  cameraFollow "hero" {
    lerp: 0.12
    offsetY: -20
    deadZoneW: 80
    deadZoneH: 60
  }
}
```

The camera won't move until the target entity leaves the dead zone rectangle. This creates a natural "soft follow" feel.

---

## Full Example: Village with NPCs

```jes
scene "Village" {
  entity "hero" {
    component Character2D {
      spriteSheet: "assets/characters/knight.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 32
      drawH: 32
      startTileX: 5
      startTileY: 8
      speed: 90
      controllable: true
      animations: "idle_down:0-0,walk_down:0-3,walk_up:4-7,walk_left:8-11,walk_right:12-15"
      startAnim: "idle_down"
    }
  }

  entity "villager_a" {
    component Character2D {
      spriteSheet: "assets/characters/villager.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 32
      drawH: 32
      startTileX: 8
      startTileY: 5
      dialogueId: "villager_gossip"
      animations: "down:0-3,up:4-7,left:8-11,right:12-15"
      startAnim: "down"
    }
    component Ai2D {
      type: "patrol"
      patrolRadius: 48
      patrolIntervalMs: 3000
      moveSpeed: 25
    }
  }

  entity "shopkeeper" {
    component Character2D {
      spriteSheet: "assets/characters/merchant.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 32
      drawH: 32
      startTileX: 12
      startTileY: 6
      dialogueId: "shop_menu"
      animations: "down:0-3"
      startAnim: "down"
    }
  }

  on key "SPACE" do interact
  on key "D" do toggleDebug

  timeline {
    cameraFollow "hero" { lerp: 0.12 offsetY: -10 }
  }
}
```

---

## Key Takeaways

1. `Character2D` combines spritesheet rendering with grid-aware movement
2. Spritesheets use `frameW`/`frameH`/`cols` to define the frame grid
3. Animations are defined as `"name:startFrame-endFrame"` comma-separated strings
4. `controllable: true` enables arrow key / WASD movement for one character
5. `dialogueId` enables NPC interaction via the `interact` input action
6. `cameraFollow` with `lerp` and dead zones gives smooth tracking
7. Combine `Character2D` with `Ai2D` for autonomous NPC behavior

---

## Next

- [Tilemap World](07-tilemap-world.md) — tile-based levels with collision and triggers
- [Back to Index](../jes-by-example.md)

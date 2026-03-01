# JES AI System

Complete reference for the AI behavior system in JES — chase, patrol, guard, flee, attack, line-of-sight, and pathfinding.

Runtime: `scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`
Model: `scripting/src/main/java/com/jvn/scripting/jes/runtime/Ai2D.java`

---

## Ai2D Component

Add AI behavior to any entity with a `Character2D` or positional component.

```jes
entity "enemy" {
  component Character2D { ... }
  component Stats { ... }
  component Ai2D {
    type: "chase_and_attack"
    target: "hero"
    aggroRange: 200
    attackRange: 40
    attackIntervalMs: 1000
    attackAmount: 10
    moveSpeed: 60
    requiresLineOfSight: true
  }
}
```

### Properties

| Property | Default | Description |
|----------|---------|-------------|
| `type` | — | AI behavior type (see below) |
| `target` | player | Entity name to target (defaults to `playerName`) |
| `aggroRange` | 0 | Detection distance (0 = always active) |
| `attackRange` | grid size | Distance at which attacks are launched |
| `attackIntervalMs` | 1000 | Cooldown between attacks (ms) |
| `attackAmount` | entity ATK | Damage per attack (falls back to `Stats.atk`) |
| `moveSpeed` | entity speed | Movement speed (falls back to `Stats.speed`, then 80) |
| `attackCooldownMs` | 0 | Initial cooldown state (usually 0) |
| `patrolRadius` | 3× grid | Wander radius around spawn point |
| `patrolIntervalMs` | 0 | Time between patrol waypoint changes (ms) |
| `requiresLineOfSight` | false | Only aggro if clear line of sight to target |
| `guardRadius` | 0 | Max distance from spawn before returning |
| `fleeDistance` | 0 | Distance at which AI runs away from target |

---

## AI Types

### `chase` / `chasehero` / `chase_and_attack`

Chases the target entity and attacks when within range.

**Behavior:**
1. Check if target is within `aggroRange` (skip if 0 = always chase)
2. If `requiresLineOfSight`, check clear path to target
3. If within `attackRange` and cooldown is ready, deal damage
4. Otherwise, move toward target with obstacle avoidance
5. Falls back to A* pathfinding when direct movement is blocked

```jes
component Ai2D {
  type: "chase_and_attack"
  aggroRange: 200
  attackRange: 40
  attackIntervalMs: 800
  moveSpeed: 70
  requiresLineOfSight: true
}
```

**Example: Aggressive melee enemy**

```jes
entity "goblin" {
  component Character2D {
    spriteSheet: "assets/characters/goblin.png"
    frameW: 16
    frameH: 16
    cols: 4
    drawW: 32
    drawH: 32
    startTileX: 12
    startTileY: 8
    speed: 70
    animations: "down:0-3,up:4-7,left:8-11,right:12-15"
    startAnim: "down"
  }
  component Stats {
    maxHp: 30
    hp: 30
    atk: 8
    def: 2
    speed: 70
    onDeathCall: "enemyDied"
    removeOnDeath: true
  }
  component Ai2D {
    type: "chase_and_attack"
    aggroRange: 150
    attackRange: 35
    attackIntervalMs: 1000
    moveSpeed: 70
    requiresLineOfSight: true
  }
}
```

### `patrol` 

Wanders randomly around the spawn point.

**Behavior:**
1. Pick a random point within `patrolRadius` of spawn position
2. Move toward the patrol goal
3. When reached (or `patrolIntervalMs` elapsed), pick a new goal
4. Repeats indefinitely

```jes
component Ai2D {
  type: "patrol"
  patrolRadius: 80
  patrolIntervalMs: 3000
  moveSpeed: 40
}
```

**Example: Wandering NPC**

```jes
entity "villager" {
  component Character2D {
    spriteSheet: "assets/characters/villager.png"
    frameW: 16
    frameH: 16
    cols: 4
    drawW: 32
    drawH: 32
    startTileX: 8
    startTileY: 6
    speed: 40
    animations: "down:0-3,up:4-7,left:8-11,right:12-15"
    startAnim: "down"
    dialogueId: "villager_chat"
  }
  component Ai2D {
    type: "patrol"
    patrolRadius: 60
    patrolIntervalMs: 2500
    moveSpeed: 35
  }
}
```

### `patrol_chase`

Patrols normally but switches to chase behavior when the target enters aggro range.

**Behavior:**
1. Patrol as normal
2. Each update, check if target is within `aggroRange`
3. If within range, switch to chase-and-attack behavior
4. Returns to patrol when target leaves range (via guard radius or aggro range)

```jes
component Ai2D {
  type: "patrol_chase"
  aggroRange: 120
  attackRange: 35
  attackIntervalMs: 1200
  patrolRadius: 80
  patrolIntervalMs: 2000
  moveSpeed: 50
}
```

### `guard`

Chases the target but returns to spawn when too far away.

**Behavior:**
1. Chase target as normal
2. Track distance from spawn position
3. If both entity and target are beyond `guardRadius` from spawn, move back toward spawn instead
4. Effectively creates a "territory" around the spawn point

```jes
component Ai2D {
  type: "guard"
  aggroRange: 150
  attackRange: 35
  guardRadius: 120
  attackIntervalMs: 1000
  moveSpeed: 60
}
```

**Example: Dungeon guard**

```jes
entity "guard_skeleton" {
  component Character2D {
    spriteSheet: "assets/characters/skeleton.png"
    frameW: 16
    frameH: 16
    cols: 4
    drawW: 32
    drawH: 32
    startTileX: 10
    startTileY: 3
    speed: 55
  }
  component Stats {
    maxHp: 50
    hp: 50
    atk: 12
    def: 5
    onDeathCall: "guardDied"
    removeOnDeath: true
  }
  component Ai2D {
    type: "guard"
    aggroRange: 120
    attackRange: 35
    guardRadius: 100
    attackIntervalMs: 1200
    moveSpeed: 55
    requiresLineOfSight: true
  }
}
```

### Flee Behavior

Any AI type with `fleeDistance > 0` will run away from the target when within that distance:

```jes
component Ai2D {
  type: "chase_and_attack"
  fleeDistance: 60
  aggroRange: 200
  moveSpeed: 80
}
```

When the target is closer than `fleeDistance`, the AI reverses direction and moves away. Useful for ranged enemies or cowardly NPCs.

---

## Movement & Pathfinding

### Direction Smoothing

AI movement uses directional smoothing (70% previous direction, 30% new direction) to prevent jittery motion. This creates natural-looking curved approaches rather than robotic straight lines.

### Obstacle Avoidance

When direct movement to the target is blocked by a collision tile:
1. AI attempts A* pathfinding (up to 512 nodes)
2. Follows the first waypoint of the computed path
3. If pathfinding fails, the AI stops moving

### Line of Sight

When `requiresLineOfSight: true`:
- AI samples positions along the line between itself and the target
- If any sampled position hits a collision tile, line of sight is blocked
- AI won't aggro or chase without clear sight

---

## Combat Integration

### Damage Application

When an AI entity attacks, damage is applied through the `Stats` system:

1. AI checks `attackRange` distance to target
2. If cooldown (`attackCooldownMs`) is ready, applies damage
3. Damage amount = `attackAmount` (or `Stats.atk` if not set)
4. Target's HP is reduced
5. If HP reaches 0 and `onDeathCall` is set, the death handler is invoked
6. If `removeOnDeath: true`, the target entity is removed from the scene

### Death Callbacks

```jes
component Stats {
  maxHp: 40
  hp: 40
  atk: 8
  onDeathCall: "enemyDied"
  removeOnDeath: true
}
```

The death handler receives:
- `entity` — name of the entity that died
- `source` — name of the entity that dealt the killing blow

```java
scene.registerCall("enemyDied", props -> {
    String enemy = (String) props.get("entity");
    String killer = (String) props.get("source");
    // Award XP, drop loot, update quest, etc.
});
```

---

## Complete Example: Enemy Variety

```jes
scene "DungeonFloor" {
  // ... tileset and map declarations ...

  entity "hero" {
    component Character2D {
      spriteSheet: "assets/characters/knight.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 32
      drawH: 32
      startTileX: 2
      startTileY: 12
      speed: 100
      controllable: true
    }
    component Stats { maxHp: 100 hp: 100 atk: 20 def: 10 }
  }

  // Fast, aggressive chaser
  entity "wolf" {
    component Character2D {
      spriteSheet: "assets/characters/wolf.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 32
      drawH: 32
      startTileX: 15
      startTileY: 4
      speed: 90
    }
    component Stats { maxHp: 25 hp: 25 atk: 12 speed: 90 onDeathCall: "enemyDied" removeOnDeath: true }
    component Ai2D {
      type: "chase_and_attack"
      aggroRange: 200
      attackRange: 30
      attackIntervalMs: 600
      moveSpeed: 90
    }
  }

  // Slow, tough guard
  entity "golem" {
    component Character2D {
      spriteSheet: "assets/characters/golem.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 48
      drawH: 48
      startTileX: 10
      startTileY: 6
      speed: 30
    }
    component Stats { maxHp: 150 hp: 150 atk: 25 def: 15 speed: 30 onDeathCall: "enemyDied" removeOnDeath: true }
    component Ai2D {
      type: "guard"
      aggroRange: 100
      attackRange: 45
      guardRadius: 80
      attackIntervalMs: 2000
      moveSpeed: 30
    }
  }

  // Patrolling enemy that chases on sight
  entity "scout" {
    component Character2D {
      spriteSheet: "assets/characters/scout.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 32
      drawH: 32
      startTileX: 8
      startTileY: 2
      speed: 65
    }
    component Stats { maxHp: 35 hp: 35 atk: 10 speed: 65 onDeathCall: "enemyDied" removeOnDeath: true }
    component Ai2D {
      type: "patrol_chase"
      aggroRange: 140
      attackRange: 35
      patrolRadius: 100
      patrolIntervalMs: 2500
      moveSpeed: 65
      requiresLineOfSight: true
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

## Related Docs

- [JES Overview](jes-scripting.md)
- [Component Reference](components.md) — `Ai2D`, `Stats`, `Character2D`
- [Tilemaps & Maps](jes-tilemaps.md) — collision, pathfinding grid
- [RPG Stats & Combat](jes-rpg.md) — damage, healing, death

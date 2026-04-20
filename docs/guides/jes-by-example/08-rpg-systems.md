# JES By Example — RPG Systems

Add stats, items, inventory, equipment, and AI-driven combat to your JES scenes.

**Difficulty:** Intermediate–Advanced
**Time:** 25 minutes
**Concepts:** `Stats`, `Inventory`, `Equipment`, `Ai2D`, `item` blocks, combat flow, built-in call handlers

---

## The Scene

```jes
scene "CombatDemo" {
  item "potion" {
    name: "Health Potion"
    type: "consumable"
    maxStack: 10
    healAmount: 50
  }

  item "iron_sword" {
    name: "Iron Sword"
    type: "equipment"
    atkBonus: 8
    equipSlot: "weapon"
  }

  entity "hero" {
    component Character2D {
      spriteSheet: "assets/characters/knight.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 48
      drawH: 48
      startTileX: 3
      startTileY: 5
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
      speed: 100
    }
    component Inventory {
      slots: 20
      items: "potion*3,iron_sword*1"
    }
    component Equipment {
      weapon: "iron_sword"
    }
  }

  entity "enemy" {
    component Character2D {
      spriteSheet: "assets/characters/goblin.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 40
      drawH: 40
      startTileX: 10
      startTileY: 5
    }
    component Stats {
      maxHp: 60
      hp: 60
      atk: 10
      def: 4
    }
    component Ai2D {
      type: "chase"
      moveSpeed: 50
      aggroRange: 150
      attackRange: 32
      attackIntervalMs: 1500
      attackAmount: 10
    }
  }

  entity "hud_hp" {
    component Label2D {
      text: "HP: 100"
      x: 10
      y: 10
      size: 16
      bold: true
      color: rgb(1, 0.3, 0.3, 1)
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

## Item Definitions

`item` blocks define an **item database** within the scene. They describe what items exist — not who owns them.

```jes
item "potion" {
  name: "Health Potion"
  type: "consumable"
  maxStack: 10
  healAmount: 50
}

item "iron_sword" {
  name: "Iron Sword"
  type: "equipment"
  atkBonus: 8
  equipSlot: "weapon"
}
```

| Property | Type | Description |
|----------|------|-------------|
| `name` | string | Display name |
| `type` | string | Category: `consumable`, `equipment`, `key`, or custom |
| `maxStack` | number | Maximum stack size in inventory (default: 1) |
| `healAmount` | number | HP restored when used (consumables) |
| `atkBonus` | number | Attack bonus when equipped |
| `defBonus` | number | Defense bonus when equipped |
| `equipSlot` | string | Equipment slot: `weapon`, `shield`, `armor`, `accessory` |

Item IDs (the quoted string after `item`) must be unique within the scene.

---

## Stats Component

`Stats` gives an entity numeric attributes for combat.

```jes
component Stats {
  maxHp: 100
  hp: 100
  atk: 15
  def: 8
  speed: 100
}
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `maxHp` | number | `0` | Maximum hit points |
| `hp` | number | `maxHp` | Current hit points |
| `atk` | number | `0` | Attack power |
| `def` | number | `0` | Defense |
| `speed` | number | `0` | Speed (used by AI movement if `Ai2D.moveSpeed` is unset) |

### Accessing Stats from Java

```java
Stats heroStats = scene.getStats("hero");
if (heroStats != null) {
    double currentHp = heroStats.getHp();
    double maxHp = heroStats.getMaxHp();
    double atk = heroStats.getAtk();
}
```

---

## Inventory Component

`Inventory` assigns item storage to an entity.

```jes
component Inventory {
  slots: 20
  items: "potion*3,iron_sword*1"
}
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `slots` | number | `10` | Maximum inventory slots |
| `items` | string | `""` | Starting items as `"itemId*count,itemId*count"` |

### Inventory Java API

```java
// Give items
scene.giveItem("hero", "potion", 5);

// Take items
boolean success = scene.takeItem("hero", "potion", 1);

// Check inventory
Inventory inv = scene.getInventory("hero");
if (inv != null) {
    int potionCount = inv.getCount("potion");
}
```

### Built-In Call Handlers for Items

| Handler | Props | Description |
|---------|-------|-------------|
| `giveItem` | `target`, `item`, `count` | Add items to entity inventory |
| `takeItem` | `target`, `item`, `count` | Remove items from inventory |
| `useItem` | `target`, `item` | Use a consumable (heals if `healAmount > 0`) |

```java
// From a timeline call or manual invocation
scene.invokeCall("giveItem", Map.of("target", "hero", "item", "potion", "count", 3));
scene.invokeCall("useItem", Map.of("target", "hero", "item", "potion"));
```

---

## Equipment Component

`Equipment` assigns items to equip slots on an entity.

```jes
component Equipment {
  weapon: "iron_sword"
  shield: ""
  armor: ""
  accessory: ""
}
```

| Slot | Description |
|------|-------------|
| `weapon` | Equipped weapon item ID |
| `shield` | Equipped shield item ID |
| `armor` | Equipped armor item ID |
| `accessory` | Equipped accessory item ID |

### Equipment Java API

```java
Equipment eq = scene.getEquipment("hero");
if (eq != null) {
    String weapon = eq.getSlot("weapon");
}
```

### Built-In Handlers

| Handler | Props | Description |
|---------|-------|-------------|
| `equipItem` | `target`, `item`, `slot` | Equip an item to a slot |
| `unequipItem` | `target`, `slot` | Remove equipment from a slot |

---

## AI Component (`Ai2D`)

`Ai2D` gives an entity autonomous behavior — movement, targeting, and combat.

```jes
component Ai2D {
  type: "chase"
  moveSpeed: 50
  aggroRange: 150
  attackRange: 32
  attackIntervalMs: 1500
  attackAmount: 10
}
```

### AI Types

| Type | Behavior |
|------|----------|
| `chase` | Moves toward target entity, attacks when in range |
| `patrol` | Wanders within a radius around spawn position |
| `patrol_chase` | Patrols normally, switches to chase if target enters aggro range |
| `guard` | Chases within guard radius, returns to spawn if target moves too far |
| `flee` | Runs away when target is within flee distance |
| `chase_and_attack` | Alias for `chase` |

### AI Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `type` | string | required | AI behavior type |
| `moveSpeed` | number | entity `speed` stat | Movement speed (pixels/sec) |
| `aggroRange` | number | unlimited | Distance to start chasing (pixels) |
| `attackRange` | number | grid size | Distance to start attacking (pixels) |
| `attackIntervalMs` | number | `1000` | Cooldown between attacks (ms) |
| `attackAmount` | number | entity `atk` stat | Damage per attack |
| `target` | string | player entity | Name of entity to target |
| `requiresLineOfSight` | boolean | `false` | Require clear line of sight to chase |
| `guardRadius` | number | `0` | Maximum distance from spawn before returning |
| `fleeDistance` | number | `0` | Distance to start fleeing |
| `patrolRadius` | number | `0` | Patrol wander radius (pixels) |
| `patrolIntervalMs` | number | `2000` | Time between patrol direction changes (ms) |

### AI Behavior Details

**Chase AI** pathfinds toward the target using obstacle-aware smoothing. If blocked by collision tiles, it falls back to A*-style grid pathfinding. When within `attackRange`, the AI stops moving and attacks at `attackIntervalMs` intervals.

**Guard AI** works like chase, but tracks distance from spawn. If the entity moves beyond `guardRadius` and the target is also beyond `guardRadius`, it returns to its spawn position.

**Flee AI** inverts the chase direction when the target is within `fleeDistance`.

---

## Combat Flow

Combat between entities with `Stats` and `Ai2D` happens automatically:

```text
1. AI entity detects target within aggroRange
2. AI moves toward target
3. When within attackRange, AI attacks at attackIntervalMs intervals
4. Each attack applies damage = attackAmount (or entity atk stat)
5. Damage reduces target HP (defense is subtracted from damage)
6. When HP reaches 0, entity can be removed via call handler
```

### Damage Calculation

The built-in damage formula:

```
effectiveDamage = max(1, attackAmount - targetDef)
```

### Timeline Damage/Heal Actions

You can also apply damage from timelines:

```jes
timeline {
  damage "enemy" { amount: 25 source: "hero" }
  heal "hero" { amount: 10 }
}
```

---

## Full Example: Arena with Waves

```jes
scene "Arena" {
  item "potion" {
    name: "Health Potion"
    type: "consumable"
    maxStack: 10
    healAmount: 30
  }

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
      animations: "down:0-3,up:4-7,left:8-11,right:12-15"
      startAnim: "down"
    }
    component Stats {
      maxHp: 120
      hp: 120
      atk: 18
      def: 10
      speed: 100
    }
    component Inventory {
      slots: 10
      items: "potion*5"
    }
  }

  entity "goblin_a" {
    component Character2D {
      spriteSheet: "assets/characters/goblin.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 36
      drawH: 36
      startTileX: 2
      startTileY: 2
    }
    component Stats {
      maxHp: 40
      hp: 40
      atk: 8
      def: 3
    }
    component Ai2D {
      type: "chase"
      moveSpeed: 45
      aggroRange: 200
      attackRange: 28
      attackIntervalMs: 1800
      attackAmount: 8
    }
  }

  entity "goblin_b" {
    component Character2D {
      spriteSheet: "assets/characters/goblin.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 36
      drawH: 36
      startTileX: 8
      startTileY: 8
    }
    component Stats {
      maxHp: 40
      hp: 40
      atk: 8
      def: 3
    }
    component Ai2D {
      type: "patrol_chase"
      moveSpeed: 40
      aggroRange: 120
      attackRange: 28
      attackIntervalMs: 2000
      attackAmount: 8
      patrolRadius: 64
    }
  }

  entity "hud_hp" {
    component Label2D {
      text: "HP: 120/120"
      x: 10
      y: 10
      size: 16
      bold: true
      color: rgb(1, 0.3, 0.3, 1)
    }
  }

  entity "hud_potions" {
    component Label2D {
      text: "Potions: 5"
      x: 10
      y: 35
      size: 14
      color: rgb(0.3, 0.8, 1.0, 1)
    }
  }

  on key "SPACE" do interact
  on key "E" do usePotion
  on key "D" do toggleDebug

  timeline {
    cameraFollow "hero" { lerp: 0.12 }
  }
}
```

Wire the `usePotion` handler in Java:

```java
scene.registerCall("usePotion", props -> {
    boolean used = scene.takeItem("hero", "potion", 1);
    if (used) {
        scene.invokeCall("useItem", Map.of("target", "hero", "item", "potion"));
        // Update HUD
        Inventory inv = scene.getInventory("hero");
        int remaining = inv != null ? inv.getCount("potion") : 0;
        scene.invokeCall("setLabelText", Map.of(
            "target", "hud_potions",
            "text", "Potions: " + remaining
        ));
        Stats stats = scene.getStats("hero");
        if (stats != null) {
            scene.invokeCall("setLabelText", Map.of(
                "target", "hud_hp",
                "text", "HP: " + (int) stats.getHp() + "/" + (int) stats.getMaxHp()
            ));
        }
    }
});
```

---

## Key Takeaways

1. `item` blocks define an item database (what items exist)
2. `Stats` gives entities HP, ATK, DEF — the basis for combat
3. `Inventory` stores items with bounded stacking
4. `Equipment` assigns items to slots (weapon, shield, armor, accessory)
5. `Ai2D` enables autonomous movement and combat behavior
6. Five AI types: `chase`, `patrol`, `patrol_chase`, `guard`, `flee`
7. Combat is automatic — AI attacks when in range at configured intervals
8. Built-in handlers: `giveItem`, `takeItem`, `useItem`, `equipItem`, `unequipItem`, `attack`

---

## Next

- [Physics Bodies](09-physics-bodies.md) — rigid body physics and sensor triggers
- [Back to Index](../jes-by-example.md)

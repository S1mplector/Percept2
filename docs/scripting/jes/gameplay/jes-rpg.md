# JES RPG Stats, Combat & Inventory

Complete reference for the Stats, Inventory, Equipment, and Item systems in JES — HP/MP management, damage/healing, death callbacks, item stacking, and equipment bonuses.

Runtime: `modules/scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`

---

## Stats Component

Attach RPG stats to any entity.

```jes
component Stats {
  maxHp: 100
  hp: 100
  maxMp: 50
  mp: 50
  atk: 15
  def: 8
  speed: 80
  onDeathCall: "entityDied"
  removeOnDeath: true
}
```

### Properties

| Property | Default | Description |
|----------|---------|-------------|
| `maxHp` | 0 | Maximum hit points |
| `hp` | maxHp | Current hit points |
| `maxMp` | 0 | Maximum mana/magic points |
| `mp` | maxMp | Current mana/magic points |
| `atk` | 0 | Base attack power |
| `def` | 0 | Base defense |
| `speed` | 0 | Base speed (used by AI movement and continuous char movement) |
| `onDeathCall` | — | Call handler invoked when HP reaches 0 |
| `removeOnDeath` | false | If true, entity is removed from the scene on death |

### Equipment Bonuses

When equipment is worn, bonus stats are computed:
- Effective ATK = `atk + atkBonus`
- Effective DEF = `def + defBonus`
- Effective Speed = `speed + speedBonus`

Bonuses are recalculated whenever equipment changes.

---

## Damage & Healing

### Timeline Actions

```jes
// Deal 25 damage to enemy from hero
damage "enemy" { amount: 25 source: "hero" }

// Heal hero for 50 HP
heal "hero" { amount: 50 }
```

### Java API

```java
scene.applyDamage("enemy", 25.0, "hero");
scene.heal("hero", 50.0, "healer");
```

### Damage Flow

1. Damage amount is applied directly to target's HP
2. HP is clamped to minimum 0
3. If `HP <= 0` (dead):
   - If `onDeathCall` is set, invoke the handler with `{ entity, source }`
   - If `removeOnDeath` is true, remove entity from scene and stats map

### Healing Flow

1. Amount is added to current HP
2. HP is clamped to `maxHp` (if maxHp > 0)

---

## Death Callbacks

```jes
component Stats {
  maxHp: 40
  hp: 40
  atk: 10
  onDeathCall: "enemyDied"
  removeOnDeath: true
}
```

The death handler receives:

| Prop | Description |
|------|-------------|
| `entity` | Name of the entity that died |
| `source` | Name of the entity that dealt the killing blow (if any) |

**Java handler:**

```java
scene.registerCall("enemyDied", props -> {
    String enemy = (String) props.get("entity");
    String killer = (String) props.get("source");
    
    // Award XP
    Stats killerStats = scene.getStats(killer);
    // ... custom logic ...
    
    // Drop loot
    scene.giveItem(killer, "gold_coin", 5);
    
    // Update quest
    scene.incrementVar("kills", 1);
});
```

---

## Item Database

Items are declared at the scene level and form a shared database.

```jes
item "itemId" {
  name: "Display Name"
  type: "consumable"
  maxStack: 10
  // custom properties
  healAmount: 50
  mpRestore: 20
  atkBonus: 5
  defBonus: 3
  equipSlot: "weapon"
  onUseCall: "useItemEffect"
}
```

### Standard Properties

| Property | Description |
|----------|-------------|
| `name` | Display name |
| `type` | Category: `"consumable"`, `"equipment"`, `"quest"`, etc. |
| `maxStack` | Maximum stack size in inventory (0 = unlimited) |
| `healAmount` | HP healed when used |
| `mpRestore` | MP restored when used |
| `atkBonus` | ATK bonus when equipped |
| `defBonus` | DEF bonus when equipped |
| `speedBonus` | Speed bonus when equipped |
| `equipSlot` | Required equipment slot name |
| `onUseCall` | Custom call handler when item is used |

Items can have any arbitrary properties — the system is flexible.

### Examples

```jes
item "health_potion" {
  name: "Health Potion"
  type: "consumable"
  maxStack: 10
  healAmount: 50
}

item "mana_potion" {
  name: "Mana Potion"
  type: "consumable"
  maxStack: 10
  mpRestore: 30
}

item "sword_iron" {
  name: "Iron Sword"
  type: "equipment"
  maxStack: 1
  atkBonus: 8
  equipSlot: "weapon"
}

item "shield_wood" {
  name: "Wooden Shield"
  type: "equipment"
  maxStack: 1
  defBonus: 5
  equipSlot: "shield"
}

item "boots_speed" {
  name: "Speed Boots"
  type: "equipment"
  maxStack: 1
  speedBonus: 20
  equipSlot: "feet"
}

item "quest_key" {
  name: "Dungeon Key"
  type: "quest"
  maxStack: 1
}
```

---

## Inventory Component

Attach an inventory to an entity.

```jes
component Inventory {
  slots: 20
  items: "health_potion*3,sword_iron*1,gold_coin*50"
}
```

### Properties

| Property | Default | Description |
|----------|---------|-------------|
| `slots` | 0 | Maximum inventory slots (0 = unlimited) |
| `items` | — | Initial items as CSV (format: `id*count,id*count,...`) |

### Item Format

```text
itemId*count
```

- `health_potion*3` — 3 health potions
- `sword_iron*1` — 1 iron sword
- `gold_coin*50` — 50 gold coins
- `quest_key` — 1 quest key (count defaults to 1 without `*`)

### Runtime Operations

**Give items:**

```jes
// Via timeline call
call "giveItem" { target: "hero" itemId: "health_potion" count: 2 }
```

```java
scene.giveItem("hero", "health_potion", 2);
```

**Take items:**

```jes
call "takeItem" { target: "hero" itemId: "gold_coin" count: 10 }
```

```java
scene.takeItem("hero", "gold_coin", 10);
```

**Use items:**

```jes
call "useItem" { user: "hero" target: "hero" itemId: "health_potion" }
```

Use item behavior:
1. Removes 1 item from user's inventory
2. If `healAmount` > 0, heals target HP (clamped to maxHp)
3. If `mpRestore` > 0, restores target MP (clamped to maxMp)
4. If `onUseCall` is set, invokes the custom handler

---

## Equipment Component

Attach equipment slots to an entity.

```jes
component Equipment {
  weapon: "sword_iron"
  shield: "shield_wood"
  helmet: ""
  armor: ""
  feet: "boots_speed"
}
```

Equipment uses **free-form slot names** — you define whatever slots make sense for your game.

### Equipping Items

```jes
call "equipItem" { user: "hero" slot: "weapon" itemId: "sword_iron" }
```

Equip behavior:
1. Checks item exists in the item database
2. Checks item type is `"equipment"` (if `type` property is set)
3. Checks `equipSlot` matches the target slot (if set on item)
4. Removes 1 item from user's inventory
5. Sets the equipment slot to the new item
6. If there was a previous item in the slot, returns it to inventory
7. Recalculates equipment bonuses on stats

### Unequipping Items

```jes
call "unequipItem" { user: "hero" slot: "weapon" }
```

Unequip behavior:
1. Removes item from equipment slot
2. Returns the item to inventory
3. Recalculates equipment bonuses

### Equipment Bonuses

When equipment changes, the engine scans all equipped items and sums their bonus properties:
- `atkBonus` → added to `Stats.atkBonus`
- `defBonus` → added to `Stats.defBonus`
- `speedBonus` → added to `Stats.speedBonus`

---

## Complete RPG Example

```jes
scene "RPGDemo" {
  // Item database
  item "health_potion" {
    name: "Health Potion"
    type: "consumable"
    maxStack: 10
    healAmount: 50
  }
  item "mana_potion" {
    name: "Mana Potion"
    type: "consumable"
    maxStack: 10
    mpRestore: 30
  }
  item "sword_iron" {
    name: "Iron Sword"
    type: "equipment"
    maxStack: 1
    atkBonus: 8
    equipSlot: "weapon"
  }
  item "shield_wood" {
    name: "Wooden Shield"
    type: "equipment"
    maxStack: 1
    defBonus: 5
    equipSlot: "shield"
  }
  item "gold_coin" {
    name: "Gold"
    maxStack: 9999
  }

  // Hero entity with full RPG stack
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
    component Stats {
      maxHp: 100
      hp: 100
      maxMp: 50
      mp: 50
      atk: 12
      def: 6
      speed: 90
    }
    component Inventory {
      slots: 20
      items: "health_potion*3,mana_potion*2,gold_coin*100"
    }
    component Equipment {
      weapon: "sword_iron"
      shield: "shield_wood"
    }
  }

  // Enemy
  entity "slime" {
    component Character2D {
      spriteSheet: "assets/characters/slime.png"
      frameW: 16
      frameH: 16
      cols: 4
      drawW: 24
      drawH: 24
      startTileX: 12
      startTileY: 6
      speed: 40
    }
    component Stats {
      maxHp: 20
      hp: 20
      atk: 5
      def: 1
      onDeathCall: "slimeDied"
      removeOnDeath: true
    }
    component Ai2D {
      type: "patrol_chase"
      aggroRange: 100
      attackRange: 30
      patrolRadius: 60
      moveSpeed: 40
    }
  }

  // HUD
  entity "hp_label" {
    component Label2D {
      text: "HP: 100/100"
      x: 10
      y: 10
      size: 14
      bold: true
      color: rgb(1, 0.3, 0.3, 1)
    }
  }

  on key "SPACE" do interact
  on key "I" do openInventory
  on key "D" do toggleDebug

  timeline {
    cameraFollow "hero" { lerp: 0.15 }
  }
}
```

---

## State Persistence

All RPG state is captured by `JesScene2D.saveState()`:

- **Stats** — HP, MP, ATK, DEF, speed, bonuses, death settings
- **Inventories** — item counts, slot limits
- **Equipment** — slot assignments

This state is restored by `loadState()`, enabling save/load across sessions.

---

## Related Docs

- [JES Overview](../overview/jes-scripting.md)
- [Component Reference](../scene/components.md) — `Stats`, `Inventory`, `Equipment`
- [AI System](jes-ai.md) — combat AI
- [Timeline & Actions](../timeline/jes-timeline.md) — `damage`, `heal`
- [Scenes & Entities](../scene/jes-scenes-entities.md) — entity lifecycle

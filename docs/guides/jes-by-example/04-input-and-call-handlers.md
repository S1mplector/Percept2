# JES By Example — Input Bindings and Call Handlers

Make scenes interactive with keyboard input bindings and Java call handlers — the bridge between JES scripts and custom game logic.

**Difficulty:** Intermediate
**Time:** 20 minutes
**Concepts:** `on key ... do`, `registerCall`, `invokeCall`, `setActionHandler`, built-in actions, Java-JES wiring

---

## The Scene

```jes
scene "Interactive" {
  entity "counter_label" {
    component Label2D {
      text: "Press SPACE to count: 0"
      x: 200
      y: 250
      size: 20
      color: rgb(1, 1, 1, 1)
    }
  }

  entity "hint" {
    component Label2D {
      text: "[SPACE] Count  |  [R] Reset  |  [D] Debug"
      x: 150
      y: 500
      size: 14
      color: rgb(0.5, 0.5, 0.5, 1)
    }
  }

  on key "SPACE" do countUp
  on key "R" do resetCount
  on key "D" do toggleDebug
}
```

---

## Input Binding Syntax

```jes
on key "KEY_NAME" do actionName
on key "KEY_NAME" do actionName { key1: value1 key2: value2 }
```

The binding fires **once per key press** (not continuously while held). The action name is matched against:

1. **Built-in actions** handled by the runtime automatically
2. **Registered call handlers** via `registerCall()`
3. **The global action handler** via `setActionHandler()`

### Key Names

Key names match the platform input system. Common keys:

| Key | Name |
|-----|------|
| Letters | `A`–`Z` |
| Numbers | `0`–`9` |
| Arrow keys | `UP`, `DOWN`, `LEFT`, `RIGHT` |
| Function keys | `F1`–`F12` |
| Special | `SPACE`, `ENTER`, `ESCAPE`, `TAB`, `BACKSPACE`, `DELETE` |
| Modifiers | `SHIFT`, `CONTROL`, `ALT` |

---

## Built-In Actions

These are handled automatically with no Java code needed:

| Action | Behavior |
|--------|----------|
| `toggleDebug` | Toggles debug visualization overlay |
| `interact` | Triggers NPC interaction (if player is near a `dialogueId` entity) |

---

## Java Call Handlers

For custom actions, register Java handlers with `registerCall()`:

```java
JesScene2D scene = JesLoader.load(inputStream);

int[] count = {0};

scene.registerCall("countUp", props -> {
    count[0]++;
    scene.invokeCall("setLabelText", Map.of(
        "target", "counter_label",
        "text", "Press SPACE to count: " + count[0]
    ));
});

scene.registerCall("resetCount", props -> {
    count[0] = 0;
    scene.invokeCall("setLabelText", Map.of(
        "target", "counter_label",
        "text", "Press SPACE to count: 0"
    ));
});
```

### `registerCall(name, handler)`

```java
scene.registerCall("handlerName", props -> {
    // props is Map<String, Object>
    String value = (String) props.get("key");
    // ... custom logic ...
});
```

- The handler receives a `Map<String, Object>` of properties
- Properties come from the binding declaration or from `invokeCall()`
- Multiple handlers can be registered; each name maps to one handler

### `invokeCall(name, props)`

Triggers a call handler programmatically:

```java
scene.invokeCall("setLabelText", Map.of(
    "target", "counter_label",
    "text", "New text"
));
```

This can trigger both built-in and custom handlers.

### `setActionHandler(handler)`

A **fallback handler** for actions that aren't matched by built-in or registered handlers:

```java
scene.setActionHandler((action, props) -> {
    switch (action) {
        case "openInventory" -> showInventoryScreen();
        case "openMap" -> toggleWorldMap();
        case "castSpell" -> handleSpellCast(props);
    }
});
```

---

## Built-In Call Handlers

These call names are handled automatically by `JesScene2D`:

| Handler | Props | Description |
|---------|-------|-------------|
| `setLabelText` | `target`, `text` | Update a `Label2D` entity's displayed text |
| `removeEntity` | `target` | Remove an entity from the scene |
| `resetBalls` | — | Reset all entities to their spawn positions |
| `warpMap` | `toTileX`, `toTileY`, `mapName` | Teleport player to tile position |
| `giveItem` | `target`, `item`, `count` | Add items to an entity's inventory |
| `takeItem` | `target`, `item`, `count` | Remove items from inventory |
| `equipItem` | `target`, `item`, `slot` | Equip an item |
| `unequipItem` | `target`, `slot` | Remove equipment |
| `attack` | `target`, `source`, `amount` | Apply damage |
| `useItem` | `target`, `item` | Use a consumable item |

---

## Passing Properties from Bindings

Bindings can include inline properties:

```jes
on key "Q" do castSpell { spellId: "fireball" damage: 25 }
on key "1" do selectSlot { slot: 1 }
on key "2" do selectSlot { slot: 2 }
```

These properties arrive in the handler's `props` map:

```java
scene.registerCall("castSpell", props -> {
    String spellId = (String) props.get("spellId");
    double damage = ((Number) props.get("damage")).doubleValue();
    // ...
});

scene.registerCall("selectSlot", props -> {
    int slot = ((Number) props.get("slot")).intValue();
    // ...
});
```

---

## Finding Entities at Runtime

```java
// Find by name
Entity2D hero = scene.find("hero");

// List all entity names
Set<String> names = scene.names();

// Export all named entities (snapshot copy)
Map<String, Entity2D> all = scene.exportNamed();
```

---

## Full Example: Interactive Counter with HUD

```jes
scene "CounterGame" {
  entity "bg" {
    component Panel2D {
      x: 0
      y: 0
      w: 800
      h: 600
      fill: rgb(0.05, 0.05, 0.1, 1)
    }
  }

  entity "score_display" {
    component Label2D {
      text: "Score: 0"
      x: 320
      y: 200
      size: 36
      bold: true
      color: rgb(0.4, 0.8, 1.0, 1)
    }
  }

  entity "high_score" {
    component Label2D {
      text: "Best: 0"
      x: 340
      y: 260
      size: 16
      color: rgb(0.6, 0.6, 0.6, 1)
    }
  }

  entity "combo_display" {
    component Label2D {
      text: ""
      x: 340
      y: 300
      size: 14
      color: rgb(1, 0.8, 0.2, 1)
    }
  }

  entity "instructions" {
    component Label2D {
      text: "[SPACE] Score  |  [R] Reset  |  [D] Debug"
      x: 180
      y: 530
      size: 12
      color: rgb(0.4, 0.4, 0.4, 1)
    }
  }

  on key "SPACE" do addScore
  on key "R" do resetScore
  on key "D" do toggleDebug
}
```

```java
JesScene2D scene = JesLoader.load(stream);

int[] score = {0};
int[] best = {0};
int[] combo = {0};
long[] lastPress = {0};

scene.registerCall("addScore", props -> {
    long now = System.currentTimeMillis();
    // Combo if pressed within 500ms
    if (now - lastPress[0] < 500) {
        combo[0]++;
    } else {
        combo[0] = 1;
    }
    lastPress[0] = now;

    int points = 10 * combo[0];
    score[0] += points;
    if (score[0] > best[0]) best[0] = score[0];

    scene.invokeCall("setLabelText", Map.of("target", "score_display", "text", "Score: " + score[0]));
    scene.invokeCall("setLabelText", Map.of("target", "high_score", "text", "Best: " + best[0]));
    scene.invokeCall("setLabelText", Map.of("target", "combo_display",
        "text", combo[0] > 1 ? "Combo x" + combo[0] + " (+" + points + ")" : ""));
});

scene.registerCall("resetScore", props -> {
    score[0] = 0;
    combo[0] = 0;
    scene.invokeCall("setLabelText", Map.of("target", "score_display", "text", "Score: 0"));
    scene.invokeCall("setLabelText", Map.of("target", "combo_display", "text", ""));
});
```

---

## Key Takeaways

1. `on key "KEY" do action` binds keyboard input to named actions
2. Built-in actions (`toggleDebug`, `interact`) need no Java code
3. Custom actions use `registerCall()` with a `Consumer<Map<String, Object>>`
4. `invokeCall()` can trigger any handler (built-in or custom) from Java
5. `setActionHandler()` is the fallback for unregistered action names
6. `scene.find("name")` retrieves entities by name for runtime manipulation
7. Bindings can pass inline properties: `on key "Q" do cast { spell: "fire" }`

---

## Next

- [Parallel Animation and Camera](05-parallel-and-camera.md) — simultaneous actions and camera control
- [Back to Index](../jes-by-example.md)

# JES VN Bridge & Java Hooks

Complete reference for integrating JES scenes with VNS scripts and Java code — call handlers, the VN bridge, scene return, and runtime extensibility.

Runtime: `scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`
Bridge: `runtime/src/main/java/com/jvn/runtime/JesVnBridge.java`

---

## Call Handlers

Call handlers are the primary extensibility mechanism in JES. They are named functions invoked by timeline `call` actions, input bindings, triggers, physics events, AI callbacks, and UI widgets.

### Registering from Java

```java
scene.registerCall("handlerName", props -> {
    // props is Map<String, Object>
    String value = (String) props.get("key");
    double number = ((Number) props.get("amount")).doubleValue();
    // ... custom logic ...
});
```

### Invoking from Timeline

```jes
call "handlerName" { key1: value1 key2: value2 }
```

### Invoking from Java

```java
scene.invokeCall("handlerName", Map.of("key", "value"));
```

### Built-in Call Handlers

| Handler | Description |
|---------|-------------|
| `warpMap` | Teleport player to tile/position |
| `useItem` | Use an inventory consumable |
| `giveItem` | Add item(s) to entity inventory |
| `takeItem` | Remove item(s) from entity inventory |
| `equipItem` | Equip an item to a slot |
| `unequipItem` | Remove equipment from a slot |
| `attack` | Apply damage between entities |
| `setLabelText` | Update a Label2D's text |
| `removeEntity` | Remove an entity from the scene |
| `resetBalls` | Reset all entities to spawn positions |
| `interactNpc` | NPC interaction (from `interact` action) |
| `handlePocket` | Pool-style pocket collision |

---

## Action Handler

For input binding actions that aren't handled by built-in actions, register a global action handler:

```java
scene.setActionHandler((action, props) -> {
    switch (action) {
        case "openInventory" -> showInventoryScreen();
        case "castSpell" -> handleSpellCast(props);
        case "openMap" -> toggleWorldMap();
    }
});
```

Input bindings forward to this handler when the action name doesn't match a built-in:

```jes
on key "I" do openInventory
on key "M" do openMap
on key "Q" do castSpell { spellId: "fireball" }
```

---

## Audio Callback

Register audio playback handling:

```java
scene.setAudioCallback((path, volume, loop, isBgm) -> {
    audioEngine.play(path, volume, loop, isBgm);
});
```

Called by `playAudio` / `stopAudio` timeline actions.

---

## VN Bridge

The VN bridge enables bidirectional communication between VNS scripts and JES scenes.

### Launching JES from VNS

```vns
[jes push game/minigames/puzzle.jes label after_puzzle]
[jes push game/minigames/arena.jes label after_arena with difficulty=hard round=3]
```

- `push` — launches JES scene, preserving VN state on a stack
- `label` — VNS label to jump to when JES returns
- `with k=v` — properties passed as launch parameters

### Returning from JES to VNS

Inside a JES scene, use `call "return"` to pop back to VNS:

```jes
call "return" { label: "after_game" score: 1500 rank: "S" }
```

**Return behavior:**
1. Pops the JES scene from the scene stack
2. Copies all props (except `label` and `goto`) into VN variables
3. Jumps to the specified VNS label

`call "vns"` is an alias for `call "return"`:

```jes
call "vns" { label: "after_boss" victory: true }
```

### End-to-End Example

**VNS script:**

```vns
@scenario adventure
@character narrator "Narrator"
@var score = 0

@label start
narrator: Time to prove yourself in the arena!

[jes push game/minigames/arena.jes label arena_result with difficulty=normal]

@label arena_result
narrator: You scored ${score} points! Rank: ${rank}.

[if score >= 1000 goto great_result]
[jump okay_result]

@label great_result
narrator: Incredible performance!
[flag arena_champion]
[end]

@label okay_result
narrator: Not bad. Try again sometime.
[end]
```

**JES scene (arena.jes):**

```jes
scene "Arena" {
  entity "hero" {
    component Sprite2D {
      image: "assets/characters/hero.png"
      x: 400
      y: 300
      w: 64
      h: 64
    }
  }

  entity "score_label" {
    component Label2D {
      text: "Score: 0"
      x: 10
      y: 10
      size: 18
      bold: true
      color: rgb(1, 1, 1, 1)
    }
  }

  on key "SPACE" do attack { target: "enemy" }
  on key "ESCAPE" do endArena

  timeline {
    // Setup phase
    playAudio "assets/audio/bgm/arena.ogg" { volume: 0.7 loop: true bgm: true }
    wait 1000
    call "spawnWave" { count: 3 }
  }
}
```

**Java hook for ending the arena:**

```java
scene.registerCall("endArena", props -> {
    double score = scene.getVar("score");
    String rank = score >= 1000 ? "S" : score >= 500 ? "A" : "B";
    
    // Return to VNS with results
    Map<String, Object> returnProps = new HashMap<>();
    returnProps.put("label", "arena_result");
    returnProps.put("score", score);
    returnProps.put("rank", rank);
    scene.invokeCall("return", returnProps);
});
```

### Launch Properties

Properties passed with `with k=v` in the VNS `[jes push]` command are available as an `init` call:

```java
scene.registerCall("init", props -> {
    String difficulty = (String) props.getOrDefault("difficulty", "normal");
    int round = ((Number) props.getOrDefault("round", 1)).intValue();
    setupDifficulty(difficulty, round);
});
```

Or access them as script variables:

```java
double difficulty = scene.getVar("difficulty"); // from launch props
```

---

## Script Variables

JES scenes maintain a script variable map for runtime state:

```java
// Set a variable
scene.setVar("score", 100.0);

// Get a variable
double score = scene.getVar("score"); // returns 0.0 if not set

// Increment a variable
scene.incrementVar("score", 10.0);
```

Variables can be passed to VNS on return via the `call "return"` mechanism.

---

## Scene Stack Operations

### From VNS

```vns
[jes push game/scenes/minigame.jes label after]     # push onto stack
[jes replace game/scenes/boss.jes]                    # replace current
[jes pop]                                              # pop back to previous
[jes call spawnWave count=5]                          # call handler on current JES scene
```

### From Java

```java
// Push a new JES scene
runtimeInterop.pushJesScene("game/scenes/minigame.jes", "after_label", props);

// Pop back to VNS
runtimeInterop.popJesScene();
```

---

## Merged Scene Loading

Load multiple JES files into a single scene (useful for shared content):

```java
JesScene2D scene = JesLoader.loadMerged(List.of(
    getResource("shared/common_items.jes"),    // shared item database
    getResource("shared/common_hud.jes"),      // shared HUD entities
    getResource("levels/dungeon_floor1.jes")   // level-specific content
));
```

All items, entities, bindings, and timeline actions are merged in order.

---

## Practical Patterns

### Pattern: Minigame with Difficulty Scaling

```vns
# VNS side
[set minigame_difficulty "hard"]
[jes push game/minigames/target.jes label target_done with difficulty=hard timeLimit=30]

@label target_done
narrator: Score: ${score}. Time bonus: ${timeBonus}.
```

```java
// JES side: init handler
scene.registerCall("init", props -> {
    String diff = (String) props.getOrDefault("difficulty", "normal");
    double timeLimit = ((Number) props.getOrDefault("timeLimit", 60)).doubleValue();
    
    int targetCount = switch (diff) {
        case "easy" -> 5;
        case "hard" -> 15;
        default -> 10;
    };
    spawnTargets(targetCount);
    startTimer(timeLimit);
});
```

### Pattern: NPC Dialogue Trigger

```jes
// In the JES scene
on key "SPACE" do interact
```

```java
// Java hook
scene.registerCall("interactNpc", props -> {
    String npc = (String) props.get("npc");
    String dialogueId = (String) props.get("dialogueId");
    
    if (dialogueId != null) {
        // Switch to VNS dialogue
        Map<String, Object> returnProps = Map.of(
            "label", dialogueId,
            "npc", npc
        );
        scene.invokeCall("vns", returnProps);
    }
});
```

### Pattern: Event-Driven Timeline

```jes
timeline {
  // Wait for player to reach a location
  waitForCall "reachedTreasure"
  
  // Play treasure opening animation
  playAudio "assets/audio/sfx/chest_open.ogg"
  fade "chest_lid" { alpha: 0 dur: 300 }
  emitParticles "sparkles" { count: 30 }
  
  // Wait for player to collect
  waitForCall "collectedTreasure"
  
  // Return to VNS
  call "return" { label: "after_treasure" foundItem: "legendary_sword" }
}
```

---

## Related Docs

- [JES Overview](../overview/jes-scripting.md)
- [Timeline & Actions](../timeline/jes-timeline.md) — `call`, `waitForCall`
- [Input Bindings](../systems/jes-input.md) — action handlers
- [Scenes & Entities](../scene/jes-scenes-entities.md) — scene lifecycle
- [VNS Interop & Integration](../../vns/integration/vns-interop.md) — VNS side of the bridge

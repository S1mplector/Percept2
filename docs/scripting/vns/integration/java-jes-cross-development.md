# Java + JES + VNS Cross Development

Complete guide to hybrid game architecture in JVN — how narrative scripts (VNS), 2D scene scripts (JES), and Java code interact, pass data, and share control flow.

Bridge source:
- `runtime/src/main/java/com/jvn/runtime/RuntimeVnInterop.java`
- `runtime/src/main/java/com/jvn/runtime/JesVnBridge.java`

---

## Overview

JVN's three layers each serve a distinct purpose:

| Layer | Language | Purpose | Iteration Speed |
|-------|---------|---------|----------------|
| **VNS** | VNS script | Branching story, dialogue pacing, menu transitions | Fastest — edit text, re-run |
| **JES** | JES script | 2D scene composition, timelines, lightweight gameplay | Fast — edit text, re-run |
| **Java** | Java | Domain systems, advanced mechanics, platform integration | Slower — compile required |

The key principle: **put content where it can be iterated fastest**. Story belongs in VNS. Scene choreography belongs in JES. Complex game mechanics belong in Java.

---

## Integration Paths

### Path 1: VNS → JES (Push/Replace Scene)

Launch a JES scene from a VNS script, optionally passing parameters and specifying a return label:

```vns
# Push a JES scene onto the scene stack (VN scene stays underneath)
[jes push game/minigames/arena.jes label after_arena with difficulty=hard round=2]

# Replace the current scene entirely
[jes replace game/cutscenes/intro.jes]

# Call a handler in the active JES scene
[jes call resetWave wave=3]

# Pop the JES scene (return to VN)
[jes pop]
```

#### Data flow: VNS → JES

```text
VNS script
  │
  │  [jes push arena.jes label after with difficulty=hard round=2]
  │
  ▼
RuntimeVnInterop
  │  1. Loads arena.jes
  │  2. Creates JesScene2D
  │  3. Sets return label = "after"
  │  4. Pushes scene onto Engine scene stack
  │  5. Fires call "init" { difficulty: "hard", round: "2" }
  │
  ▼
JES scene receives init call
  │  scene.registerCall("init", props -> {
  │      String difficulty = props.get("difficulty");
  │      int round = Integer.parseInt(props.get("round"));
  │  });
```

#### Push vs. Replace

| Command | Behavior | Use When |
|---------|----------|----------|
| `jes push` | VN scene stays on stack, JES scene goes on top | Minigames that return results |
| `jes replace` | VN scene is removed, JES scene takes over | Permanent scene transitions |

---

### Path 2: JES → VNS (Return with Payload)

When a JES scene completes, it returns data to the VNS script:

```jes
// In the JES scene, when the minigame ends:
call "return" { label: "after_arena" score: 1200 rank: "A" timeMs: 45000 }
```

#### Data flow: JES → VNS

```text
JES scene
  │
  │  call "return" { label: "after_arena" score: 1200 rank: "A" }
  │
  ▼
JesVnBridge
  │  1. Pops JES scene from stack
  │  2. Copies all props (except "label"/"goto") into VN variables:
  │     - $score = "1200"
  │     - $rank = "A"
  │  3. Resumes VN scene
  │  4. Jumps to return label "after_arena"
  │
  ▼
VNS script continues at @label after_arena
  │  narrator: You scored ${score} with rank ${rank}!
```

**Important:** All JES return values become VNS string variables. Use VNS condition expressions for numeric comparisons:

```vns
@label after_arena
narrator: Score: ${score}, Rank: ${rank}
[if score >= 1000 goto victory]
[jump defeat]
```

#### Aliases

`call "vns" { ... }` works identically to `call "return" { ... }`.

---

### Path 3: VNS → Java (Reflection Call)

Call a static Java method directly from VNS:

```vns
[java com.example.GameHooks#beginEncounter goblin 3]
[java com.example.Analytics#trackEvent chapter_complete ch1]
```

#### Java side:

```java
package com.example;

public class GameHooks {
    /**
     * Called from VNS via [java com.example.GameHooks#beginEncounter <enemy> <count>]
     * @param args string arguments from the VNS command
     */
    public static void beginEncounter(String... args) {
        String enemyType = args.length > 0 ? args[0] : "slime";
        int count = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        // ... spawn enemies, set up combat ...
    }
}
```

#### Best Practices for Java Interop

- **Keep method signatures stable** — VNS scripts call by exact `Class#method` path
- **Accept `String...` args** — all VNS values are strings
- **Handle missing args gracefully** — provide defaults
- **Avoid side effects on the UI thread** — the call runs synchronously
- **Document the contract** — script authors need to know expected args

---

### Path 4: JES → Java (Call Handlers)

Register Java handlers that JES `call` actions can invoke:

```java
JesScene2D scene = JesLoader.load(inputStream);

// Named handler — called by `call "spawnWave" { count: 5 }`
scene.registerCall("spawnWave", props -> {
    int count = Integer.parseInt(props.getOrDefault("count", "3"));
    double speed = Double.parseDouble(props.getOrDefault("speed", "100"));
    for (int i = 0; i < count; i++) {
        spawnEnemy(speed);
    }
});

// Fallback handler — catches any unregistered call name
scene.setActionHandler((name, props) -> {
    switch (name) {
        case "gameOver" -> showGameOverScreen(props);
        case "checkpoint" -> saveCheckpoint(props);
        default -> LOG.warn("Unknown call: {}", name);
    }
});
```

JES side:

```jes
timeline {
    wait 2000
    call "spawnWave" { count: 5 speed: 120 }
    wait 5000
    call "spawnWave" { count: 8 speed: 150 }
    waitForCall { name: "allDefeated" }
    call "gameOver" { result: "win" score: 1500 }
}
```

---

### Path 5: Java → VNS (Scene Launch)

Java code can push a VNS scene onto the engine:

```java
VnScenario scenario = new VnScenarioLoader().load("scripts/story/prologue.vns");
VnScene vnScene = new VnScene(scenario);
vnScene.setAudioFacade(audio);
vnScene.setInterop(engine.getVnInteropFactory().create(engine));

// Apply current settings
VnSettings s = vnScene.getState().getSettings();
s.setTextSpeed(settings.getTextSpeed());
s.setBgmVolume(settings.getBgmVolume());

engine.scenes().push(vnScene);
```

---

### Path 6: Java → JES (Scene Load)

Java code can create and manage JES scenes:

```java
InputStream in = getClass().getResourceAsStream("/game/scenes/demo.jes");
JesScene2D scene = JesLoader.load(in);

// Register handlers before pushing
scene.registerCall("onComplete", props -> {
    engine.scenes().pop();
});

engine.scenes().push(scene);
```

---

## Complete End-to-End Example

### VNS Script (story with minigame)

```vns
@scenario arcade_story
@character narrator "Narrator"
@character hero "Yuki"

@var high_score = 0
@var last_score = 0
@var last_rank = ""

@label start
[bg arcade]
[bgm assets/audio/bgm/arcade.ogg]
narrator: Welcome to the arcade!

@label hub
hero: What should I play?

> Shooter game -> play_shooter
> Puzzle game -> play_puzzle
> Check high score -> show_score
> Leave -> leave

@label play_shooter
narrator: Loading shooter...
[jes push game/minigames/shooter.jes label shooter_done with difficulty=normal lives=3]

@label shooter_done
[set last_score ${score}]
[set last_rank ${rank}]
narrator: You scored ${last_score} (Rank ${last_rank})!
[if last_score > high_score]
  [set high_score ${last_score}]
  [sfx assets/audio/sfx/fanfare.ogg]
  narrator: {b}New high score!{/b}
[endif]
[jump hub]

@label play_puzzle
narrator: Loading puzzle...
[jes push game/minigames/puzzle.jes label puzzle_done with level=1]

@label puzzle_done
narrator: Puzzle score: ${score}
[if score > high_score]
  [set high_score ${score}]
[endif]
[jump hub]

@label show_score
narrator: Your high score is ${high_score}.
[jump hub]

@label leave
narrator: See you next time!
[end]
```

### JES Minigame (shooter.jes)

```jes
scene "Shooter" {
  entity "bg" {
    component Panel2D { x: 0 y: 0 w: 800 h: 600 fill: rgb(0.02, 0.02, 0.08, 1) }
  }

  entity "player" {
    component Sprite2D {
      image: "assets/minigames/ship.png"
      x: 370
      y: 500
      w: 60
      h: 60
    }
  }

  entity "score_label" {
    component Label2D {
      text: "Score: 0"
      x: 10
      y: 10
      size: 20
      bold: true
      color: rgb(1, 1, 1, 1)
    }
  }

  on key "LEFT" do moveLeft
  on key "RIGHT" do moveRight
  on key "SPACE" do fire

  timeline {
    playAudio "assets/audio/bgm/battle.ogg" { volume: 0.5 loop: true bgm: true }
    call "spawnWave" { count: 5 speed: 80 }
    waitForCall { name: "waveCleared" }
    call "spawnWave" { count: 8 speed: 120 }
    waitForCall { name: "waveCleared" }
    // Game over — return to VNS
    call "return" { label: "shooter_done" score: 1500 rank: "A" }
  }
}
```

### Java Handler (registered in RuntimeVnInterop)

```java
scene.registerCall("spawnWave", props -> {
    int count = Integer.parseInt(props.getOrDefault("count", "5"));
    double speed = Double.parseDouble(props.getOrDefault("speed", "100"));
    waveManager.spawnWave(count, speed, () -> {
        scene.fireCall("waveCleared", Map.of());
    });
});
```

---

## Hybrid Scene Strategy

For complex gameplay, use Java `Scene` classes with JES for presentation:

```java
public class BattleScene implements Scene {
    private JesScene2D hudOverlay;
    private BattleSystem battle;

    @Override
    public void init(Engine engine) {
        // Java handles game logic
        battle = new BattleSystem(engine);

        // JES handles HUD and effects
        hudOverlay = JesLoader.load(getClass().getResourceAsStream("/game/ui/battle_hud.jes"));
        hudOverlay.registerCall("useItem", props -> battle.useItem(props.get("id")));
        hudOverlay.registerCall("attack", props -> battle.attack(props.get("target")));
    }

    @Override
    public void update(double dt) {
        battle.update(dt);
        hudOverlay.update(dt);

        // Push data from Java to JES
        hudOverlay.findEntity("hp_text", Label2D.class)
            .ifPresent(l -> l.setText("HP: " + battle.getPlayerHp()));
    }
}
```

This keeps:
- **Heavy logic in strongly-typed Java** — combat math, AI, inventory management
- **Iterative content in editable scripts** — HUD layout, effects, dialogue triggers

---

## Data Type Mapping

| VNS/JES Type | Java Arrival Type | Notes |
|-------------|------------------|-------|
| String | `String` | Direct mapping |
| Number | `String` | Parse with `Integer.parseInt()` or `Double.parseDouble()` |
| Boolean | `String` | `"true"` / `"false"`, compare with `Boolean.parseBoolean()` |
| Color | `double[4]` | `rgb()` values in JES; rarely passed across boundaries |

All cross-boundary data is string-typed. Always validate and parse on the receiving side.

---

## Error Handling

### Missing JES script

```vns
[jes push game/nonexistent.jes label fallback]
```

If the script isn't found, the interop logs a warning and doesn't push. Execution continues at the next VNS line.

### Missing return label

If the JES `call "return"` specifies a label that doesn't exist in the VNS scenario, the bridge logs a warning and resumes at the VNS scene's current position.

### Missing Java method

```vns
[java com.example.Missing#method arg1]
```

If the class or method isn't found via reflection, a warning is logged. Execution continues.

### Unregistered call handler in JES

If a `call "name"` in JES doesn't have a registered handler and no fallback `actionHandler` is set, the call is silently ignored.

---

## Team Conventions (Recommended)

1. **Prefix return props clearly** — use descriptive names: `score`, `timeMs`, `result`, `rank`
2. **One canonical return label per call site** — `label after_shooter`, `label after_puzzle`
3. **Document Java interop contracts** — each `[java ...]` target should have Javadoc explaining expected args
4. **Validate all incoming props** — never assume a prop exists or has a valid numeric value
5. **Keep call handler names stable** — JES scripts reference them by string
6. **Add parser/lint checks in CI** — catch broken cross-references before release
7. **Use `with` params for configuration, return props for results** — clean separation of input vs. output

---

## Related Docs

- [VNS Interop Commands](vns-interop.md)
- [JES Bridge & Java Hooks](../../jes/integration/jes-bridge.md)
- [JES Scenes & Entities](../../jes/scene/jes-scenes-entities.md)
- [Runtime Interop Guide](../../../runtime/core/interop.md)
- [VNS Flow Control](../flow/vns-flow-control.md)
- [Cookbook: Minigame Integration](../../../guides/cookbook.md)

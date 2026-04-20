# JES ↔ VNS Integration Cookbook

Complete, copy-pasteable code examples for every integration direction between VNS scripts, JES scenes, and Java code. Each recipe includes **all three layers** so you can see how data flows end to end.

Source reference:
- `runtime/src/main/java/com/jvn/runtime/RuntimeVnInterop.java`
- `runtime/src/main/java/com/jvn/runtime/JesVnBridge.java`
- `runtime/src/main/java/com/jvn/runtime/BridgedVnScene.java`
- `scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`
- `core/src/main/java/com/jvn/core/vn/DefaultVnInterop.java`

---

## Quick Reference

| Direction | Trigger | Mechanism |
|-----------|---------|-----------|
| VNS → JES | `[jes push file.jes]` | Scene stack push, `init` call handler |
| JES → VNS | `call "return" { ... }` | Scene stack pop, variables injected |
| JES → VNS (dialogue) | `call "startVns" { ... }` | JesVnBridge, BridgedVnScene |
| VNS → Java | `[java Class#method args]` | Reflection |
| JES → Java | `call "handler"` | registerCall / setActionHandler |
| Java → JES | `JesLoader.load()` + `engine.push()` | Direct API |
| Java → VNS | `VnScenarioLoader` + `engine.push()` | Direct API |
| VNS ↔ JES (animation) | `timeline { ... }` | Inline timeline / TimelineRunner |

---

## Recipe 1: VNS Launches a JES Minigame

The most common pattern — a VNS story launches a JES gameplay scene, gets results back, and continues the narrative.

### VNS Script (`scripts/story/chapter1.vns`)

```vns
@scenario Chapter1
@character narrator ""
@character hero "Yuki"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero happy assets/characters/yuki/happy.png

@background arcade assets/backgrounds/arcade.png

@var best_score = 0

@label start
[bg arcade]
[show hero center neutral]
hero: Time to set a new high score!

# Push a JES scene — VNS pauses, JES takes over
# "label arcade_done" — where VNS resumes after JES returns
# "with difficulty=hard lives=3" — launch parameters sent to JES
[jes push game/minigames/shooter.jes label arcade_done with difficulty=hard lives=3]

# ── VNS resumes here when JES calls "return" ──
@label arcade_done
# JES return props are now VNS variables: ${score}, ${rank}, ${time_ms}
[show hero center happy]
hero: I scored ${score} points! Rank: ${rank}!

[if score > best_score]
  [set best_score score]
  [hud New High Score!]
[endif]

narrator: Time spent: ${time_ms}ms
[end]
```

### JES Scene (`game/minigames/shooter.jes`)

```jes
scene "Shooter" {
  entity "bg" {
    component Panel2D { x: 0 y: 0 w: 800 h: 600 fill: rgb(0.02, 0.02, 0.08, 1) }
  }

  entity "player" {
    component Sprite2D {
      image: "assets/minigames/ship.png"
      x: 370 y: 500 w: 60 h: 60
    }
  }

  entity "score_label" {
    component Label2D {
      text: "Score: 0"
      x: 10 y: 10 size: 20 bold: true
      color: rgb(1, 1, 1, 1)
    }
  }

  entity "lives_label" {
    component Label2D {
      text: "Lives: 3"
      x: 680 y: 10 size: 20 bold: true
      color: rgb(1, 0.3, 0.3, 1)
    }
  }

  on key "LEFT" do moveLeft
  on key "RIGHT" do moveRight
  on key "SPACE" do fire
  on key "ESCAPE" do endGame

  timeline {
    playAudio "assets/audio/bgm/arcade.ogg" { volume: 0.6 loop: true bgm: true }
    wait 1000
    call "spawnWave" { count: 5 speed: 80 }
    waitForCall { name: "waveCleared" }
    wait 500
    call "spawnWave" { count: 8 speed: 120 }
    waitForCall { name: "waveCleared" }
    wait 500
    call "spawnWave" { count: 12 speed: 160 }
    waitForCall { name: "allDone" }
  }
}
```

### Java Glue

```java
// Register handlers before the scene is pushed
// (RuntimeVnInterop does this automatically for "init" and "return",
//  but you register game-specific handlers from Java)

scene.registerCall("init", props -> {
    // Receive launch params from VNS [jes push ... with difficulty=hard lives=3]
    String difficulty = (String) props.getOrDefault("difficulty", "normal");
    int lives = Integer.parseInt(String.valueOf(props.getOrDefault("lives", "3")));
    
    setupDifficulty(difficulty);
    scene.setVar("lives", lives);
    scene.setVar("score", 0);
});

scene.registerCall("spawnWave", props -> {
    int count = Integer.parseInt(String.valueOf(props.getOrDefault("count", "5")));
    double speed = Double.parseDouble(String.valueOf(props.getOrDefault("speed", "100")));
    waveManager.spawn(count, speed, () -> {
        // When all enemies in this wave are defeated:
        scene.invokeCall("waveCleared", Map.of());
    });
});

scene.registerCall("endGame", props -> {
    double score = scene.getVar("score");
    long timeMs = System.currentTimeMillis() - startTime;
    String rank = score >= 1000 ? "S" : score >= 500 ? "A" : "B";
    
    // Return to VNS — this pops JES, injects variables, jumps to label
    scene.invokeCall("return", Map.of(
        "score", String.valueOf((int) score),
        "rank", rank,
        "time_ms", String.valueOf(timeMs)
    ));
});
```

### What Happens at Runtime

```text
1. VNS hits [jes push shooter.jes label arcade_done with difficulty=hard lives=3]
2. RuntimeVnInterop loads shooter.jes → creates JesScene2D
3. Registers "return" handler (pops JES, copies props to VNS vars, jumps to label)
4. Pushes JES scene onto Engine scene stack
5. Fires "init" call with { difficulty: "hard", lives: "3" }
6. VNS pauses (no update() calls)
7. JES runs gameplay...
8. Player finishes → "endGame" handler fires → invokeCall("return", {...})
9. "return" handler: pops JES, sets VNS vars (score, rank, time_ms), jumps to @label arcade_done
10. VNS resumes with ${score}, ${rank}, ${time_ms} available
```

---

## Recipe 2: JES Triggers VNS Dialogue

A JES gameplay scene launches a VNS dialogue segment when the player interacts with an NPC, then resumes gameplay after the dialogue ends.

### JES Scene (`game/scenes/town.jes`)

```jes
scene "Town" {
  entity "player" {
    component Character2D {
      spriteSheet: "assets/characters/hero_walk.png"
      x: 400 y: 300 w: 32 h: 48
      frameW: 32 frameH: 48
      animations: {
        idle: { row: 0 frames: 1 speed: 0 }
        walk_down: { row: 0 frames: 4 speed: 8 }
        walk_up: { row: 3 frames: 4 speed: 8 }
      }
      currentAnim: "idle"
    }
  }

  entity "npc_elder" {
    component Sprite2D {
      image: "assets/npcs/elder.png"
      x: 500 y: 200 w: 32 h: 48
    }
  }

  on key "ARROW_UP" do moveUp
  on key "ARROW_DOWN" do moveDown
  on key "ARROW_LEFT" do moveLeft
  on key "ARROW_RIGHT" do moveRight
  on key "SPACE" do interact
}
```

### Java — NPC Interaction Handler

```java
scene.registerCall("interactNpc", props -> {
    String npcId = (String) props.get("npc");
    
    switch (npcId) {
        case "npc_elder" -> {
            // Launch VNS dialogue — JES pauses, VNS takes over
            jesVnBridge.startVns(
                "scripts/dialogue/elder.vns",  // VNS script to load
                "greeting",                     // label to jump to
                Map.of(                         // properties passed to VNS
                    "npc", "elder",
                    "player_level", String.valueOf(playerLevel)
                ),
                true    // push (not replace) — JES stays on stack
            );
        }
        case "npc_merchant" -> {
            jesVnBridge.startVns(
                "scripts/dialogue/merchant.vns",
                "shop",
                Map.of("gold", String.valueOf(playerGold)),
                true
            );
        }
    }
});

// When VNS dialogue ends, this fires automatically
scene.registerCall("vnsEnded", props -> {
    // VNS is done — JES resumes
    // Check if VNS set any variables we care about
    String result = (String) props.getOrDefault("result", "");
    if ("bought_item".equals(result)) {
        playerGold -= Integer.parseInt((String) props.getOrDefault("cost", "0"));
    }
});
```

### VNS Script (`scripts/dialogue/elder.vns`)

```vns
@scenario elder_dialogue
@character narrator ""
@character elder "Elder"

@charimg elder neutral assets/characters/elder/neutral.png
@charimg elder wise assets/characters/elder/wise.png

@background none

@label greeting
[show elder center neutral]
elder: Ah, young adventurer. What brings you here?

> Ask about the forest
  [show elder center wise]
  elder: The forest to the north is dangerous.
  elder: You'll need at least level 5 to survive.
  [if player_level < 5]
    elder: You're only level ${player_level}. Train more first.
  [else]
    elder: At level ${player_level}, you should be fine.
    [flag elder_approved_forest]
  [endif]
  [jump farewell]

> Ask about the town
  elder: This town has stood for centuries.
  elder: We've weathered many storms.
  [jump farewell]

> Leave
  [jump farewell]

@label farewell
elder: Safe travels, young one.
[hide elder]
[end]
# [end] triggers BridgedVnScene.onExit → JES resumes + "vnsEnded" fires
```

### What Happens at Runtime

```text
1. Player walks near NPC, presses SPACE → "interact" action
2. JES detects NPC collision → fires "interactNpc" handler
3. Java calls jesVnBridge.startVns("elder.vns", "greeting", props, true)
4. JesVnBridge:
   a. Loads VnScenario from elder.vns
   b. Creates BridgedVnScene (extends VnScene)
   c. Sets onExit: resume JES + fire "vnsEnded"
   d. Injects launch props as VNS variables (player_level, npc)
   e. Jumps to @label greeting
   f. Pushes BridgedVnScene onto Engine stack
   g. Pauses JES scene
5. VNS dialogue plays normally...
6. Player reaches [end]
7. BridgedVnScene.onExit fires:
   a. Pops VNS from stack
   b. Resumes JES (setPaused(false))
   c. Fires "vnsEnded" call on JES scene
8. JES gameplay continues
```

---

## Recipe 3: Bidirectional Hub World

A JES overworld where the player can enter VNS story segments and return, with state carried across both directions.

### VNS Hub Entry (`scripts/story/hub.vns`)

```vns
@scenario Hub
@character narrator ""
@character hero "Yuki"

@charimg hero neutral assets/characters/yuki/neutral.png

@background town assets/backgrounds/town.png

@var gold = 500
@var level = 1
@var quests_done = 0

@label start
[bg town]
[show hero center neutral]
narrator: Welcome to the hub town.

> Explore the world
  # Launch JES overworld — hero moves around, talks to NPCs, fights
  [jes push game/overworld/town.jes label returned_from_world with gold=${gold} level=${level}]

> Visit the shop
  [gosub shop_scene]
  [jump start]

> Save
  [save]
  [hud Saved!]
  [jump start]

> Quit
  [end]

# ── JES returns here with updated state ──
@label returned_from_world
# JES set these vars on return:
narrator: Back in town. Gold: ${gold}, Level: ${level}
[if quest_completed == "true"]
  [inc quests_done]
  narrator: Quest complete! Total: ${quests_done}
[endif]
[jump start]

@label shop_scene
narrator: The shop is under construction.
[return]
```

### JES Overworld (`game/overworld/town.jes`)

```jes
scene "TownOverworld" {
  entity "player" {
    component Character2D {
      spriteSheet: "assets/characters/hero_overworld.png"
      x: 400 y: 300 w: 32 h: 48
      frameW: 32 frameH: 48
      animations: {
        idle: { row: 0 frames: 1 speed: 0 }
        walk_down: { row: 0 frames: 4 speed: 8 }
        walk_left: { row: 1 frames: 4 speed: 8 }
        walk_right: { row: 2 frames: 4 speed: 8 }
        walk_up: { row: 3 frames: 4 speed: 8 }
      }
      currentAnim: "idle"
    }
  }

  entity "gold_label" {
    component Label2D {
      text: "Gold: 0"
      x: 10 y: 10 size: 16 bold: true
      color: rgb(1, 0.85, 0, 1)
    }
  }

  entity "quest_npc" {
    component Sprite2D {
      image: "assets/npcs/quest_giver.png"
      x: 600 y: 200 w: 32 h: 48
    }
  }

  on key "ARROW_UP" do moveUp
  on key "ARROW_DOWN" do moveDown
  on key "ARROW_LEFT" do moveLeft
  on key "ARROW_RIGHT" do moveRight
  on key "SPACE" do interact
  on key "ESCAPE" do returnToHub
}
```

### Java — Overworld Bridge

```java
scene.registerCall("init", props -> {
    int gold = Integer.parseInt(String.valueOf(props.getOrDefault("gold", "0")));
    int level = Integer.parseInt(String.valueOf(props.getOrDefault("level", "1")));
    scene.setVar("gold", gold);
    scene.setVar("level", level);
    
    // Update HUD
    scene.findEntity("gold_label", Label2D.class)
        .ifPresent(l -> l.setText("Gold: " + gold));
});

scene.registerCall("interactNpc", props -> {
    String npc = (String) props.get("npc");
    if ("quest_npc".equals(npc)) {
        // Launch quest dialogue in VNS
        jesVnBridge.startVns(
            "scripts/dialogue/quest.vns",
            "quest_offer",
            Map.of(
                "gold", String.valueOf((int) scene.getVar("gold")),
                "level", String.valueOf((int) scene.getVar("level"))
            ),
            true
        );
    }
});

scene.registerCall("vnsEnded", props -> {
    // VNS dialogue finished — check if quest was accepted
    String accepted = (String) props.getOrDefault("quest_accepted", "false");
    if ("true".equals(accepted)) {
        startQuestGameplay();
    }
});

scene.registerCall("returnToHub", props -> {
    // Return to VNS hub with current state
    scene.invokeCall("return", Map.of(
        "gold", String.valueOf((int) scene.getVar("gold")),
        "level", String.valueOf((int) scene.getVar("level")),
        "quest_completed", String.valueOf(questCompleted)
    ));
});
```

---

## Recipe 4: Inline Timeline Animation in VNS

Embed JES-style animation directly in a VNS script for cinematic moments without leaving the VN scene.

### VNS Script

```vns
@scenario cutscene
@character narrator ""
@character hero "Yuki"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero determined assets/characters/yuki/determined.png

@background cliff assets/backgrounds/cliff_edge.png

@label start
[bg cliff]
[char hero global on]
[show hero far_left neutral]
narrator: The wind howled at the cliff's edge.

# Inline timeline — runs inside VNS, no JES scene needed
timeline {
  entity "hero" {
    0ms { x: 100, y: 396 }
    600ms { x: 640, y: 396, easing: ease_out_cubic }
  }
  cameraMove 600ms 0 0 0.95
  playAudio "assets/audio/sfx/footsteps_run.ogg"
}

[wait 700]
[show hero center determined]
hero: I made it!

# Another inline timeline for dramatic camera
timeline {
  cameraMove 500ms 0 -20 1.1
  playAudio "assets/audio/sfx/wind_strong.ogg"
}

[wait 600]
narrator: The view was breathtaking.

# Reset camera
timeline {
  cameraMove 400ms 0 0 1.0
}
[wait 500]

[end]
```

### When to Use Inline Timelines vs JES Push

| Approach | Best For |
|----------|----------|
| Inline `timeline {}` | Quick camera moves, character slides, audio cues **within VNS** |
| `[jes push]` | Full gameplay, physics, input handling, complex scenes |
| Puppeteer export | Pre-authored complex animations (many entities, many keyframes) |

---

## Recipe 5: Java Creates Both JES and VNS Scenes

For maximum control, Java code can create, configure, and push both scene types.

### Creating and Pushing a JES Scene

```java
public class GameLauncher {
    
    public void startMinigame(Engine engine, String difficulty) {
        // Load JES scene
        InputStream in = getClass().getResourceAsStream("/game/scenes/puzzle.jes");
        JesScene2D scene = JesLoader.load(in);
        
        // Register handlers
        scene.registerCall("init", props -> {
            setupPuzzle(difficulty);
        });
        
        scene.registerCall("puzzleSolved", props -> {
            int score = (int) scene.getVar("score");
            
            // Pop JES and push a VNS reward scene
            engine.scenes().pop();
            startRewardScene(engine, score);
        });
        
        // Set audio callback
        scene.setAudioCallback((path, volume, loop, isBgm) -> {
            engine.getAudio().play(path, volume, loop, isBgm);
        });
        
        // Push onto engine
        engine.scenes().push(scene);
        
        // Fire init
        scene.invokeCall("init", Map.of("difficulty", difficulty));
    }
    
    public void startRewardScene(Engine engine, int score) {
        // Load VNS script
        VnScenario scenario = new VnScenarioLoader().load("scripts/rewards/puzzle_complete.vns");
        VnScene vnScene = new VnScene(scenario);
        
        // Configure
        vnScene.setInterop(engine.getVnInteropFactory().create(engine));
        vnScene.setAudioFacade(engine.getAudio());
        
        // Inject variables
        vnScene.getState().getVariables().put("score", score);
        vnScene.getState().getVariables().put("rank", score >= 100 ? "S" : "A");
        
        // Push
        engine.scenes().push(vnScene);
    }
}
```

### Creating and Pushing a VNS Scene

```java
public void startDialogue(Engine engine, String scriptPath, String label,
                           Map<String, Object> variables) {
    VnScenario scenario = new VnScenarioLoader().load(scriptPath);
    VnScene vnScene = new VnScene(scenario);
    
    // Wire up interop and audio
    vnScene.setInterop(engine.getVnInteropFactory().create(engine));
    vnScene.setAudioFacade(engine.getAudio());
    
    // Apply settings from engine
    VnSettings s = vnScene.getState().getSettings();
    s.setTextSpeed(engine.getSettings().getTextSpeed());
    s.setBgmVolume(engine.getSettings().getBgmVolume());
    s.setSfxVolume(engine.getSettings().getSfxVolume());
    
    // Inject variables
    variables.forEach((k, v) -> vnScene.getState().getVariables().put(k, v));
    
    // Jump to label if specified
    if (label != null) {
        vnScene.jumpToLabel(label);
    }
    
    engine.scenes().push(vnScene);
}
```

---

## Recipe 6: Merged JES Scenes for Modular Content

Load multiple JES files into one scene — shared HUD, shared items, level-specific content.

### File Structure

```text
game/
├── shared/
│   ├── common_hud.jes        # HP bar, gold counter, minimap
│   ├── common_items.jes      # Item definitions, equipment
│   └── common_input.jes      # Shared input bindings
├── levels/
│   ├── dungeon_floor1.jes    # Level geometry, enemies, triggers
│   └── dungeon_floor2.jes
```

### Java — Merged Loading

```java
JesScene2D scene = JesLoader.loadMerged(List.of(
    getResource("game/shared/common_hud.jes"),
    getResource("game/shared/common_items.jes"),
    getResource("game/shared/common_input.jes"),
    getResource("game/levels/dungeon_floor1.jes")
));

// All entities, bindings, items, and timelines are merged
// Register handlers that work across all merged content
scene.registerCall("init", props -> {
    initHud(scene);
    initInventory(scene);
    initLevel(scene);
});

engine.scenes().push(scene);
scene.invokeCall("init", Map.of());
```

### `common_hud.jes`

```jes
scene "HUD" {
  entity "hp_bar_bg" {
    component Panel2D { x: 10 y: 10 w: 200 h: 20 fill: rgb(0.3, 0, 0, 0.8) }
  }
  entity "hp_bar" {
    component Panel2D { x: 10 y: 10 w: 200 h: 20 fill: rgb(0.8, 0.1, 0.1, 1) }
  }
  entity "gold_text" {
    component Label2D { text: "Gold: 0" x: 10 y: 40 size: 16 color: rgb(1, 0.85, 0, 1) }
  }
}
```

### `dungeon_floor1.jes`

```jes
scene "DungeonFloor1" {
  entity "player" {
    component Character2D {
      spriteSheet: "assets/characters/hero.png"
      x: 100 y: 300 w: 32 h: 48
      frameW: 32 frameH: 48
    }
    component PhysicsBody2D { shape: "box" w: 28 h: 44 }
    component Stats { hp: 100 maxHp: 100 atk: 15 def: 8 }
    component Inventory {}
  }

  entity "enemy_goblin" {
    component Character2D {
      spriteSheet: "assets/enemies/goblin.png"
      x: 500 y: 300 w: 32 h: 32
      frameW: 32 frameH: 32
    }
    component PhysicsBody2D { shape: "box" w: 28 h: 28 }
    component Stats { hp: 30 maxHp: 30 atk: 8 def: 3 }
    component Ai2D { behavior: "chase" target: "player" speed: 60 range: 200 }
  }

  on key "ARROW_UP" do moveUp
  on key "ARROW_DOWN" do moveDown
  on key "SPACE" do attack { target: "nearest_enemy" }
  on key "I" do openInventory
  on key "ESCAPE" do returnToVns

  timeline {
    playAudio "assets/audio/bgm/dungeon.ogg" { volume: 0.5 loop: true bgm: true }
  }
}
```

---

## Recipe 7: Event-Driven JES with VNS Cutscenes

A JES scene where specific gameplay events trigger VNS cutscenes mid-gameplay.

### JES Scene

```jes
scene "BossArena" {
  entity "player" {
    component Character2D { ... }
    component Stats { hp: 100 maxHp: 100 atk: 20 def: 10 }
  }

  entity "boss" {
    component Character2D { ... }
    component Stats { hp: 500 maxHp: 500 atk: 30 def: 15 }
    component Ai2D { behavior: "guard" target: "player" speed: 40 range: 300 attackRange: 50 }
  }

  timeline {
    playAudio "assets/audio/bgm/boss.ogg" { volume: 0.8 loop: true bgm: true }
  }
}
```

### Java — Phase Transitions

```java
scene.registerCall("init", props -> {
    bossPhase = 1;
    bossMaxHp = 500;
});

// Called every frame in update loop
public void checkBossPhase(JesScene2D scene) {
    double bossHp = scene.getEntityStat("boss", "hp");
    
    // Phase 2 cutscene at 50% HP
    if (bossPhase == 1 && bossHp <= bossMaxHp * 0.5) {
        bossPhase = 2;
        
        // Pause JES and show a VNS cutscene
        jesVnBridge.startVns(
            "scripts/boss/phase2_cutscene.vns",
            "phase2",
            Map.of("boss_hp", String.valueOf((int) bossHp)),
            true  // push — JES stays underneath
        );
    }
    
    // Victory cutscene at 0 HP
    if (bossHp <= 0) {
        jesVnBridge.startVns(
            "scripts/boss/victory.vns",
            "victory",
            Map.of(
                "player_hp", String.valueOf((int) scene.getEntityStat("player", "hp")),
                "time_elapsed", String.valueOf(elapsedSeconds)
            ),
            true
        );
    }
}

// VNS cutscene finishes → gameplay resumes
scene.registerCall("vnsEnded", props -> {
    if (bossPhase == 2) {
        // Boss enrages after cutscene
        scene.setEntityStat("boss", "atk", 45);
        scene.setEntityStat("boss", "speed", 80);
    }
});
```

### VNS Cutscene (`scripts/boss/phase2_cutscene.vns`)

```vns
@scenario boss_phase2
@character narrator ""
@character boss "Dragon King"

@charimg boss angry assets/characters/boss/angry.png

@label phase2
[screen shake 16 800]
[sfx assets/audio/sfx/roar.ogg]

[show boss center angry]
boss: You think you can defeat me?!
[screen flash 0.7 300 255 100 0]
boss: I'll show you TRUE POWER!

[wait 500]
[hide boss]
narrator: The Dragon King's eyes burned with fury.
[end]
# JES resumes automatically → "vnsEnded" fires
```

---

## Recipe 8: JesVnBridge — Launching VNS from JES (Full API)

The `JesVnBridge` is the runtime bridge that JES scenes use to launch VNS segments. Here's the complete API with examples.

### startVns (Push)

```java
// Minimal — just load a script
jesVnBridge.startVns("scripts/dialogue/intro.vns", null, Map.of(), true);

// With label
jesVnBridge.startVns("scripts/dialogue/intro.vns", "greeting", Map.of(), true);

// With label and variables
jesVnBridge.startVns(
    "scripts/dialogue/shop.vns",
    "shop_menu",
    Map.of("gold", "500", "has_vip", "true"),
    true    // push (JES stays on stack)
);
```

### startVns (Replace)

```java
// Replace JES with VNS — JES is removed from stack
jesVnBridge.startVns(
    "scripts/story/ending.vns",
    "credits",
    Map.of("final_score", "9999"),
    false   // replace (JES is gone)
);
```

### Lifecycle Hooks

```java
// The BridgedVnScene created by JesVnBridge has:
//
// onEnter: inherits audio facade from parent VN scene (if any)
// onExit:
//   1. Resumes JES (jes.setPaused(false))
//   2. Fires "vnsEnded" call on JES scene
//   3. Pops VNS from engine stack (if popOnExit=true)
//
// You don't configure these directly — JesVnBridge sets them up.
// React to them via the "vnsEnded" handler on your JES scene:

scene.registerCall("vnsEnded", props -> {
    // VNS dialogue ended
    // props may contain variables set during the VNS segment
    System.out.println("VNS segment ended");
});
```

---

## Recipe 9: Passing Complex State Across Boundaries

### VNS → JES (Multiple Parameters)

```vns
[set player_name "Yuki"]
[set player_level 5]
[set equipped_weapon "flame_sword"]
[set party_size 3]

[jes push game/battle.jes label after_battle with name=${player_name} level=${player_level} weapon=${equipped_weapon} party=${party_size}]
```

### JES Init Handler

```java
scene.registerCall("init", props -> {
    String name = (String) props.getOrDefault("name", "Hero");
    int level = Integer.parseInt(String.valueOf(props.getOrDefault("level", "1")));
    String weapon = (String) props.getOrDefault("weapon", "basic_sword");
    int partySize = Integer.parseInt(String.valueOf(props.getOrDefault("party", "1")));
    
    setupPlayer(name, level, weapon);
    spawnPartyMembers(partySize);
});
```

### JES → VNS (Rich Return Data)

```java
scene.invokeCall("return", Map.of(
    "battle_result", victory ? "victory" : "defeat",
    "damage_dealt", String.valueOf(totalDamage),
    "damage_taken", String.valueOf(damageTaken),
    "items_found", String.valueOf(itemsFound),
    "gold_earned", String.valueOf(goldEarned),
    "exp_earned", String.valueOf(expEarned),
    "turns_taken", String.valueOf(turnCount),
    "party_survived", String.valueOf(partySurvived)
));
```

### VNS Processes Return Data

```vns
@label after_battle
[if battle_result == "victory"]
  narrator: Victory! You dealt ${damage_dealt} damage in ${turns_taken} turns.
  [inc gold gold_earned]
  [inc exp exp_earned]
  
  [if items_found > 0]
    narrator: You found ${items_found} items!
  [endif]
  
  [if party_survived == "false"]
    narrator: But some party members fell in battle...
  [endif]
[else]
  narrator: Defeat. You took ${damage_taken} damage.
  [dec gold 50]
  narrator: Lost 50 gold.
[endif]
```

---

## Recipe 10: Registered Timeline from Puppeteer in VNS

Use Puppeteer-authored animations inside VNS via the timeline registry.

### Register Timeline from Java

```java
// Load timeline data (exported from Puppeteer)
TimelineData data = TimelineData.load("animations/hero_entrance.timeline");

// Register in the global registry
TimelineRegistry.register("hero_entrance", data);
```

### Use in VNS

```vns
@label dramatic_entrance
[bg throne_room]
narrator: The doors burst open.

# Play a registered timeline animation
[call jes_timeline hero_entrance]

[wait 800]
[show hero center determined]
hero: I've come to end this.
```

### Use in JES

```jes
timeline {
  call "playTimeline" { name: "hero_entrance" }
  wait 800
  call "beginDialogue" {}
}
```

---

## Data Type Quick Reference

All cross-boundary data is string-typed. Parse on the receiving side:

| VNS Value | Arrives in Java as | Parse With |
|-----------|-------------------|------------|
| `difficulty=hard` | `String "hard"` | Direct use |
| `level=5` | `String "5"` | `Integer.parseInt()` |
| `ratio=1.5` | `String "1.5"` | `Double.parseDouble()` |
| `flag=true` | `String "true"` | `Boolean.parseBoolean()` |
| `${variable}` | Interpolated string | Depends on variable type |

| Java Return Value | Arrives in VNS as | Use With |
|-------------------|-------------------|----------|
| `"victory"` | String variable | `[if result == "victory"]` |
| `"1500"` | String variable | `[if score >= 1000]` (auto-coerced) |
| `"true"` | String variable | `[if flag]` (truthy check) |

---

## Error Handling Patterns

### Missing JES Script

```vns
[jes push game/nonexistent.jes label fallback]
# If the file doesn't exist: warning logged, execution continues at next line
```

### Missing Return Label

```java
// If the label doesn't exist in VNS: warning logged, VNS resumes at current position
scene.invokeCall("return", Map.of("label", "nonexistent_label", "score", "100"));
```

### Missing Call Handler

```jes
call "unregistered_handler" { key: "value" }
# If no handler registered and no actionHandler fallback: silently ignored
```

### Defensive Java Handler

```java
scene.registerCall("init", props -> {
    // Always use getOrDefault with sensible fallbacks
    String difficulty = String.valueOf(props.getOrDefault("difficulty", "normal"));
    
    int level;
    try {
        level = Integer.parseInt(String.valueOf(props.getOrDefault("level", "1")));
    } catch (NumberFormatException e) {
        level = 1;
    }
    
    double speed;
    try {
        speed = Double.parseDouble(String.valueOf(props.getOrDefault("speed", "100")));
    } catch (NumberFormatException e) {
        speed = 100.0;
    }
});
```

---

## Related Docs

- [VNS By Example — JES Integration](vns-by-example/10-jes-and-java-integration.md) — tutorial walkthrough
- [JES By Example — VNS Bridge](jes-by-example/10-vns-bridge.md) — tutorial walkthrough
- [VNS ↔ JES Architecture](../scripting/vns/integration/vns-jes-architecture.md) — internal architecture
- [Java + JES Cross Development](../scripting/vns/integration/java-jes-cross-development.md) — hybrid development
- [JES Bridge & Java Hooks](../scripting/jes/integration/jes-bridge.md) — JES-side API reference
- [VNS Interop](../scripting/vns/integration/vns-interop.md) — VNS-side interop reference
- [Puppeteer JES DSL](../editor/puppeteer/puppeteer-jes-dsl.md) — timeline animation reference

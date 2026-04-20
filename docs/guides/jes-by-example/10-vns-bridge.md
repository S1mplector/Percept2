# JES By Example — VNS Bridge Integration

Launch JES scenes from VNS scripts, pass parameters in, and return gameplay data back to the story — the core pattern for hybrid visual novel + gameplay projects.

**Difficulty:** Advanced
**Time:** 25 minutes
**Concepts:** `[jes push]`, `[jes replace]`, launch parameters, `init` handler, `return` handler, VN variables from JES, scene stack lifecycle

---

## Overview

The VNS-JES bridge enables two-way communication:

```text
VNS ──[jes push]──→ JES scene receives parameters via "init" handler
                      ↓
                    Player interacts with JES scene
                      ↓
JES ──[return]────→ VNS resumes with new variables
```

---

## VNS Script

```vns
@scenario story
@character narrator "Narrator"

@label before_game
narrator: Time for a challenge!
[jes push game/scenes/arena.jes label after_game with difficulty=hard rounds=3]

@label after_game
narrator: You scored ${arena_score} points in ${arena_rounds} rounds!
[if arena_score >= 100 goto great_ending]
[if arena_score >= 50 goto good_ending]
narrator: Better luck next time.
[jump end]

@label great_ending
narrator: Incredible performance!
[jump end]

@label good_ending
narrator: Well done!
[jump end]

@label end
narrator: Thanks for playing.
```

---

## JES Scene

```jes
scene "Arena" {
  entity "bg" {
    component Panel2D {
      x: 0
      y: 0
      w: 800
      h: 600
      fill: rgb(0.1, 0.05, 0.15, 1)
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

  entity "round_label" {
    component Label2D {
      text: "Round: 1"
      x: 10
      y: 35
      size: 14
      color: rgb(0.7, 0.7, 0.7, 1)
    }
  }

  on key "SPACE" do scorePoint
  on key "ESCAPE" do endGame
  on key "D" do toggleDebug
}
```

---

## Java Wiring

```java
scene.registerCall("init", props -> {
    String difficulty = (String) props.getOrDefault("difficulty", "normal");
    int rounds = Integer.parseInt(String.valueOf(props.getOrDefault("rounds", "1")));
    scene.invokeCall("setLabelText", Map.of(
        "target", "round_label",
        "text", "Round: 1 / " + rounds + " (" + difficulty + ")"
    ));
});

int[] score = {0};
int[] round = {1};

scene.registerCall("scorePoint", props -> {
    score[0] += 10;
    scene.invokeCall("setLabelText", Map.of(
        "target", "score_label",
        "text", "Score: " + score[0]
    ));
});

scene.registerCall("endGame", props -> {
    scene.invokeCall("return", Map.of(
        "arena_score", String.valueOf(score[0]),
        "arena_rounds", String.valueOf(round[0])
    ));
});
```

---

## The `[jes]` Command

### `[jes push]` — Push JES Scene onto Stack

```vns
[jes push path/to/scene.jes label return_label with key1=value1 key2=value2]
```

| Part | Required | Description |
|------|----------|-------------|
| `push` | yes | Pushes JES scene onto the engine's scene stack (VNS pauses) |
| `path/to/scene.jes` | yes | Path to the JES file (relative to project root) |
| `label return_label` | no | VNS label to jump to when JES scene returns |
| `with key=value ...` | no | Parameters passed to the JES scene's `init` handler |

The VNS scene **pauses** while the JES scene is active. When the JES scene calls `return`, it pops off the stack and VNS resumes.

### `[jes replace]` — Replace VNS with JES

```vns
[jes replace path/to/scene.jes with key1=value1]
```

Replaces the VNS scene entirely. The VNS scene is **removed** from the stack — there is no automatic return. Use this for permanent transitions (e.g., switching from story mode to gameplay mode).

---

## Launch Parameters

Parameters in the `with` clause become the `props` map in the `init` handler:

```vns
[jes push arena.jes with difficulty=hard rounds=3 playerName=Hero]
```

```java
scene.registerCall("init", props -> {
    // props = { "difficulty": "hard", "rounds": "3", "playerName": "Hero" }
    String difficulty = (String) props.get("difficulty");
    int rounds = Integer.parseInt((String) props.get("rounds"));
    String name = (String) props.get("playerName");
});
```

All values arrive as strings. Parse numbers explicitly.

---

## Returning Data to VNS

The `return` call handler pops the JES scene and passes data back as VN variables:

```java
scene.invokeCall("return", Map.of(
    "arena_score", "150",
    "arena_rounds", "3",
    "arena_result", "victory"
));
```

After return, VNS has these variables available:

```vns
@label after_game
narrator: Score was ${arena_score}.
[if arena_result == "victory" goto won]
[if arena_result == "defeat" goto lost]
```

### Return Keys Become VN Variables

Every key in the `return` map becomes a VN variable accessible via `${key}` interpolation and `[if key ...]` conditions.

### Return Without Data

If you just want to pop the JES scene with no data:

```java
scene.invokeCall("return", Map.of());
```

VNS resumes at the `label` specified in the `[jes push]` command (or continues from the next line if no label was specified).

---

## Scene Stack Lifecycle

```text
Stack: [VNS]
  ↓  [jes push arena.jes]
Stack: [VNS (paused), JES:Arena]
  ↓  Player plays the minigame
Stack: [VNS (paused), JES:Arena]
  ↓  JES calls "return"
Stack: [VNS (resumed)]
  ↓  VNS continues at return label
```

### Lifecycle Hooks

| Event | What Happens |
|-------|--------------|
| `push` | VNS pauses (`onPause`), JES loads and enters (`onEnter`) |
| JES active | JES receives `update`/`render` each frame |
| `return` | JES exits (`onExit`), VNS resumes (`onResume`) |
| `replace` | VNS exits (`onExit`), JES enters (`onEnter`) — no return |

---

## Patterns

### Minigame with Score

The most common pattern — VNS launches a minigame, gets a score back, branches on it:

```vns
[jes push game/fishing.jes label fishing_done with baitType=worm]

@label fishing_done
[if fish_caught > 0 goto caught_fish]
narrator: No luck today.
[jump continue]

@label caught_fish
narrator: You caught ${fish_caught} fish!
[inc gold fish_caught]
```

### Exploration with Discovery

VNS launches an exploration scene, gets discovered items/flags back:

```vns
[jes push game/dungeon.jes label dungeon_done]

@label dungeon_done
[if found_treasure == "true" goto treasure]
[if found_secret == "true" goto secret]
narrator: You explored the dungeon but found nothing special.
[jump continue]
```

### Battle with Outcome

```vns
[jes push game/battle.jes label battle_done with enemy=goblin_king hero_hp=${hp}]

@label battle_done
[if battle_result == "victory" goto victory]
[if battle_result == "defeat" goto defeat]
[if battle_result == "flee" goto fled]
```

### Cutscene (No Return Data)

Push a purely cinematic JES scene with no data exchange:

```vns
[jes push game/cutscenes/opening.jes label after_cutscene]

@label after_cutscene
narrator: The adventure begins...
```

The JES scene plays its timeline, then calls `return` with an empty map when done.

### Permanent Mode Switch

Use `replace` when there's no going back:

```vns
narrator: The world opens before you.
[jes replace game/overworld.jes with startZone=forest]
```

---

## Passing VN State to JES

VN variables can be passed as launch parameters:

```vns
@var player_level = 5
@var player_gold = 200

[jes push game/shop.jes label shop_done with level=${player_level} gold=${player_gold}]
```

The JES scene receives interpolated values:

```java
scene.registerCall("init", props -> {
    int level = Integer.parseInt((String) props.getOrDefault("level", "1"));
    int gold = Integer.parseInt((String) props.getOrDefault("gold", "0"));
    // Set up shop inventory based on level, deduct gold on purchase
});
```

And returns updated state:

```java
scene.invokeCall("return", Map.of(
    "player_gold", String.valueOf(remainingGold),
    "items_bought", String.join(",", purchasedItems)
));
```

```vns
@label shop_done
[set gold ${player_gold}]
narrator: You have ${gold} gold remaining.
```

---

## Data Flow Diagram

```text
VNS                              JES
 │                                │
 │ [jes push arena.jes            │
 │   label after_game             │
 │   with difficulty=hard         │
 │         rounds=3]              │
 │ ─────────────────────────────→ │
 │                                │ init handler receives:
 │                                │   { difficulty: "hard",
 │                                │     rounds: "3" }
 │                                │
 │        (VNS paused)            │ Player interacts...
 │                                │
 │                                │ invokeCall("return",
 │                                │   { arena_score: "150",
 │ ←───────────────────────────── │     arena_rounds: "3" })
 │                                │
 │ Resumes at @label after_game   │ (JES scene popped)
 │ Variables:                     │
 │   arena_score = 150            │
 │   arena_rounds = 3             │
 │                                │
 │ [if arena_score >= 100 ...]    │
```

---

## Key Takeaways

1. `[jes push file.jes label return_label with key=value]` launches JES from VNS
2. `[jes replace file.jes]` permanently switches to JES (no return)
3. The `init` call handler receives launch parameters as `Map<String, Object>`
4. The `return` call handler pops the JES scene and passes data back as VN variables
5. VN variables from `return` are accessible via `${key}` and `[if key ...]`
6. All parameter values are strings — parse numbers explicitly in Java
7. Use `push` for temporary gameplay segments, `replace` for permanent mode switches
8. VN state can be forwarded to JES via `${variable}` interpolation in `with` clauses

---

## Related Docs

- [VNS-JES Architecture](../../scripting/vns/integration/vns-jes-architecture.md) — full architecture reference
- [Java-JES Cross-Development](../../scripting/vns/integration/java-jes-cross-development.md) — hybrid development guide
- [JES Bridge & Java Hooks](../../scripting/jes/integration/jes-bridge.md) — complete Java API reference
- [Back to Index](../jes-by-example.md)

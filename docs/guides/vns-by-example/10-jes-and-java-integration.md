# VNS By Example — JES and Java Integration

Launch JES gameplay scenes from VNS, call Java code, embed inline timelines, and build hybrid visual novel + gameplay projects.

**Difficulty:** Advanced
**Time:** 25 minutes
**Concepts:** `[jes push]`, `[jes replace]`, `[jes pop]`, `[jes_call]`, `[java]`, `[call provider payload]`, inline `timeline {}`, data flow

---

## The Script

```vns
@scenario hybrid_game
@character narrator ""
@character hero "Yuki"
@character guide "Guide"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero determined assets/characters/yuki/determined.png
@charimg hero happy assets/characters/yuki/happy.png
@charimg guide neutral assets/characters/guide/neutral.png

@background town assets/backgrounds/town.png
@background arena_entrance assets/backgrounds/arena_gate.png

@var player_level = 1
@var gold = 100

@label start
[bg town]
[show guide center neutral]
guide: Welcome, adventurer! Ready for the arena?

> Enter the Arena
  [jump arena_prep]
> Not yet
  guide: Come back when you're ready.
  [end]

@label arena_prep
[bg arena_entrance]
[show hero center determined]
hero: Let's do this.

# Launch JES minigame with parameters
[jes push game/scenes/arena.jes label arena_done with difficulty=normal level=${player_level} gold=${gold}]

@label arena_done
# JES scene returned — variables arena_score and arena_result are now set
[bg town]
[show hero center happy]
[show guide center neutral]

guide: How did it go?

[if arena_result == "victory"]
  hero: I won! Scored ${arena_score} points!
  [inc gold arena_score]
  [inc player_level]
  guide: Congratulations! Level ${player_level} now!
[elif arena_result == "defeat"]
  hero: I lost... only scored ${arena_score}.
  guide: Don't give up!
[else]
  hero: I had to retreat.
  guide: There's no shame in that.
[endif]

narrator: Gold: ${gold}

> Play again
  [jump arena_prep]
> Leave
  guide: See you next time!
  [end]
```

---

## `[jes]` Commands

### `[jes push]` — Launch JES (Pausable)

```vns
[jes push path/to/scene.jes]
[jes push path/to/scene.jes label return_label]
[jes push path/to/scene.jes label return_label with key1=val1 key2=val2]
```

| Part | Required | Description |
|------|----------|-------------|
| `push` | yes | Pushes JES scene onto scene stack |
| `path` | yes | Path to `.jes` file |
| `label return_label` | no | VNS label to jump to when JES returns |
| `with key=value ...` | no | Parameters sent to JES `init` handler |

VNS **pauses** while JES is active. When JES calls `return`, VNS resumes.

### `[jes replace]` — Permanent Switch

```vns
[jes replace game/overworld.jes]
[jes replace game/overworld.jes with zone=forest]
```

Replaces VNS entirely. No return. Use for permanent mode switches.

### `[jes pop]` — Pop JES from Stack

```vns
[jes pop]
```

Forces the top JES scene off the stack. Rarely needed — JES scenes typically pop themselves via `return`.

### `[jes_call]` — Call into Active JES

```vns
[jes_call spawnWave count=5]
[jes_call setDifficulty level=hard]
```

Sends a call to the currently active JES scene without pushing a new one.

### Direct Commands

```vns
[jes_push game/arena.jes]
[jes_replace game/boss.jes]
[jes_pop]
```

Equivalent to `[jes push ...]`, `[jes replace ...]`, `[jes pop]` but as single-word commands.

---

## Data Flow: VNS ↔ JES

### VNS → JES (Launch Parameters)

```vns
[jes push arena.jes with difficulty=hard level=5 gold=200]
```

JES receives these in its `init` handler:

```java
scene.registerCall("init", props -> {
    String difficulty = (String) props.get("difficulty");
    int level = Integer.parseInt((String) props.get("level"));
    int gold = Integer.parseInt((String) props.get("gold"));
});
```

### JES → VNS (Return Data)

```java
scene.invokeCall("return", Map.of(
    "arena_score", "150",
    "arena_result", "victory"
));
```

VNS receives these as variables:

```vns
@label arena_done
[if arena_result == "victory"]
  narrator: You scored ${arena_score}!
[endif]
```

### Passing VNS Variables

Use `${variable}` interpolation in the `with` clause:

```vns
@var player_level = 5
@var gold = 200

[jes push arena.jes label done with level=${player_level} gold=${gold}]
```

---

## `[java]` — Java Method Calls

Call static Java methods directly from VNS:

```vns
[java com.example.GameHooks#beginEncounter goblin 3]
[java com.example.Analytics#trackEvent chapter_complete]
```

### Format

```
[java fully.qualified.Class#methodName arg1 arg2 ...]
```

The method must be `public static` and accept `String...` or appropriate arguments.

### Example Java Handler

```java
public class GameHooks {
    public static void beginEncounter(String enemy, String count) {
        int n = Integer.parseInt(count);
        System.out.println("Starting encounter: " + n + "x " + enemy);
    }
}
```

---

## `[call provider payload]` — Interop Providers

Call registered interop providers with arbitrary payloads:

```vns
[call jes_timeline hero_entrance]
[call hud Achievement unlocked!]
[call analytics chapter_complete]
```

### How Providers Work

Providers are registered in Java:

```java
vnInterop.registerProvider("analytics", payload -> {
    System.out.println("Analytics: " + payload);
});
```

The `payload` is the remaining text after the provider name.

---

## Inline Timelines

Embed JES timeline actions directly in VNS scripts:

```vns
timeline {
  entity "hero" {
    0ms { x: 640, y: 396 }
    300ms { x: 780, y: 396, easing: ease_out }
  }
  cameraMove 300ms 0 0 0.92
  playAudio "assets/audio/sfx/whoosh.ogg"
}
```

### When to Use Inline Timelines

| Approach | Best For |
|----------|----------|
| `[jes push]` | Full gameplay scenes, minigames |
| Inline `timeline {}` | Quick animation within a VN scene |
| Puppeteer export | Complex pre-authored animations |

Inline timelines run inside the active VN scene. They're useful for:
- Character entrance/exit animations
- Camera effects during dialogue
- Quick visual flourishes

---

## Patterns

### Minigame with Score Branching

```vns
@label before_minigame
narrator: Time for a challenge!
[jes push game/fishing.jes label fishing_done with bait=worm]

@label fishing_done
[if fish_caught > 0 goto caught_fish]
narrator: No luck today.
[jump continue]

@label caught_fish
narrator: You caught ${fish_caught} fish!
[inc gold fish_caught]
```

### Exploration with Discovery Flags

```vns
[jes push game/dungeon.jes label dungeon_done]

@label dungeon_done
[if found_treasure == "true"]
  narrator: You found hidden treasure!
  [inc gold 500]
[endif]
[if found_secret == "true"]
  [flag secret_unlocked]
  narrator: A secret passage has been revealed.
[endif]
```

### Battle System

```vns
@label before_battle
[set hero_hp_before ${hp}]
[jes push game/battle.jes label battle_done with enemy=goblin_king hero_hp=${hp} hero_atk=${atk}]

@label battle_done
[if battle_result == "victory" goto victory]
[if battle_result == "defeat" goto defeat]
[if battle_result == "flee" goto fled]

@label victory
narrator: Victory!
[inc exp battle_exp]
[inc gold battle_gold]
[set hp battle_hero_hp]
hero: That was tough!
[jump continue]

@label defeat
narrator: Defeat...
[set hp 1]
hero: I barely made it out...
[jump continue]

@label fled
narrator: You escaped safely.
[jump continue]
```

### Cutscene (No Data Return)

```vns
# Push a cinematic JES scene, no data exchange needed
[jes push game/cutscenes/opening.jes label after_cutscene]

@label after_cutscene
narrator: The adventure begins...
```

The JES scene plays its timeline and calls `return` with an empty map when done.

### Permanent World Switch

```vns
narrator: The world opens before you.
[jes replace game/overworld.jes with startZone=forest player=${player_name}]
# No return — VNS is gone
```

### Stage Lighting Before JES

```vns
@stagepreset sunset_park config/stage/sunset_park.stagepreset

[stage sunset_park]
narrator: The golden hour.
# Stage lighting affects the VN scene
# When pushing to JES, the JES scene handles its own rendering

[jes push game/park_explore.jes label explore_done]
```

---

## Architecture Overview

```text
┌──────────────────────────────┐
│           Engine             │
│  ┌────────┐  ┌────────────┐ │
│  │  VNS   │  │    JES     │ │
│  │ Scene  │  │   Scene    │ │
│  │        │←→│            │ │
│  │ State  │  │  Entities  │ │
│  │ Vars   │  │  Timeline  │ │
│  │ Script │  │  Physics   │ │
│  └────┬───┘  └─────┬──────┘ │
│       │             │        │
│  ┌────┴─────────────┴────┐   │
│  │      Scene Stack      │   │
│  │  [VNS] → [JES push]  │   │
│  │  [VNS] ← [JES return]│   │
│  └───────────────────────┘   │
└──────────────────────────────┘
```

### Key Architectural Points

- VNS and JES don't call each other directly
- They communicate through the **scene stack** and **VnInterop**
- Launch parameters flow VNS → JES via the `init` handler
- Return data flows JES → VNS as session variables
- Each system handles its own rendering and update loop

---

## Full Example: Adventure Hub

```vns
@scenario adventure
@character narrator ""
@character hero "Yuki"
@character guild "Guildmaster"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero happy assets/characters/yuki/happy.png
@charimg hero determined assets/characters/yuki/determined.png
@charimg guild neutral assets/characters/guild/neutral.png
@charimg guild pleased assets/characters/guild/pleased.png

@background guild_hall assets/backgrounds/guild_hall.png
@background world_map assets/backgrounds/world_map.png

@var gold = 500
@var level = 1
@var quests_complete = 0

@label hub
[bg guild_hall]
[show guild center neutral]
guild: Welcome back! What'll it be?
narrator: Level: ${level} | Gold: ${gold} | Quests: ${quests_complete}

> Take a Quest
  [jump quest_select]
> Visit the Shop
  [jes push game/shop.jes label shop_done with gold=${gold} level=${level}]
  @label shop_done
  [set gold shop_remaining_gold]
  guild: Got some new gear?
  [jump hub]
> Train (Level Up)
  [jes push game/training.jes label train_done with level=${level}]
  @label train_done
  [if training_passed == "true"]
    [inc level]
    [hud Level Up! Now level ${level}]
    [show guild center pleased]
    guild: You've grown stronger!
  [else]
    guild: Keep at it.
  [endif]
  [jump hub]
> Save and Quit
  [save]
  [hud Saved!]
  [end]

@label quest_select
[bg world_map]
[show hero center determined]
hero: Where should I go?

> Forest (Easy)
  [jes push game/quests/forest.jes label quest_done with difficulty=easy level=${level}]
> Mountains (Medium)
  [if level >= 3 goto mountains_quest]
  hero: I need to be level 3 for the mountains.
  [jump quest_select]
> Dragon's Lair (Hard)
  [if level >= 5 goto dragon_quest]
  hero: Level 5 required. I'm not ready.
  [jump quest_select]
> Back
  [jump hub]

@label mountains_quest
[jes push game/quests/mountains.jes label quest_done with difficulty=medium level=${level}]

@label dragon_quest
[jes push game/quests/dragon.jes label quest_done with difficulty=hard level=${level}]

@label quest_done
[bg guild_hall]
[show guild center neutral]

[if quest_result == "victory"]
  [inc quests_complete]
  [inc gold quest_reward]
  [show guild center pleased]
  guild: Excellent work! Here's ${quest_reward} gold.
  [show hero center happy]
  hero: Another quest complete!
[elif quest_result == "defeat"]
  guild: Don't worry. Rest up and try again.
[else]
  guild: Smart to retreat when outmatched.
[endif]

[jump hub]
```

---

## Key Takeaways

1. `[jes push file.jes label return with key=val]` launches JES from VNS
2. `[jes replace]` permanently switches to JES (no return)
3. `[jes_call]` sends commands to an already active JES scene
4. `[java Class#method args]` calls static Java methods
5. `[call provider payload]` invokes registered interop providers
6. Inline `timeline {}` embeds JES animation in VN scenes
7. Data flows VNS → JES via `with` parameters and JES → VNS via `return` variables
8. VNS variables (`${var}`) can be interpolated into launch parameters

## Next

Continue to [Reactive UI with Facets](11-reactive-ui-and-facets.md) to build a variable-driven story overlay.

---

## Related Docs

- [JES By Example](../jes-by-example.md) — the JES tutorial series
- [VNS-JES Architecture](../../scripting/vns/integration/vns-jes-architecture.md) — full architecture reference
- [Java-JES Cross-Development](../../scripting/vns/integration/java-jes-cross-development.md) — hybrid development guide
- [JES Bridge & Java Hooks](../../scripting/jes/integration/jes-bridge.md) — complete Java API reference
- [VNS Interop](../../scripting/vns/integration/vns-interop.md) — interop provider reference
- [Back to Index](../vns-by-example.md)

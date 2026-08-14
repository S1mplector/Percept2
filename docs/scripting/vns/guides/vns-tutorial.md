# VNS Tutorial: Building a Complete Visual Novel

Step-by-step walkthrough that builds a fully-featured mini visual novel from scratch, introducing VNS features incrementally. By the end you'll have a multi-scene story with characters, choices, branching, audio, transitions, variables, save/load, and a JES minigame integration.

---

## What We're Building

A short adventure called **"The Forgotten Key"**, meant to be a mystery story where the player explores a town, meets characters, solves a puzzle, and reaches one of three endings based on their choices.

Features covered:
- Script structure and directives
- Characters with layered sprites
- Backgrounds and transitions
- Dialogue with text effects
- Choices and branching
- Variables, flags, and conditions
- Audio (BGM, SFX, voice)
- Subroutines (`[gosub]`/`[return]`)
- Save/load integration
- Screen effects (shake, flash)
- JES minigame integration
- Multiple endings

---

## Step 1: Project Setup

Create the directory structure:

```text
game/
├── scripts/
│   ├── common/
│   │   ├── characters.vns
│   │   ├── backgrounds.vns
│   │   └── variables.vns
│   ├── prologue.vns
│   ├── chapter1.vns
│   └── endings.vns
├── backgrounds/
│   ├── town_square.png
│   ├── library.png
│   ├── garden.png
│   └── tower.png
├── characters/
│   ├── lena/
│   │   ├── neutral.png
│   │   ├── happy.png
│   │   ├── worried.png
│   │   └── surprised.png
│   └── old_man/
│       ├── neutral.png
│       └── smile.png
└── audio/
    ├── bgm/
    │   ├── town.ogg
    │   ├── mystery.ogg
    │   └── victory.ogg
    └── sfx/
        ├── door.ogg
        ├── key_found.ogg
        └── clock_chime.ogg
```

---

## Step 2: Shared Definitions

### characters.vns

```vns
# common/characters.vns — character declarations
@character narrator "Narrator"
@character lena "Lena"
@character old_man "Old Man"
@character ??? "???"

# Lena's expressions
@charimg lena neutral assets/characters/lena/neutral.png
@charimg lena happy assets/characters/lena/happy.png
@charimg lena worried assets/characters/lena/worried.png
@charimg lena surprised assets/characters/lena/surprised.png

# Old Man's expressions
@charimg old_man neutral assets/characters/old_man/neutral.png
@charimg old_man smile assets/characters/old_man/smile.png
```

### backgrounds.vns

```vns
# common/backgrounds.vns — background declarations
@background town_square assets/backgrounds/town_square.png
@background library assets/backgrounds/library.png
@background garden assets/backgrounds/garden.png
@background tower assets/backgrounds/tower.png
```

### variables.vns

```vns
# common/variables.vns — game state
@var clues_found = 0
@var has_key = false
@var has_map = false
@var talked_to_old_man = false
@var trust_lena = 0
@var ending = ""
```

---

## Step 3: The Prologue

```vns
# scripts/prologue.vns
@scenario prologue
@include common/characters.vns
@include common/backgrounds.vns
@include common/variables.vns

@label start
[bgm assets/audio/bgm/mystery.ogg]
[transition FADE 1200 town_square]
[wait 800]

narrator: A small town at the edge of the mountains.
narrator: You've come here following a letter — unsigned, mysterious.

[show lena center neutral]
lena: Oh! You must be the one the letter mentioned.
lena: I'm Lena. I've been waiting for you.

narrator: She held out her hand, her expression cautious but kind.

> Take her hand -> accept
> Keep your distance -> cautious

@label accept
[inc trust_lena 2]
lena: {b}Thank you.{/b} I knew you'd understand.
[show lena center happy]
[jump intro_continue]

@label cautious
lena: I understand. This must all seem strange.
[show lena center worried]
[jump intro_continue]

@label intro_continue
lena: There's something hidden in this town.
lena: A key — one that unlocks the old tower.
lena: The letter you received... I wrote it.

narrator: She paused, glancing toward the tower in the distance.

[sfx assets/audio/sfx/clock_chime.ogg]
[screen shake 3 500]

lena: Did you hear that? The clock tower...
lena: It hasn't chimed in years.

narrator: {i}Something about this town isn't right.{/i}

[save auto]
[goto chapter1:start]
```

### What this demonstrates

- **`@include`** — shared definitions loaded from header files
- **`[transition FADE]`** — smooth scene entrance
- **`[show]`** — character display with expressions
- **Choices** — branching with `>` syntax and label targets
- **`[inc]`** — modifying a variable based on player choice
- **`[jump]`** — merging branches back to shared content
- **Text effects** — `{b}bold{/b}` and `{i}italic{/i}`
- **`[sfx]` + `[screen shake]`** — combined audio and visual effects
- **`[save auto]`** — checkpoint before chapter transition
- **`[goto]`** — cross-script navigation

---

## Step 4: Chapter 1 — Exploration

```vns
# scripts/chapter1.vns
@scenario chapter1
@include common/characters.vns
@include common/backgrounds.vns
@include common/variables.vns

@label start
[bg town_square]
[bgm_crossfade assets/audio/bgm/town.ogg 1500]

narrator: The town square. Three paths lead in different directions.

@label hub
narrator: Where do you want to go?

> Visit the library [if !has_map] -> library
> Explore the garden [if !has_key] -> garden
> Talk to the old man [if !talked_to_old_man] -> old_man_scene
> Go to the tower [if has_key && has_map] -> tower_entrance
> Look around -> look_around

# ─── Library ───

@label library
[transition FADE 600 library]
[hide lena]

narrator: The library was dusty and quiet. Shelves stretched to the ceiling.
narrator: A book caught your eye — a map of the town's underground tunnels.

[sfx assets/audio/sfx/key_found.ogg]
[flag has_map]
[inc clues_found 1]

narrator: {b}Obtained: Tunnel Map{/b}
narrator: This could be useful.

[hud "Map acquired!" 1500]

[transition FADE 600 town_square]
[jump hub]

# ─── Garden ───

@label garden
[transition FADE 600 garden]

[show lena right neutral]
lena: I sometimes come here to think.
lena: There's a statue in the center — look closely.

narrator: Behind the statue, partially buried in ivy, you found a rusted key.

[sfx assets/audio/sfx/key_found.ogg]
[flag has_key]
[inc clues_found 1]

narrator: {b}Obtained: Rusted Key{/b}

> Ask Lena about the key -> lena_key_info
> Pocket the key silently -> pocket_key

@label lena_key_info
[inc trust_lena 1]
[show lena right happy]
lena: That must be the key to the tower gate!
lena: The old man might know more about it.
[jump garden_done]

@label pocket_key
narrator: You slipped the key into your pocket without a word.
[show lena right worried]
lena: ...You found something, didn't you?
[jump garden_done]

@label garden_done
[hide lena]
[transition FADE 600 town_square]
[jump hub]

# ─── Old Man ───

@label old_man_scene
[show old_man center neutral]
[flag talked_to_old_man]

old_man: Ah, a visitor. It's been a long time.
old_man: You're looking for the tower, aren't you?

> Yes, I need to get inside -> old_man_yes
> Who are you? -> old_man_who

@label old_man_yes
old_man: The tower holds secrets. Be careful what you unlock.
[show old_man center smile]
old_man: But if you must go... take this advice:
[gosub old_man_advice]
[jump old_man_done]

@label old_man_who
old_man: I'm the last keeper of this town's history.
old_man: The tower was sealed fifty years ago after... the incident.
[inc clues_found 1]
[gosub old_man_advice]
[jump old_man_done]

@label old_man_advice
old_man: The tower has three floors.
old_man: The first holds memories. The second holds regret.
old_man: The third... well, you'll see for yourself.
[return]

@label old_man_done
[hide old_man]
[jump hub]

# ─── Look Around ───

@label look_around
narrator: You take in the scenery.
narrator: Clues found so far: ${clues_found}
[if has_key]
  narrator: You have the rusted key.
[endif]
[if has_map]
  narrator: You have the tunnel map.
[endif]
[if !has_key && !has_map]
  narrator: You haven't found anything yet. Keep exploring.
[endif]
[jump hub]

# ─── Tower Entrance ───

@label tower_entrance
[save auto]
[bgm_fadeout 1500]
[transition FADE 1000 tower]
[wait 500]

[sfx assets/audio/sfx/door.ogg]
[screen shake 4 600]

narrator: The key turned with a grinding screech.
narrator: The tower door swung open, releasing decades of stale air.

[bgm assets/audio/bgm/mystery.ogg]

[if trust_lena >= 3]
  [show lena center worried]
  lena: I'm coming with you.
  narrator: Lena stepped forward, determined.
  [set ending "together"]
[elif trust_lena >= 1]
  [show lena right neutral]
  lena: Be careful in there.
  narrator: She waited at the entrance.
  [set ending "solo_good"]
[else]
  narrator: You entered alone. Lena was nowhere to be seen.
  [set ending "solo_bad"]
[endif]

[goto endings:tower_climb]
```

### What this demonstrates

- **Hub pattern** — central label with conditional choices that unlock as the player progresses
- **Conditional choices** — `[if !has_map]` hides visited locations
- **`[flag]`** — boolean flags for inventory items
- **`[gosub]`/`[return]`** — reusable advice subroutine
- **Variable interpolation** — `${clues_found}` in dialogue
- **`[if]`/`[elif]`/`[else]`/`[endif]`** — multi-branch conditional blocks
- **`[bgm_fadeout]` + `[bgm_crossfade]`** — audio transitions
- **Score-gated endings** — `trust_lena` determines the ending

---

## Step 5: Endings

```vns
# scripts/endings.vns
@scenario endings
@include common/characters.vns
@include common/backgrounds.vns
@include common/variables.vns

@label tower_climb
narrator: You ascended the spiral staircase.
narrator: Each floor revealed fragments of the town's past.

[wait 1000]

[if ending == "together"]
  [jump ending_together]
[elif ending == "solo_good"]
  [jump ending_solo_good]
[else]
  [jump ending_solo_bad]
[endif]

# ─── Ending A: Together ───

@label ending_together
[show lena center happy]
narrator: At the top of the tower, you found a sealed letter.
narrator: Together, you and Lena broke the seal.

lena: It's a deed! This tower... it belongs to {b}my family{/b}.
lena: The town sealed it because they were afraid of what was inside.
lena: But there was nothing to fear. Just... memories.

[screen flash 1.0 300]
[sfx assets/audio/sfx/key_found.ogg]

narrator: The clock tower chimed again — this time, a warm, resonant tone.

[bgm_crossfade assets/audio/bgm/victory.ogg 2000]

lena: Thank you for trusting me.
lena: {rainbow}We solved the mystery together.{/rainbow}

narrator: {b}ENDING A — The Forgotten Key: Together{/b}
[flag ending_a_seen]
[save auto]
[end]

# ─── Ending B: Solo Good ───

@label ending_solo_good
narrator: At the top, you found the sealed letter alone.
narrator: Inside was a deed — and a photograph of a young girl.

narrator: You recognized her. It was Lena, decades younger.
narrator: The mystery was deeper than you thought.

[bgm_crossfade assets/audio/bgm/victory.ogg 2000]

narrator: You brought the letter to Lena, who was waiting outside.
[show lena center surprised]

lena: This is... my grandmother's handwriting.
lena: {i}All these years, the answer was right here.{/i}

narrator: {b}ENDING B — The Forgotten Key: Delivered{/b}
[flag ending_b_seen]
[save auto]
[end]

# ─── Ending C: Solo Bad ───

@label ending_solo_bad
narrator: The tower was empty. Dust and silence.
narrator: Whatever secrets it held were lost to time.

narrator: You descended alone, the key heavy in your pocket.

[bgm_fadeout 2000]

narrator: The town square was deserted. Lena was gone.
narrator: Perhaps the mystery was never meant to be solved.

narrator: {b}ENDING C — The Forgotten Key: Alone{/b}
[flag ending_c_seen]
[save auto]
[end]
```

### What this demonstrates

- **String comparison** — `[if ending == "together"]`
- **Multiple endings** — three distinct endings based on accumulated choices
- **`[screen flash]`** — dramatic visual punctuation
- **`{rainbow}` text effect** — celebratory emphasis
- **Ending flags** — `[flag ending_a_seen]` for gallery tracking
- **Autosave at endings** — preserving completion state

---

## Step 6: Adding a JES Minigame (Optional)

Let's add a puzzle minigame when entering the tower. Modify the tower entrance in `chapter1.vns`:

```vns
@label tower_entrance
[save auto]
[bgm_fadeout 1500]

narrator: The lock is old and complex. You'll need to solve the mechanism.

# Launch JES puzzle minigame
[jes push game/minigames/lock_puzzle.jes label puzzle_done with difficulty=normal]

@label puzzle_done
# JES returns: solved=true/false, time=<ms>
[if solved == "true"]
  [sfx assets/audio/sfx/door.ogg]
  narrator: The lock clicked open!
  narrator: Solved in ${time}ms.
[else]
  narrator: The lock wouldn't budge. You forced it open.
  [screen shake 6 500]
  [dec trust_lena 1]
[endif]

[transition FADE 1000 tower]
# ... continue with existing tower content
```

The JES minigame returns `solved` and `time` as VNS variables via `call "return" { solved: true time: 3200 }`.

---

## Step 7: Polish

### Add text speed variation for dramatic moments

```vns
[textspeed 60]
narrator: The tower was silent...
[wait 800]
[textspeed 15]
narrator: {shake}Something moved in the shadows!{/shake}
[textspeed 30]
```

### Add BGM volume changes for voice scenes

```vns
[volume bgm 0.3]
[voice assets/audio/voices/lena/confession.ogg]
lena: I've been searching for this my whole life.
[volume bgm 0.7]
```

### Add a history-friendly narrator tag

```vns
# Use narrator for scene descriptions so they appear in history
narrator: The sun began to set over the mountains.
```

---

## Step 8: Testing Checklist

Run through these checks before considering the script complete:

1. **Parse check** — `./gradlew :core:test` passes with no VNS parse errors
2. **All paths reachable** — use the Label Flow Map to verify
3. **Hub exhaustion** — visit all locations, then verify the tower option appears
4. **Variable state** — add `narrator: DEBUG: clues=${clues_found} key=${has_key} map=${has_map} trust=${trust_lena}` at the hub for testing
5. **All three endings** — play through each branch
6. **Save/load** — save at the hub, load, verify state is correct
7. **Rollback** — roll back through dialogue at the tower entrance
8. **Skip mode** — enable skip mode and verify it advances through read text
9. **Audio** — verify BGM crossfades and SFX play at correct moments
10. **Remove debug lines** — strip any temporary `narrator: DEBUG:` lines

---

## Full Feature Summary

| Feature | Where Used |
|---------|------------|
| `@include` | All scripts — shared definitions |
| `@character` + `@charimg` | `characters.vns` |
| `@background` | `backgrounds.vns` |
| `@var` + `@flag` | `variables.vns`, `chapter1.vns` |
| Dialogue (colon form) | All scripts |
| Text effects | `prologue.vns`, `endings.vns` |
| Choices (`>`) | `prologue.vns`, `chapter1.vns` |
| Conditional choices | `chapter1.vns` hub |
| `[jump]` | Branch merging |
| `[goto]` | Cross-script navigation |
| `[gosub]`/`[return]` | Old man advice subroutine |
| `[if]`/`[elif]`/`[else]`/`[endif]` | Tower entrance, endings |
| `[set]`/`[inc]`/`[dec]`/`[flag]` | Throughout |
| `${variable}` interpolation | `chapter1.vns` look_around |
| `[bgm]`/`[bgm_fadeout]`/`[bgm_crossfade]` | Scene transitions |
| `[sfx]` | Key found, door open, clock chime |
| `[transition FADE]` | Scene changes |
| `[screen shake]`/`[screen flash]` | Dramatic moments |
| `[wait]` | Pacing |
| `[show]`/`[hide]` | Character display |
| `[save auto]` | Checkpoints |
| `[hud]` | Item acquisition notification |
| `[textspeed]` | Dramatic pacing |
| `[volume]` | Voice scene support |
| `[jes push]` | Minigame integration |
| `[end]` | Script termination |

---

## Next Steps

After completing this tutorial, explore:

- [Layered Character Presets](../presentation/vns-layered-charpresets.md) — build expression presets from sprite layers
- [Movable Character Layer Groups](../presentation/vns-movable-layer-groups.md) — move nested sprite parts such as heads, faces, and arms as one target
- [Inline Timelines](../integration/vns-interop.md#inline-timelines-puppeteer-compatible) — animate characters with JES keyframes
- [Localization](../runtime/vns-localization.md) — translate your script to multiple languages
- [Project Organization](vns-project-organization.md) — scale up to a full-length visual novel
- [Best Practices](vns-best-practices.md) — patterns for clean, maintainable scripts

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md) — feature overview and quick start
- [Commands Reference](../language/vns-commands.md) — complete command catalog
- [Choices & Branching](../language/vns-choices.md) — choice patterns
- [Variables & Conditions](../language/vns-variables.md) — variable system
- [Audio Commands](../presentation/vns-audio.md) — audio reference
- [Characters & Sprites](../presentation/vns-characters.md) — character system

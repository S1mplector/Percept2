# VNS Directives & Declarations

Directives are header-level instructions that configure your script before story content begins. They start with `@` and define characters, assets, variables, macros, and structural metadata.

Parser source: `core/src/main/java/com/jvn/core/vn/script/VnScriptParser.java`

---

## `@scenario`

Declares the scenario identity for this script.

```text
@scenario <id>
```

- Optional but recommended.
- Must appear before any content lines if used.
- Defaults to `untitled` when omitted.
- Only one `@scenario` per script (duplicates are parse errors).

**Examples:**

```vns
@scenario prologue
```

```vns
@scenario chapter_2_the_forest
```

---

## `@character`

Registers a display name for a speaker ID used in dialogue lines.

```text
@character <id> "Display Name"
```

- `id` is used in dialogue lines and commands.
- `"Display Name"` is what the renderer shows to the player.
- Multiple `@character` declarations can exist per script.
- If a speaker ID is used without a matching `@character`, the raw ID is shown as the display name.

**Examples:**

```vns
@character narrator "Narrator"
@character hero "Aria"
@character villain "Lord Wraith"
@character npc_guard "City Guard"
```

**Usage in dialogue:**

```vns
hero: Hello there!
# Renderer shows "Aria: Hello there!"

narrator: The wind howled.
# Renderer shows "Narrator: The wind howled."
```

---

## `@background`

Maps a background ID to an image file path.

```text
@background <id> <path>
```

- `id` is used in `[bg]` / `[background]` commands and transitions.
- `path` is relative to the project asset root.

**Examples:**

```vns
@background classroom assets/backgrounds/school/classroom_day.png
@background classroom_night assets/backgrounds/school/classroom_night.png
@background forest assets/backgrounds/nature/forest_path.png
@background sky_sunset assets/backgrounds/sky/sunset_wide.png
```

**Usage:**

```vns
[bg classroom]
narrator: The morning light streamed through the windows.

[transition FADE 800 classroom_night]
narrator: Hours passed in silence.
```

---

## `@charimg`

Adds an expression-specific sprite path for a character.

```text
@charimg <characterId> <expressionId> <path>
```

- `characterId` matches a declared `@character` (or creates an implicit one).
- `expressionId` is a named expression like `neutral`, `happy`, `angry`.
- `path` is a single image path, or multiple layered paths separated by `|`.

**Single-layer examples:**

```vns
@charimg hero neutral assets/characters/aria/aria_neutral.png
@charimg hero happy assets/characters/aria/aria_happy.png
@charimg hero angry assets/characters/aria/aria_angry.png
@charimg hero surprised assets/characters/aria/aria_surprised.png
```

**Multi-layer example (composited sprites):**

```vns
@charimg nora battle assets/characters/nora/body_base.png | assets/characters/nora/head_smile.png | assets/characters/nora/accessory_glasses.png
```

The renderer draws layers left-to-right (first layer is bottommost) on the same character slot. This is useful for mix-and-match character art pipelines.

**Usage:**

```vns
[show hero center neutral]
hero: I'm ready.

[show hero center happy]
hero: Let's go!
```

---

## `@charlayer`

Registers a named layer path for reuse in expression presets.

```text
@charlayer <characterId> <layerId> <path>
```

- Defines reusable parts (body, eyes, mouth, accessories) that can be mixed via `@charpreset`.
- Path cannot be empty (parse error).

**Examples:**

```vns
@charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
@charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
@charlayer lavender eyes_happy assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_happy.png
@charlayer lavender eyes_angry assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_angry.png
@charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
@charlayer lavender mouth_frown assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_frown.png
@charlayer lavender mouth_open assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_open.png
```

---

## `@charpreset`

Builds an expression from layer references and/or literal paths.

```text
@charpreset <characterId> <expressionId> <layerSpec>
```

### Layer reference rules

- `$layerId` — resolves from the **same** character's `@charlayer` declarations.
- `$otherChar.layerId` or `$otherChar:layerId` — resolves from **another** character's layers.
- Direct paths can be mixed with references, separated by `|`.
- Layer spec cannot be empty (parse error).

**Examples:**

```vns
# Basic preset from same-character layers
@charpreset lavender neutral $base | $eyes_neutral | $mouth_smile
@charpreset lavender happy $base | $eyes_happy | $mouth_smile
@charpreset lavender angry $base | $eyes_angry | $mouth_frown
@charpreset lavender talking $base | $eyes_neutral | $mouth_open

# Mixing a reference with a literal path
@charpreset lavender special $base | $eyes_happy | assets/characters/lavender/mouth/custom_grin.png

# Cross-character layer reference
@charpreset twin_sister neutral $base | $lavender.eyes_neutral | $mouth_smile
```

**Full workflow example:**

```vns
@character lavender "Lavender"
@charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
@charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
@charlayer lavender eyes_happy assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_happy.png
@charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
@charlayer lavender mouth_open assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_open.png

@charpreset lavender neutral $base | $eyes_neutral | $mouth_smile
@charpreset lavender happy $base | $eyes_happy | $mouth_smile
@charpreset lavender talking $base | $eyes_neutral | $mouth_open

@label start
[show lavender center neutral]
lavender: Hello there.

[show lavender center happy]
lavender: Nice to meet you!
```

---

## `@label`

Declares a named jump target in the script.

```text
@label <name>
```

- Label name pattern: `^[A-Za-z_][A-Za-z0-9_.:-]*$`
- Duplicate labels in the same script are parse errors.
- Labels are used by `[jump]`, choices (`-> target`), and `[if ... goto ...]`.

**Legacy form (still accepted):**

```text
label <name>
```

**Examples:**

```vns
@label start
narrator: The story begins.

@label chapter1_intro
narrator: Chapter 1.

@label route_a.ending
narrator: Route A ending.

@label hub:return_point
narrator: Back at the hub.
```

---

## `@var`

Declares and initializes a variable at parse time.

```text
@var key = value
@var key value
@var flagOnly
```

- Emits an equivalent `[set key value]` command at the position it appears.
- `@var flagOnly` (no value) defaults to `true`, making it a boolean flag.

**Examples:**

```vns
# Set a numeric variable
@var score = 0
@var player_level = 1

# Set a string variable
@var player_name = Alice
@var difficulty = normal

# Set boolean flags
@var seen_intro
@var has_key
@var debug_mode = false
```

**Equivalent runtime behavior:**

```vns
# These two are identical:
@var score = 0
[set score 0]
```

---

## `@define`

Defines a compile-time macro for text substitution.

```text
@define KEY value
@define KEY "quoted value"
```

- Substitutes `${KEY}` tokens in **all subsequent script lines** during parsing.
- This is parser-time text replacement (not runtime interpolation).
- Useful for build-level constants like version strings, character name overrides, or path prefixes.

**Examples:**

```vns
@define PLAYER_NAME "Alice"
@define VERSION "1.2.0"
@define ASSET_ROOT assets/demo

@character hero "${PLAYER_NAME}"
@background park ${ASSET_ROOT}/backgrounds/park.png
@charimg hero neutral ${ASSET_ROOT}/characters/hero/neutral.png

@label start
hero: My name is ${PLAYER_NAME}. (Build ${VERSION})
```

After macro substitution, the parser sees:

```vns
@character hero "Alice"
@background park assets/demo/backgrounds/park.png
hero: My name is Alice. (Build 1.2.0)
```

**Key difference from `${var}` runtime interpolation:**
- `@define` runs at **parse time** — the substituted text is baked into the scenario.
- `${variableName}` in dialogue runs at **runtime** — it reads from the live variable map.

---

## `@include`

Includes another VNS file's content at the current position.

```text
@include <path>
```

- Requires an include resolver (provided by runtime and editor).
- Path is resolved relative to the current source file.
- Include cycles are detected and rejected with a parse error.
- Useful for shared character declarations, common labels, or reusable dialogue segments.

**Examples:**

```vns
# Include shared character definitions
@include common/characters.vns

# Include a reusable dialogue segment
@include common/opening_narration.vns

# Include system-level helpers
@include system/debug_commands.vns
```

**Typical project structure using includes:**

```text
scripts/
  common/
    characters.vns     # shared @character and @charimg declarations
    backgrounds.vns    # shared @background declarations
  story/
    prologue.vns       # @include common/characters.vns
    chapter1.vns       # @include common/characters.vns
```

**`common/characters.vns`:**

```vns
@character hero "Aria"
@character mentor "Professor Vale"
@charimg hero neutral assets/characters/aria/neutral.png
@charimg hero happy assets/characters/aria/happy.png
@charimg mentor neutral assets/characters/vale/neutral.png
```

**`story/prologue.vns`:**

```vns
@scenario prologue
@include common/characters.vns
@include common/backgrounds.vns

@label start
[bg classroom]
[show hero center neutral]
hero: Here we go.
```

---

## Directive Ordering Rules

1. `@scenario` must come before any content (if used).
2. `@define` and `@include` are processed before other directives on each line.
3. `@character`, `@background`, `@charimg`, `@charlayer`, `@charpreset` can appear in any order relative to each other.
4. `@var` can appear anywhere — it emits a set command at that position.
5. `@label` must be unique within the script (including included files).

**Recommended ordering:**

```vns
@scenario my_story
@define PLAYER "Alice"
@include common/characters.vns

@character narrator "Narrator"
@background park assets/backgrounds/park.png
@charimg hero neutral assets/characters/hero/neutral.png

@var score = 0

@label start
# ... story content ...
```

---

## Related Docs

- [VNS Overview](vns-scripting.md)
- [Characters & Sprites](vns-characters.md) — detailed character system docs
- [Variables & Conditions](vns-variables.md) — runtime variable system
- [Parsing Internals](vns-parsing.md) — how directives are processed

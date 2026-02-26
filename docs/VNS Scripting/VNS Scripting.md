# VNS Scripting

VNS is JVN's line-oriented visual novel scripting DSL.

Parser source of truth:
- `core/src/main/java/com/jvn/core/vn/script/VnScriptParser.java`

Condition grammar:
- `core/src/main/java/com/jvn/core/vn/VnConditionEvaluator.java`

## Quick Start

Create a script:

```vns
@scenario demo
@character narrator "Narrator"

@label start
Narrator: Hello from VNS.
[end]
```

Run with runtime:

```bash
./gradlew :runtime:run --args='--script demo.vns'
```

## Script Structure

VNS is read top-to-bottom.

Ignored lines:
- blank lines
- lines starting with `#`

Typical order:
1. declarations (`@scenario`, `@character`, `@background`, etc.)
2. labels
3. dialogue/choices/commands

## Directives

### `@scenario`

```text
@scenario <id>
```

- optional but recommended
- defaults to `untitled` when omitted
- must appear before other content if used

### `@character`

```text
@character <id> "Display Name"
```

Registers display name mapping for speaker ids.

### `@background`

```text
@background <id> <path>
```

Maps background id to image path.

### `@charimg`

```text
@charimg <characterId> <expressionId> <path>
```

Adds expression-specific sprite path for a character.

You can also provide multiple layered image paths for one expression by separating them with `|`:

```text
@charimg nora battle assets/characters/nora/body_base.png | assets/characters/nora/head_smile.png | assets/characters/nora/accessory_glasses.png
```

The renderer draws those layers in order on the same character slot.

### `@charlayer`

```text
@charlayer <characterId> <layerId> <path>
```

Registers a named layer path for reuse in expression presets.

### `@charpreset`

```text
@charpreset <characterId> <expressionId> <layerSpec>
```

Builds an expression from layer references and/or literal paths.

- Layer references start with `$`.
- `$layerId` resolves from the same character.
- `$otherChar.layerId` or `$otherChar:layerId` resolves from another character.
- `layerSpec` can mix references and direct paths separated by `|`.

Example:

```text
@charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png
@charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png
@charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
@charpreset lavender talking $base | $eyes_neutral | $mouth_smile
```

### `@label`

```text
@label <name>
```

- jump target declaration
- label name pattern: `^[A-Za-z_][A-Za-z0-9_.:-]*$`
- duplicate labels are parser errors

Legacy label form is still accepted:

```text
label start
```

### `@var`

```text
@var key = value
@var key value
@var flagOnly
```

- emits equivalent variable command at parse time
- `@var flagOnly` defaults to `true`

### `@define`

```text
@define PLAYER_NAME "Alice"
@define DIFF hard
```

Then `${PLAYER_NAME}` tokens in script text are macro-substituted during parse.

### `@include`

```text
@include common/opening.vns
```

- include resolver based
- include cycles are detected and rejected

## Dialogue Forms

### Colon form

```text
Speaker: text
```

### Quoted form

```text
speaker "quoted text with escapes"
```

Both resolve display name through registered `@character` where available.

## Choices

### Multi-line choice syntax

```text
> Choice text -> targetLabel
> Another choice
> Conditional choice [if score >= 10] -> reward
```

### Inline choice command

```text
[choice Continue->next | Exit->ending]
```

Choice condition suffix is supported in both forms:

```text
[choice Continue->next [if flags.ready] | Exit->end]
```

## Command Reference

All commands use `[ ... ]` form.

### Scene/background flow

```text
[background <bgId>]
[bg <bgId>]
[jump <label>]
[end]
```

### Audio playback

```text
[bgm <track>]
[bgm_stop]
[bgm_fadeout [ms]]
[sfx <track>]
[sfx_stop]
[voice <track>]
[voice_stop]
[audio_stop_all]
```

### Advanced audio control

```text
[bgm_pause]
[bgm_resume]
[bgm_seek <seconds>]
[bgm_crossfade <track> <ms> [loop]]
[audio_pause_all]
[audio_resume_all]
[audio <payload>]
```

`[audio <payload>]` forwards directly to the audio interop provider for advanced backend control.

### Timing and visuals

```text
[wait <ms>]
[show <charId> <pos> [expression]]
[hide <charId>]
[transition <type> [durationMs] [bgId]]
[screen shake [intensity] [durationMs]]
[screen flash [strength] [durationMs] [r g b]]
```

Character positions:
- full: `LEFT`, `CENTER`, `RIGHT`, `FAR_LEFT`, `FAR_RIGHT`
- shortcuts: `L`, `C`, `R`, `FL`, `FR`

Transition types:
- `FADE`, `DISSOLVE`, `CROSSFADE`, `SLIDE_LEFT`, `SLIDE_RIGHT`, `WIPE`

### Settings and player modes

```text
[textspeed <msPerChar>]
[autodelay <msBetweenLines>]
[volume bgm|sfx|voice <0..1>]

[skip [on|off|toggle]]
[auto [on|off|toggle]]

[ui [hide|show|toggle]]
[history [toggle|show|hide]]
[history scroll <lines>]
[history clear]

[save]
[quickload]
[hud <message>]
```

### Variable and flow helpers

```text
[set key value]
[inc key [delta]]
[dec key [delta]]
[flag key]
[unflag key]
[clear key]
```

Conditional forms:

```text
[if <condition>]
  ...
[elif <condition>]
  ...
[else]
  ...
[endif]
```

and shortcut jump:

```text
[if <condition> goto <label>]
```

### Menu and script switching

```text
[menu <payload>]
[settings]
[mainmenu [script]]
[load <scriptOrId>]
[goto <labelOrArc:label>]
```

### Interop commands

```text
[call <provider> <payload>]
[jes <payload>]
[java <payload>]

[jes_push <script.jes>]
[jes_replace <script.jes>]
[jes_pop]
[jes_call <name> k=v ...]
```

## Character Motion and Global Position Workflows

For character choreography beyond plain `[show]`, use the `char` provider commands:

```text
[char <characterId> global on|off]
[char <characterId> at <position>]
[char <characterId> move <position> [expression]]
[char <characterId> expression <expression>]
[char <characterId> hide]
```

Example:

```vns
@label intro
[show codel center neutral]
[char codel global on]

Codel: I will keep my own anchor slot.
[char codel at center]
[char codel move right smile]
Codel: Now I moved to the right and switched expression.
```

Recommended usage pattern:
1. Enable global mode once for a character.
2. Set/adjust anchor via `at`.
3. Use `move` for in-scene motion.
4. Use `expression` for facial changes without changing slot.

This avoids duplicate sprite slots and keeps long scenes visually stable.

## Inline Timeline Blocks (Puppeteer-Compatible)

VNS supports inline JES timeline blocks with `timeline { ... }` syntax.
The parser converts these blocks to runtime `jes_timeline_inline` interop.

```vns
timeline {
  entity "codel" {
    0ms { x: 640, y: 396 }
    300ms { x: 780, y: 396, easing: ease_out }
  }
  cameraMove 300ms 0 0 0.92
  playAudio "assets/audio/sfx/whoosh.ogg"
}
```

Use named registry timelines when you want reusable animation clips:

```vns
[call jes_timeline codel_entrance]
```

Use inline blocks when you want one-off animation close to story text.

## Conditions

Condition syntax supports:

- logical: `&&`, `||`, `!`, `and`, `or`, `not`
- comparison: `==`, `!=`, `>`, `<`, `>=`, `<=`
- parentheses
- literals: numbers, booleans, quoted strings
- identifiers resolved from VN variable map

Examples:

```text
flags.ready
score >= 10 && lives > 0
not seen_intro
(playerClass == "mage" and mana >= 20) or debug
```

## Variable Interpolation in Dialogue/Choice/HUD

Runtime interpolation syntax:

```text
${variableName}
```

Example:

```vns
Narrator: Welcome back, ${playerName}.
> Spend ${coins} coins -> shop
[hud Score: ${score}]
```

Notes:
- missing vars resolve to empty string
- interpolation is single-pass
- use `${...}` form to avoid collisions with text-effect tags like `{shake}`

## Parser Strictness and Diagnostics

VNS parser is intentionally strict. It throws parse errors for:

- unknown commands
- malformed command args
- duplicate labels
- undefined referenced labels (jump/choice/if-goto)
- invalid condition syntax
- invalid `if/elif/else/endif` structure
- unclosed conditional blocks
- unrecognized non-empty syntax lines

This strictness is also surfaced in editor diagnostics.

## Example: Branching + Conditions + Interop

```vns
@scenario tutorial
@character narrator "Narrator"
@character hero "Hero"
@background room assets/backgrounds/room.png

@label start
[bg room]
[set score 0]
Narrator: Welcome, ${playerName}.
Hero: Let's begin.

> Play minigame -> minigame
> Skip ahead [if debug] -> ending

@label minigame
[jes push game/minigames/aim.jes label after_game with stage=1]

@label after_game
Narrator: You returned with score ${score}.
[if score >= 100 goto good]
[jump bad]

@label good
Narrator: Great result.
[end]

@label bad
Narrator: Try again.
[end]
```

## Related Docs

- Parsing internals: `docs/VNS Scripting/VNS Parsing.md`
- Runtime interop: `docs/Runtime/Interop.md`
- JES language: `docs/JES Scripting/JES Scripting.md`
- Timeline integration: `docs/Timeline Scripting/Timeline Scripting.md`

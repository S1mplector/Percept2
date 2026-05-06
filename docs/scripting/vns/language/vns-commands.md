# VNS Commands Reference

Complete catalog of all VNS commands. Commands use `[command args]` syntax.

Parser source: `modules/core/src/main/java/com/jvn/core/vn/script/VnScriptParser.java`

---

## Scene & Background

### `[background <bgId>]` / `[bg <bgId>]`

Changes the current background instantly.

```vns
[bg classroom]
[background forest_night]
```

The `bgId` must match a declared `@background` or a known asset path.

---

## Flow Control

### `[jump <label>]`

Unconditionally jumps to a label.

```vns
[jump chapter2]
[jump ending_a]
```

### `[end]`

Terminates the scenario. The runtime returns to menu or exits.

```vns
@label good_ending
narrator: And they lived happily ever after.
[end]
```

### `[goto <labelOrArc:label>]`

Jumps to a label, optionally in another script arc.

```vns
[goto chapter2_start]
[goto Chapter2:beginning]
```

When using `Arc:label` form, runtime loads the target script and jumps to that label.

### `[call <label>]` (subroutine)

Pushes the current position onto the call stack and jumps to a label. Use with `[return]`.

```vns
[call shared_cutscene]
narrator: Back from the cutscene.

@label shared_cutscene
narrator: This is a reusable cutscene.
[return]
```

### `[return]`

Pops the call stack and returns to the position after the last `[call]`.

```vns
@label helper_routine
narrator: Doing some shared work.
[return]
```

---

## Character Display

### `[show <charId> <position> [expression] [layer]]`

Shows a character at a named position with an optional expression and layer order.

```vns
[show hero center]
[show hero center happy]
[show hero left angry]
[show villain right evil 10]
[show hero center @happy]
[show hero center @happy+$glasses]
[show hero center $base+$eyes_happy+$mouth_smile]
```

**Predefined positions:**

| Full Name | Shortcut | Typical Screen X (center) |
|-----------|----------|--------------------------|
| `FAR_LEFT` | `FL` | ~10% |
| `LEFT` | `L` | ~25% |
| `CENTER` | `C` | ~50% |
| `RIGHT` | `R` | ~75% |
| `FAR_RIGHT` | `FR` | ~90% |

**Named custom positions:**

If you've declared a custom position with `@position`, use its name directly:

```vns
@position balcony 0.3 0.6

[show hero balcony neutral]
```

**Inline custom positions (`at x,y[,z]`):**

Place a character at arbitrary screen coordinates without pre-declaring a position:

```vns
[show hero at 0.3,0.5]                # x=0.3, y=0.5
[show hero at 0.3,0.5 happy]          # with expression
[show hero at 0.3,0.5,10]             # with layer order (z=10)
[show hero at 0.3,0.5,10 happy]       # with both
```

- `x` — horizontal screen fraction (`0.0` = left, `1.0` = right)
- `y` — vertical screen fraction (`0.0` = top, `1.0` = bottom)
- `z` — optional layer order integer (higher = in front)

**Layer order:** optional integer controlling z-depth (higher = in front). Default is position-based:
- `FAR_LEFT` → -20
- `LEFT` → -10
- `CENTER` → 0
- `RIGHT` → 10
- `FAR_RIGHT` → 20
- Custom positions → 0 (unless `z` is specified inline)

```vns
# hero appears in front of villain
[show villain center neutral 0]
[show hero center determined 10]

# place a character at a precise spot with layer order
[show narrator at 0.5,0.3,20 thinking]
```

### `[move <charId> <position> [expression] [easing] [durationMs]]`

Moves a character to a new position. The animation style depends on whether **global position mode** is enabled for that character:

- **Global mode ON** — the character **slides** from its current position to the target (a true move tween).
- **Global mode OFF** (default) — the character is removed from its old slot and **fades in** at the target position from the screen edge (an entrance animation, not a slide).

To get a smooth slide, enable global mode first with `[char <charId> global on]`. See [Global Position Mode](../presentation/vns-characters.md#global-position-mode) for details.

```vns
# Without global mode — entrance animation at new position
[show hero left neutral]
[move hero right]               # hero disappears from left, fades in from right edge

# With global mode — smooth slide
[char hero global on]
[show hero left neutral]
[move hero right]               # hero slides from left to right
[move hero center happy]        # slides to center, switches to happy
[move hero far_left neutral ease_out_bounce 500]  # with easing + duration
[move hero center @happy+$glasses ease_out_back 500]
```

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `charId` | Yes | Character ID to move |
| `position` | Yes | Target position (predefined, named `@position`, or `at x,y`) |
| `expression` | No | Expression to switch to after move |
| `easing` | No | Easing curve for the slide animation (see table below) |
| `durationMs` | No | Duration in ms (default: ~320ms for slides, ~200ms for entrances) |

**With custom positions:**

```vns
@position balcony 0.3 0.6

[move hero balcony]
[move hero at 0.8,0.4 smile]
[move hero at 0.15,0.85 neutral ease_out_back 600]
```

**Easing types** (case-insensitive):

| Easing | Description |
|--------|-------------|
| `linear` | Constant speed |
| `ease_in_quad` | Accelerate (quadratic) |
| `ease_out_quad` | Decelerate (quadratic) |
| `ease_in_out_quad` | Accelerate then decelerate |
| `ease_in_cubic` | Accelerate (cubic) |
| `ease_out_cubic` | Decelerate (cubic) |
| `ease_in_out_cubic` | Accelerate then decelerate (cubic) |
| `ease_in_quart` | Accelerate (quartic) |
| `ease_out_quart` | Decelerate (quartic) |
| `ease_in_out_quart` | Accelerate then decelerate (quartic) |
| `ease_in_expo` | Accelerate (exponential) |
| `ease_out_expo` | Decelerate (exponential) |
| `ease_in_out_expo` | Accelerate then decelerate (exponential) |
| `ease_in_sine` | Accelerate (sine) |
| `ease_out_sine` | Decelerate (sine) |
| `ease_in_out_sine` | Accelerate then decelerate (sine) |
| `ease_in_elastic` | Elastic wind-up |
| `ease_out_elastic` | Elastic overshoot |
| `ease_in_out_elastic` | Elastic both ends |
| `ease_in_back` | Pull back before moving |
| `ease_out_back` | Overshoot then settle |
| `ease_in_out_back` | Pull back and overshoot |
| `ease_in_bounce` | Bounce at start |
| `ease_out_bounce` | Bounce at end |
| `ease_in_out_bounce` | Bounce both ends |

See also `[char <charId> move ...]` in [Characters & Sprites](../presentation/vns-characters.md) for the provider-command form.

### `[hide <charId>]`

Hides a character with an exit animation.

```vns
[hide hero]
[hide villain]
```

---

## Timing

### `[wait <ms>]`

Pauses execution for the specified duration in milliseconds.

```vns
[wait 500]
narrator: After a brief pause...
[wait 1500]
narrator: ...the story continues.
```

---

## Audio

### `[bgm <track> [options]]`

Plays background music (loops by default). Supports optional keyword arguments for loop and volume control.

```vns
[bgm assets/audio/bgm/main_theme.ogg]
[bgm assets/audio/bgm/fanfare.ogg loop=false]
[bgm assets/audio/bgm/calm.ogg vol=0.6]
[bgm assets/audio/bgm/battle.ogg loop=true vol=0.8]
```

**Options:**

| Option | Values | Default | Description |
|--------|--------|---------|-------------|
| `loop` | `true`/`false`/`on`/`off`/`1`/`0` | `true` | Whether the track loops |
| `vol` / `volume` | `0.0` – `1.0` | current | Set BGM volume alongside playback |

**Shorthand:** a bare boolean as the second argument sets loop (backward-compatible):

```vns
[bgm assets/audio/bgm/victory.ogg false]   # equivalent to loop=false
```

### `[bgm_stop]`

Stops BGM immediately.

### `[bgm_fadeout [ms]]`

Fades out BGM over the specified duration (default varies).

```vns
[bgm_fadeout 2000]
```

### `[bgm_pause]` / `[bgm_resume]`

Pauses and resumes BGM playback.

### `[bgm_seek <seconds>]`

Seeks BGM to the specified position.

```vns
[bgm_seek 30.5]
```

### `[bgm_crossfade <track> <ms> [loop]]`

Crossfades from current BGM to a new track.

```vns
[bgm_crossfade assets/audio/bgm/battle_theme.ogg 1500]
[bgm_crossfade assets/audio/bgm/calm.ogg 2000 true]
```

### `[sfx <track>]`

Plays a sound effect (one-shot).

```vns
[sfx assets/audio/sfx/door_open.ogg]
[sfx assets/audio/sfx/explosion.ogg]
```

### `[sfx_stop]`

Stops current SFX.

### `[voice <track>]`

Plays a voice clip.

```vns
[voice assets/audio/voices/hero_line_42.ogg]
```

### `[voice_stop]`

Stops current voice playback.

### `[audio_stop_all]`

Stops all audio channels (BGM, SFX, voice).

### `[audio_pause_all]` / `[audio_resume_all]`

Pauses or resumes all audio channels.

### `[audio <payload>]`

Forwards raw payload to the audio interop provider for advanced control.

See [Audio Commands](../presentation/vns-audio.md) for detailed audio documentation.

---

## Transitions

### `[transition <type> [durationMs] [bgId]]`

Plays a visual transition effect, optionally changing the background.

```vns
[transition FADE 800]
[transition DISSOLVE 1200 forest_night]
[transition CROSSFADE 1000 sunset_beach]
[transition SLIDE_LEFT 600 next_room]
[transition SLIDE_RIGHT 600 prev_room]
[transition WIPE 800 new_scene]
```

**Transition types:** `FADE`, `DISSOLVE`, `CROSSFADE`, `SLIDE_LEFT`, `SLIDE_RIGHT`, `WIPE`

---

## Screen Effects

### `[screen shake [intensity] [durationMs]]`

Triggers a screen shake effect.

```vns
[screen shake]              # defaults: intensity=8, duration=300ms
[screen shake 12 500]       # strong shake for 500ms
[screen shake 4 200]        # subtle shake
```

### `[screen flash [strength] [durationMs] [r g b]]`

Triggers a screen flash overlay.

```vns
[screen flash]                    # white flash, defaults
[screen flash 0.8 200]           # strong white flash
[screen flash 0.6 300 255 0 0]  # red flash
[screen flash 0.5 150 0 0 255]  # blue flash
```

### `[screen clear]`

Force-clears any active shake or flash effects immediately.

```vns
[screen clear]
```

---

## Settings & Player Modes

### `[textspeed <msPerChar>]`

Sets the text reveal speed.

```vns
[textspeed 20]   # fast
[textspeed 50]   # normal
[textspeed 80]   # slow
```

### `[autodelay <ms>]`

Sets the auto-advance delay between lines.

```vns
[autodelay 2000]  # 2 seconds between auto-advances
```

### `[volume bgm|sfx|voice <0..1>]`

Adjusts volume for a channel.

```vns
[volume bgm 0.5]
[volume sfx 0.8]
[volume voice 1.0]
```

### `[skip [on|off|toggle]]`

Controls skip mode (fast-forward through read text).

```vns
[skip on]
[skip off]
[skip toggle]
```

### `[auto [on|off|toggle]]`

Controls auto-advance mode.

```vns
[auto on]
[auto off]
```

---

## UI Control

### `[ui [hide|show|toggle]]`

Controls dialogue UI visibility (textbox, name plate, etc.).

```vns
[ui hide]      # hide UI (CG viewing mode)
[ui show]      # restore UI
[ui toggle]    # toggle visibility
```

### `[history [toggle|show|hide]]`

Controls the history/backlog overlay.

```vns
[history show]
[history hide]
[history toggle]
[history scroll 5]    # scroll back 5 lines
[history clear]       # clear scroll position
```

### `[visualizer ...]` / `[viz]`

Controls the in-scene audio visualizer layer (off by default). `[viz]` is a shorthand alias.

```vns
[visualizer on]
[visualizer on bars=48]
[visualizer set color=#7de2ff z=-15]
[visualizer off]
[visualizer toggle]
[visualizer status]
[visualizer reset]
[viz on]              # shorthand alias
[viz toggle]
```

Useful options:

- `bars=<8..96>`
- `color=<css-color|auto>`
- `accent=<css-color|auto>`
- `alpha=<0.1..1.0>`
- `glow=<on|off>`
- `style=<dynamic|minimal>`
- `height=<0.2..1.0>`
- `z=<int>` / `z-index=<int>` for layer order relative to character sprites

### `[hud <message>]`

Displays a temporary on-screen message. Messages support `${var}` variable interpolation and ICU formatting for dynamic content.

```vns
[hud Saved!]
[hud Chapter 2 — The Forest]
[hud Score: ${score}]
[hud {score, plural, one{# enemy} other{# enemies}} defeated!]
```

**Behavior:**
- Variables are resolved at the time the command executes, using current VN state.
- Missing variables resolve to empty string.
- HUD messages auto-expire after 2000ms by default.

For more advanced formatting (plurals, selection, number formatting), see **[Text Formatting & ICU](vns-text-formatting.md)**.

---

## Save & Load

### `[save]`

Triggers a quick save.

```vns
[save]
```

### `[quickload]`

Loads the last quick save.

```vns
[quickload]
```

---

## Variables

### `[set key value]`

Sets a variable.

```vns
[set score 0]
[set player_name "Alice"]
[set has_key true]
```

### `[inc key [delta]]` / `[dec key [delta]]`

Increments or decrements a numeric variable.

```vns
[inc score]       # +1
[inc score 5]     # +5
[dec lives]       # -1
[dec gold 50]     # -50
```

### `[flag key]` / `[unflag key]`

Sets a boolean flag to true/false.

```vns
[flag seen_intro]
[unflag door_locked]
```

### `[clear key]`

Removes a variable entirely.

```vns
[clear temp_data]
```

### `[mul key factor]`

Multiplies a numeric variable by a factor.

```vns
[mul score 2]       # score *= 2
[mul gold 1.5]      # gold *= 1.5
```

### `[div key divisor]`

Divides a numeric variable by a divisor. Division by zero is silently ignored.

```vns
[div gold 2]        # gold /= 2
```

### `[toggle key]`

Flips a boolean variable between `true` and `false`.

```vns
[toggle door_locked]
[toggle debug_mode]
```

### Character framing overrides

Special runtime variables control character rendering:

```vns
[set ui.characterHeightFactor 1.28]
[set ui.characterBaselineY 1.42]
```

See [Variables & Conditions](vns-variables.md) for detailed documentation.

### Persistent Variables

`[persistent]` manages cross-save persistent variables that survive across playthroughs.

```vns
[persistent set endings_seen 1]
[persistent inc endings_seen]
[persistent dec endings_seen]
[persistent flag true_route_unlocked]
[persistent unflag true_route_unlocked]
[persistent toggle new_game_plus]
[persistent clear endings_seen]
[persistent load endings_seen]          # copy persistent → session variable
[persistent load endings_seen my_var]   # copy persistent → named session variable
[persistent store endings_seen]         # copy session variable → persistent
[persistent store endings_seen my_var]  # copy named session variable → persistent
[persistent reload]                     # reload persistent store from disk
[persistent reset]                      # clear all persistent data
```

Persistent values are automatically mirrored into the session variable map as `persistent.<key>`, so you can read them in conditions:

```vns
[if persistent.endings_seen >= 3 goto true_route]
```

See [Variables & Conditions](vns-variables.md) for detailed documentation.

---

## Stage Lighting

### `[stage <presetId>]`

Activates a stage lighting preset loaded via `@stagepreset`. The preset controls scene lighting, background grading, occluders, and response zones at runtime.

```vns
[stage sunset_park]
[stage night_cave]
[stage preset=indoor_warm]
```

### `[stage clear|off|none]`

Deactivates the current stage preset.

```vns
[stage clear]
[stage off]
[stage none]
```

**Options:**

| Option | Aliases | Description |
|--------|---------|-------------|
| `preset` | `id`, `name`, `mode` | The stage preset ID to activate |

If only a bare token is provided (no `key=value`), it is used as the preset ID directly.

When authoring in the editor, Puppeteer Launcher tracks the active stage at the caret. Launching Puppeteer from that line carries the lighting context into the animation project, and named exports preserve it in Puppeteer metadata comments.

See [Directives](vns-directives.md#stagepreset--stage-lighting-preset) for `@stagepreset` declaration syntax.

---

## Conditionals

### Block form

```vns
[if score >= 100]
  narrator: Excellent score!
[elif score >= 50]
  narrator: Not bad.
[else]
  narrator: Try harder.
[endif]
```

`[/if]` is accepted as an alias for `[endif]`.

### Shortcut jump

```vns
[if score >= 100 goto good_ending]
[if has_key goto unlock_door]
[jump default_path]
```

See [Variables & Conditions](vns-variables.md) for the complete condition reference.

---

## Menu & Script Navigation

### `[menu <payload>]`

Opens a menu scene.

```vns
[menu settings]
[menu save]
[menu load]
[menu main]
```

### `[settings]`

Shorthand to open the settings menu.

### `[mainmenu [script]]`

Returns to the main menu, optionally setting the default script.

```vns
[mainmenu]
[mainmenu scripts/story/prologue.vns]
```

### `[load <scriptOrId>]`

Loads a different VNS script.

```vns
[load scripts/story/chapter2.vns]
```

---

## Interop Commands

### `[gosub <label>]`

Subroutine call — pushes the current position onto the call stack and jumps to a label. Use with `[return]`. This is the recommended form for subroutine calls (distinct from `[call <provider> ...]` which is for interop).

```vns
[gosub shared_cutscene]
narrator: Back from the cutscene.

@label shared_cutscene
narrator: This is reusable.
[return]
```

### `[call <provider> <payload>]`

Calls an interop provider with a payload.

```vns
[call jes_timeline hero_entrance]
[call hud Achievement unlocked!]
```

### `[jes <payload>]`

JES scene interop (push/replace/pop/call).

```vns
[jes push game/minigames/puzzle.jes label after with difficulty=hard]
[jes pop]
```

### `[java <payload>]`

Calls a static Java method via reflection.

```vns
[java com.example.GameHooks#beginEncounter goblin 3]
```

### `[java]` ... `[/java]` (Inline Java Block)

Embeds a multi-line Java code block directly in the script. The code is compiled in-memory
and executed with access to the current `VnScene` and `VnState`. Similar to how Ren'Py
supports Python blocks inside `.rpy` files.

The generated method signature is:
```java
public static void execute(VnScene scene) throws Exception {
    VnState state = scene.getState();
    // <your code here>
}
```

**Example:**
```vns
[java]
int hp = ((Number) state.getVariable("hp")).intValue();
hp = Math.max(0, hp - 10);
state.setVariable("hp", hp);
if (hp <= 0) {
    state.setVariable("game_over", true);
}
[/java]
```

- Blank lines and comments (`//`, `/* */`) are preserved inside the block.
- Lines starting with `#` are **not** treated as VNS comments inside `[java]` blocks.
- Compiled classes are cached by content hash (MD5) to avoid recompilation.
- Only available when running on a JDK (requires `javax.tools.JavaCompiler`).
- Import `com.jvn.core.vn.*` and `com.jvn.core.vn.script.Vn` are provided automatically.
- Compiler errors are remapped to the original script line numbers.

### `$` (Single-Line Java Shorthand)

Execute a single line of Java without opening a full block. Equivalent to a one-line `[java]...[/java]`.

```vns
$ state.setVariable("hp", 100);
$ Vn.setVar("score", Vn.intVar("score") + 10);
$ Vn.playBgm("victory_theme");
```

### `@jimport` (Custom Java Imports)

Declare additional import statements for all subsequent `[java]` blocks and `$` lines in the same script. Placed before blocks that need the imports.

```vns
@jimport java.util.Random
@jimport com.mygame.combat.DamageCalculator

[java]
Random rng = new Random();
int damage = DamageCalculator.roll(rng, 5, 20);
Vn.setVar("damage", damage);
[/java]
```

### `@bind` (Variable Bridge)

Declare variable bindings that automatically read from `VnState` before execution and write back after. Eliminates manual `getVariable`/`setVariable` boilerplate.

Format: `@bind type:name` where type is a Java type (int, double, boolean, String, etc.)

```vns
@bind int:hp
@bind int:defense
@bind String:playerName

[java]
hp = Math.max(0, hp - (20 - defense));
Vn.hud(playerName + " takes damage! HP: " + hp);
[/java]
```

The variable bridge generates preamble/epilogue code:
- **Read:** extracts the typed value from state before your code runs
- **Write:** stores the value back into state after your code completes

### `Vn.*` Facade API

A static facade class available in all Java blocks, providing concise access to engine operations:

| Method | Description |
|--------|-------------|
| `Vn.setVar(key, value)` | Set a game variable |
| `Vn.getVar(key)` / `Vn.getVar(key, default)` | Get a variable |
| `Vn.intVar(key)` / `Vn.doubleVar(key)` / `Vn.strVar(key)` / `Vn.boolVar(key)` | Typed getters |
| `Vn.hasVar(key)` | Check if variable exists |
| `Vn.show(charId, expression, position)` | Show character sprite |
| `Vn.hide(charId)` | Hide character |
| `Vn.playBgm(trackId)` / `Vn.stopBgm()` | Background music |
| `Vn.playSfx(trackId)` | Sound effect |
| `Vn.playVoice(trackId)` / `Vn.stopVoice()` | Voice playback |
| `Vn.setBackground(bgId)` | Change background |
| `Vn.getBackground()` | Get current background ID |
| `Vn.jump(label)` | Jump to label |
| `Vn.hud(message)` / `Vn.hud(message, durationMs)` | Show HUD message |
| `Vn.setPersistent(key, value)` / `Vn.getPersistent(key)` | Persistent store |
| `Vn.currentScene()` / `Vn.currentState()` | Direct access |
| `Vn.log(message)` / `Vn.log(format, args...)` | Debug logging (varargs overload) |
| `Vn.screenShake(intensity, durationMs)` | Trigger screen shake effect |
| `Vn.flash(r, g, b, strength, durationMs)` / `Vn.flash(durationMs)` | Trigger screen flash (white if RGB omitted) |
| `Vn.isVisible(characterId)` | Check if character is visible |
| `Vn.getExpression(characterId)` | Get character's current expression |
| `Vn.getPosition(characterId)` | Get character's current position |
| `Vn.clearCharacters()` | Hide all characters |
| `Vn.hideUi()` / `Vn.showUi()` / `Vn.toggleUi()` / `Vn.isUiHidden()` | UI visibility control |
| `Vn.isComplete()` | Check if scenario is complete |
| `Vn.nodeIndex()` | Get current node index |
| `Vn.wait(durationMs)` | Pause execution for specified duration |

### `[init java]` ... `[/init]` (Load-Time Initialization)

Define static utility methods that are compiled once at load time and persist across the scenario. 
The body becomes the content of a generated class. If it contains a `public static void init(VnScene scene)` method, that method is called immediately at load time.

```vns
[init java]
public static int clampHp(int hp) {
    return Math.max(0, Math.min(999, hp));
}

public static void init(VnScene scene) {
    // Runs once when scenario loads
    scene.getState().setVariable("max_hp", 999);
}
[/init]
```

### `[java class ClassName]` ... `[/java]` (Reusable Class Definitions)

Define a named class that becomes available to all subsequent `[java]` blocks in the same scenario. Useful for combat systems, utility libraries, or data structures.

```vns
[java class CombatUtils]
public static int calculateDamage(int atk, int def) {
    return Math.max(1, atk - def);
}

public static boolean isCritical(double rate) {
    return Math.random() < rate;
}
[/java]

# Later in the script...
[java]
int dmg = CombatUtils.calculateDamage(Vn.intVar("atk"), Vn.intVar("def"));
if (CombatUtils.isCritical(0.15)) dmg *= 2;
Vn.setVar("last_damage", dmg);
[/java]
```

### Error Line Remapping

When a compilation error occurs, line numbers in error messages are remapped back to the original VNS script lines, making debugging significantly easier:

```
Inline Java compilation failed:
  line 14: ';' expected
  line 15: cannot find symbol: variable foo
```

The line numbers correspond to your `.vns` script, not the generated Java source.

### `[jes_push]` / `[jes_replace]` / `[jes_pop]` / `[jes_call]`

Direct JES scene stack commands.

```vns
[jes_push game/minigames/arena.jes]
[jes_replace game/minigames/boss.jes]
[jes_pop]
[jes_call spawnWave count=5]
```

See [Interop & Integration](../integration/vns-interop.md) for detailed documentation.

---

## Character Motion (Advanced)

### `[char <charId> <subcommand>]` / `[character]`

Advanced character choreography commands. `[character]` is accepted as an alias for `[char]`.

**Subcommands:**

| Subcommand | Aliases | Description |
|------------|---------|-------------|
| `global` | `global_position` | Enable/disable persistent position mode |
| `at` | `position`, `pos` | Set the character's anchor position |
| `move` | — | Animated slide to a new position (with optional expression, easing, duration) |
| `show` | — | Show character at a position with an expression |
| `expression` | `expr` | Change expression without moving |
| `hide` | — | Animated exit |

```vns
[char hero global on]                        # enable global position mode
[char hero at center]                        # set anchor position
[char hero pos left]                         # alias for 'at'
[char hero move right smile]                 # animated move with expression
[char hero move right smile ease_out_quad 500]  # with easing and duration
[char hero show center happy]                # show at position with expression
[char hero show center @happy+$glasses]      # explicit preset/layer composite
[char hero expression angry]                 # change expression only
[char hero expression @thinking+$hat]        # inline composite switch
[char hero expr surprised]                   # shorthand alias
[char hero hide]                             # animated hide
```

**Inline custom positions in `[char]` commands:**

The `move`, `show`, and `at` subcommands also accept `at x,y` inline coordinates:

```vns
[char hero move at 0.3,0.5]           # animated move to x=0.3, y=0.5
[char hero move at 0.3,0.5 smile]     # with expression change
[char hero show at 0.8,0.4 neutral]   # show at inline position
[char hero at at 0.5,0.5]             # set anchor to inline position
```

See [Characters & Sprites](../presentation/vns-characters.md) for detailed documentation.

---

## Inline Timelines

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

See [Interop & Integration](../integration/vns-interop.md) for detailed documentation.

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [Audio Commands](../presentation/vns-audio.md)
- [Characters & Sprites](../presentation/vns-characters.md)
- [Variables & Conditions](vns-variables.md)
- [Transitions & Screen Effects](../presentation/vns-transitions.md)
- [Interop & Integration](../integration/vns-interop.md)

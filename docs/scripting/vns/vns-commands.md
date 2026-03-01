# VNS Commands Reference

Complete catalog of all VNS commands. Commands use `[command args]` syntax.

Parser source: `core/src/main/java/com/jvn/core/vn/script/VnScriptParser.java`

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
```

**Positions:**
| Full Name | Shortcut |
|-----------|----------|
| `LEFT` | `L` |
| `CENTER` | `C` |
| `RIGHT` | `R` |
| `FAR_LEFT` | `FL` |
| `FAR_RIGHT` | `FR` |

**Layer order:** optional integer controlling z-depth (higher = in front). Default is position-based:
- `FAR_LEFT` → -20
- `LEFT` → -10
- `CENTER` → 0
- `RIGHT` → 10
- `FAR_RIGHT` → 20

```vns
# hero appears in front of villain
[show villain center neutral 0]
[show hero center determined 10]
```

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

### `[bgm <track>]`

Plays background music (loops by default).

```vns
[bgm assets/audio/bgm/main_theme.ogg]
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

See [Audio Commands](vns-audio.md) for detailed audio documentation.

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

### `[visualizer [on|off|toggle] [bars=<count>]]`

Controls the in-scene audio visualizer layer (off by default).

```vns
[visualizer on]
[visualizer on bars=48]
[visualizer off]
[visualizer toggle]
```

### `[hud <message>]`

Displays a temporary on-screen message.

```vns
[hud Saved!]
[hud Chapter 2 — The Forest]
[hud Score: ${score}]
```

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

### Character framing overrides

Special runtime variables control character rendering:

```vns
[set ui.characterHeightFactor 1.28]
[set ui.characterBaselineY 1.42]
```

See [Variables & Conditions](vns-variables.md) for detailed documentation.

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

### `[jes_push]` / `[jes_replace]` / `[jes_pop]` / `[jes_call]`

Direct JES scene stack commands.

```vns
[jes_push game/minigames/arena.jes]
[jes_replace game/minigames/boss.jes]
[jes_pop]
[jes_call spawnWave count=5]
```

See [Interop & Integration](vns-interop.md) for detailed documentation.

---

## Character Motion (Advanced)

### `[char <charId> <subcommand>]`

Advanced character choreography commands.

```vns
[char hero global on]            # enable global position mode
[char hero at center]            # set anchor position
[char hero move right smile]     # animated move with expression
[char hero expression angry]     # change expression only
[char hero hide]                 # animated hide
```

See [Characters & Sprites](vns-characters.md) for detailed documentation.

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

See [Interop & Integration](vns-interop.md) for detailed documentation.

---

## Related Docs

- [VNS Overview](vns-scripting.md)
- [Audio Commands](vns-audio.md)
- [Characters & Sprites](vns-characters.md)
- [Variables & Conditions](vns-variables.md)
- [Transitions & Screen Effects](vns-transitions.md)
- [Interop & Integration](vns-interop.md)

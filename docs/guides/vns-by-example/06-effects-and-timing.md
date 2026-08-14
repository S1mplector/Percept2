# VNS By Example — Screen Effects and Timing

Add atmosphere and dramatic impact with particle weather, screen shake, flash effects, timed pauses,
text speed control, UI visibility, and HUD messages.

**Difficulty:** Intermediate
**Time:** 15 minutes
**Concepts:** `[particles]`, `[weather]`, `[screen shake]`, `[screen flash]`, `[screen clear]`, `[wait]`, `[textspeed]`, `[autodelay]`, `[ui]`, `[hud]`, `[skip]`, `[auto]`

---

## The Script

```vns
@scenario effects_demo
@character narrator ""
@character hero "Yuki"
@character villain "Shadow"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero shocked assets/characters/yuki/shocked.png
@charimg villain angry assets/characters/villain/angry.png

@background alley assets/backgrounds/dark_alley.png

@label start
[textspeed 30]
[bg alley]
[bgm assets/audio/bgm/tension.ogg vol=0.5]
[weather rain intensity=0.45 opacity=0.7 wind=-12 prewarm=1800]

[show hero center neutral]
narrator: The alley was silent.
[wait 1000]

[sfx assets/audio/sfx/footstep.ogg]
narrator: ...
[wait 500]

[show villain right angry]
[screen shake 12 500]
[sfx assets/audio/sfx/impact.ogg]
villain: Found you!

[show hero center shocked]
[screen flash 0.8 200]
hero: !!

[wait 300]
[textspeed 60]
hero: W-what do you want?

[screen shake 6 300]
villain: You know exactly what I want.

[hud Chapter 2 — The Confrontation]
[wait 2000]

[ui hide]
[wait 1500]
[ui show]

narrator: There was no escape.
[end]
```

---

## Screen Shake

Shakes the screen to convey impact, earthquakes, or dramatic tension.

```vns
[screen shake]                # defaults: intensity=8, duration=300ms
[screen shake 12 500]         # strong shake for 500ms
[screen shake 4 200]          # subtle shake for 200ms
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| intensity | `8` | Shake amplitude in pixels |
| duration | `300` | Duration in milliseconds |

### When to Use

- Physical impacts (punches, explosions, crashes)
- Earthquakes and environmental events
- Emotional shock moments
- Jump scares

---

## Particle Weather And Ambience

Start one of JVN's tuned continuous particle presets with `[particles]`. `[weather]`, `[pfx]`, and
`[fx]` are equivalent aliases.

```vns
[particles snow]
[weather rain intensity=0.7 layer=120]
[pfx sakura opacity=0.8 speed=0.65 wind=10]
[fx fireflies duration=8000 prewarm=2500]
[particles stop]
```

The built-in presets are `snow`, `rain`, `sakura`, `fireflies`, `dust`, and `leaves`. A new command
replaces the active particle configuration. Stopping disables new emission while particles already
on screen finish fading.

Shape a preset with named options:

| Option | What It Changes |
|---|---|
| `intensity=0..1` | Emission rate and particle count |
| `layer=<integer>` | Draw order; the default is `100` |
| `opacity=0..1` | Preset alpha |
| `speed=<scale>` | Particle velocity |
| `wind=<number>` | Horizontal drift; negative values move left |
| `size=<scale>` | Particle dimensions |
| `duration=<ms>` | Automatic stop time; `0` keeps running |
| `prewarm=<ms>` | Simulated lead-in so the first frame is already populated |
| `texture=<path>` | Custom project/classpath sprite |
| `tint=<hex>` | RGB or ARGB color override |

For a custom snow sprite that is already spread across the scene when dialogue begins:

```vns
[particles snow texture=assets/vfx/snowflake.png size=1.4 prewarm=3000 opacity=0.75]
```

`prewarm` is capped at 60 seconds to protect the render thread. Use the complete
[VNS Commands Reference](../../scripting/vns/language/vns-commands.md#particles-options-weather-pfx-fx)
for preset aliases, positional syntax, and defaults.

---

## Screen Flash

Overlays a colored flash on the screen.

```vns
[screen flash]                        # white flash, defaults
[screen flash 0.8 200]               # strong white flash for 200ms
[screen flash 0.6 300 255 0 0]       # red flash
[screen flash 0.5 150 0 0 255]       # blue flash
[screen flash 0.3 400 255 255 0]     # yellow flash
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| strength | `0.5` | Opacity of the flash (0.0–1.0) |
| duration | `200` | Duration in milliseconds |
| r g b | `255 255 255` | Color channels (0–255) |

### Common Flash Colors

| Color | RGB | Use Case |
|-------|-----|----------|
| White | `255 255 255` | Lightning, magic, bright impact |
| Red | `255 0 0` | Damage, danger, blood |
| Blue | `0 0 255` | Ice, water, cold |
| Yellow | `255 255 0` | Electricity, power-up |
| Green | `0 255 0` | Heal, poison, nature |

---

## Screen Clear

Force-clears any active shake or flash effects:

```vns
[screen clear]
```

Use this to immediately cancel lingering effects.

---

## Wait (Timed Pause)

Pauses script execution for a duration:

```vns
[wait 500]       # pause 500ms
[wait 1500]      # pause 1.5 seconds
[wait 3000]      # pause 3 seconds
```

The player **cannot advance** during a `[wait]`. Use this for:

- Dramatic pauses between dialogue
- Timing effects (flash → wait → shake)
- Letting audio or transitions complete
- Building suspense

### Wait Patterns

**Dramatic silence:**
```vns
hero: I have to tell you something.
[wait 1000]
hero: ...I'm leaving.
```

**Effect timing:**
```vns
[screen flash 0.7 100]
[wait 150]
[screen shake 10 400]
[sfx assets/audio/sfx/explosion.ogg]
```

**Transition breathing room:**
```vns
[transition FADE 800 forest]
[wait 500]
narrator: The forest stretched endlessly.
```

---

## Text Speed

Controls how fast dialogue text reveals character by character.

```vns
[textspeed 20]    # fast (20ms per character)
[textspeed 50]    # normal (50ms per character)
[textspeed 80]    # slow (80ms per character)
[textspeed 0]     # instant (no reveal animation)
```

Lower = faster. The player can always click/tap to instantly reveal the full text.

### Narrative Pacing

```vns
[textspeed 30]
narrator: The battle raged on.

[textspeed 80]
narrator: And then... everything... stopped.

[textspeed 20]
hero: What happened?!
```

---

## Auto-Advance Delay

Controls how long auto-mode waits between lines:

```vns
[autodelay 2000]     # 2 seconds between auto-advances
[autodelay 4000]     # 4 seconds (slower reading)
```

Only affects auto-mode (`[auto on]`). Manual reading is unaffected.

---

## Auto and Skip Modes

### Auto Mode

```vns
[auto on]        # enable auto-advance
[auto off]       # disable auto-advance
[auto toggle]    # toggle
```

When enabled, the game automatically advances dialogue after a delay.

### Skip Mode

```vns
[skip on]        # enable fast-forward
[skip off]       # disable fast-forward
[skip toggle]    # toggle
```

Skip mode rapidly advances through already-read text. Useful for replays.

---

## UI Visibility

Show or hide the dialogue UI (textbox, name plate, etc.):

```vns
[ui hide]        # hide UI (CG viewing mode)
[ui show]        # restore UI
[ui toggle]      # toggle visibility
```

### CG Viewing Pattern

Hide UI to let the player appreciate a full-screen image:

```vns
[bg special_cg]
[ui hide]
[wait 3000]
[ui show]
narrator: That was a sight to remember.
```

### Cinematic Moment

```vns
[ui hide]
[transition DISSOLVE 2000 panorama]
[wait 2000]
[bgm assets/audio/bgm/epic.ogg]
[wait 1500]
[ui show]
narrator: The kingdom stretched to the horizon.
```

---

## HUD Messages

Display temporary on-screen messages (toasts):

```vns
[hud Saved!]
[hud Chapter 2 — The Forest]
[hud Score: ${score}]
[hud New item acquired!]
```

HUD messages appear briefly and fade out automatically. They support variable interpolation.

---

## History / Backlog

Control the dialogue history overlay:

```vns
[history show]       # open history
[history hide]       # close history
[history toggle]     # toggle
[history scroll 5]   # scroll back 5 entries
[history clear]      # clear scroll position
```

---

## Visualizer

Control the in-scene audio visualizer:

```vns
[visualizer on]
[visualizer on bars=48]
[visualizer set color=#7de2ff z=-15]
[visualizer off]
[viz toggle]              # shorthand alias
```

---

## Patterns

### Jump Scare

```vns
narrator: The house was completely empty.
[wait 800]
narrator: ...
[wait 600]
[sfx assets/audio/sfx/scream.ogg]
[screen flash 0.9 100 255 0 0]
[screen shake 16 600]
[show ghost center scary]
hero: AHHH!
```

### Power-Up Moment

```vns
hero: I can feel the power rising!
[screen flash 0.5 300 255 255 0]
[wait 200]
[screen flash 0.7 200 255 255 255]
[screen shake 8 400]
[sfx assets/audio/sfx/power_up.ogg]
[hud Power Level: Maximum!]
hero: Let's do this!
```

### Slow Revelation

```vns
[textspeed 100]
narrator: The door...
[wait 500]
narrator: ...slowly...
[wait 500]
narrator: ...creaked open.
[textspeed 30]
[sfx assets/audio/sfx/door_creak.ogg]
[screen shake 3 800]
```

### Chapter Transition

```vns
[bgm_fadeout 2000]
[ui hide]
[transition FADE 1500]
[wait 1000]
[hud Chapter 3 — The Summit]
[wait 2000]
[transition FADE 1000 mountain_peak]
[bgm assets/audio/bgm/epic_arrival.ogg]
[wait 500]
[ui show]
narrator: At last, the summit.
```

---

## Key Takeaways

1. `[screen shake intensity duration]` shakes the screen for impact
2. `[particles preset ...]` starts atmospheric weather or ambience; `[particles stop]` ends emission
3. `[screen flash strength duration r g b]` overlays a colored flash
4. `[screen clear]` cancels active shake and flash effects immediately
5. `[wait ms]` pauses execution (player cannot advance)
6. `[textspeed ms]` controls text reveal speed (lower = faster)
7. `[ui hide/show]` toggles the dialogue UI for CG viewing or cinematics
8. `[hud message]` displays temporary on-screen toast messages
9. Combine effects with timing for dramatic sequences: weather → flash → wait → shake → SFX

---

## Next

- [Character Motion](07-character-motion.md) — smooth moves, global position mode, easing, custom positions
- [Back to Index](../vns-by-example.md)

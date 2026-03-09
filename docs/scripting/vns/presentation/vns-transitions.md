# VNS Transitions & Screen Effects

Complete reference for visual transitions, screen shake, screen flash, and UI visibility control in VNS.

---

## Transitions

Transitions are blocking visual effects used to change backgrounds or signal scene changes.

### Syntax

```text
[transition <type> [durationMs] [bgId]]
```

- `type` — the transition effect
- `durationMs` — duration in milliseconds (default: 500ms)
- `bgId` — optional background to transition to

### Transition Types

| Type | Effect |
|------|--------|
| `FADE` | Fades to black, then fades in on new background |
| `DISSOLVE` | Blends old and new backgrounds together |
| `CROSSFADE` | Smooth alpha crossfade between backgrounds |
| `SLIDE_LEFT` | New background slides in from right |
| `SLIDE_RIGHT` | New background slides in from left |
| `WIPE` | Horizontal wipe reveals new background |

### Examples

```vns
# Simple fade with background change
[transition FADE 800 classroom_night]

# Slow dissolve for mood shifts
[transition DISSOLVE 1500 sunset_beach]

# Fast crossfade for location changes
[transition CROSSFADE 600 corridor]

# Slide for horizontal movement
[transition SLIDE_LEFT 500 next_room]
[transition SLIDE_RIGHT 500 prev_room]

# Dramatic wipe
[transition WIPE 800 battlefield]

# Fade without changing background (blackout and back)
[transition FADE 1000]
```

### Transition Blocking

Transitions are **blocking** — script execution pauses until the transition completes. Plan your timing accordingly:

```vns
narrator: The scene changes before your eyes.
[transition FADE 1200 forest]
# This line runs AFTER the 1.2s fade completes
narrator: You find yourself in a dense forest.
```

### Background Change vs. Transition

Compare these approaches:

```vns
# Instant background change (no animation)
[bg forest]

# Animated background change
[transition FADE 800 forest]

# Background change then separate transition
[bg forest]
[transition FADE 600]
```

---

## Screen Shake

Triggers a camera shake effect for impact or drama.

### Syntax

```text
[screen shake [intensity] [durationMs]]
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `intensity` | 8 | Pixel offset magnitude |
| `durationMs` | 300 | Duration in milliseconds |

### Examples

```vns
# Default shake
[screen shake]

# Subtle tremor
[screen shake 3 200]

# Medium impact
[screen shake 8 400]

# Heavy earthquake
[screen shake 15 800]

# Quick jolt
[screen shake 12 150]
```

### Practical Usage

```vns
# Explosion
[sfx assets/audio/sfx/explosion.ogg]
[screen shake 12 500]
narrator: The ground shook violently.

# Footsteps approaching
[screen shake 3 200]
[wait 400]
[screen shake 3 200]
[wait 400]
[screen shake 4 250]
narrator: Something large was approaching.

# Door slam
[sfx assets/audio/sfx/door_slam.ogg]
[screen shake 6 200]

# Combat hit
[sfx assets/audio/sfx/hit.ogg]
[screen shake 10 300]
[screen flash 0.5 150 255 200 200]
```

---

## Screen Flash

Triggers a color overlay flash effect.

### Syntax

```text
[screen flash [strength] [durationMs] [r g b]]
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `strength` | 0.7 | Opacity of flash (0.0-1.0) |
| `durationMs` | 180 | Duration in milliseconds |
| `r g b` | 255 255 255 | Flash color (0-255 each) |

### Examples

```vns
# Default white flash
[screen flash]

# Bright white flash (strong)
[screen flash 0.9 200]

# Subtle white flash
[screen flash 0.3 150]

# Red flash (damage)
[screen flash 0.6 300 255 0 0]

# Blue flash (magic)
[screen flash 0.5 250 0 100 255]

# Golden flash (power-up)
[screen flash 0.7 200 255 215 0]

# Green flash (healing)
[screen flash 0.4 200 0 255 100]

# Dark flash (shadow magic)
[screen flash 0.8 400 30 0 50]
```

### Clearing Screen Effects

Force-clear any active shake or flash:

```vns
[screen clear]
```

---

## Combining Effects

Stack transitions, shake, flash, and audio for cinematic moments:

### Example: Lightning Strike

```vns
narrator: The sky darkened ominously.
[bgm_fadeout 1000]
[wait 800]
[screen flash 0.9 100]
[sfx assets/audio/sfx/thunder.ogg]
[wait 200]
[screen shake 10 600]
[screen flash 0.6 300]
narrator: Lightning split the sky.
```

### Example: Scene Transition with Impact

```vns
[screen shake 4 200]
[sfx assets/audio/sfx/rumble.ogg]
[wait 300]
[transition FADE 1000 ruins]
[wait 200]
[screen flash 0.3 200 200 200 255]
narrator: The ancient ruins stretched before you.
```

### Example: Power Awakening

```vns
[bgm_crossfade assets/audio/bgm/power_theme.ogg 1500]
[wait 500]
[screen flash 0.5 200 255 215 0]
[wait 300]
[screen shake 6 400]
[screen flash 0.7 300 255 255 200]
[wait 200]
[show hero center determined 10]
hero: I can feel it... the power!
[screen flash 0.9 150 255 255 255]
[screen shake 12 500]
```

### Example: Dramatic Reveal

```vns
[transition FADE 1500 throne_room]
[wait 300]
[bgm assets/audio/bgm/villain_theme.ogg]
[wait 500]
[screen shake 4 300]
[show villain center smug]
[wait 200]
villain: Welcome to my domain.
```

---

## UI Visibility

Control the dialogue interface visibility for CG-viewing or dramatic pauses.

### Syntax

```text
[ui hide]      # hide dialogue UI
[ui show]      # show dialogue UI
[ui toggle]    # toggle visibility
```

### Practical Usage

```vns
# Show a CG with no UI overlay
[bg cg_sunset_kiss]
[ui hide]
[wait 3000]
[ui show]
narrator: A moment they would never forget.
```

```vns
# Dramatic pause before reveal
narrator: And the winner is...
[ui hide]
[wait 2000]
[screen flash 0.5 200 255 215 0]
[ui show]
narrator: You!
```

---

## Audio Visualizer

An optional audio-reactive overlay for music-heavy scenes.

```vns
# Enable visualizer
[visualizer on]

# Enable with custom bar count
[visualizer on bars=64]

# Put the visualizer behind character sprites
[visualizer set z=-15]

# Disable
[visualizer off]

# Toggle
[visualizer toggle]
```

The visualizer renders frequency bars over the scene. It sets internal variables including `ui.audioVisualizer` (boolean), `ui.audioVisualizerBars` (integer, minimum 8), and `ui.audioVisualizerZ` (integer layer order relative to characters).

---

## History Overlay

The backlog/history overlay for reviewing past dialogue.

```vns
[history show]        # open overlay
[history hide]        # close overlay
[history toggle]      # toggle
[history scroll 10]   # scroll back 10 lines
[history clear]       # reset scroll position
```

---

## Effect Timing Tips

- **Shake + Flash together** create strong impact. Stagger them by 50-100ms for a more natural feel.
- **Transition duration** should match the mood: 400-600ms for fast cuts, 1000-2000ms for moody transitions.
- **Always pair effects with audio** — visual effects without sound feel hollow.
- **Use `[wait]`** between stacked effects to prevent them from running simultaneously when you want sequential timing.
- **Avoid rapid-fire effects** in long loops — they can cause visual fatigue and performance issues on lower-end hardware.

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [Commands Reference](../language/vns-commands.md) — full command list
- [Audio Commands](vns-audio.md) — audio pairing
- [Characters & Sprites](vns-characters.md) — character show/hide with effects

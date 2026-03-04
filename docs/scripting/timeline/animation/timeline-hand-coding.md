# Hand-Coding Puppeteer Timelines

Complete guide to writing JES timeline animations by hand — without the Puppeteer keyframe editor. Covers the time cursor model, every action type, easing selection, and 15+ fully annotated examples for common animation scenarios.

Parser source: `core/src/main/java/com/jvn/core/animation/TimelineDataParser.java`
Runtime: `core/src/main/java/com/jvn/core/animation/TimelineRunner.java`

---

## Why Hand-Code?

The Puppeteer visual editor is excellent for complex multi-entity choreography, but hand-coding timelines is often faster when you:

- Need a quick entrance/exit animation for a character
- Want precise control over timing values
- Are embedding animations directly in VNS scripts
- Are iterating on a JES scene and want the animation inline
- Don't need visual preview — you know the pixel coordinates
- Are building reusable animation patterns (copy-paste templates)

You can always paste hand-coded timelines into Puppeteer later for visual fine-tuning.

---

## Where to Write Timelines

### 1. Inside a JES scene file

```jes
scene "MyScene" {
  entity "hero" {
    component Sprite2D {
      image: "assets/characters/hero.png"
      x: 100
      y: 300
      w: 128
      h: 128
      alpha: 0
    }
  }

  timeline {
    // Your animation here
    move "hero" { x: 400 y: 300 dur: 500 easing: ease_out_cubic }
    fade "hero" { alpha: 1.0 dur: 300 easing: ease_out_quad }
  }
}
```

### 2. Inline in a VNS script

```vns
narrator: Watch this entrance!

timeline {
  move "hero" { x: 640 y: 468 dur: 400 easing: ease_out_back }
  fade "hero" { alpha: 1.0 dur: 300 easing: ease_out_quad }
}

[wait 500]
hero: I have arrived!
```

The VNS parser collects the `timeline { ... }` block and passes it to `TimelineDataParser`, which converts it into `TimelineData` for playback by `TimelineRunner`. The animation runs asynchronously — VNS continues to the next node immediately.

### 3. As a standalone timeline file

Save to `scripts/timelines/hero_entrance.jes` and register from Java:

```java
TimelineData data = TimelineDataParser.parse("hero_entrance",
    Files.readString(Path.of("scripts/timelines/hero_entrance.jes")));
TimelineRegistry.register(data);
```

Then call from VNS:

```vns
[external jes_timeline hero_entrance]
```

---

## The Time Cursor Model

Understanding the **time cursor** is essential for hand-coding. The parser maintains a cursor (starting at 0ms) that determines when actions place their keyframes.

### Core Rules

1. **`wait N`** advances the cursor by `N` milliseconds
2. **Actions** (move, fade, etc.) place keyframes from `cursor` to `cursor + dur`
3. **Actions do NOT advance the cursor** — only `wait` does
4. **Sequential actions without `wait`** start from the same cursor position (they overlap!)

### Walkthrough

```jes
timeline {
  // Cursor = 0ms
  move "hero" { x: 200 dur: 300 }
  // Cursor still = 0ms (move doesn't advance it)
  // Keyframes: start=0ms, end=300ms

  fade "hero" { alpha: 1 dur: 200 }
  // Cursor still = 0ms
  // Keyframes: start=0ms, end=200ms
  // ⚠ This overlaps with the move! Both run from 0ms.

  wait 300
  // Cursor = 300ms

  scale "hero" { sx: 1.2 sy: 1.2 dur: 200 }
  // Cursor still = 300ms
  // Keyframes: start=300ms, end=500ms
}
```

### Common Patterns

**Sequential actions** (one after another):

```jes
timeline {
  move "hero" { x: 200 dur: 300 }     // 0–300ms
  wait 300
  fade "hero" { alpha: 0 dur: 200 }   // 300–500ms
  wait 200
  scale "hero" { sx: 2 sy: 2 dur: 100 } // 500–600ms
}
```

**Parallel actions** (simultaneous — just don't put `wait` between them):

```jes
timeline {
  move "hero" { x: 200 dur: 500 }     // 0–500ms
  fade "hero" { alpha: 1 dur: 300 }    // 0–300ms (same cursor)
  rotate "hero" { angle: 15 dur: 500 } // 0–500ms (same cursor)
}
```

**Staggered starts** (different delays per entity):

```jes
timeline {
  fade "hero" { alpha: 1 dur: 300 }      // 0–300ms
  wait 100
  fade "sidekick" { alpha: 1 dur: 300 }  // 100–400ms
  wait 100
  fade "villain" { alpha: 1 dur: 300 }   // 200–500ms
}
```

---

## Action Reference (Quick Table)

| Action | Target | Properties | Description |
|--------|--------|-----------|-------------|
| `move` | `"entity"` | `x`, `y`, `dur`, `easing` | Animate position |
| `pivot` | `"entity"` | `ox`, `oy`, `dur`, `easing` | Animate origin point (0–1) |
| `rotate` | `"entity"` | `angle`/`rotation`, `dur`, `easing` | Animate rotation (degrees) |
| `scale` | `"entity"` | `x`/`scale_x`, `y`/`scale_y`, `dur`, `easing` | Animate scale factor |
| `fade` | `"entity"` | `alpha`, `dur`, `easing` | Animate opacity (0–1) |
| `cameraMove` | (none) | `x`, `y`, `dur`, `easing` | Animate camera position |
| `cameraZoom` | (none) | `zoom`, `dur`, `easing` | Animate camera zoom |
| `playAudio` | `"path"` | `volume`, `loop`, `bgm`, `channel`, `fadein` | Trigger audio cue |
| `wait` | (none) | milliseconds | Advance time cursor |

### Property Aliases

Some actions accept alternate property names:

| Action | Property | Aliases |
|--------|----------|---------|
| `rotate` | `angle` | `rotation` |
| `scale` | `x` | `scale_x` |
| `scale` | `y` | `scale_y` |
| `move` | `dur` | `duration` |
| `playAudio` | `fadein` | `fadein_ms`, `fade_in`, `fadeinms` |

---

## Easing Selection Guide

Every tween action accepts an `easing` property. Choose based on the feel you want:

### Quick Decision Chart

| Situation | Recommended Easing | Why |
|-----------|-------------------|-----|
| UI element sliding into view | `ease_out_cubic` | Fast start, smooth deceleration — feels responsive |
| Element leaving the screen | `ease_in_cubic` | Accelerates away naturally |
| Dialogue box opening | `ease_out_quad` | Gentle deceleration |
| Camera pan | `ease_in_out_quad` | Smooth start and end — cinematic |
| Bounce landing | `ease_out_bounce` | Playful, cartoon-like |
| Overshoot snap | `ease_out_back` | Slight overshoot then settle — energetic |
| Elastic wobble | `ease_out_elastic` | Spring-like — attention-grabbing |
| Breathing/floating loop | `ease_in_out_sine` | Perfectly smooth — organic |
| Linear movement | `linear` | Constant speed (default) |
| Dramatic zoom | `ease_in_out_expo` | Very pronounced curve |

### All 26 Easing Types

| Family | In (slow→fast) | Out (fast→slow) | In-Out (slow→fast→slow) |
|--------|----------------|-----------------|------------------------|
| **Quad** (t²) | `ease_in_quad` | `ease_out_quad` | `ease_in_out_quad` |
| **Cubic** (t³) | `ease_in_cubic` | `ease_out_cubic` | `ease_in_out_cubic` |
| **Quartic** (t⁴) | `ease_in_quart` | `ease_out_quart` | `ease_in_out_quart` |
| **Expo** (2^t) | `ease_in_expo` | `ease_out_expo` | `ease_in_out_expo` |
| **Sine** | `ease_in_sine` | `ease_out_sine` | `ease_in_out_sine` |
| **Elastic** | `ease_in_elastic` | `ease_out_elastic` | `ease_in_out_elastic` |
| **Back** | `ease_in_back` | `ease_out_back` | `ease_in_out_back` |
| **Bounce** | `ease_in_bounce` | `ease_out_bounce` | `ease_in_out_bounce` |

Plus `linear` (constant speed, default when omitted).

### Easing Tips

- **Omit `easing` for linear** — `linear` is the default and doesn't need to be written
- **`ease_out_*`** is the most commonly useful family — things decelerate into their final position
- **`ease_in_out_*`** is best for camera moves — smooth at both ends
- **`elastic` and `bounce`** are loud — use sparingly for emphasis
- **`back`** is subtle overshoot — great for UI elements snapping into place
- **Stronger curves** (quart > cubic > quad) mean more pronounced acceleration

---

## Examples

### Example 1: Character Slide-In (Entrance)

A character slides in from the left edge and fades in simultaneously.

```jes
timeline {
  // Set starting position (instant — dur: 0)
  move "hero" { x: -150 y: 350 dur: 0 }
  fade "hero" { alpha: 0 dur: 0 }

  // Animate entrance
  wait 100
  move "hero" { x: 400 y: 350 dur: 600 easing: ease_out_cubic }
  fade "hero" { alpha: 1 dur: 400 easing: ease_out_quad }
}
```

**Key techniques:**
- `dur: 0` sets initial values instantly (places a keyframe at cursor with no interpolation gap)
- Move and fade start at the same cursor position → they run in parallel
- Move takes 600ms, fade only 400ms → character is fully opaque before stopping

---

### Example 2: Character Slide-Out (Exit)

```jes
timeline {
  // Slide right and fade out
  move "hero" { x: 900 dur: 500 easing: ease_in_cubic }
  fade "hero" { alpha: 0 dur: 400 easing: ease_in_quad }
}
```

**Tip:** Use `ease_in_*` for exits — the acceleration away feels natural.

---

### Example 3: Bounce-In Entrance

A playful entrance with scale overshoot.

```jes
timeline {
  // Start small and invisible
  scale "popup" { x: 0.3 y: 0.3 dur: 0 }
  fade "popup" { alpha: 0 dur: 0 }

  // Pop in with overshoot
  wait 50
  scale "popup" { x: 1.1 y: 1.1 dur: 250 easing: ease_out_cubic }
  fade "popup" { alpha: 1 dur: 200 easing: ease_out_quad }

  // Settle to normal size
  wait 250
  scale "popup" { x: 0.95 y: 0.95 dur: 100 easing: ease_in_out_quad }
  wait 100
  scale "popup" { x: 1.0 y: 1.0 dur: 100 easing: ease_out_quad }
}
```

**Duration breakdown:** 50 + 250 + 100 + 100 = 500ms total.

---

### Example 4: Attention Shake

Horizontal shake effect — great for damage feedback or emphasis.

```jes
timeline {
  move "target" { x: -12 dur: 50 easing: ease_out_quad }
  wait 50
  move "target" { x: 12 dur: 50 easing: ease_out_quad }
  wait 50
  move "target" { x: -8 dur: 50 easing: ease_out_quad }
  wait 50
  move "target" { x: 8 dur: 50 easing: ease_out_quad }
  wait 50
  move "target" { x: -4 dur: 40 easing: ease_out_quad }
  wait 40
  move "target" { x: 0 dur: 40 easing: ease_out_quad }
}
```

**Pattern:** Decreasing amplitude on each oscillation → natural dampening.

---

### Example 5: Pulse / Heartbeat

Scale pulse for attention without being as aggressive as shake.

```jes
timeline {
  scale "icon" { x: 1.15 y: 1.15 dur: 150 easing: ease_out_quad }
  wait 150
  scale "icon" { x: 1.0 y: 1.0 dur: 200 easing: ease_in_out_quad }
}
```

**For a repeating pulse**, wrap in a JES `loop`:

```jes
loop 3 {
  scale "icon" { x: 1.15 y: 1.15 dur: 150 easing: ease_out_quad }
  wait 150
  scale "icon" { x: 1.0 y: 1.0 dur: 200 easing: ease_in_out_quad }
  wait 200
}
```

---

### Example 6: Floating / Hovering (Idle Loop)

Gentle vertical bob — perfect for floating items, NPCs, or menu highlights.

```jes
timeline {
  move "gem" { y: -10 dur: 1000 easing: ease_in_out_sine }
  wait 1000
  move "gem" { y: 0 dur: 1000 easing: ease_in_out_sine }
  wait 1000
  move "gem" { y: -10 dur: 1000 easing: ease_in_out_sine }
}
```

**Note:** In a JES scene you can use `loop` for infinite repetition:

```jes
loop until "stopFloat" {
  move "gem" { y: -10 dur: 1000 easing: ease_in_out_sine }
  wait 1000
  move "gem" { y: 0 dur: 1000 easing: ease_in_out_sine }
  wait 1000
}
```

---

### Example 7: Camera Pan and Zoom

Cinematic camera move with zoom — establishing shot for a scene.

```jes
timeline {
  // Start zoomed out, looking at the left side
  cameraMove { x: -200 y: 0 dur: 0 }
  cameraZoom { zoom: 0.8 dur: 0 }

  // Slow pan to the right while zooming in
  wait 500
  cameraMove { x: 200 y: 0 dur: 2000 easing: ease_in_out_quad }
  cameraZoom { zoom: 1.2 dur: 2000 easing: ease_in_out_quad }

  // Hold for a beat, then reset
  wait 2500
  cameraMove { x: 0 y: 0 dur: 800 easing: ease_in_out_cubic }
  cameraZoom { zoom: 1.0 dur: 800 easing: ease_in_out_cubic }
}
```

**Camera tips:**
- Camera actions have no target entity — they operate on the scene camera
- Use `ease_in_out_*` for camera — smooth start/end feels cinematic
- `zoom: 1.0` is default, `> 1.0` zooms in, `< 1.0` zooms out

---

### Example 8: Dramatic Zoom on Character

Quick zoom to focus on a character during a reveal or critical moment.

```jes
timeline {
  // Snap zoom in
  cameraMove { x: 400 y: 300 dur: 300 easing: ease_out_expo }
  cameraZoom { zoom: 1.8 dur: 300 easing: ease_out_expo }

  // Hold
  wait 1000

  // Ease back out
  cameraMove { x: 0 y: 0 dur: 600 easing: ease_in_out_quad }
  cameraZoom { zoom: 1.0 dur: 600 easing: ease_in_out_quad }
}
```

---

### Example 9: Multi-Entity Staggered Entrance

Three characters enter one after another with a slight delay.

```jes
timeline {
  // Starting positions (all off-screen left, invisible)
  move "char_a" { x: -150 y: 350 dur: 0 }
  move "char_b" { x: -150 y: 350 dur: 0 }
  move "char_c" { x: -150 y: 350 dur: 0 }
  fade "char_a" { alpha: 0 dur: 0 }
  fade "char_b" { alpha: 0 dur: 0 }
  fade "char_c" { alpha: 0 dur: 0 }

  // Char A enters
  wait 200
  move "char_a" { x: 250 y: 350 dur: 500 easing: ease_out_cubic }
  fade "char_a" { alpha: 1 dur: 300 easing: ease_out_quad }

  // Char B enters (staggered by 150ms)
  wait 150
  move "char_b" { x: 450 y: 350 dur: 500 easing: ease_out_cubic }
  fade "char_b" { alpha: 1 dur: 300 easing: ease_out_quad }

  // Char C enters (staggered by another 150ms)
  wait 150
  move "char_c" { x: 650 y: 350 dur: 500 easing: ease_out_cubic }
  fade "char_c" { alpha: 1 dur: 300 easing: ease_out_quad }
}
```

**Stagger pattern:** `wait 150` between each entry creates a cascade effect.

---

### Example 10: Battle Intro Cutscene

Two sides slide in, VS text slams in, camera shakes.

```jes
timeline {
  // Place entities at starting positions
  move "hero" { x: -200 y: 300 dur: 0 }
  move "enemy" { x: 1000 y: 300 dur: 0 }
  fade "hero" { alpha: 0 dur: 0 }
  fade "enemy" { alpha: 0 dur: 0 }
  scale "vs_text" { x: 0.1 y: 0.1 dur: 0 }
  fade "vs_text" { alpha: 0 dur: 0 }

  // Both characters slide in simultaneously
  wait 200
  move "hero" { x: 200 y: 300 dur: 700 easing: ease_out_back }
  fade "hero" { alpha: 1 dur: 500 easing: ease_out_quad }
  move "enemy" { x: 600 y: 300 dur: 700 easing: ease_out_back }
  fade "enemy" { alpha: 1 dur: 500 easing: ease_out_quad }

  // VS text slams in
  wait 800
  scale "vs_text" { x: 2.0 y: 2.0 dur: 250 easing: ease_out_elastic }
  fade "vs_text" { alpha: 1 dur: 150 easing: ease_out_quad }
  playAudio "assets/audio/sfx/impact.ogg" { volume: 0.9 }

  // Camera reacts
  wait 50

  // VS text settles to normal
  wait 400
  scale "vs_text" { x: 1.0 y: 1.0 dur: 300 easing: ease_in_out_quad }

  // Brief hold, then signal ready
  wait 800
  fade "vs_text" { alpha: 0 dur: 300 easing: ease_in_quad }
}
```

---

### Example 11: Title Card / Splash Screen

Logo fades in, scales up, title text appears, BGM starts.

```jes
timeline {
  // Start state
  fade "logo" { alpha: 0 dur: 0 }
  scale "logo" { x: 0.8 y: 0.8 dur: 0 }
  fade "title_text" { alpha: 0 dur: 0 }
  move "title_text" { y: 420 dur: 0 }
  cameraZoom { zoom: 0.9 dur: 0 }

  // BGM fades in
  wait 300
  playAudio "assets/audio/bgm/main_theme.ogg" {
    volume: 0.6
    loop: true
    bgm: true
    fadein: 1500
  }

  // Logo fades in and scales up
  wait 200
  fade "logo" { alpha: 1 dur: 800 easing: ease_out_quad }
  scale "logo" { x: 1.0 y: 1.0 dur: 1000 easing: ease_out_cubic }
  cameraZoom { zoom: 1.0 dur: 1000 easing: ease_out_quad }

  // Title text slides up and fades in
  wait 800
  move "title_text" { y: 380 dur: 600 easing: ease_out_cubic }
  fade "title_text" { alpha: 1 dur: 500 easing: ease_out_quad }

  // Subtle reveal SFX
  wait 100
  playAudio "assets/audio/sfx/shimmer.ogg" { volume: 0.4 }
}
```

---

### Example 12: Dialogue Box Animation

Text box slides up from bottom, name plate fades in.

```jes
timeline {
  // Start below screen
  move "textbox" { y: 700 dur: 0 }
  fade "nameplate" { alpha: 0 dur: 0 }

  // Slide up
  wait 50
  move "textbox" { y: 480 dur: 350 easing: ease_out_cubic }

  // Name plate fades in slightly after
  wait 200
  fade "nameplate" { alpha: 1 dur: 200 easing: ease_out_quad }
}
```

**Reverse for closing:**

```jes
timeline {
  fade "nameplate" { alpha: 0 dur: 150 easing: ease_in_quad }
  wait 100
  move "textbox" { y: 700 dur: 300 easing: ease_in_cubic }
}
```

---

### Example 13: Item Pickup Effect

Item floats up, scales down, fades out — classic "collected" feedback.

```jes
timeline {
  // Float upward while shrinking
  move "item" { y: -50 dur: 600 easing: ease_out_quad }
  scale "item" { x: 0.3 y: 0.3 dur: 600 easing: ease_in_quad }
  fade "item" { alpha: 0 dur: 400 easing: ease_in_quad }

  // Sparkle SFX
  playAudio "assets/audio/sfx/pickup.ogg" { volume: 0.7 }
}
```

**Note:** All three actions (move, scale, fade) start at cursor=0, so they run in parallel. The move and scale take 600ms, fade takes only 400ms — the item becomes invisible before the motion completes.

---

### Example 14: Screen Transition (Fade to Black)

Full-screen panel fades in, holds, then fades out — scene transition overlay.

```jes
timeline {
  // Black overlay starts invisible
  fade "black_overlay" { alpha: 0 dur: 0 }

  // Fade to black
  fade "black_overlay" { alpha: 1 dur: 500 easing: ease_in_quad }

  // Hold black screen
  wait 1000

  // Fade from black
  fade "black_overlay" { alpha: 0 dur: 500 easing: ease_out_quad }
}
```

Use with a Panel2D entity:

```jes
entity "black_overlay" {
  component Panel2D {
    x: 0
    y: 0
    w: 1280
    h: 720
    fill: rgb(0, 0, 0, 1)
  }
}
```

---

### Example 15: Complex Cutscene (Multi-Phase)

A full cutscene with multiple phases: establish, action, resolution.

```jes
timeline {
  // === PHASE 1: Establish scene ===

  // Camera starts wide
  cameraZoom { zoom: 0.7 dur: 0 }
  cameraMove { x: -100 y: 0 dur: 0 }

  // BGM
  playAudio "assets/audio/bgm/tension.ogg" {
    volume: 0.5
    bgm: true
    loop: true
    fadein: 800
  }

  // Slow camera pan across the scene
  wait 300
  cameraMove { x: 100 y: 0 dur: 3000 easing: ease_in_out_sine }
  cameraZoom { zoom: 0.85 dur: 3000 easing: ease_in_out_sine }

  // === PHASE 2: Characters enter ===

  wait 2000
  move "hero" { x: 250 y: 380 dur: 800 easing: ease_out_cubic }
  fade "hero" { alpha: 1 dur: 600 easing: ease_out_quad }

  wait 400
  move "ally" { x: 180 y: 400 dur: 700 easing: ease_out_cubic }
  fade "ally" { alpha: 1 dur: 500 easing: ease_out_quad }

  // === PHASE 3: Dramatic reveal ===

  wait 1200

  // Camera snaps to villain
  cameraMove { x: 500 y: 200 dur: 400 easing: ease_out_expo }
  cameraZoom { zoom: 1.3 dur: 400 easing: ease_out_expo }

  // Villain appears
  wait 200
  fade "villain" { alpha: 1 dur: 300 easing: ease_out_quad }
  scale "villain" { x: 1.1 y: 1.1 dur: 400 easing: ease_out_back }

  // Impact SFX
  playAudio "assets/audio/sfx/reveal.ogg" { volume: 0.8 }

  // Villain settles
  wait 500
  scale "villain" { x: 1.0 y: 1.0 dur: 200 easing: ease_in_out_quad }

  // === PHASE 4: Resolution ===

  // Camera pulls back to show everyone
  wait 600
  cameraMove { x: 300 y: 300 dur: 1000 easing: ease_in_out_quad }
  cameraZoom { zoom: 0.9 dur: 1000 easing: ease_in_out_quad }
}
```

---

### Example 16: Audio-Synced Animation

Animating to the beat — audio cues with precisely timed movements.

```jes
timeline {
  // Beat 1 — kick
  playAudio "assets/audio/sfx/beat_kick.ogg" { volume: 0.8 }
  scale "dancer" { x: 1.1 y: 0.9 dur: 100 easing: ease_out_quad }
  wait 100
  scale "dancer" { x: 1.0 y: 1.0 dur: 100 easing: ease_in_out_quad }

  // Wait for next beat (assume 120 BPM = 500ms per beat)
  wait 300

  // Beat 2 — snare
  playAudio "assets/audio/sfx/beat_snare.ogg" { volume: 0.7 }
  scale "dancer" { x: 0.9 y: 1.1 dur: 100 easing: ease_out_quad }
  move "dancer" { y: -10 dur: 100 easing: ease_out_quad }
  wait 100
  scale "dancer" { x: 1.0 y: 1.0 dur: 100 easing: ease_in_out_quad }
  move "dancer" { y: 0 dur: 100 easing: ease_in_out_quad }

  // Beat 3 — kick
  wait 300
  playAudio "assets/audio/sfx/beat_kick.ogg" { volume: 0.8 }
  rotate "dancer" { angle: 5 dur: 100 easing: ease_out_quad }
  wait 100
  rotate "dancer" { angle: 0 dur: 150 easing: ease_out_elastic }

  // Beat 4 — accent
  wait 250
  playAudio "assets/audio/sfx/beat_accent.ogg" { volume: 0.9 }
  scale "dancer" { x: 1.2 y: 1.2 dur: 80 easing: ease_out_quad }
  wait 80
  scale "dancer" { x: 1.0 y: 1.0 dur: 200 easing: ease_out_bounce }
}
```

**Audio sync tip:** Calculate beat timings from BPM. At 120 BPM, each beat is 500ms. Subtract action durations from the gap to get the `wait` value.

---

### Example 17: VNS Inline — Character Emotion Reaction

Used directly inside a VNS script to animate a character reacting.

```vns
hero: I can't believe it...

timeline {
  // Quick scale pulse — shock reaction
  scale "hero" { x: 1.08 y: 1.08 dur: 100 easing: ease_out_quad }
  wait 100
  scale "hero" { x: 0.97 y: 0.97 dur: 80 easing: ease_in_out_quad }
  wait 80
  scale "hero" { x: 1.0 y: 1.0 dur: 120 easing: ease_out_quad }
}

[wait 300]
hero: How could you do this?!

timeline {
  // Angry shake
  move "hero" { x: -8 dur: 40 easing: ease_out_quad }
  wait 40
  move "hero" { x: 8 dur: 40 easing: ease_out_quad }
  wait 40
  move "hero" { x: -5 dur: 40 easing: ease_out_quad }
  wait 40
  move "hero" { x: 0 dur: 40 easing: ease_out_quad }
}

[wait 200]
hero: I'll never forgive you!
```

---

### Example 18: Parallax Background Scroll

Animate multiple background layers at different speeds for a parallax scrolling effect.

```jes
timeline {
  // Distant mountains — slow
  move "bg_mountains" { x: -50 dur: 5000 easing: linear }

  // Mid-ground trees — medium
  move "bg_trees" { x: -150 dur: 5000 easing: linear }

  // Foreground grass — fast
  move "bg_grass" { x: -400 dur: 5000 easing: linear }

  // Character stays centered (camera follows via cameraFollow in JES)
}
```

---

## Complete JES Scene with Hand-Coded Timeline

Putting it all together — a self-contained scene file:

```jes
scene "IntroScene" {
  // Background
  entity "bg" {
    component Panel2D {
      x: 0
      y: 0
      w: 1280
      h: 720
      fill: rgb(0.05, 0.05, 0.12, 1)
    }
  }

  // Stars background image
  entity "stars" {
    component Sprite2D {
      image: "assets/backgrounds/stars.png"
      x: 0
      y: 0
      w: 1280
      h: 720
      alpha: 0
    }
  }

  // Title logo
  entity "logo" {
    component Sprite2D {
      image: "assets/ui/logo.png"
      x: 440
      y: 200
      w: 400
      h: 200
      alpha: 0
    }
  }

  // Subtitle
  entity "subtitle" {
    component Label2D {
      text: "A New Beginning"
      x: 540
      y: 430
      size: 24
      color: rgb(0.8, 0.8, 0.9, 1)
      align: center
    }
  }

  // "Press any key" prompt
  entity "prompt" {
    component Label2D {
      text: "Press any key to start"
      x: 490
      y: 600
      size: 18
      color: rgb(0.6, 0.6, 0.7, 1)
      align: center
    }
  }

  timeline {
    // Initialize hidden states
    fade "subtitle" { alpha: 0 dur: 0 }
    fade "prompt" { alpha: 0 dur: 0 }
    scale "logo" { x: 0.7 y: 0.7 dur: 0 }
    cameraZoom { zoom: 0.95 dur: 0 }

    // Stars fade in
    wait 500
    fade "stars" { alpha: 0.6 dur: 2000 easing: ease_out_quad }

    // BGM starts
    wait 500
    playAudio "assets/audio/bgm/title_theme.ogg" {
      volume: 0.5
      bgm: true
      loop: true
      fadein: 2000
    }

    // Logo appears
    wait 1000
    fade "logo" { alpha: 1 dur: 1000 easing: ease_out_quad }
    scale "logo" { x: 1.0 y: 1.0 dur: 1200 easing: ease_out_cubic }
    cameraZoom { zoom: 1.0 dur: 1200 easing: ease_out_cubic }

    // Reveal SFX
    wait 800
    playAudio "assets/audio/sfx/chime.ogg" { volume: 0.4 }

    // Subtitle fades in
    wait 400
    fade "subtitle" { alpha: 1 dur: 600 easing: ease_out_quad }

    // Prompt fades in
    wait 1000
    fade "prompt" { alpha: 1 dur: 400 easing: ease_out_quad }
  }
}
```

---

## Common Mistakes

### Forgetting `wait` between sequential actions

```jes
// ❌ WRONG — both actions start at cursor=0, they overlap
move "hero" { x: 200 dur: 500 }
fade "hero" { alpha: 0 dur: 300 }

// ✅ CORRECT — fade starts after move completes
move "hero" { x: 200 dur: 500 }
wait 500
fade "hero" { alpha: 0 dur: 300 }
```

### Using wrong `wait` value

```jes
// ❌ WRONG — wait doesn't match the previous action's duration
move "hero" { x: 200 dur: 500 }
wait 300  // gap of 200ms where move is still running
scale "hero" { sx: 2 sy: 2 dur: 200 }  // starts at 300ms, overlaps with move
```

### Missing `dur: 0` for initial values

```jes
// ❌ WRONG — without dur: 0, the parser may not create a keyframe at t=0
// The entity may interpolate from its default position
wait 200
move "hero" { x: 400 dur: 500 }

// ✅ CORRECT — explicitly set start position
move "hero" { x: -100 dur: 0 }
wait 200
move "hero" { x: 400 dur: 500 easing: ease_out_cubic }
```

### Using `deg` vs `angle` in rotate

```jes
// Both work — they're aliases
rotate "item" { angle: 360 dur: 600 }   // ✅
rotate "item" { rotation: 360 dur: 600 } // ✅
rotate "item" { deg: 360 dur: 600 }      // ✅ (in JES runtime timeline)
```

Note: `TimelineDataParser` accepts `angle` and `rotation`. The JES runtime timeline (`JesScene2D`) accepts `deg`. Both work for hand-coded timelines.

### Forgetting camera has no target entity

```jes
// ❌ WRONG
cameraMove "hero" { x: 200 dur: 500 }

// ✅ CORRECT — no target entity for camera actions
cameraMove { x: 200 y: 100 dur: 500 easing: ease_in_out_quad }
```

---

## Reusable Template Library

Copy-paste these templates and customize the entity names, positions, and timings.

### Template: Quick Fade In

```jes
fade "ENTITY" { alpha: 0 dur: 0 }
wait 50
fade "ENTITY" { alpha: 1 dur: 400 easing: ease_out_quad }
```

### Template: Quick Fade Out

```jes
fade "ENTITY" { alpha: 0 dur: 400 easing: ease_in_quad }
```

### Template: Slide From Left

```jes
move "ENTITY" { x: -200 dur: 0 }
fade "ENTITY" { alpha: 0 dur: 0 }
wait 50
move "ENTITY" { x: TARGET_X dur: 500 easing: ease_out_cubic }
fade "ENTITY" { alpha: 1 dur: 300 easing: ease_out_quad }
```

### Template: Slide From Right

```jes
move "ENTITY" { x: 1400 dur: 0 }
fade "ENTITY" { alpha: 0 dur: 0 }
wait 50
move "ENTITY" { x: TARGET_X dur: 500 easing: ease_out_cubic }
fade "ENTITY" { alpha: 1 dur: 300 easing: ease_out_quad }
```

### Template: Pop In (Scale)

```jes
scale "ENTITY" { x: 0 y: 0 dur: 0 }
fade "ENTITY" { alpha: 0 dur: 0 }
wait 50
scale "ENTITY" { x: 1.0 y: 1.0 dur: 350 easing: ease_out_back }
fade "ENTITY" { alpha: 1 dur: 200 easing: ease_out_quad }
```

### Template: Emphasis Pulse

```jes
scale "ENTITY" { x: 1.12 y: 1.12 dur: 150 easing: ease_out_quad }
wait 150
scale "ENTITY" { x: 1.0 y: 1.0 dur: 200 easing: ease_in_out_quad }
```

### Template: Camera Focus

```jes
cameraMove { x: FOCUS_X y: FOCUS_Y dur: 500 easing: ease_in_out_quad }
cameraZoom { zoom: 1.3 dur: 500 easing: ease_in_out_quad }
```

### Template: Camera Reset

```jes
cameraMove { x: 0 y: 0 dur: 600 easing: ease_in_out_quad }
cameraZoom { zoom: 1.0 dur: 600 easing: ease_in_out_quad }
```

---

## Related Docs

- [Puppeteer JES DSL Reference](../../../editor/puppeteer/puppeteer-jes-dsl.md) — complete action/property reference and export modes
- [Puppeteer Editor Guide](../../../editor/puppeteer/puppeteer-editor-guide.md) — visual keyframe editor usage
- [JES Timeline & Actions](../../jes/timeline/jes-timeline.md) — JES runtime timeline actions (superset including combat, flow control)
- [Puppeteer Animation Timelines](timeline-animation.md) — TimelineData model, TimelineRunner, TimelineRegistry
- [Timeline Overview](../overview/timeline-scripting.md) — story timelines vs animation timelines

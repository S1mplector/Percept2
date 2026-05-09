# Hand-Coding Puppeteer Timelines

Complete guide to writing JES timeline animations by hand — without the Puppeteer keyframe editor. Covers the time cursor model, every action type, easing selection, and 15+ fully annotated examples for common animation scenarios.

Parser source: `modules/core/src/main/java/com/jvn/core/animation/TimelineDataParser.java`
Runtime: `modules/core/src/main/java/com/jvn/core/animation/TimelineRunner.java`

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
| `event` | `"type"` | arbitrary key-value payload | Fire event cue at current cursor |
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

### Easing Families

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

Plus `linear` (constant speed, default when omitted), parameterized springs via `spring(...)` / `damped_spring(...)`, and named reusable curves like `hero_pop`, `ui_soft_in`, and `camera_glide`.

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

### Template: Slide From Bottom

```jes
move "ENTITY" { y: 800 dur: 0 }
fade "ENTITY" { alpha: 0 dur: 0 }
wait 50
move "ENTITY" { y: TARGET_Y dur: 450 easing: ease_out_cubic }
fade "ENTITY" { alpha: 1 dur: 250 easing: ease_out_quad }
```

### Template: Slide To Top (Exit)

```jes
move "ENTITY" { y: -200 dur: 400 easing: ease_in_cubic }
fade "ENTITY" { alpha: 0 dur: 300 easing: ease_in_quad }
```

### Template: Spin In

```jes
scale "ENTITY" { x: 0 y: 0 dur: 0 }
fade "ENTITY" { alpha: 0 dur: 0 }
rotate "ENTITY" { angle: -180 dur: 0 }
wait 50
scale "ENTITY" { x: 1.0 y: 1.0 dur: 500 easing: ease_out_back }
fade "ENTITY" { alpha: 1 dur: 300 easing: ease_out_quad }
rotate "ENTITY" { angle: 0 dur: 500 easing: ease_out_cubic }
```

### Template: Fade-Through (Cross-Dissolve)

Fade out entity A while fading in entity B at the same position:

```jes
fade "ENTITY_A" { alpha: 0 dur: 400 easing: ease_in_quad }
fade "ENTITY_B" { alpha: 1 dur: 400 easing: ease_out_quad }
```

### Template: Typewriter Reveal (Staggered Letters)

For entities representing individual letters/characters:

```jes
fade "letter_1" { alpha: 0 dur: 0 }
fade "letter_2" { alpha: 0 dur: 0 }
fade "letter_3" { alpha: 0 dur: 0 }
fade "letter_4" { alpha: 0 dur: 0 }
fade "letter_5" { alpha: 0 dur: 0 }

wait 200
fade "letter_1" { alpha: 1 dur: 50 }
wait 60
fade "letter_2" { alpha: 1 dur: 50 }
wait 60
fade "letter_3" { alpha: 1 dur: 50 }
wait 60
fade "letter_4" { alpha: 1 dur: 50 }
wait 60
fade "letter_5" { alpha: 1 dur: 50 }
```

### Template: Breathing / Idle Scale Loop

```jes
scale "ENTITY" { x: 1.02 y: 1.02 dur: 1200 easing: ease_in_out_sine }
wait 1200
scale "ENTITY" { x: 1.0 y: 1.0 dur: 1200 easing: ease_in_out_sine }
wait 1200
scale "ENTITY" { x: 1.02 y: 1.02 dur: 1200 easing: ease_in_out_sine }
```

Register with `setLooping(true)` in Java for infinite repetition.

---

## Event Cues

Event cues are instant markers on the timeline that trigger callbacks at specific times. They carry a **type** string and a **payload** map of key-value pairs. The `SceneAccessor` receives them via `onEventCue(type, payload)`.

### Syntax

```jes
event "type_name" {
  key1: value1
  key2: value2
}
```

The event fires at the current time cursor position (just like `playAudio`). Events have no `dur` — they are instantaneous.

### Use Cases

| Type | Purpose | Typical Payload |
|------|---------|----------------|
| `expression` | Change a character's facial expression mid-animation | `target: hero`, `value: angry` |
| `dialogue` | Trigger a dialogue line at a specific animation beat | `text: Watch out!`, `speaker: hero` |
| `sfx` | Fire a sound effect at a precise moment (alternative to `playAudio`) | `path: assets/audio/sfx/hit.ogg` |
| `marker` | Signal a named point in the animation for external systems | `name: phase2_start` |
| `spawn` | Signal entity creation to the hosting scene | `entity: particle_burst`, `x: 400`, `y: 300` |
| `callback` | Invoke a named handler in the host scene | `name: onImpact`, `damage: 50` |

### Example: Expression Change During Animation

A character slides in, and their expression changes mid-entrance:

```jes
timeline {
  // Start off-screen, neutral expression
  move "hero" { x: -150 y: 350 dur: 0 }
  fade "hero" { alpha: 0 dur: 0 }

  event "expression" {
    target: hero
    value: neutral
  }

  // Slide in
  wait 100
  move "hero" { x: 400 y: 350 dur: 600 easing: ease_out_cubic }
  fade "hero" { alpha: 1 dur: 400 easing: ease_out_quad }

  // Mid-entrance — switch to determined expression
  wait 350
  event "expression" {
    target: hero
    value: determined
  }
}
```

In VNS scenes, `VnCharacterSceneAccessor` handles `"expression"` events and applies the expression change to the character sprite.

### Example: Multi-Event Cutscene Beat

```jes
timeline {
  // Character charges forward
  move "hero" { x: 600 dur: 300 easing: ease_in_cubic }

  // Impact frame
  wait 300
  event "marker" {
    name: impact
  }
  playAudio "assets/audio/sfx/slash.ogg" { volume: 0.9 }

  // Knockback
  move "enemy" { x: 850 dur: 200 easing: ease_out_quad }
  wait 100

  event "expression" {
    target: enemy
    value: hurt
  }

  // Camera reacts
  wait 50
  cameraMove { x: 50 dur: 100 easing: ease_out_quad }
  wait 100
  cameraMove { x: 0 dur: 200 easing: ease_in_out_quad }
}
```

### Event Cues in Looping Timelines

Event cues re-trigger each loop cycle, just like audio cues. If you have an idle animation with periodic expression changes, the events fire every loop:

```jes
timeline {
  event "expression" {
    target: npc
    value: blink
  }
  wait 2000
  event "expression" {
    target: npc
    value: neutral
  }
  wait 500
}
```

With `setLooping(true)`, this creates a repeating blink every 2.5 seconds.

---

## Property Alias Reference

The **parser** (`TimelineDataParser`) and the **exporter** (`CodeExporter`) use slightly different property names for some actions. Both are valid when hand-coding, but be aware of which context you're in:

### Parser Accepts (for hand-coding)

| Action | Property | Aliases | Maps To |
|--------|----------|---------|---------|
| `move` | `x` | — | `X` |
| `move` | `y` | — | `Y` |
| `move` | `dur` | `duration` | duration |
| `pivot` | `ox` | — | `PIVOT_X` |
| `pivot` | `oy` | — | `PIVOT_Y` |
| `rotate` | `angle` | `rotation` | `ROTATION` |
| `scale` | `x` | `scale_x` | `SCALE_X` |
| `scale` | `y` | `scale_y` | `SCALE_Y` |
| `fade` | `alpha` | — | `ALPHA` |
| `cameraMove` | `x` | — | `CAMERA_X` |
| `cameraMove` | `y` | — | `CAMERA_Y` |
| `cameraZoom` | `zoom` | — | `CAMERA_ZOOM` |
| `playAudio` | `fadein` | `fadein_ms`, `fade_in`, `fadeinms` | fade-in duration |
| all | `easing` | — | easing type |

### Exporter Emits (what Puppeteer generates)

| Action | Property | Note |
|--------|----------|------|
| `move` | `x`, `y` | Same as parser |
| `pivot` | `ox`, `oy` | Same as parser |
| `rotate` | `deg` | **Different** — parser uses `angle`/`rotation` |
| `scale` | `sx`, `sy` | **Different** — parser uses `x`/`scale_x` |
| `fade` | `alpha` | Same as parser |
| `cameraMove` | `x`, `y` | Same as parser |
| `cameraZoom` | `zoom` | Same as parser |

### Key Difference: `rotate` and `scale`

When pasting Puppeteer-exported code:
- `rotate` with `deg:` — the parser accepts `angle` and `rotation`, but **not** `deg` directly. The JES runtime handles `deg`, but `TimelineDataParser` does not. If you paste exported code into a VNS inline `timeline {}` block, change `deg:` to `angle:`.
- `scale` with `sx:`/`sy:` — the parser accepts `x`/`scale_x` and `y`/`scale_y`, but **not** `sx`/`sy`. Change `sx:` to `scale_x:` and `sy:` to `scale_y:` when using inline VNS timelines.

```jes
// ❌ Exported by Puppeteer — NOT parsed by TimelineDataParser
rotate "logo" { deg: 360 dur: 600 }
scale "button" { sx: 1.5 sy: 1.5 dur: 300 }

// ✅ Hand-coded equivalents for inline VNS / standalone file parsing
rotate "logo" { angle: 360 dur: 600 }
scale "button" { scale_x: 1.5 scale_y: 1.5 dur: 300 }
```

This discrepancy exists because the JES runtime (`JesScene2D`) and the Puppeteer parser (`TimelineDataParser`) are independent systems. The exporter targets JES syntax; the parser accepts a broader set of aliases.

---

## Duration Calculation

When using `[wait N]` in VNS to synchronize with a timeline, you need to know the total duration. Here's how to calculate it.

### Rule

The total duration is the **maximum** of (`cursor position at any wait` + `duration of the longest action starting from that cursor`).

### Step-by-Step

```jes
timeline {
  // cursor = 0
  move "hero" { x: 400 dur: 500 }   // ends at 0+500 = 500ms
  fade "hero" { alpha: 1 dur: 300 }   // ends at 0+300 = 300ms

  wait 200
  // cursor = 200
  scale "hero" { x: 1.2 y: 1.2 dur: 400 }  // ends at 200+400 = 600ms

  wait 600
  // cursor = 800
  fade "hero" { alpha: 0 dur: 200 }  // ends at 800+200 = 1000ms
}
// Total duration = max(500, 300, 600, 1000) = 1000ms
```

### Quick Formula

For each action, compute `cursor_at_action + dur`. The timeline's total duration is the maximum of all these values.

### Accounting for Wait

Remember that `wait` values are **cumulative**:

```jes
wait 100    // cursor = 100
wait 200    // cursor = 300 (not 200!)
wait 50     // cursor = 350
```

### VNS Synchronization

```vns
timeline {
  move "hero" { x: 500 dur: 400 easing: ease_out_cubic }
  fade "hero" { alpha: 1 dur: 300 easing: ease_out_quad }
}
// Total = max(400, 300) = 400ms
// Add a small buffer for frame timing
[wait 420]
hero: I'm here!
```

**Tip:** Add 10–30ms buffer to your `[wait]` value to account for frame timing granularity.

### Duration for Looping Timelines

Looping timelines cycle at their computed duration. The loop period equals the total duration. If you want a precise loop period, ensure your last `wait` + last action's `dur` equals exactly the desired period:

```jes
timeline {
  // 2000ms total cycle
  move "gem" { y: -10 dur: 1000 easing: ease_in_out_sine }
  wait 1000
  move "gem" { y: 0 dur: 1000 easing: ease_in_out_sine }
  // max(1000, 1000+1000) = 2000ms per cycle
}
```

---

## Looping Timelines

The inline `timeline {}` syntax has **no looping directive** — looping can only be enabled programmatically in Java. This is by design: inline timelines in VNS are meant for one-shot animations.

### Setting Up Looping from Java

```java
TimelineData data = TimelineDataParser.parse("npc_idle", """
    timeline {
      move "npc" { y: -8 dur: 1000 easing: ease_in_out_sine }
      wait 1000
      move "npc" { y: 0 dur: 1000 easing: ease_in_out_sine }
    }
    """);
data.setLooping(true);
TimelineRegistry.register(data);
```

Then call from VNS:

```vns
[external jes_timeline npc_idle]
```

The runner loops until:
- The scene changes (runners are discarded with the scene)
- The runner is explicitly removed from `VnState.activeTimelines`

### Looping Design Tips

- **Seamless loops** — ensure the last keyframe value matches the implicit starting value for the next cycle. Otherwise there'll be a visible snap at the loop boundary.
- **Loop-safe audio** — audio cues re-trigger each cycle. Use this for rhythmic effects, but avoid placing BGM starts in looping timelines.
- **Loop-safe events** — event cues also re-trigger. Good for periodic expression changes (blink cycles), bad for one-time triggers.

```jes
// ✅ Seamless — ends where it started (y: 0 → y: -8 → y: 0)
timeline {
  move "gem" { y: -8 dur: 800 easing: ease_in_out_sine }
  wait 800
  move "gem" { y: 0 dur: 800 easing: ease_in_out_sine }
}
// Total: 1600ms per cycle

// ❌ Not seamless — snaps from y: 100 back to y: 0 on loop
timeline {
  move "gem" { y: 100 dur: 800 easing: ease_out_quad }
}
```

### Stopping a Looping Timeline

There is no VNS command to stop a specific looping timeline. Workarounds:

1. **Scene transition** — pushing or replacing a scene clears all runners
2. **Java hook** — register a call handler that removes the runner:

```java
vnState.getActiveTimelines().removeIf(r ->
    r.getTimeline().getName().equals("npc_idle"));
```

3. **Overriding timeline** — start a new non-looping timeline on the same entity/property to override the looping one (the last writer wins each frame)

---

## Compact Syntax

`CodeExporter.exportCompact()` produces a condensed single-line-per-action format. This is useful for short timelines where readability isn't a priority:

```jes
timeline {
  move "hero" { x:-150 y:350 dur:0 }
  fade "hero" { alpha:0 dur:0 }
  wait 100
  move "hero" { x:400 y:350 dur:600 easing:ease_out_cubic }
  fade "hero" { alpha:1 dur:400 easing:ease_out_quad }
}
```

The parser handles this format identically to the multi-line format. Key differences:
- Properties on one line inside `{ }` instead of one-per-line
- No spaces after colons (optional — spaces are fine too)
- Useful for short templates and quick inline VNS blocks

You can hand-write in compact format and the parser handles it correctly:

```vns
narrator: Here she comes!
timeline {
  move "lena" { x:640 y:400 dur:400 easing:ease_out_back }
  fade "lena" { alpha:1 dur:250 easing:ease_out_quad }
}
[wait 420]
lena: I made it!
```

---

## VNS Choreography Patterns

Patterns specifically designed for visual novel character animation in VNS scripts.

### Pattern: Dramatic Character Entrance

Full entrance with audio, camera, and expression change:

```vns
[show hero center neutral]
[show hero center neutral layer 10]

timeline {
  // Start off-screen left, invisible
  move "hero" { x: -200 y: 380 dur: 0 }
  fade "hero" { alpha: 0 dur: 0 }

  // Slide in with camera follow
  wait 100
  move "hero" { x: 640 y: 380 dur: 700 easing: ease_out_back }
  fade "hero" { alpha: 1 dur: 400 easing: ease_out_quad }
  cameraMove { x: -50 dur: 300 easing: ease_out_quad }

  // Camera settles
  wait 400
  cameraMove { x: 0 dur: 400 easing: ease_in_out_quad }

  // Confident expression at landing
  wait 200
  event "expression" {
    target: hero
    value: confident
  }
}

playAudio "assets/audio/sfx/whoosh.ogg" { volume: 0.6 }

[wait 750]
hero: Sorry to keep you waiting.
```

### Pattern: Two-Character Confrontation

Two characters face each other with staggered entrances:

```vns
[show hero left neutral]
[show villain right neutral]

timeline {
  // Both start off-screen
  move "hero" { x: -200 y: 380 dur: 0 }
  move "villain" { x: 1400 y: 380 dur: 0 }
  fade "hero" { alpha: 0 dur: 0 }
  fade "villain" { alpha: 0 dur: 0 }

  // Hero slides in first
  wait 200
  move "hero" { x: 300 y: 380 dur: 600 easing: ease_out_cubic }
  fade "hero" { alpha: 1 dur: 400 easing: ease_out_quad }

  // Villain follows with slight delay
  wait 200
  move "villain" { x: 900 y: 380 dur: 600 easing: ease_out_cubic }
  fade "villain" { alpha: 1 dur: 400 easing: ease_out_quad }

  // Camera widens to show both
  wait 300
  cameraZoom { zoom: 0.9 dur: 500 easing: ease_in_out_quad }
}

[wait 1200]
hero: So we meet again.
villain: Indeed.
```

### Pattern: Reaction Bump

Quick scale + position jolt for surprise/impact — place immediately before a dialogue line:

```vns
hero: Everything is fine—

timeline {
  // Shock reaction
  scale "hero" { scale_x: 1.06 scale_y: 1.06 dur: 80 easing: ease_out_quad }
  move "hero" { y: -6 dur: 80 easing: ease_out_quad }
  wait 80
  scale "hero" { scale_x: 1.0 scale_y: 1.0 dur: 150 easing: ease_in_out_quad }
  move "hero" { y: 0 dur: 150 easing: ease_in_out_quad }
}

[screen shake 3 300]
hero: What was THAT?!
```

### Pattern: Slow Approach

Character walks closer — zoom tracks them:

```vns
[show old_man far_right neutral]

timeline {
  // Slow walk toward center
  move "old_man" { x: 640 dur: 2000 easing: ease_in_out_sine }

  // Camera slowly zooms as they approach
  cameraZoom { zoom: 1.1 dur: 2000 easing: ease_in_out_sine }
}

[wait 1000]
narrator: The old man approached slowly.

[wait 1100]
old_man: I've been expecting you.

// Reset camera
timeline {
  cameraZoom { zoom: 1.0 dur: 600 easing: ease_in_out_quad }
}
```

### Pattern: Character Exit with Audio Fade

```vns
lena: I'll leave you to think about it.

timeline {
  // Walk off-screen right
  move "lena" { x: 1400 dur: 800 easing: ease_in_cubic }
  fade "lena" { alpha: 0 dur: 600 easing: ease_in_quad }
}

[wait 400]
playAudio "assets/audio/sfx/footsteps_fade.ogg" { volume: 0.5 }

[wait 500]
[hide lena]
narrator: She was gone.
```

### Pattern: Scene Transition with Animation

Animate elements out, change background, animate new elements in:

```vns
// Animate current characters out
timeline {
  fade "hero" { alpha: 0 dur: 400 easing: ease_in_quad }
  fade "ally" { alpha: 0 dur: 400 easing: ease_in_quad }
}
[wait 420]

[hide hero]
[hide ally]
[transition FADE 800 library]

[wait 900]
[show hero center neutral]

// Animate hero in at new location
timeline {
  move "hero" { x: 640 y: 400 dur: 0 }
  fade "hero" { alpha: 0 dur: 0 }
  wait 200
  fade "hero" { alpha: 1 dur: 400 easing: ease_out_quad }
}
[wait 650]

narrator: The library was quiet.
hero: Now, where was that book...
```

---

## Debugging Hand-Coded Timelines

### Timeline doesn't play

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| No visible movement | Entity name doesn't match any shown character | Check `[show]` character ID matches the `"name"` in the timeline |
| HUD shows "inline timeline: empty block" | Empty or whitespace-only block | Verify content between `timeline {` and `}` |
| HUD shows "jes_timeline: not found: X" | Named timeline not registered | Register from Java or Puppeteer before calling |
| HUD shows "inline timeline: no scene accessor" | No `SceneAccessor` configured | Ensure `RuntimeVnInterop` is used (not bare `DefaultVnInterop`) |

### Animation looks wrong

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Entity snaps instead of animating | No starting keyframe at `t=0` | Add `dur: 0` action to set initial value |
| Actions overlap when they shouldn't | Missing `wait` between sequential actions | Add `wait <duration_of_previous_action>` |
| Animation starts from wrong position | Implicit start keyframe uses entity's current value | Add explicit `dur: 0` initial position |
| Scale uses `sx`/`sy` but nothing happens | Parser doesn't recognize `sx`/`sy` | Use `scale_x`/`scale_y` or just `x`/`y` inside `scale` |
| Rotation uses `deg` but nothing happens | Parser doesn't recognize `deg` | Use `angle` or `rotation` inside `rotate` |
| Easing has no effect | Easing string not recognized | Check spelling; use lowercase with underscores (e.g., `ease_out_cubic`) |
| Two timelines fight over same property | Both animate the same property on the same entity | Let one finish before starting the other |

### Timing issues

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `[wait]` in VNS is too short | Duration calculation was wrong | Recalculate: max(cursor + dur) for all actions |
| Animation finishes but dialogue appears too late | `[wait]` buffer too large | Reduce to actual duration + 10–20ms |
| Looping timeline has a visible "hiccup" | End value ≠ start value at loop boundary | Ensure last keyframe returns to the initial value |

### Quick Debugging Technique

Add a temporary HUD message to verify timing:

```vns
[hud "Timeline starting" 500]
timeline {
  move "hero" { x: 500 dur: 400 easing: ease_out_cubic }
}
[wait 420]
[hud "Timeline done" 500]
hero: I'm here!
```

If "Timeline starting" appears but "Timeline done" appears before the animation finishes, increase the `[wait]` value. If the animation never plays, check entity name matching.

---

## Advanced: Building Timelines Programmatically

Beyond `TimelineDataParser.parse()`, you can construct `TimelineData` directly in Java for dynamic animations.

### Parameterized Shake

Shake amplitude and frequency driven by a damage value:

```java
public static TimelineData buildDamageShake(String entity, double damage) {
    double amplitude = Math.min(20.0, damage * 0.5);
    int oscillations = Math.min(6, (int)(damage / 10) + 2);
    double stepMs = 50.0;
    double totalMs = oscillations * stepMs;

    TimelineData data = new TimelineData("damage_shake_" + entity, totalMs);
    TimelineData.Track track = new TimelineData.Track(entity);

    for (int i = 0; i < oscillations; i++) {
        double time = i * stepMs;
        double sign = (i % 2 == 0) ? -1 : 1;
        double decay = 1.0 - ((double) i / oscillations);
        double offset = sign * amplitude * decay;

        track.addKeyframe(TimelineData.Property.X,
            new TimelineData.Keyframe(time, offset, Easing.Type.EASE_OUT_QUAD));
    }
    // Return to zero
    track.addKeyframe(TimelineData.Property.X,
        new TimelineData.Keyframe(totalMs, 0, Easing.Type.EASE_OUT_QUAD));

    data.addTrack(track);
    return data;
}
```

### Staggered Entrance Factory

Generate entrance timelines for any number of characters:

```java
public static TimelineData buildStaggeredEntrance(
        List<String> entityNames,
        double targetX, double targetY,
        double staggerMs, double slideMs) {

    double totalMs = staggerMs * (entityNames.size() - 1) + slideMs;
    TimelineData data = new TimelineData("staggered_entrance", totalMs);

    for (int i = 0; i < entityNames.size(); i++) {
        String name = entityNames.get(i);
        double startMs = i * staggerMs;
        double endMs = startMs + slideMs;

        TimelineData.Track track = new TimelineData.Track(name);

        // Start off-screen
        track.addKeyframe(TimelineData.Property.X,
            new TimelineData.Keyframe(0, -200, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.ALPHA,
            new TimelineData.Keyframe(0, 0, Easing.Type.LINEAR));

        // Slide in
        track.addKeyframe(TimelineData.Property.X,
            new TimelineData.Keyframe(startMs, -200, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.X,
            new TimelineData.Keyframe(endMs, targetX + (i * 150), Easing.Type.EASE_OUT_CUBIC));

        track.addKeyframe(TimelineData.Property.ALPHA,
            new TimelineData.Keyframe(startMs, 0, Easing.Type.LINEAR));
        track.addKeyframe(TimelineData.Property.ALPHA,
            new TimelineData.Keyframe(startMs + slideMs * 0.6, 1, Easing.Type.EASE_OUT_QUAD));

        data.addTrack(track);
    }

    return data;
}
```

### Adding Event Cues Programmatically

```java
TimelineData data = new TimelineData("hero_entrance", 800);
// ... add tracks ...

// Expression change at 400ms
data.addEventCue(new TimelineData.EventCue(
    400.0,
    "expression",
    Map.of("target", "hero", "value", "confident")
));

// Custom marker at 700ms
data.addEventCue(new TimelineData.EventCue(
    700.0,
    "marker",
    Map.of("name", "landing_complete")
));
```

### Registering and Playing

```java
// Register
TimelineData timeline = buildDamageShake("enemy", playerDamage);
TimelineRegistry.register(timeline);

// Or play immediately without registering
TimelineRunner runner = new TimelineRunner(timeline, sceneAccessor);
vnState.addTimelineRunner(runner);
```

---

## Related Docs

- [Puppeteer JES DSL Reference](../../../editor/puppeteer/puppeteer-jes-dsl.md) — complete action/property reference and export modes
- [Puppeteer Editor Guide](../../../editor/puppeteer/puppeteer-editor-guide.md) — visual keyframe editor usage
- [JES Timeline & Actions](../../jes/timeline/jes-timeline.md) — JES runtime timeline actions (superset including combat, flow control)
- [Puppeteer Animation Timelines](timeline-animation.md) — TimelineData model, TimelineRunner, TimelineRegistry
- [Timeline Overview](../overview/timeline-scripting.md) — story maps vs animation timelines

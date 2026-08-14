# Story Map And Timeline Scripting

JVN has two distinct sequencing DSLs that serve fundamentally different purposes. This landing page explains the Story Map for narrative structure and animation timelines for runtime choreography.

---

## The Two Systems

### 1. Story Map — Narrative Structure

A DSL for mapping your game's narrative architecture — which scripts exist, how they connect, and how the player can traverse them. Think of it as a **story map**.

```text
arc "Intro" script "scripts/story/prologue.vns" entry "start" cluster "Main" at 40,40
arc "Chapter1" script "scripts/story/ch1.vns" entry "start" cluster "Main" at 300,40
arc "SideQuest" script "scripts/story/side.vns" entry "entry_side" cluster "Optional" at 300,180

link Intro:intro_choice -> Chapter1:start
link Chapter1:offer_side -> SideQuest:entry_side
```

- **File:** `config/story/story.storymap`
- **Editor:** Story Map sidebar panel (visual graph editor)
- **Purpose:** Authoring-time planning and validation, not runtime execution
- **Full reference:** [Story Arcs & Links DSL](../story/timeline-story-arcs.md)

### 2. Animation Timeline — Entity Motion

Keyframe-based animation data that interpolates entity properties, camera properties, advanced visual channels, audio cues, and event cues over time. Think of it as a **choreography system**.

```jes
timeline {
  move "hero" { x: 640 y: 468 dur: 400 easing: ease_out_back }
  wait 200
  fade "hero" { alpha: 0 dur: 500 easing: ease_in_quad }
  playAudio "assets/audio/sfx/whoosh.ogg" { volume: 0.8 }
}
```

- **Created with:** Puppeteer editor (visual keyframe editing) or inline JES blocks
- **Played by:** `TimelineRunner` at runtime
- **Purpose:** Runtime entity animation and scene choreography
- **Full reference:** [Puppeteer Animation Timelines](../animation/timeline-animation.md)
- **Reusable source fragments:** [Puppeteer Motifs](../../../editor/puppeteer/puppeteer-motifs.md)

---

## Side-by-Side Comparison

| Aspect | Story Map | Animation Timeline |
|--------|---------------|-------------------|
| **Purpose** | Map narrative structure | Animate entities over time |
| **Scope** | Entire project | Single scene/sequence |
| **Created by** | Story graph editor or DSL text | Puppeteer editor or JES code |
| **File format** | `.storymap` (arc/link DSL) | `TimelineData` (Java object) |
| **Runtime role** | Authoring & validation only | Active playback via `TimelineRunner` |
| **VNS integration** | `[goto Arc:label]` | `[call jes_timeline name]` or `[external jes_timeline name]` |
| **Entities involved** | VNS scripts (arcs) | JES scene entities |
| **Editing granularity** | Arc-level (which scripts) | Keyframe-level (per-property, per-ms) |
| **Output** | Directed graph of arcs/links | Sequence of tween actions |

---

## When to Use Which

### Use Story Map when you need to:

- Plan a branching narrative with multiple routes
- Visualize which scripts connect to which
- Validate that all cross-scenario jumps (`[goto Arc:label]`) have valid targets
- Organize scripts into clusters (Main Story, Side Quests, Endings)
- Communicate story structure to team members

### Use Animation Timeline when you need to:

- Move characters across the screen
- Fade elements in/out
- Shake or zoom the camera
- Play audio cues at specific moments
- Create cutscene choreography
- Build particle effects sequences
- Orchestrate complex parallel animations

---

## Story Map Quick Start

### 1. Create the file

Create `config/story/story.storymap` in your project.

### 2. Declare arcs

Each arc represents one VNS script:

```text
arc "Prologue" script "scripts/story/prologue.vns" entry "start" cluster "Main" at 40,80
arc "Chapter1" script "scripts/story/ch1.vns" entry "start" cluster "Main" at 300,80
arc "BadEnd" script "scripts/story/bad_end.vns" entry "start" cluster "Endings" at 560,160
arc "GoodEnd" script "scripts/story/good_end.vns" entry "start" cluster "Endings" at 560,40
```

### 3. Declare links

Links describe how the player can move between arcs:

```text
link Prologue:route_split -> Chapter1:start
link Chapter1:fail -> BadEnd:start
link Chapter1:succeed -> GoodEnd:start
```

### 4. Visualize

Open the Story Map sidebar panel to see the graph. Arcs appear as nodes, links as directed edges. Clusters are color-coded groups.

---

## Animation Timeline Quick Start

### Option A: Puppeteer Editor (Visual)

The fastest way to create complex animations — point-and-click keyframe editing with real-time preview.

1. Open a `.vns` or `.jes` file in the editor
2. Open the **Puppeteer Launcher** sidebar panel
3. Place cursor where characters are visible (for VNS files)
4. If the scene uses `[stage ...]`, confirm the launcher shows the active stage
5. Click **Launch @ Cursor**
6. Select an entity → drag to reposition → keyframes auto-create at playhead
7. Apply presets (Fade In, Slide, Bounce, Shake) for common patterns
8. Click **Register** to save to `TimelineRegistry`, or **Copy Code** for the raw JES block
9. Use in VNS: `[call jes_timeline my_animation]`

See [Puppeteer Editor Guide](../../../editor/puppeteer/puppeteer-editor-guide.md) for complete UI reference.

### Option B: Inline JES Block (Code)

Write timeline actions directly — ideal for quick animations where you know the coordinates.

```jes
scene "Cutscene" {
  entity "hero" {
    component Sprite2D {
      image: "assets/characters/hero.png"
      x: -100
      y: 300
      w: 128
      h: 128
      alpha: 0
    }
  }

  timeline {
    move "hero" { x: 400 y: 300 dur: 800 easing: ease_out_back }
    fade "hero" { alpha: 1.0 dur: 600 easing: ease_in_quad }
    wait 500
    cameraZoom { zoom: 1.2 dur: 400 easing: ease_in_out_quad }
    wait 300
    playAudio "assets/audio/sfx/dramatic.ogg" { volume: 0.8 }
  }
}
```

See [Hand-Coding Timelines](../animation/timeline-hand-coding.md) for 18 annotated examples.

### Option C: VNS Inline Timeline

Embed timeline blocks directly in VNS dialogue scripts — no registration needed.

```vns
[show hero center neutral]

timeline {
  move "hero" { x: -200 dur: 0 }
  fade "hero" { alpha: 0 dur: 0 }
  wait 50
  move "hero" { x: 640 dur: 600 easing: ease_out_back }
  fade "hero" { alpha: 1 dur: 400 easing: ease_out_quad }
}

[wait 700]
hero: I have arrived.
```

**Important:** Inline timelines run asynchronously — VNS advances immediately. Use `[wait N]` after the block if you need to synchronize dialogue with the animation.

### Option D: Registered Timeline from Java

For programmatic or reusable animations:

```java
TimelineData data = TimelineDataParser.parse("hero_entrance", """
    timeline {
      move "hero" { x: 640 y: 468 dur: 400 easing: ease_out_back }
      fade "hero" { alpha: 1.0 dur: 300 easing: ease_out_quad }
    }
    """);
TimelineRegistry.register(data);
```

Then from VNS:

```vns
[call jes_timeline hero_entrance]
```

---

## Choosing Your Approach

| Scenario | Best Approach | Why |
|----------|--------------|-----|
| Complex multi-entity cutscene | **Puppeteer Editor** | Visual positioning, drag, preview, presets |
| Quick character entrance/exit | **Hand-code inline** | Faster than opening the editor for simple animations |
| Polished cutscene with precise timing | **Hybrid** (editor + hand-tuning) | Layout visually, fine-tune values manually |
| Shared animation used in many scripts | **Registered timeline** | Register once, call from anywhere |
| One-off animation during dialogue | **VNS inline block** | No registration, lives with the narrative |
| Dynamic animation (damage shake, etc.) | **Java API** | Amplitude/duration varies by game state |

---

## Animation Architecture Overview

---

## Common Animation Scenarios

Quick reference for which actions to use for typical VN/game animations:

| Scenario | Actions | Duration Guide |
|----------|---------|---------------|
| **Character entrance** | `move` + `fade` | 400–700ms, `ease_out_cubic` |
| **Character exit** | `move` + `fade` | 300–500ms, `ease_in_cubic` |
| **Emphasis shake** | `move` (oscillating x) | 200–400ms total, decreasing amplitude |
| **Scale pulse** | `scale` (up then down) | 300–400ms, `ease_out_quad` |
| **Camera pan** | `cameraMove` | 1000–3000ms, `ease_in_out_quad` |
| **Camera zoom** | `cameraZoom` | 300–800ms, `ease_in_out_quad` |
| **Dramatic zoom-in** | `cameraMove` + `cameraZoom` | 300–500ms, `ease_out_expo` |
| **Dialogue box open** | `move` (slide up) | 250–400ms, `ease_out_cubic` |
| **Fade to black** | `fade` on overlay | 400–600ms, `ease_in_quad` |
| **Item pickup** | `move` + `scale` + `fade` | 400–600ms, `ease_out_quad` |
| **Title reveal** | `fade` + `scale` + `cameraZoom` | 800–1200ms, `ease_out_quad` |
| **BGM start** | `playAudio` with `fadein` | N/A (fadein: 1000–2000ms) |
| **SFX hit** | `playAudio` | N/A (instant trigger) |
| **Idle float/bob** | `move` (y oscillation) | 1000–2000ms per cycle, `ease_in_out_sine` |

---

## Easing Quick Reference

Choose easing based on the animation's feel:

| Direction | Meaning | Best For |
|-----------|---------|----------|
| `ease_out_*` | Fast start, smooth stop | Entrances (things arriving) |
| `ease_in_*` | Slow start, fast end | Exits (things leaving) |
| `ease_in_out_*` | Smooth both ends | Camera moves, UI transitions |
| `linear` | Constant speed | Scrolling, mechanical movement |

| Strength | Families (gentle → dramatic) |
|----------|------------------------------|
| **Gentle** | `quad` (t²) |
| **Medium** | `cubic` (t³), `sine` |
| **Strong** | `quart` (t⁴), `expo` (2^t) |
| **Bouncy** | `back` (overshoot), `bounce` (bouncing ball) |
| **Springy** | `elastic` (spring wobble) |

See [Hand-Coding Timelines](../animation/timeline-hand-coding.md) for a complete easing decision chart with 26 types.

---

## Story Patterns

The Story Map DSL supports several common narrative patterns:

| Pattern | Description | Example |
|---------|-------------|---------|
| **Linear** | A → B → C | Prologue → Chapter1 → Chapter2 |
| **Branch/Merge** | A → B or C → D | Choice leads to two paths that converge |
| **Hub-and-Spoke** | Hub → A, Hub → B, Hub → C | Town hub with multiple locations |
| **Failure Loop** | A → B, B fails → A | Retry pattern for challenges |
| **Parallel Routes** | A → B and A → C (independent) | Multiple storylines |

See [Story Arcs & Links DSL](../story/timeline-story-arcs.md) for full examples of each pattern.

---

## Sub-Document Reference

### Story Structure

- **[Story Arcs & Links DSL](../story/timeline-story-arcs.md)** — arc declarations, link syntax, clusters, validation rules, story patterns (linear, branch/merge, hub-and-spoke, failure loops), editor features, team conventions

### Animation

- **[Puppeteer Animation Timelines](../animation/timeline-animation.md)** — TimelineData model, keyframe interpolation, inline JES block syntax, audio cues, TimelineRunner playback, TimelineRegistry, VNS integration, easing types
- **[Puppeteer Motifs](../../../editor/puppeteer/puppeteer-motifs.md)** — reusable named animation fragments with parameters, defaults, nesting, and source-expansion rules
- **[Hand-Coding Timelines](../animation/timeline-hand-coding.md)** — writing timeline animations by hand without the Puppeteer editor, time cursor model, easing selection guide, 18 annotated examples, reusable copy-paste templates

---

## Related Docs

- [Documentation Index](../../../INDEX.md)
- [VNS Scripting](../../vns/overview/vns-scripting.md) — runtime story flow
- [JES Timeline & Actions](../../jes/timeline/jes-timeline.md) — all 27 JES timeline actions
- [Puppeteer Editor Guide](../../../editor/puppeteer/puppeteer-editor-guide.md) — visual keyframe editor
- [Puppeteer JES DSL Reference](../../../editor/puppeteer/puppeteer-jes-dsl.md) — exported timeline code syntax
- [Editor Guide](../../../editor/core/editor.md) — story graph editing mode

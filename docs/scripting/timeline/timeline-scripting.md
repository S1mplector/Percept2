# Timeline Scripting

JVN has two distinct timeline systems that serve fundamentally different purposes. This landing page explains both, when to use each, and how they integrate with the rest of the engine.

---

## The Two Timeline Systems

### 1. Story Timeline — Narrative Structure

A DSL for mapping your game's narrative architecture — which scripts exist, how they connect, and how the player can traverse them. Think of it as a **story map**.

```text
arc "Intro" script "scripts/story/prologue.vns" entry "start" cluster "Main" at 40,40
arc "Chapter1" script "scripts/story/ch1.vns" entry "start" cluster "Main" at 300,40
arc "SideQuest" script "scripts/story/side.vns" entry "entry_side" cluster "Optional" at 300,180

link Intro:intro_choice -> Chapter1:start
link Chapter1:offer_side -> SideQuest:entry_side
```

- **File:** `config/timeline/story.timeline`
- **Editor:** Story Timeline sidebar panel (visual graph editor)
- **Purpose:** Authoring-time planning and validation, not runtime execution
- **Full reference:** [Story Arcs & Links DSL](timeline-story-arcs.md)

### 2. Animation Timeline — Entity Motion

Keyframe-based animation data that interpolates entity properties (position, rotation, scale, alpha, camera) over time. Think of it as a **choreography system**.

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
- **Full reference:** [Puppeteer Animation Timelines](timeline-animation.md)

---

## Side-by-Side Comparison

| Aspect | Story Timeline | Animation Timeline |
|--------|---------------|-------------------|
| **Purpose** | Map narrative structure | Animate entities over time |
| **Scope** | Entire project | Single scene/sequence |
| **Created by** | Story graph editor or DSL text | Puppeteer editor or JES code |
| **File format** | `.timeline` (arc/link DSL) | `TimelineData` (Java object) |
| **Runtime role** | Authoring & validation only | Active playback via `TimelineRunner` |
| **VNS integration** | `[goto Arc:label]` | `[external jes_timeline name]` |
| **Entities involved** | VNS scripts (arcs) | JES scene entities |
| **Editing granularity** | Arc-level (which scripts) | Keyframe-level (per-property, per-ms) |
| **Output** | Directed graph of arcs/links | Sequence of tween actions |

---

## When to Use Which

### Use Story Timeline when you need to:

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

## Story Timeline Quick Start

### 1. Create the file

Create `config/timeline/story.timeline` in your project.

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

Open the Story Timeline sidebar panel to see the graph. Arcs appear as nodes, links as directed edges. Clusters are color-coded groups.

---

## Animation Timeline Quick Start

### Option A: Puppeteer Editor (Visual)

1. Open a JES scene in the editor
2. Launch Puppeteer from the sidebar
3. Select an entity, set keyframes at different times
4. Puppeteer generates `TimelineData` and can export JES code

### Option B: Inline JES Block (Code)

Write timeline actions directly in a JES scene:

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
    // Hero slides in from left
    parallel {
      move "hero" { x: 400 y: 300 dur: 800 easing: ease_out_back }
      fade "hero" { alpha: 1.0 dur: 600 easing: ease_in_quad }
    }
    wait 500

    // Camera zooms in
    cameraZoom { zoom: 1.2 dur: 400 easing: ease_in_out_quad }
    wait 300

    // Play dramatic sound
    playAudio "assets/audio/sfx/dramatic.ogg" { volume: 0.8 }
    wait 200

    // Hero bounces
    move "hero" { x: 400 y: 280 dur: 200 easing: ease_out_quad }
    move "hero" { x: 400 y: 300 dur: 200 easing: ease_in_quad }

    // Camera returns
    cameraZoom { zoom: 1.0 dur: 300 easing: ease_out_quad }
  }
}
```

### Option C: VNS Inline Timeline

Trigger timeline animations from VNS scripts:

```vns
[show hero center neutral]
timeline {
  entity "hero" {
    0ms { x: -200, alpha: 0.0 }
    800ms { x: 640, alpha: 1.0, easing: ease_out }
  }
  playAudio "assets/audio/sfx/whoosh.ogg"
}
[wait 200]
hero: I have arrived.
```

---

## Story Patterns

The Story Timeline DSL supports several common narrative patterns:

| Pattern | Description | Example |
|---------|-------------|---------|
| **Linear** | A → B → C | Prologue → Chapter1 → Chapter2 |
| **Branch/Merge** | A → B or C → D | Choice leads to two paths that converge |
| **Hub-and-Spoke** | Hub → A, Hub → B, Hub → C | Town hub with multiple locations |
| **Failure Loop** | A → B, B fails → A | Retry pattern for challenges |
| **Parallel Routes** | A → B and A → C (independent) | Multiple storylines |

See [Story Arcs & Links DSL](timeline-story-arcs.md) for full examples of each pattern.

---

## Sub-Document Reference

### Story Structure

- **[Story Arcs & Links DSL](timeline-story-arcs.md)** — arc declarations, link syntax, clusters, validation rules, story patterns (linear, branch/merge, hub-and-spoke, failure loops), editor features, team conventions

### Animation

- **[Puppeteer Animation Timelines](timeline-animation.md)** — TimelineData model, keyframe interpolation, inline JES block syntax, audio cues, TimelineRunner playback, TimelineRegistry, VNS integration, easing types
- **[Hand-Coding Timelines](timeline-hand-coding.md)** — writing timeline animations by hand without the Puppeteer editor, time cursor model, easing selection guide, 18 annotated examples, reusable copy-paste templates

---

## Related Docs

- [Documentation Index](../../INDEX.md)
- [VNS Scripting](../vns/vns-scripting.md) — runtime story flow
- [JES Timeline & Actions](../jes/jes-timeline.md) — all 22 JES timeline actions
- [Puppeteer Editor Guide](../../editor/puppeteer-editor-guide.md) — visual keyframe editor
- [Puppeteer JES DSL Reference](../../editor/puppeteer-jes-dsl.md) — exported timeline code syntax
- [Editor Guide](../../editor/editor.md) — story graph editing mode

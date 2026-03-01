# Timeline Scripting

JVN has two distinct timeline systems:

1. **Story Timeline** — a DSL for mapping narrative arcs, script files, entry labels, clusters, and arc-to-arc links into a visual story graph.
2. **Puppeteer Animation Timelines** — keyframe-based animation data that interpolates entity properties (position, rotation, scale, alpha, camera) over time, created with the Puppeteer editor or inline JES blocks.

---

## Quick Start — Story Arcs

```text
arc "Intro" script "scripts/story/prologue.vns" entry "start" cluster "Main" at 40,40
arc "Chapter1" script "scripts/story/ch1.vns" entry "start" cluster "Main" at 300,40
arc "SideQuest" script "scripts/story/side.vns" entry "entry_side" cluster "Optional" at 300,180

link Intro:intro_choice -> Chapter1:start
link Chapter1:offer_side -> SideQuest:entry_side
```

File: `config/timeline/story.timeline`

## Quick Start — Animation Timeline

```jes
timeline {
  move "hero" { x: 640 y: 468 dur: 400 easing: ease_out_back }
  wait 200
  fade "hero" { alpha: 0 dur: 500 easing: ease_in_quad }
  playAudio "assets/audio/sfx/whoosh.ogg" { volume: 0.8 }
}
```

---

## Sub-Document Reference

### Story Structure

- **[Story Arcs & Links DSL](timeline-story-arcs.md)** — arc declarations, link syntax, clusters, validation rules, story patterns (linear, branch/merge, hub-and-spoke, failure loops), editor features, team conventions

### Animation

- **[Puppeteer Animation Timelines](timeline-animation.md)** — TimelineData model, keyframe interpolation, inline JES block syntax, audio cues, TimelineRunner playback, TimelineRegistry, VNS integration, easing types

---

## Key Concepts

| Concept | Story Timeline | Animation Timeline |
|---------|---------------|-------------------|
| **Purpose** | Project-level narrative map | Entity property animation |
| **Created by** | Story graph editor / DSL text | Puppeteer editor / inline JES |
| **File format** | `.timeline` (arc/link DSL) | `TimelineData` (Java object) |
| **Runtime role** | Authoring & validation only | Playback via `TimelineRunner` |
| **VNS integration** | `[goto Arc:label]` | `[external jes_timeline name]` |

---

## Related Docs

- [Documentation Index](../../INDEX.md)
- [VNS Scripting](../vns/vns-scripting.md) — runtime story flow
- [JES Timeline & Actions](../jes/jes-timeline.md) — JES scene timeline blocks
- [Puppeteer Editor](../../editor/puppeteer.md) — visual keyframe editor
- [Editor Guide](../../editor/editor.md) — story graph editing mode

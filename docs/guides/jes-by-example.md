# JES By Example

A progressive tutorial series that builds increasingly complex JES scenes — from a single label to animated worlds, RPG systems, particles, interactive controls, and VNS integration.

Each chapter is a self-contained document covering one topic in depth with full examples, property references, and design patterns.

Read chapters 1–4 in order if JES is new to you. Chapters 5–12 can then be followed by feature area, although the advanced examples assume you understand entities, components, timelines, and call handlers.

Source reference:
- Parser: `modules/scripting/src/main/java/com/jvn/scripting/jes/JesParser.java`
- Loader: `modules/scripting/src/main/java/com/jvn/scripting/jes/JesLoader.java`
- Runtime: `modules/scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`

---

## Prerequisites

- JVN project built and running ([Getting Started](getting-started.md))
- Basic familiarity with what JES is ([JES Overview](../scripting/jes/overview/jes-scripting.md))

---

## Chapters

### Beginner

| # | Chapter | What You Learn |
|---|---------|---------------|
| 1 | [Hello World](jes-by-example/01-hello-world.md) | Scene block, entities, `Label2D`, color values, running a scene |
| 2 | [Shapes and Layout](jes-by-example/02-shapes-and-layout.md) | `Panel2D` rectangles, entity layering, z-order, UI compositions |
| 3 | [Sprites and Animation](jes-by-example/03-sprites-and-animation.md) | `Sprite2D`, timeline basics, `move`/`fade`/`scale`/`rotate`, easing curves |

### Intermediate

| # | Chapter | What You Learn |
|---|---------|---------------|
| 4 | [Input and Call Handlers](jes-by-example/04-input-and-call-handlers.md) | Key bindings, `registerCall`, `invokeCall`, `setActionHandler`, built-in actions |
| 5 | [Parallel Animation and Camera](jes-by-example/05-parallel-and-camera.md) | `parallel` blocks, `cameraMove`/`cameraZoom`/`cameraShake`, cinematic sequencing |
| 6 | [Animated Characters](jes-by-example/06-animated-characters.md) | `Character2D`, spritesheet layout, animation definitions, `cameraFollow`, NPCs |
| 7 | [Tilemap World](jes-by-example/07-tilemap-world.md) | Tilesets, tile maps, collision/trigger layers, CSV data, `warpMap`, audio |

### Advanced

| # | Chapter | What You Learn |
|---|---------|---------------|
| 8 | [RPG Systems](jes-by-example/08-rpg-systems.md) | `Stats`, `Inventory`, `Equipment`, `Ai2D`, items, combat, AI behavior types |
| 9 | [Physics Bodies](jes-by-example/09-physics-bodies.md) | `PhysicsBody2D`, circle/box shapes, static bodies, sensors, `onTrigger` |
| 10 | [VNS Bridge Integration](jes-by-example/10-vns-bridge.md) | `[jes push]`, launch parameters, `return` handler, data flow, scene stack |
| 11 | [Particle Effects](jes-by-example/11-particle-effects.md) | `ParticleEmitter2D`, bursts, continuous emission, motion, color, blending |
| 12 | [Interactive UI](jes-by-example/12-interactive-ui.md) | `Button2D`, `Slider2D`, widget states, Java callbacks, live labels |

---

## What's Next

- [JES Overview & Reference](../scripting/jes/overview/jes-scripting.md) — complete language reference
- [Component Reference](../scripting/jes/scene/components.md) — all 12 component types
- [Timeline Actions](../scripting/jes/timeline/jes-timeline.md) — all 27 timeline action types
- [JES UI Widgets](../scripting/jes/gameplay/jes-ui-widgets.md) — buttons, sliders, and HUD patterns
- [VN Bridge & Java Hooks](../scripting/jes/integration/jes-bridge.md) — full integration reference
- [VNS By Example](vns-by-example.md) — the same progressive tutorial series for VNS scripting
- [Puppeteer Editor Guide](../editor/puppeteer/puppeteer-editor-guide.md) — visual animation authoring

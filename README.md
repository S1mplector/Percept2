# Java Vector Nexus Engine

<div align="left">
    <img src="docs/images/jvn_logo.png" width="512">
    <br><br>
</div>

Modern, modular 2D game engine with a lightweight scene graph, simple physics, a custom DSL (JES) for content, and a JavaFX Editor for iteration.

- Modules: `core`, `swing`, `fx`, `scripting`, `runtime`, `editor`
- Rendering backends: Swing (`swing`) and JavaFX (`fx`)
- Scene editor: Open a JES file, preview, select entities, tweak properties live

## Quick Start

Prereqs: JDK 17+, Gradle (wrapper included).

### Run Editor
```bash
./gradlew :editor:run
```

## Docs

- VNS: `docs/VNS Scripting/VNS Scripting.md`
- JES: `docs/JES Scripting/JES Scripting.md`
- Interop: `docs/Interop.md`
- Performance: `docs/Performance.md`

## Modules

- `core`: scene graph (`Scene2DBase`, `Entity2D`), components (`Panel2D`, `Label2D`, `Sprite2D`), physics (`RigidBody2D`, `PhysicsWorld2D`)
- `swing`: Swing implementation of `Blitter2D`
- `fx`: JavaFX implementation `FxBlitter2D` and app launcher helpers
- `scripting`: JES tokenizer, parser, AST, and loader → builds `JesScene2D`
- `runtime`: command-line runner (loads JES with `--jes`, selects UI via `--ui`)
- `editor`: JavaFX### Editor
- JavaFX-based scene preview
- Entity selection and property inspection
- Scene Graph panel for named entity navigation
- Inspector panels for all component types
  - Label2D: text, size, bold, align, color
  - Sprite2D: image path, size, alpha, origin
  - Panel2D: dimensions
  - PhysicsBody2D: mass, restitution
- Canvas picking for all entity types
- Hot reload supportation

## License
TBD.

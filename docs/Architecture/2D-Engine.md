# 2D Engine Notes

This document focuses on the JES + Scene2D stack used for gameplay overlays and minigames.

## Core 2D Stack

- Scene runtime base: `Scene2DBase`
- Entities: `Entity2D` and specializations (`Sprite2D`, `Label2D`, `Panel2D`, `TileMap2D`, `ParticleEmitter2D`, etc.)
- Camera: `Camera2D`
- Input abstraction: `Input` + action binding support
- Physics: `PhysicsWorld2D` + `RigidBody2D`
- JES runtime scene: `JesScene2D`

## JES Scene Assembly

`JesLoader` converts JES AST to a runtime scene and wires:

- entities/components
- tilemaps/collision layers
- input bindings
- timeline actions
- optional RPG-style components (`Stats`, `Inventory`, `Equipment`, `Ai2D`)

## Supported JES Components (Current)

- `Panel2D`
- `Sprite2D`
- `Label2D`
- `ParticleEmitter2D`
- `PhysicsBody2D`
- `Character2D`
- `Stats`
- `Inventory`
- `Equipment`
- `Ai2D`
- `Button2D`
- `Slider2D`

See detailed per-property docs in `docs/JES Scripting/Components.md`.

## Timeline Actions in Runtime

Parser/runtime support includes:

- `wait`, `waitForCall`, `call`
- `move`, `walkToTile`, `rotate`, `scale`, `fade`, `visible`
- `cameraMove`, `cameraZoom`, `cameraShake`, `cameraFollow`
- `playAudio`, `stopAudio`
- `damage`, `heal`, `emitParticles`, `setParallax`
- flow controls: `label`, `jump`, `parallel`, `loop`

## Physics Model

Physics world capabilities currently include:

- circle + AABB bodies
- static/dynamic/sensor modes
- restitution and damping controls
- raycasts and collision callback hooks
- tilemap collider generation for map layers marked `collision`

When profiling scene performance, body count and pairwise checks are major cost drivers.

## Asset Resolution

Assets are loaded through `AssetCatalog` with manager selection from runtime startup:

- classpath manager by default
- optional filesystem overlay via `--assets <projectDir>`

Recommended JES asset conventions:
- images/sprites in `assets/` (project side) or `game/images/` (classpath side)
- scripts in `scripts/` (project) or `game/scripts/` (classpath)

## VN + JES Coexistence

JVN lets narrative scenes and 2D scripted scenes cooperate:

- VNS pushes a JES scene for a minigame.
- JES returns values back to VN variables.
- VN flow resumes from a chosen label.

This enables "story + gameplay segment + story" loops without leaving the engine runtime.

## Current Practical Limits

- No broadphase physics partitioning yet (dense body scenes are more expensive).
- JES timeline is intentionally declarative; heavy per-frame custom logic should stay in Java scene code.
- Parser validates known properties but intentionally allows unknown component types for extension paths.

## Short-Term Extension Opportunities

- richer UI component set for JES overlays
- more timeline easing/curve controls
- dedicated in-editor collision/trigger visualization
- scene profiling overlays (entity count, physics steps, timeline action timings)

# core

Foundation module for the JVN engine. Every other module depends on `:core`.

## Packages

| Package | Purpose |
|---------|---------|
| `animation` | Sprite-sheet animation, frame sequencing |
| `assets` | Asset catalog, filesystem/classpath resolution |
| `audio` | Audio channel abstractions (BGM, SFX, Voice) |
| `config` | Project configuration loading (`jvn.project`) |
| `engine` | Engine update loop, delta smoothing, SceneManager |
| `generalhelp` | Jane assistant, TAGI consensus grounding, and optional Gemini or ONNX chat backends |
| `graphics` | Camera2D, ViewportScaler2D |
| `input` | Input polling, key/mouse bindings |
| `localization` | Locale-aware resource resolution |
| `math` | Scalar helpers plus 2D vector, geometry, ray, segment, and transform utilities |
| `menu` | Menu profile system — screens, layouts, styles, actions, registry, inheritance |
| `phone` | In-game phone UI model, config codec, and command/runtime support for chats, apps, calls, and typed messages |
| `physics` | PhysicsWorld2D, broadphase, raycasts, rigid bodies |
| `rpg` | Stats, inventory, equipment, items, damage/heal |
| `scene` / `scene2d` | Scene graph, Entity2D, Sprite2D, Label2D, Panel2D, SpriteAnimation2D, parallax |
| `tween` | Tweening and easing functions |
| `ui` | Shared UI model types |
| `vn` | Full visual novel runtime — VnScene, VnState, parser, save/load, rollback, settings, text formatting, scenario loader |

## Build

```bash
./gradlew :core:build
```

Core keeps dependencies small, but includes ONNX Runtime for Jane's optional ONNX chat backend.

## Documentation

- [System Architecture](../../docs/architecture/core/system-architecture.md)
- [2D Engine](../../docs/architecture/core/2d-engine.md)
- [TAGI General Help](../../docs/architecture/core/tagi-general-help.md)
- [Overview](../../docs/architecture/core/overview.md)

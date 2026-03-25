# core

Zero-dependency foundation module for the JVN engine. Every other module depends on `:core`.

## Packages

| Package | Purpose |
|---------|---------|
| `animation` | Sprite-sheet animation, frame sequencing |
| `assets` | Asset catalog, filesystem/classpath resolution |
| `audio` | Audio channel abstractions (BGM, SFX, Voice) |
| `config` | Project configuration loading (`jvn.project`) |
| `engine` | Engine update loop, delta smoothing, SceneManager |
| `graphics` | Camera2D, ViewportScaler2D |
| `input` | Input polling, key/mouse bindings |
| `localization` | Locale-aware resource resolution |
| `math` | Vector, interpolation, easing utilities |
| `menu` | Menu profile system — screens, layouts, styles, actions, registry, inheritance |
| `nativebridge` | JNI bridge abstractions for native-math |
| `phone` | In-game phone UI model |
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

No external dependencies.

## Documentation

- [System Architecture](../docs/architecture/core/system-architecture.md)
- [2D Engine](../docs/architecture/core/2d-engine.md)
- [Overview](../docs/architecture/core/overview.md)

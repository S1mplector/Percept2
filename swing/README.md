# swing

Alternative Java Swing rendering backend for the JVN engine. Provides a lightweight, non-JavaFX launcher for environments where JavaFX is unavailable.

## Key Classes

| Class | Purpose |
|-------|---------|
| `SwingLauncher` | Swing-based application launcher and window manager |
| `SwingBlitter2D` | `Graphics2D`-based 2D scene renderer |
| `SwingSceneRendererRegistry` | Scene renderer dispatch for the Swing backend |

## Dependencies

- `:core` — engine abstractions, scene graph

## Build

```bash
./gradlew :swing:build
```

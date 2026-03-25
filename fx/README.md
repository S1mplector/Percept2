# fx

JavaFX rendering backend for the JVN engine. Provides the primary desktop windowed launcher, 2D scene rendering, VN dialogue/menu rendering, and audio integration.

## Key Classes

| Class / Package | Purpose |
|-----------------|---------|
| `FxLauncher` | Main JavaFX `Application` — window management, render loop, input routing |
| `FxLauncherBindings` | Launcher configuration bindings |
| `render/` | `FxBlitter2D` — Canvas-based 2D scene renderer |
| `scene2d/` | `FxSceneRendererRegistry` — scene renderer dispatch |
| `vn/` | `VnRenderer` — dialogue box, name box, choices, HUD, screen effects |
| `menu/` | `MenuRenderer` — menu screen rendering with styles and button skins |
| `phone/` | Phone UI renderer |
| `audio/` | JavaFX media audio bridge |
| `ui/` | Shared UI rendering helpers |

## Dependencies

- `:core` — engine abstractions, scene graph, VN model
- `:audio-fx` — native synthesizer integration
- JavaFX 21 (`javafx-base`, `javafx-graphics`, `javafx-controls`, `javafx-media`, `javafx-swing`)

## Build

```bash
./gradlew :fx:build
```

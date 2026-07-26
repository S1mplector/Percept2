# Web Runtime Bootstrap

**Module:** `modules/web-runtime/`  
**Status:** Executable browser bootstrap; not game-ready

## What Exists

The module now contains an executable TeaVM/Canvas 2D platform slice:

- `WebCanvasRenderSurface`
- `WebRenderer` and `WebRendererFactory`
- `WebImageCache`
- `WebGameLoop`
- `WebLauncher` and `WebRuntimeSession`
- `WebMain`, a TeaVM browser entrypoint
- a dependency-free JSON reader for the JavaScript-to-`ApplicationConfig` launch boundary
- TeaVM 0.15 and its browser SLF4J backend
- typed JSO bindings for the DOM, Canvas 2D, images, and `requestAnimationFrame`
- `webDist`, which creates a static HTML/JavaScript directory
- `webSmoke`, which executes the generated bundle against a minimal DOM harness
- JVM-side unit tests for configuration, loop lifecycle, caching, and renderer decisions

The renderer is named `Canvas 2D`. There is no WebGL renderer implementation.

## Launcher Configuration Boundary

`WebLauncher.startGame(configJson, canvasId)` now validates and consumes its
`configJson` argument instead of launching with unconditional hardcoded values.
The same behavior can be tested on the JVM without requiring a canvas or DOM
through `WebLauncher.parseConfig(configJson)`.

Supported fields:

| Field | Type and bounds | Web default |
|---|---|---|
| `title` | string | `JVN Game` |
| `width` | positive 32-bit integer | `1280` |
| `height` | positive 32-bit integer | `720` |
| `fixedUpdateMs` | non-negative integer | `0` |
| `fixedUpdateMaxSteps` | positive 32-bit integer | `5` |
| `timeScale` | number from `0.0` through `10.0` | `1.0` |

The input must be a top-level JSON object. Missing fields retain the defaults,
unknown fields are ignored for forward compatibility, and malformed JSON,
duplicate keys, invalid known-field types, and out-of-range known values fail
with `IllegalArgumentException`.

```json
{
  "title": "Browser Story",
  "width": 1920,
  "height": 1080,
  "fixedUpdateMs": 16,
  "fixedUpdateMaxSteps": 8,
  "timeScale": 0.75
}
```

The checked-in bootstrap page stores this JSON in the `jvn-config` script
element. `WebMain` reads it, sizes the `jvn-canvas` backing store for the
browser's device pixel ratio, and starts the engine loop.

## Build And Smoke-Test

```bash
./gradlew :web-runtime:test
./gradlew :web-runtime:webDist
./gradlew :web-runtime:webSmoke
```

`webDist` writes:

```text
modules/web-runtime/build/distributions/web/
├── index.html
└── js/
    ├── jvn-web.js
    └── jvn-web.js.map
```

Serve that directory over HTTP with any static server. `webSmoke` requires Node
and verifies the generated TeaVM export, JSON configuration, high-DPI canvas
sizing, first rendered frame, and animation-frame rescheduling.

## Current Bootstrap Behavior

The browser output starts a real `Engine`, updates it from
`requestAnimationFrame`, draws a Canvas 2D bootstrap frame, and exposes the live
engine, surface, renderer, and game loop through `WebRuntimeSession`. A Java web
bootstrap can push an initial scene and replace the frame renderer.

This does not yet make normal JVN game projects web-playable. There is no
adapter that renders the existing VN/menu scene family through `WebRenderer`.

## What Still Does Not Exist

- VN/menu scene rendering through the Canvas 2D backend;
- a web game-project entrypoint and automatic asset/script copy;
- keyboard, pointer, touch, and wheel routing into engine input;
- Web Audio and voice/music lifecycle support;
- LocalStorage or IndexedDB save/persistent storage;
- real Chromium, Firefox, and WebKit integration tests;
- WebAssembly GC output or a supported browser release package.

## Definition Of Game-Deployable

Before this target can be marked supported for games, it needs scene rendering,
game asset/script packaging, input, audio, per-game browser storage, and
cross-browser tests. TeaVM compilation, DOM/canvas bindings, an animation loop,
and a reproducible static hosting artifact are now established.

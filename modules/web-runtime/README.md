# Web Runtime Bootstrap

This module now produces a static TeaVM/Canvas 2D browser bootstrap. It is an
executable platform preview, not yet a game-ready JVN web runtime.

Implemented today:

- TeaVM 0.15 JavaScript compilation;
- typed TeaVM JSO bindings for DOM, Canvas 2D, images, and animation frames;
- device-pixel-ratio-aware canvas sizing;
- a validated JSON-to-`ApplicationConfig` boundary;
- a testable engine/update/render/present loop;
- a static HTML/JavaScript distribution and generated-bundle smoke test.

The Java-side launcher accepts a top-level JSON object:

```json
{
  "title": "Browser Story",
  "width": 1280,
  "height": 720,
  "fixedUpdateMs": 16,
  "fixedUpdateMaxSteps": 5,
  "timeScale": 1.0
}
```

Call `WebLauncher.parseConfig(json)` to validate this boundary without invoking
the browser APIs. Missing fields use web defaults, unknown fields are
ignored for forward compatibility, and malformed or invalid known fields throw
`IllegalArgumentException`.

Build and verify:

```bash
./gradlew :web-runtime:test
./gradlew :web-runtime:webDist
./gradlew :web-runtime:webSmoke
```

The static output is written under
`modules/web-runtime/build/distributions/web/`. Serve that directory over HTTP
to open the bootstrap page.

The remaining support boundary is substantial: the browser loop does not yet
adapt JVN's VN/menu scenes to Canvas 2D, package a game's assets and scripts,
or provide browser input, audio, save storage, and real cross-browser tests.

See [the platform status page](../../docs/runtime/platforms/web-runtime.md).

# Web Runtime Scaffold

**Module:** `modules/web-runtime/`  
**Status:** Architectural scaffold; not deployable

## What Exists

The module contains JVM-compilable shapes for a Canvas 2D backend:

- `WebCanvasRenderSurface`
- `WebRenderer` and `WebRendererFactory`
- `WebImageCache`
- `WebGameLoop`
- `WebLauncher`
- a `ServiceLoader` registration and small JVM-side tests

The factory's current display name is `WebGL/Canvas2D`, but the source is a
Canvas 2D-shaped scaffold. There is no WebGL renderer implementation.

## What Does Not Exist

This repository does not currently contain:

- a TeaVM, GWT, or other Java-to-web Gradle plugin;
- JavaScript or WebAssembly compilation tasks;
- a generated HTML loader or static distribution;
- implemented and verified DOM bindings;
- complete engine-scene rendering from `WebGameLoop`;
- browser input, Web Audio, LocalStorage/IndexedDB, or browser integration tests.

The source uses unresolved `native` methods with JavaScript-like comments.
Those methods are not executable on the JVM, and no configured transpiler turns
them into browser code.

## What You Can Run Today

```bash
./gradlew :web-runtime:test
./gradlew :web-runtime:compileJava
```

These commands validate scaffold code only. `gwtCompile`, TeaVM, WASM, and web
distribution tasks do not exist in this build.

## Definition of Deployable

Before this target can be marked supported, it needs a chosen transpiler and
compatible core subset, real DOM/canvas bindings, scene rendering, input,
audio, per-game browser storage, cross-browser tests, and a reproducible static
hosting artifact.

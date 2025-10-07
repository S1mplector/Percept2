# JVN - Java Visual Novel Engine

A modular JavaFX based Visual Novel engine.

## Modules
- core: engine-agnostic domain/services
- fx: JavaFX adapter (rendering, input, UI)
- runtime: desktop launcher
- scripting: VN scripting layer (stub)
- audio-integration: bridge to audio backend (Simp3 later)
- editor: future GUI authoring tool
- demo-game: example content structure
- testkit: shared test utilities

## Requirements
- JDK 21 (recommended; JDK 17 compatible with minor changes)
- Gradle 8+ (or use Gradle Wrapper once added)

## Running
From the project root:

```
./gradlew :runtime:run
```

First run may download JavaFX. A blank window should appear.

## JavaFX Platform
The build uses the OpenJFX Gradle plugin to resolve platform artifacts automatically.

## Next steps
- Integrate Simp3 as a nested repo and wire `audio-integration`.
- Flesh out scripting, scene system, and asset pipeline.

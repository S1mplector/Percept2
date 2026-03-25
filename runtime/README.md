# runtime

Standalone application entry point for running JVN projects. Boots the engine, wires VN interop, and launches the game loop.

## Key Classes

| Class | Purpose |
|-------|---------|
| `JvnApp` | `main()` entry point — CLI parsing, engine bootstrap, asset lookup, scene initialization |
| `RuntimeVnInterop` | Wires VNS commands to engine systems at runtime — scene accessor, locale-aware script loading |
| `JesVnBridge` | Bridges JES and VNS scene stacks — call handlers, return data, launch properties |
| `BridgedVnScene` | VnScene adapter for cross-system scene coordination |

## Dependencies

- `:core`, `:fx`, `:scripting`, `:audio`, `:swing`
- `:demo-game`, `:billiards-game` (runtime classpath for bundled demo assets)
- `logback-classic` — logging

## Build & Run

```bash
./gradlew :runtime:run
```

## Documentation

- [Runtime Guide](../docs/runtime/core/runtime.md)
- [Interop Guide](../docs/runtime/core/interop.md)
- [VNS Scene Lifecycle](../docs/scripting/vns/runtime/vns-scene-lifecycle.md)

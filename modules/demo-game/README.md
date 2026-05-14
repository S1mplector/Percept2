# demo-game

Bundled demo project used as the default runtime content. Provides sample VNS scripts, assets, and config files that ship with the engine for testing and demonstration purposes.

## Usage

This module is included on the `:runtime` classpath so its resources are available when launching the engine without an external project.

```bash
./gradlew :runtime:run
```

The demo game assets are resolved automatically by the runtime's asset discovery logic.

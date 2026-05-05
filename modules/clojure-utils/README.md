# clojure-utils

Clojure DSL utilities for authoring JVN content. Provides idiomatic Clojure functions for defining menus, scenes, and timelines that compile to core engine data structures.

## Source Files

| File | Purpose |
|------|---------|
| `menu.clj` | Menu screen, layout, and style declarations |
| `scene.clj` | JES scene and entity definitions |
| `timeline.clj` | Animation timeline keyframe builders |

## Dependencies

- `:core` — engine model types
- Clojure 1.11.2
- `clojurephant` Gradle plugin for AOT compilation

## Build

```bash
./gradlew :clojure-utils:build
```

Requires the Clojars repository (configured automatically in `build.gradle.kts`).

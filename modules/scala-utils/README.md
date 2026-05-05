# scala-utils

Type-safe Scala 3 DSL builders for authoring JVN content programmatically. Provides a concise, functional alternative to hand-editing `.jes`, `.menu`, `.layout`, `.style`, and button layout files.

## DSL Files

| File | Purpose |
|------|---------|
| `SceneDsl.scala` | JES scene and entity declarations |
| `MenuDsl.scala` | Menu screens, styles, layouts, and button layout builders |
| `TimelineDsl.scala` | Animation timeline keyframe sequences |

## Example

```scala
import com.jvn.scala.dsl.MenuDsl.*

buttonLayout("main", resolution = "1920x1080", menuType = "main") {
  button("new_game") {
    bounds(100, 200, 300, 50)
    label("New Game")
    asset("btn_new.png")
  }
}
```

## Dependencies

- `:core` — engine model types
- Scala 3.3.3

## Build

```bash
./gradlew :scala-utils:build
```

## Documentation

- [Scala DSL Reference](../../docs/scripting/ui/layout/reference/scala-dsl.md)

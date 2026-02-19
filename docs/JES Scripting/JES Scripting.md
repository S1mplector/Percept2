# JES Scripting

JES (JVN Engine Script) is a DSL for authoring 2D scenes, entities, input bindings, and timelines.

Core files:
- tokenizer: `scripting/src/main/java/com/jvn/scripting/jes/JesTokenizer.java`
- parser: `scripting/src/main/java/com/jvn/scripting/jes/JesParser.java`
- loader: `scripting/src/main/java/com/jvn/scripting/jes/JesLoader.java`
- runtime scene: `scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`

## Quick Start

Minimal scene:

```jes
scene "Demo" {
  entity "title" {
    component Label2D {
      text: "Hello JES"
      x: 60
      y: 80
      size: 24
      color: rgb(1,1,1,1)
    }
  }
}
```

Run directly:

```bash
./gradlew :runtime:run --args='--jes game/minigames/demo.jes'
```

## Language Blocks

### Scene block

```text
scene "Name" { ... }
```

Scene can contain:
- `tileset` declarations
- `item` declarations
- `map` declarations
- `entity` declarations
- input bindings (`on key ... do ...`)
- `timeline { ... }`
- scene-level props (`key: value`)

### Entity block

```text
entity "entityName" {
  component Type { ... }
  component AnotherType { ... }
}
```

### Input binding

```text
on key "D" do toggleDebug
on key "SPACE" do strike { power: 0.8 }
```

### Timeline block

```text
timeline {
  move "title" { x: 60 y: 40 dur: 400 easing: ease_out_back }
  wait 250
  call "flash" { strength: 0.7 }
}
```

## Value Types

- number: `1`, `-2`, `0.5`
- string: `"text"`
- boolean: `true`, `false`
- color functions: `rgb(r,g,b[,a])` / `rgba(r,g,b,a)` with numeric values
- bare identifiers in value position are treated as strings

## Timeline Actions (Current)

- `wait <ms>`
- `waitForCall "name"`
- `call "name" { props... }`
- `move "entity" { x y dur easing }`
- `walkToTile "entity" { tx ty [dur easing] }`
- `rotate "entity" { deg dur easing }`
- `scale "entity" { sx sy dur easing }`
- `fade "entity" { alpha dur easing }`
- `visible "entity" { value }`
- `cameraMove { x y dur easing }`
- `cameraZoom { zoom dur easing }`
- `cameraShake { ampX ampY dur }`
- `damage "entity" { amount [source] }`
- `heal "entity" { amount [source] }`
- `playAudio "id" { volume loop bgm }`
- `stopAudio "id"`
- `emitParticles "entity" { count }`
- `cameraFollow ["target"] { target lerp offsetX offsetY deadZoneW deadZoneH }`
- `setParallax "entity" { px py }`
- `label "name"`
- `jump "labelName"`
- `parallel { ...actions... }`
- `loop <count> { ... }` or `loop until "event" { ... }`

## Easing Types

Common easing enum values include:
- `linear`
- `ease_in_quad`, `ease_out_quad`, `ease_in_out_quad`
- `ease_in_cubic`, `ease_out_cubic`, `ease_in_out_cubic`
- `ease_in_quart`, `ease_out_quart`, `ease_in_out_quart`
- `ease_in_expo`, `ease_out_expo`, `ease_in_out_expo`
- `ease_in_sine`, `ease_out_sine`, `ease_in_out_sine`
- `ease_in_elastic`, `ease_out_elastic`, `ease_in_out_elastic`
- `ease_in_back`, `ease_out_back`, `ease_in_out_back`
- `ease_in_bounce`, `ease_out_bounce`, `ease_in_out_bounce`

## VN Bridge Usage

When launched from VNS, JES can return results back:

```jes
call "return" { label: "after_game" score: 12345 }
```

VNS call site:

```vns
[jes push game/minigames/brickbreaker.jes label after_game with difficulty=hard]
```

## Java Hook Usage

Expose callable hooks:

```java
scene.registerCall("spawnWave", props -> {
  // custom logic
});
```

Invoke from JES:

```jes
call "spawnWave" { count: 4 speed: 100 }
```

## Example Scene

```jes
scene "CombatPreview" {
  entity "hud" {
    component Panel2D {
      x: 20
      y: 20
      w: 460
      h: 120
      fill: rgb(0.1,0.12,0.18,0.85)
    }
    component Label2D {
      text: "Wave 1"
      x: 40
      y: 60
      size: 24
      bold: true
      color: rgb(1,1,1,1)
    }
  }

  on key "D" do toggleDebug
  on key "SPACE" do strike

  timeline {
    move "hud" { x: 20 y: 16 dur: 350 easing: ease_out_back }
    wait 200
    move "hud" { x: 20 y: 20 dur: 300 easing: ease_in_out_sine }
    label "idle"
    loop 3 {
      call "spawnWave" { count: 2 }
      wait 500
    }
    jump "idle"
  }
}
```

## Related Docs

- Parser internals: `docs/JES Scripting/JES Parsing.md`
- Component property reference: `docs/JES Scripting/Components.md`
- Runtime interop details: `docs/Interop.md`

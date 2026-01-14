# JES Language

JES (JVN Engine Script) is a small DSL for scenes, entities, components, input, and timelines.

## Quickstart

- Create a JES file, e.g. `game/minigames/brickbreaker.jes`:
  ```jes
  scene "Demo" {
    entity "title" { component Label2D { text: "Hello JES" x: 60 y: 80 size: 24 } }
  }
  ```
- Run directly via runtime:
  ```bash
  ./gradlew :runtime:run --args="--jes game/minigames/brickbreaker.jes"
  ```
- Launch from a VNS script:
  ```vns
  [jes push game/minigames/brickbreaker.jes label after_game with difficulty=hard]
  ```
- Return to VNS from JES:
  ```jes
  call "return" { label: "after_game" score: 123 }
  ```
- See also: `docs/VNS Scripting/VNS Scripting.md` (calling JES), `docs/Timeline Scripting/Timeline Scripting.md` (narrative flow), and `docs/Interop.md`.

## Example

```jes
scene "Demo" {
  entity "board" { component Panel2D { x: 40 y: 40 w: 400 h: 260 fill: rgb(0.12,0.14,0.18,0.9) } }
  entity "title" { component Label2D { text: "JES Demo" x: 60 y: 80 size: 24 bold: true color: rgb(1,1,1,1) align: left } }
<  entity "ball"  { component PhysicsBody2D { shape: circle x: 200 y: 180 r: 20 mass: 1 restitution: 0.8 color: rgb(0.2,0.7,0.9,1) vx: 50 vy: -30 } }>
  entity "fire" { 
    component ParticleEmitter2D {
      x: 400 y: 200
      emissionRate: 20
      minLife: 0.5 maxLife: 1.5
      minSize: 4 maxSize: 12 endSizeScale: 0.1
      minSpeed: 20 maxSpeed: 60
      minAngle: 250 maxAngle: 290
      gravityY: -30
      startColor: rgb(1,0.8,0.2,1)
      endColor: rgb(1,0.2,0,0)
    }
  }

  on key "D" do toggleDebug
  on key "C" do spawnCircle { r: 20 restitution: 0.9 }
  on key "B" do spawnBox { w: 40 h: 40 restitution: 0.4 }

  timeline {
    move "title" { x: 60 y: 60 dur: 500 easing: ease_out_back }
    wait 300
    move "title" { x: 60 y: 80 dur: 500 easing: ease_in_out_sine }
    rotate "ball" { deg: 360 dur: 2000 easing: ease_in_out_cubic }
  }
}
```

## Values
- number: 12, 0.5
- string: "text"
- boolean: true/false
- color: rgb(r,g,b[,a]) with 0..1 floats
- identifier-as-string in value position: left, right, circle, box

## Blocks
- scene "Name" { ... }
- entity "Name" { component ... }
- component Type { key: value ... }
- on key "K" do Action { props? }
- timeline { actions... }

## Timeline actions
- wait ms
- move "entity" { x: , y: , dur: , easing: }
- rotate "entity" { deg: , dur: , easing: }
- scale "entity" { sx: , sy: , dur: , easing: }
- call "functionName"  (placeholder in runtime)

## VN Bridge (Interop)

When a JES scene is launched from a VNS script, the runtime wires several calls:

- From JES to VN
  - `call "return" { label: "after" score: 123 }`
    - Pops the JES scene, copies props (except `label`/`goto`) into VN variables, jumps to the label (props `label` wins; falls back to label provided in the VNS `[jes push ... label L]`).
  - `call "vns" { ... }` alias of `return`.
  - `call "hud" { msg: "text" }` shows a HUD message in the VN layer.
  - `call "pop" {}` pops the JES scene without jumping.

- From VN to JES
  - `[jes call <name> k=v ...]` invokes a registered `call` handler in `JesScene2D`.
  - Launch-time init: VNS can pass props on launch via `with k=v ...`. The runtime calls `call "init" { ... }` once after the scene loads so you can configure initial state.

Example inside a JES minigame:

```jes
// Called by VNS on launch
call "init" { difficulty: hard lives: 3 }

// End of game
call "return" { label: "after_game" score: 12345 }
```

See also: docs/VNS Scripting.md → "JES interop from VNS".

## Runtime hooks (Java)

If you load JES from Java, you can register handlers to respond to `call` statements, input events, or triggers.

```java
JesScene2D scene = JesLoader.load(in);

scene.registerCall("spawnWave", props -> {
  // Use props like { count: 5, speed: 120 }
});

scene.setActionHandler((name, props) -> {
  // Fallback for any call not explicitly registered
});
```

Notes:
- `registerCall` attaches specific handlers by name.
- `setActionHandler` is a catch-all callback used if no handler matches.
- Built-in `call` names like `warpMap` or `attack` are handled internally before handlers run.

## Easing Types
Optional easing property for timeline actions:
- linear (default)
- ease_in_quad, ease_out_quad, ease_in_out_quad
- ease_in_cubic, ease_out_cubic, ease_in_out_cubic
- ease_in_quart, ease_out_quart, ease_in_out_quart
- ease_in_expo, ease_out_expo, ease_in_out_expo
- ease_in_sine, ease_out_sine, ease_in_out_sine
- ease_in_elastic, ease_out_elastic, ease_in_out_elastic
- ease_in_back, ease_out_back, ease_in_out_back
- ease_in_bounce, ease_out_bounce, ease_in_out_bounce

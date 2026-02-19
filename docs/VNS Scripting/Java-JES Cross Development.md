# Java–JES Hybrid Development

This project supports hybrid scenes where core gameplay is implemented in Java, while presentation/UI/timeline scripting is authored in JES. The billiards game demonstrates this pattern.

## What runs where
- Java (core): physics, rules, rendering, input – `BilliardsHybridScene`, `BilliardsGame`, `BilliardsRenderer2D`.
- JES (overlay): UI labels, HUD, timeline animations, editor-friendly bindings.

`BilliardsHybridScene` extends `JesScene2D`, so it can import JES content and render it in screen space over the Java-rendered world.

## Files of interest
- `billiards-game/src/main/java/com/jvn/billiards/app/BilliardsGameApp.java`
  - Launches JavaFX, creates `BilliardsHybridScene`, and imports `samples/billiards.jes` if present.
- `billiards-game/src/main/java/com/jvn/billiards/scene/BilliardsHybridScene.java`
  - Java scene that renders the billiards world and HUD.
  - Bridges JES actions/timeline calls into Java (see below).
- `scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`
  - JES runtime. Timeline and key bindings live here.
  - Added APIs: `registerCall(name, handler)` and `setActionHandler((action, props) -> {})`.

## How JES is loaded
`BilliardsGameApp` tries to load `samples/billiards.jes` (relative to the repo root) and calls `scene2D.importFromJesScene(...)`. By default we import:
- Timeline and key bindings
- Only UI entities (labels) to avoid duplicating physics or gameplay state

Adjust `labelsOnly` in `importFromJesScene` if you want to bring in more components.

## Bridging Java and JES
Two integration paths are available:

1) Input bindings (JES → Java)
```
// In .jes
on key "D" do toggleDebug
on key "SPACE" do strike            // calls Java strike
on key "R" do respawnCue            // calls Java respawn
```
- Built-in actions: `toggleDebug`, `spawnCircle`, `spawnBox` are handled by JES runtime.
- Other actions fall back to Java via `setActionHandler` in `BilliardsHybridScene`.

2) Timeline calls (JES → Java)
```
// In .jes
timeline {
  call "setPower"
  wait 500
  call "playSfx"
}
```
- `JesScene2D` looks up handlers registered via `registerCall("setPower", handler)` and invokes them.
- Note: the current parser does not accept props for timeline `call` steps. To pass data, use input bindings or invoke `[jes call name k=v ...]` from VNS.

### Java side: handled actions/calls in billiards
`BilliardsHybridScene` wires both bridging modes:
- `setActionHandler` handles these action names from bindings:
  - `strike` – begins a shot and strikes the cue
  - `respawnCue` – respawns the cue ball
  - `setPower` – sets cue power (`p` in [0..1])
  - `playSfx` – passes `name` to the audio facade
- `registerCall` exposes the same verbs for timeline `call` steps:
  - `respawnCue`, `playSfx`, `setPower`

Use whichever is more convenient in your JES file (binding vs timeline).

## Driving Java × JES from VNS scripts

You can orchestrate both Java and JES directly from VNS without writing a hybrid scene:

- **Call Java (static) methods**
```
[java fully.qualified.Class#method arg1 arg2 ...]
```
  - Uses reflection via `DefaultVnInterop`. Arguments are parsed/coerced to primitive types when possible.

- **Push/replace/pop JES scenes**
```
[jes push <script.jes> [label <returnLabel>] [with k=v ...]]
[jes replace <script.jes> ...]
[jes pop]
```
  - On launch, the runtime automatically calls `call "init" { ... }` in the JES scene with props from `with k=v`.
  - Inside JES, `call "return" { label: L ... }` pops the scene, copies props to VN variables (except `label`/`goto`), and jumps to `L` (or the label passed on push).

- **Invoke JES functions at runtime**
```
[jes call <name> k=v ...]
```
  - Invokes a handler registered in `JesScene2D` via `registerCall(name, handler)`.

### End-to-end VNS snippet

```vns
@label start
[java com.acme.Session#begin]
Alice: Let’s play!
[jes push game/minigames/brickbreaker.jes label after with difficulty=hard lives=3]

@label after
Alice: Welcome back!
# At this point VN variables may contain values set by JES (e.g., score)
```

### Caveats
- Arguments support double quotes and backslash escapes in `[java]` and `[jes ...]` payloads.
- No variable interpolation in command arguments; use simple scalars.

## Writing JES overlays
A minimal overlay might include labels and a toggle for debug:
```
scene "overlay" {
  entity "title" {
    component Label2D {
      text: "Billiards"
      x: 20
      y: 40
      size: 20
      color: rgb(1,1,1,1)
    }
  }

  on key "D" do toggleDebug
  on key "SPACE" do strike

  timeline {
    call "setPower" { p: 0.6 }
    wait 1000
    call "playSfx" { name: "strike" }
  }
}
```

## Running the game
- Gradle:
  - `./gradlew :billiards-game:run -x test`
- From the Editor (JavaFX):
  - File → Open Project… (select the repo root)
  - Project → Run Billiards Game
  - Logs stream to a window; the game window appears on success.

## Editing in the Editor
- Left tabs: Project and Scene. Double-click a `.jes` to load into the canvas and code tab.
- Toolbar icons: Open/Reload/Apply/Fit/Reset; logo at left.
- Apply Code (Cmd/Ctrl+Enter) reinterprets the code into the live preview.

## Notes
- Key names are normalized to uppercase (`UP`, `DOWN`, `SPACE`, `R`).
- The hybrid import defaults to labels-only to prevent duplicate physics; switch off if you plan to drive more of the scene from JES.
- Audio is routed through `Simp3AudioService` (stubbed); swap implementation to connect to a real audio backend.

## Extending further
- Add more verbs to `setActionHandler` and `registerCall` in your hybrid scene.
- Expose Java state to scripts by naming entities you import/register and addressing them in timeline actions (`move`, `rotate`, `scale`).

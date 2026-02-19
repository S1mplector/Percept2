# Java + JES + VNS Cross Development

JVN supports hybrid game architecture where narrative, scripted 2D behavior, and Java systems collaborate.

## Typical Responsibility Split

- **VNS**: branching story flow, dialogue pacing, menu transitions
- **JES**: 2D scene composition, timeline choreography, lightweight scripted interactions
- **Java**: domain/gameplay systems, advanced mechanics, platform integrations

## Integration Paths

## 1) VNS -> JES

Use interop commands in VNS:

```vns
[jes push game/minigames/arena.jes label after_arena with difficulty=hard round=2]
[jes call resetWave wave=3]
[jes pop]
```

Runtime behavior:
- JES scene is loaded and pushed/replaced.
- `with k=v` props are passed to JES via `call "init" { ... }` when available.
- return label fallback can be set on push/replace.

## 2) JES -> VNS

In JES, return to VN with payload:

```jes
call "return" { label: "after_arena" score: 1200 rank: "A" }
```

Runtime bridge behavior:
- pops JES scene
- copies props into VN variables (`score`, `rank`, etc., excluding `label`/`goto`)
- jumps to return label

`call "vns" { ... }` is supported as alias.

## 3) VNS -> Java

Reflection-based static call:

```vns
[java com.example.GameHooks#beginEncounter goblin 3]
```

Guidelines:
- keep exposed methods stable and side-effect aware
- prefer thin script-safe wrappers over deep internal APIs

## 4) JES -> Java

When JES scene is loaded in Java, register call handlers:

```java
JesScene2D scene = JesLoader.load(in);
scene.registerCall("spawnWave", props -> {
  // custom gameplay logic
});
scene.setActionHandler((name, props) -> {
  // fallback handling
});
```

JES side:

```jes
call "spawnWave" { count: 5 speed: 120 }
```

## Runtime Bridge Components

- `runtime/src/main/java/com/jvn/runtime/RuntimeVnInterop.java`
- `runtime/src/main/java/com/jvn/runtime/JesVnBridge.java`

These classes provide default bridge behavior for runtime-launched projects.

## End-to-End Example

VNS:

```vns
@label start
Narrator: Entering challenge mode.
[jes push game/minigames/challenge.jes label after with mode=ranked]

@label after
Narrator: Score ${score}, combo ${combo}.
[if score >= 1000 goto win]
[jump lose]

@label win
Narrator: Victory.
[end]

@label lose
Narrator: Defeat.
[end]
```

JES snippet:

```jes
// when challenge ends
call "return" { label: "after" score: 1320 combo: 18 }
```

## Hybrid Scene Strategy

For complex gameplay, use Java scene classes and import/use JES for presentation overlays, HUD, and timelines.

This keeps:
- heavy logic in strongly-typed Java
- iterative content in editable scripts

## Team Conventions (Recommended)

- Prefix return props clearly (`score`, `timeMs`, `result`, etc.).
- Keep one canonical return label per minigame call site.
- Document each `java` interop method contract in code comments and docs.
- Add parser/lint checks in CI for all shipped VNS/JES scripts.

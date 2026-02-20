# Interop Guide

Interop is how VNS scripts, JES scenes, and Java code coordinate behavior at runtime.

Primary classes:
- `core/src/main/java/com/jvn/core/vn/DefaultVnInterop.java`
- `runtime/src/main/java/com/jvn/runtime/RuntimeVnInterop.java`

## Command Routing Model

When VNS parser sees `[call <provider> <payload>]` (or shorthand forms like `[jes ...]`), it emits a `VnExternalCommand`.

At runtime:

1. `VnScene` reaches that node.
2. active `VnInterop` receives provider + payload.
3. interop returns `advance` or `stay` depending on whether control flow already moved.

## Default Providers (`DefaultVnInterop`)

### `hud`

- shows temporary HUD message
- example: `[hud Saved!]`

### `java`

- reflection call to public static method
- syntax: `[java fully.qualified.Class#method arg1 arg2 ...]`
- coercion supports `int/long/double/boolean` + string fallback

### `var`

- state variable operations:
  - `set`, `inc`, `dec`, `flag`, `unflag`, `clear`
- examples:
  - `[set score 10]`
  - `[inc score 5]`

### `cond`

- conditional jump logic
- payload form: `if <expr> goto <label>`
- uses `VnConditionEvaluator`

### `settings`

- live settings changes (`textspeed`, `autodelay`, `volume`)

### `save`

- quick save/load behavior (`[save]`, `[quickload]`)

### `mode`

- skip/auto mode toggles

### `ui`

- UI visibility controls (`show/hide/toggle`)

### `history`

- history overlay controls (`show/hide/toggle/scroll/clear`)

### `audio`

- `pause`, `resume`, `seek`, `crossfade`

### `screen`

- `shake`, `flash`, `clear` visual effects

## Runtime-Only Providers (`RuntimeVnInterop`)

### `jes`

- `push`, `replace`, `pop`, `call`
- supports launch props using `with k=v`

Example:

```vns
[jes push game/minigames/puzzle.jes label after_puzzle with difficulty=hard lives=3]
```

### `menu`

- opens menu scenes:
  - `settings`
  - `save`
  - `load <defaultScript>`
  - `main <defaultScript>`
  - custom menu ids

### `vns`

- script flow transitions:
  - `push <script> [label L]`
  - `replace <script> [label L]`
  - `goto <label>` or `goto Arc:label`

## JES <-> VNS Bridge

When runtime loads JES from VNS, it attaches helpers:

- JES -> VN:
  - `call "return" { label: "after", score: 42 }`
  - `call "vns" { ... }` (alias)
  - `call "hud" { msg: "text" }`
  - `call "pop" {}`

Return behavior:
- pops JES scene
- copies props (except `label`/`goto`) into VN variables
- jumps to return label (explicit prop overrides default label from push call)

- VN -> JES:
  - `[jes call <name> k=v ...]` invokes registered JES call handlers

## Data Type Conventions

Common token parsing rules:
- `true` / `false` -> boolean
- numeric tokens -> integer or double
- other tokens -> string
- quoted strings preserve spaces for tokenized providers

## Practical Patterns

### Pattern: launch minigame, return score

```vns
[jes push game/minigames/aim.jes label after_game with stage=2]

@label after_game
Narrator: Final score was ${score}.
```

Inside JES:

```jes
call "return" { label: "after_game" score: 987 }
```

### Pattern: central Java utility call

```vns
[java com.example.GameDebug#logEvent chapter_start]
```

## Safety Notes

- Java interop is reflection-based and should be treated as trusted-script functionality.
- Prefer stable wrapper utility methods instead of exposing deep internals directly.
- Keep provider payload formats explicit in team scripting conventions.

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
- `sfx_stop`, `voice_stop`, `stop_all`
- `pause_all`, `resume_all`

### `screen`

- `shake`, `flash`, `clear` visual effects

### `char`

Character choreography helper provider. `[character]` is accepted as an alias for `[char]`.

**Subcommands:**

| Subcommand | Aliases | Description |
|------------|---------|-------------|
| `global` | `global_position` | Enable/disable persistent position mode |
| `at` | `position`, `pos` | Set the character's anchor position |
| `move` | — | Animated slide to a new position (with optional expression, easing, duration) |
| `show` | — | Show character at a position with an expression |
| `expression` | `expr` | Change expression without moving |
| `hide` | — | Animated exit |

**Move with easing and duration:**

```vns
[char hero move right smile ease_out_quad 500]
```

**Show subcommand:**

```vns
[char hero show center happy]
[char hero show at 0.3,0.5 neutral]
```

**Global mode** gives the character persistent position memory. When enabled, `[move]` and `[show]` produce smooth slide tweens instead of entrance animations. See [Character Motion](../scripting/vns/vns-characters.md#global-position-mode) for details.

Example:

```vns
[char hero global on]
[char hero at center]
[char hero move right smile]
[char hero expr surprised]
[char hero hide]
```

### `jes_timeline` / `jes_timeline_inline`

- `jes_timeline`: plays named timeline from `TimelineRegistry`
- `jes_timeline_inline`: plays inline timeline payload generated from VNS `timeline { ... }` blocks
- supports entity transform keyframes, camera keyframes, and timeline audio cues

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

### Pattern: reusable Puppeteer timeline from VNS

```vns
[call jes_timeline hero_intro_pan]
```

### Pattern: one-off inline timeline near story text

```vns
timeline {
  cameraMove 0ms 0 0 1.0
  cameraMove 400ms 0 0 0.92
  playAudio "assets/audio/sfx/whoosh.ogg"
}
```

## Safety Notes

- Java interop is reflection-based and should be treated as trusted-script functionality.
- Prefer stable wrapper utility methods instead of exposing deep internals directly.
- Keep provider payload formats explicit in team scripting conventions.

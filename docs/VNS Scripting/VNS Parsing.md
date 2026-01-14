# VNS Parsing

This document explains how VNS text is parsed into an executable scenario.

## Pipeline

- Input: plain text `.vns`
- Parser: `core/src/main/java/com/jvn/core/vn/script/VnScriptParser.java`
- Output: `VnScenario` built via `VnScenarioBuilder`
- Runtime: `VnScene` executes dialogue, choices, and external commands via an `VnInterop` implementation

## Line-oriented grammar (regex based)

Patterns in `VnScriptParser`:
- Scenario: `@scenario <id>`
- Character: `@character <id> "Display Name"`
- Background: `@background <id> <path>`
- Label: `@label <name>`
- Dialogue: `<Speaker>: <text>`
- Choice: `> <text> [-> <targetLabel>]`
  - Conditional suffix recognized by the parser: `... [if <expr>]`
    - Example: `> Explore [if flags.cave_open]`
- Command: `[ ... ]` (see below)

Whitespace-only lines and `#` comments are ignored. Order is top-to-bottom. Choices buffer until a non-choice line is seen, then are flushed to the builder.

## Commands (inside square brackets)

The parser recognizes the following commands and maps them to builder or external calls:

- Background/flow
  - `[background <bgId>]`, `[bg <bgId>]`
  - `[jump <label>]`, `[end]`

- Characters and transitions
  - `[show <charId> <pos> [expression]]`
  - `[hide <charId>]`
  - `[transition <type> [durationMs] [bgId]]`
  - `[screen shake [intensity] [durationMs]]`
  - `[screen flash [strength] [durationMs] [r g b]]`

- Timing
  - `[wait <millis>]`

- Audio
  - `[bgm <id>]`, `[bgm_stop]`, `[bgm_fadeout [ms]]`
  - `[bgm_pause]`, `[bgm_resume]`, `[bgm_seek <seconds>]`, `[bgm_crossfade <id> <ms> [loop]]`
  - `[sfx <id>]`, `[voice <id>]`

- Player settings / modes / UI / history
  - `[textspeed <msPerChar>]`, `[autodelay <msBetweenLines>]`
  - `[volume bgm|sfx|voice <0..1>]`
  - `[skip [on|off|toggle]]`, `[auto [on|off|toggle]]`
  - `[ui [hide|show|toggle]]`
  - `[history [toggle|show|hide]]`, `[history scroll <lines>]`, `[history clear]`
  - `[save]`, `[quickload]`
  - `[hud <message>]`

- External integration
  - Generic: `[call <provider> <payload...>]` → creates a `VnExternalCommand`
  - Shortcuts: `[jes <payload>]`, `[java <payload>]`
  - Convenience for JES:
    - `[jes_push <script.jes>]`, `[jes_replace <script.jes>]`, `[jes_pop]`, `[jes_call <name> k=v ...]`

The parser forwards external calls to the active `VnInterop` implementation at runtime (see below).

## Interop at runtime

Two interop implementations are relevant:
- `DefaultVnInterop`
  - `hud`: shows a HUD message
  - `java`: calls a public static Java method using reflection
  - `var`, `cond`, `settings`, `save`, `mode`, `ui`, `history`, `audio` helpers
- `RuntimeVnInterop` (extends default behavior)
  - Adds `jes` and `vns` providers to push/replace/pop JES and VNS scenes
  - Bridges JES calls back into VN (e.g., `return`, `hud`, `pop`) and invokes `init` with provided props

## Java interop (reflection)

Handled by `DefaultVnInterop`:
- Syntax: `[java fully.qualified.Class#method arg1 arg2 ...]`
- Args are parsed as `boolean`, numeric (`int` or `double`), else `String`
- A public static method with matching arity is looked up; arguments are coerced to parameter types (`int`, `long`, `double`, `boolean`; else `String`)
- Result is shown as a HUD message; return values are not stored in variables

Example:
```
[java com.acme.Util#sum 2 3]     # calls Util.sum(int,int)
[call java com.acme.Log#info Started]
```

Limitations:
- No quoting; arguments cannot contain spaces (use underscores if needed)
- Only public static methods are supported; overloaded resolution is by name + arity

## JES interop

Handled by `RuntimeVnInterop`:
- `[jes push <script.jes> [label <returnLabel>] [with k=v ...]]`
- `[jes replace ...]`, `[jes pop]`
- `[jes call <name> k=v ...]` calls a registered handler in the active `JesScene2D`
- On push/replace, the runtime invokes `call "init" { ... }` on the JES scene with props from `with k=v` if provided
- `call "return" { label: L ... }` inside JES pops the scene, copies props (excluding `label`/`goto`) into VN variables, and jumps to `L` (or the default label specified on push)

## Error handling

- Parser throws `IOException` on malformed lines
- Unknown commands are ignored by the parser but may be handled by custom interop

## Minimal example

```vns
@scenario demo
@label start
Alice: Hello VNS!
[bgm theme]
[java com.acme.Log#info Started]
[jes push game/minigames/brickbreaker.jes label after_game with difficulty=hard]
```

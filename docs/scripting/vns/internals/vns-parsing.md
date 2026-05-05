# VNS Parsing Internals

Complete reference for the VNS parsing pipeline — how `VnScriptParser` transforms `.vns` source text into executable `VnScenario` data, including directives, commands, conditional lowering, include/macro handling, label validation, and error reporting.

Parser source: `modules/core/src/main/java/com/jvn/core/vn/script/VnScriptParser.java`

---

## Pipeline Overview

Each stage operates line-by-line. Errors at any stage halt processing with source file, line number, and the offending line text.

---

## Directives

Directives start with `@` and are processed before runtime. They declare metadata, assets, variables, and structure.

| Directive | Syntax | Purpose |
|-----------|--------|---------|
| `@scenario` | `@scenario <name>` | Declares the scenario ID (one per file) |
| `@character` | `@character <id> "Display Name"` | Registers a character |
| `@charimg` | `@charimg <id> <expression> <path>` | Maps character expression to image |
| `@charlayer` | `@charlayer <id> <layer_name> <path>` | Defines a compositing layer for a character |
| `@charpreset` | `@charpreset <id> <preset> $layer1 \| $layer2 \| ...` | Builds named expression from layers |
| `@background` | `@background <id> <path>` | Registers a background image |
| `@position` | `@position <name> <x> [<y>]` | Defines a named custom character position |
| `@stagepreset` | `@stagepreset <id> <path>` | Loads a Scene Lighting Studio `.stagepreset` file |
| `@var` | `@var <name> = <value>` | Declares a variable with initial value |
| `@label` | `@label <name>` | Declares a jump target |
| `@define` | `@define KEY value` | Parser-time text macro |
| `@include` | `@include <path>` | Includes another `.vns` file |

### Directive Processing Order

1. `@define` macros are stored immediately and applied to all subsequent lines
2. `@include` triggers recursive parsing of the included file
3. `@scenario` must appear before any content nodes
4. All other directives can appear anywhere before they're referenced

---

## Command Parsing

Commands use `[command args...]` bracket syntax. The parser's `parseCommand` method routes each command to the appropriate builder node.

### Built-in Command Categories

**Scene commands:**

| Command | Example | Node Type |
|---------|---------|-----------|
| `bg` | `[bg park]` | Background change |
| `show` | `[show hero center happy]` | Character show |
| `hide` | `[hide hero]` | Character hide |
| `transition` | `[transition FADE 800 park]` | Scene transition |
| `wait` | `[wait 1500]` | Timed wait |
| `end` | `[end]` | Scenario end |

**Flow commands:**

| Command | Example | Node Type |
|---------|---------|-----------|
| `jump` | `[jump chapter2]` | Label jump |
| `goto` | `[goto RouteA:start]` | Cross-scenario jump |
| `if` | `[if gold >= 100 goto rich]` | Conditional jump |
| `if`/`elif`/`else`/`endif` | Block conditional | Lowered to synthetic labels |
| `set` | `[set gold 500]` | Variable assignment |
| `inc` / `dec` | `[inc gold 50]` | Variable arithmetic |
| `flag` / `unflag` | `[flag quest_started]` | Boolean flag toggle |

**Audio commands:**

| Command | Example | Node Type |
|---------|---------|-----------|
| `bgm` | `[bgm assets/audio/bgm/calm.ogg]` | Play BGM |
| `bgm stop` | `[bgm stop]` | Stop BGM |
| `bgm_fadeout` | `[bgm_fadeout 1500]` | Fade out BGM |
| `bgm_crossfade` | `[bgm_crossfade track.ogg 2000]` | Crossfade BGM |
| `sfx` | `[sfx assets/audio/sfx/click.ogg]` | Play SFX |
| `voice` | `[voice assets/audio/voices/line.ogg]` | Play voice |
| `volume` | `[volume bgm 0.5]` | Set channel volume |

**Interop commands:**

| Command | Example | Node Type |
|---------|---------|-----------|
| `jes` | `[jes push scene.jes label after]` | JES scene control |
| `java` | `[java com.example.Hook#method args]` | Java reflection call |
| `settings` | `[settings textspeed 20]` | Settings modification |
| `save` | `[save]` | Auto-save |
| `hud` | `[hud Chapter 2 — The Forest]` | HUD message |
| `persistent` | `[persistent set key val]` | Persistent variable operations |
| `stage` | `[stage sunset_park]` | Stage lighting preset activation |

**Visual commands:**

| Command | Example | Node Type |
|---------|---------|-----------|
| `screen shake` | `[screen shake 5 300]` | Screen shake effect |
| `screen flash` | `[screen flash 0.5 200]` | Screen flash effect |

**Unknown commands** are rejected with a parse error including the line number and command text.

---

## Dialogue Parsing

The parser recognizes two dialogue forms:

### Colon form

```vns
hero: Hello, world!
narrator: The story begins.
```

Pattern: `<identifier>: <text>` — the identifier must match a declared `@character` ID.

### Quoted form

```vns
hero "Hello, world!"
```

Pattern: `<identifier> "<text>"` — less common but valid.

### Dialogue with text effects

Text effects are embedded inline and passed through to the renderer:

```vns
hero: This is {b}bold{/b} and {shake}shaky{/shake} text.
```

The parser does not process text effects — they're stored as raw text in the dialogue node and interpreted at render time.

---

## Choice Parsing

### Multi-line choices

```vns
> Go to the park -> park_scene
> Stay home -> home_scene
> Ask for directions [if !knows_way] -> ask_directions
```

Choices are buffered line-by-line. When a non-choice line is encountered, the buffer is flushed as a single choice node. Each choice can have:
- **Label text** — the display string
- **Target** — jump target after `->` (optional; if omitted, falls through)
- **Condition** — `[if <expr>]` suffix for conditional visibility

### Inline choices

```vns
[choice Go left | left_path | Go right | right_path]
```

Pattern: `[choice text1 | target1 | text2 | target2 | ...]`

---

## Include and Macro Handling

### `@define` — Parser-Time Macros

```vns
@define HERO_NAME Yuki
@define START_GOLD 200

@var gold = ${START_GOLD}
narrator: ${HERO_NAME} begins the journey.
```

- Macros are stored as a `key → value` map
- `${KEY}` tokens in subsequent lines are replaced with the value
- Substitution happens at parse time, before any other processing
- Macros from included files are available in the including file

### `@include` — File Inclusion

```vns
@include common/characters.vns
@include common/variables.vns
```

- The include resolver resolves paths relative to the current source file
- Included files are parsed recursively — their directives, labels, and content merge into the current scenario
- **Cycle detection** — an include stack tracks active includes; recursive includes throw a parse error
- Included files can themselves include other files

---

## Conditional Block Lowering

Structured `if`/`elif`/`else`/`endif` blocks are **lowered** into synthetic labels and jumps, keeping the runtime execution model linear.

### Input

```vns
[if gold >= 100]
  narrator: You're rich!
  [inc gold 50]
[elif gold >= 50]
  narrator: You're doing okay.
[else]
  narrator: You're broke.
[endif]
```

### Lowered output (conceptual)

```text
COND_JUMP (gold >= 100) → __if_then_0
JUMP → __if_false_0

__if_then_0:
  DIALOGUE "You're rich!"
  INC gold 50
  JUMP → __if_end_0

__if_false_0:
  COND_JUMP (gold >= 50) → __elif_then_0_1
  JUMP → __if_else_0

__elif_then_0_1:
  DIALOGUE "You're doing okay."
  JUMP → __if_end_0

__if_else_0:
  DIALOGUE "You're broke."

__if_end_0:
  (continue)
```

### Nesting

Blocks can nest — each nesting level gets a unique counter suffix:

```vns
[if has_key]
  [if gold >= 50]
    narrator: Open the treasure chest!
  [else]
    narrator: You need 50 gold to open it.
  [endif]
[endif]
```

---

## Label Tracking and Validation

The parser maintains two data structures:

1. **Declared labels** — `Map<String, LineInfo>` of all `@label` declarations
2. **Referenced labels** — `Set<String>` of all labels mentioned in jumps, choices, and conditionals

### Post-Parse Validation

After all lines are processed:

1. **Unresolved references** — any referenced label not in the declared table causes a hard parse error
2. **Duplicate labels** — declaring the same label twice causes a parse error
3. **Unclosed if blocks** — `[if]` without matching `[endif]` causes a parse error
4. **Unmatched elif/else** — `[elif]` or `[else]` without a preceding `[if]` causes a parse error

---

## Condition Validation

`VnConditionEvaluator.validate(expression)` is called at parse time for:
- `[if <expr>]` / `[elif <expr>]` block conditions
- `[if <expr> goto <label>]` shortcut expressions
- Choice condition suffixes: `> text [if <expr>] -> target`

Supported condition operators: `==`, `!=`, `>`, `>=`, `<`, `<=`, `&&`, `||`, `!`

Malformed expressions fail at parse time — not at runtime.

---

## Error Model

All parse errors include source context:

```text
Parse error in scripts/story/prologue.vns at line 42: <message> -> <line text>
```

### Error Catalog

| Error | Cause | Fix |
|-------|-------|-----|
| `Duplicate @scenario declaration` | Two `@scenario` directives in one file | Remove the duplicate |
| `Duplicate label '<name>'` | Same `@label` name declared twice | Rename one of the labels |
| `Undefined label '<name>'` | Jump/choice references a non-existent label | Add `@label <name>` or fix the typo |
| `Unknown command '<cmd>'` | Unrecognized `[command]` | Check spelling against command reference |
| `Invalid command arguments` | Wrong number or type of args | Check command syntax |
| `Unmatched [elif]` | `elif` without preceding `if` | Add `[if]` or remove stray `elif` |
| `Unmatched [else]` | `else` without preceding `if` | Add `[if]` or remove stray `else` |
| `Unclosed [if] block` | `if` without matching `endif` | Add `[endif]` |
| `Invalid condition expression` | Malformed boolean expression | Fix operator/operand syntax |
| `Include cycle detected` | File A includes B which includes A | Break the circular include |
| `Invalid label name '<name>'` | Label contains illegal characters | Use alphanumeric + underscores |

---

## Editor Integration

The VNS parser powers several editor features:

- **VNS Diagnostics panel** — shows all parse errors and warnings with click-to-jump navigation
- **Label Flow Map** — visualizes label-to-label jumps as a directed graph
- **Syntax highlighting** — directives, commands, dialogue, choices get distinct colors
- **Auto-complete** — character IDs, label names, background IDs
- **Inline error markers** — red underlines on lines with parse errors

### CI Integration

Parse all VNS scripts in a build step to catch errors before release:

```bash
./gradlew :core:test  # includes VNS parser tests
```

Or build a custom validation script that loads each `.vns` file through `VnScriptParser` and reports errors.

---

## Why Strict Parsing Matters

1. **Catches content bugs before runtime** — typos in label names, missing commands, broken conditions
2. **Improves editor diagnostics** — precise line numbers and error messages
3. **Enables CI gating** — reject builds with script parse errors
4. **Reduces runtime surprises** — no silent fallthrough on broken scripts
5. **Keeps team contracts clear** — everyone knows the exact syntax rules

In practice, VNS parse failures should be treated as **content compilation errors** — the same way a Java compiler error blocks a build.

---

## Related Docs

- [VNS Scripting Overview](../overview/vns-scripting.md)
- [VNS Directives](../language/vns-directives.md)
- [VNS Commands](../language/vns-commands.md)
- [VNS Flow Control](../flow/vns-flow-control.md)
- [VNS Choices](../language/vns-choices.md)
- [JES Parsing Internals](../../jes/internals/jes-parsing.md)

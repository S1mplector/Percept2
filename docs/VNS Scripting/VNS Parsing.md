# VNS Parsing Internals

This document explains how `VnScriptParser` turns `.vns` text into executable scenario data.

Parser file:
- `core/src/main/java/com/jvn/core/vn/script/VnScriptParser.java`

## Parse Pipeline

1. Read line-by-line, UTF-8.
2. Strip comments/blank lines.
3. Process directives and commands.
4. Build `VnScenarioBuilder` nodes sequentially.
5. Validate label references and conditional block integrity.
6. Build final `VnScenario`.

## Core Regex Patterns

Key patterns used by parser include:

- directives: `@scenario`, `@character`, `@background`, `@charimg`, `@charlayer`, `@charpreset`, `@var`, `@label`, `@define`, `@include`
- legacy label: `label <name>`
- dialogue forms:
  - `Speaker: text`
  - `speaker "quoted text"`
- command block: `[ ... ]`
- choice condition suffix: `... [if <expr>]`
- `if-goto` shortcut: `<expr> goto <label>`

## Include and Macro Handling

### `@define`

- parser stores key/value map
- substitutes `${KEY}` tokens in subsequent lines
- substitution is parser-time text replacement

### `@include`

- requires include resolver
- resolves relative include paths using current source path
- detects include cycles using include stack

## Conditional Block Lowering

Structured blocks:

```text
[if cond]
...
[elif cond2]
...
[else]
...
[endif]
```

are lowered into synthetic labels and jumps.

Parser internally creates labels like:
- `__if_then_N`
- `__if_false_N`
- `__if_end_N`

This keeps runtime execution model linear while preserving block semantics.

## Label Tracking

Parser keeps:
- declared label table (source + line)
- referenced label list (jump/choice/if-goto)

After parsing, unresolved labels produce hard parse errors.

## Command Handling Map

`parseCommand` routes commands into:

- direct builder nodes (`background`, `jump`, `end`, `wait`, `show`, `hide`, `transition`)
- audio builder nodes (`bgm`, `sfx`, `voice`, `bgm_stop`, `bgm_fadeout`)
- external interop commands (`settings`, `menu`, `var`, `cond`, `jes`, `java`, etc.)

Unknown commands are rejected with parse error.

## Error Model

Errors are reported as `IOException` with source + line context:

```text
Parse error in <source> at line <n>: <message> -> <line text>
```

Common errors:
- duplicate scenario declaration
- invalid/missing command args
- invalid label names
- duplicate labels
- undefined label references
- unmatched `elif`/`else`/`endif`
- unclosed `if` blocks
- invalid condition expression syntax

## Condition Validation

`VnConditionEvaluator.validate(expression)` is called during parse for:
- `if`/`elif` block conditions
- choice condition suffixes
- `if ... goto ...` expressions

So malformed conditions fail before runtime.

## Choice Parsing Details

Multi-line choices (`> ...`) are buffered until a non-choice line appears, then flushed as one choice node list.

Inline choices (`[choice ... | ...]`) are parsed immediately.

Both support optional targets and optional trailing condition suffix.

## Why Strict Parse Matters

Strictness improves:
- editor diagnostics quality
- CI confidence for narrative content
- reduced runtime surprises from script typos

In practice, parser failures should be treated as content compilation failures.

# JES Parsing Internals

This page documents parser behavior and strict validation in JES.

## Pipeline

1. `JesTokenizer` tokenizes source to `JesToken` stream.
2. `JesParser` builds AST (`JesAst`).
3. `JesLoader` builds `JesScene2D` from AST.
4. `JesScene2D` runs input, physics, timeline, and call handlers.

## Tokenization Rules

Tokenizer supports:
- identifiers, strings, numbers
- punctuation tokens (`{}`, `:`, `,`, `()`)
- line comments via `//`
- line/column metadata for diagnostics

## Grammar Summary

Top-level expects one or more scene blocks:

```text
scene "Name" { ... }
```

Inside a scene:
- `tileset "name" { ... }`
- `item "id" { ... }`
- `map "name" { ... layer "name" { ... } ... }`
- `entity "name" { component Type { ... } ... }`
- `on key "K" do action { ... }`
- `timeline { ... }`
- `key: value` (scene prop)

## Strict Property Validation

Parser validates known property keys for:

- component types (`Panel2D`, `Sprite2D`, `Label2D`, etc.)
- timeline action prop blocks (`move`, `cameraMove`, `playAudio`, etc.)

Unknown key in a known type/action triggers parse error with line/column.

Notes:
- unknown component **types** are currently tolerated for extension flexibility
- `Equipment` and timeline `call` allow free-form props

## Timeline Parse Semantics

Timeline actions are parsed into `JesAst.TimelineAction` with:
- `type`
- optional `target`
- `props` map
- optional `children` for composite actions (`parallel`, `loop`)

`label` actions are indexed by runtime for `jump` resolution.

## AST to Runtime Conversion

`JesLoader` maps AST entities/components into runtime objects and registers them by name.

Loader responsibilities include:
- tileset/map construction
- tile collision setup
- trigger layer registration
- component creation
- input binding attachment
- timeline injection

## Runtime Action Handling

`JesScene2D` executes timeline actions per update and supports:
- wait state and call waiting
- entity tweens
- camera transitions
- audio pass-through callbacks
- branching actions (`label`/`jump`)
- composite actions (`parallel`/`loop`)

## Parser Error Style

Parser raises `JesParseException` with source position details for malformed structure, unknown action keys, and invalid block usage.

Examples:
- unknown timeline action
- unterminated block
- missing expected tokens (`{`, `:`, etc.)
- invalid property names for known component/action

## Why This Strictness Matters

- catches content bugs before runtime
- improves editor lint signal quality
- keeps script contracts clear in team environments

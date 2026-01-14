# Interop Guide

This document describes how VNS, JES, and runtime code talk to each other.

## VNS → Runtime

VNS `[command ...]` lines are parsed into `VnExternalCommand` entries. The runtime handles them through `VnInterop`:

- `core/src/main/java/com/jvn/core/vn/DefaultVnInterop.java`
- `runtime/src/main/java/com/jvn/runtime/RuntimeVnInterop.java`

### Default providers (engine-wide)

- `hud <msg>` → temporary HUD toast.
- `java <Class#method args...>` → invoke a public static Java method.
- `var set|inc|dec|flag|unflag|clear ...` → set VN variables.
- `cond if <expr>` → conditional jump logic (used by choices).
- `settings` / `mode` / `ui` / `history` → player settings & overlays.
- `audio pause|resume|seek|crossfade ...`
- `screen shake|flash ...`
- `save` (quick save/load helpers).

### Runtime providers

- `jes push|replace|pop|call ...`
- `vns push|replace|goto ...`
- `menu settings|save|load|main ...`

These are implemented by `RuntimeVnInterop` and only available in the runtime module.

## VNS ↔ JES

From VNS:

- `[jes push <script.jes> label <returnLabel> with k=v ...]`
- `[jes replace <script.jes> ...]`
- `[jes pop]`
- `[jes call <name> k=v ...]`

From JES:

- `call "return" { label: "after_game", score: 123 }`
  - Pops the JES scene.
  - Copies props into VN variables (except `label`/`goto`).
  - Jumps to the return label (prop wins, else VNS `label` argument).
- `call "vns"` is an alias of `return`.
- `call "hud" { msg: "Saved!" }` shows a VN toast.
- `call "pop" {}` pops without jumping.

## JES Call Handlers

If you load JES from Java, you can attach handlers:

```java
JesScene2D scene = JesLoader.load(in);
scene.registerCall("spawnWave", props -> { /* ... */ });
scene.setActionHandler((name, props) -> { /* fallback */ });
```

Handlers are invoked for `call "name" { ... }` statements or trigger volumes.

## Data Types

Interop props are parsed as:
- `true/false` → boolean
- numbers with `.` → double, integers otherwise
- everything else → string

Avoid spaces in values (`difficulty=hard`, `title=Hello_World`).

# VNS Scripting

VNS is JVN's line-oriented visual novel scripting DSL. It handles story flow, dialogue, branching, character staging, audio, transitions, variables, and integration with JES scenes and Java code.

Parser source: `modules/core/src/main/java/com/jvn/core/vn/script/VnScriptParser.java`

---

## Who This Is For

Use this page if you want to build a visual novel scene in plain text and you mostly care about:

- dialogue
- backgrounds and character staging
- choices and branching
- variables and story state
- calling Puppeteer timelines, JES scenes, or Java hooks when needed

If you want frame-by-frame gameplay scenes, physics, or direct entity/component control, start with [JES Overview](../../jes/overview/jes-scripting.md) instead.

## What You Will Learn

By the end of this page, you should understand:

- what VNS is responsible for in JVN
- what a minimal `.vns` file looks like
- how declarations, labels, dialogue, choices, and commands fit together
- which reference docs to open next depending on your task

## Read This Next

- New to JVN overall: [Choose Your Path in JVN](../../../guides/choose-your-path.md)
- Need file-level orientation: [Common JVN File Types](../../../guides/common-file-types.md)
- Want a full guided build: [Tutorial: Building a Complete VN](../guides/vns-tutorial.md)

---

## Quick Start

```vns
@scenario demo
@character narrator "Narrator"
@character hero "Aria"
@background park assets/backgrounds/park.png
@charimg hero neutral assets/characters/aria/neutral.png
@charimg hero happy assets/characters/aria/happy.png

@label start
[bg park]
[bgm assets/audio/bgm/calm.ogg]
[show hero center neutral]

narrator: Welcome to the demo.
hero: Nice to meet you!

[show hero center happy]
hero: Let's get started.

> Begin the adventure -> adventure
> Just say goodbye -> goodbye

@label adventure
narrator: The journey begins...
[end]

@label goodbye
hero: See you next time!
[end]
```

Run with runtime:

```bash
./gradlew :runtime:run --args='--script demo.vns'
```

What happened in that example:

1. The header declared a scenario, characters, backgrounds, and sprite assets.
2. `@label start` marked the first story entry point.
3. Commands such as `[bg]`, `[bgm]`, and `[show]` staged the scene.
4. Dialogue advanced the story.
5. Choice lines (`>`) branched to different labels.

---

## Script Structure

VNS is read top-to-bottom. Blank lines and lines starting with `#` are ignored.

Typical order:
1. Declarations (`@scenario`, `@character`, `@background`, `@charimg`, `@position`, etc.)
2. Labels and story content (dialogue, choices, commands)

---

## Sub-Document Reference

Each VNS feature area has its own detailed documentation with extensive examples:

### Language Features

- **[Directives & Declarations](../language/vns-directives.md)** — `@scenario`, `@character`, `@background`, `@charimg`, `@charlayer`, `@charpreset`, `@position`, `@label`, `@var`, `@define`, `@include`
- **[Dialogue & Text](../language/vns-dialogue.md)** — colon form, quoted form, inline text effects (`{shake}`, `{wave}`, `{color}`, `{speed}`, `{delay}`), typewriter reveal, skip/auto modes
- **[Choices & Branching](../language/vns-choices.md)** — multi-line choices, inline choices, conditional visibility, branching patterns (hub, merge, score-gated, nested)
- **[Variables & Conditions](../language/vns-variables.md)** — set/inc/dec/flag/unflag/clear, condition expressions, if/elif/else/endif blocks, conditional jumps, interpolation
- **[Text Formatting & ICU](../language/vns-text-formatting.md)** — `${var}` interpolation, `{var, plural, ...}`, `{var, select, ...}`, `{var, number}` formatting

### Systems

- **[Characters & Sprites](../presentation/vns-characters.md)** — character declarations, layered sprites, expression presets, positioning, animation, global position mode, layer ordering
- **[Audio Commands](../presentation/vns-audio.md)** — BGM, SFX, voice, crossfade, seek, pause/resume, visualizer
- **[Transitions & Screen Effects](../presentation/vns-transitions.md)** — fade/dissolve/crossfade/slide/wipe, screen shake, screen flash, UI visibility
- **[Subroutines & Flow Control](../flow/vns-flow-control.md)** — labels, jumps, call/return, conditionals, script switching, menu navigation
- **[Interop & Integration](../integration/vns-interop.md)** — JES push/replace/pop, Java reflection calls, inline timelines, menu commands, timeline registry

### Runtime & State

- **[Scene Lifecycle & State](../runtime/vns-scene-lifecycle.md)** — VnScene node loop, VnState, node types, preflight, character visuals/tweening, screen effects, HUD messages, timeline runners
- **[Save System](../runtime/vns-save-system.md)** — named slots, autosave, quick save/load, schema migration, JSON format, atomic writes, sidecar thumbnails, RPG passthrough
- **[Rollback & History](../runtime/vns-rollback-history.md)** — rollback stack, forward/backward, dialogue history backlog, capture/restore
- **[Settings & Playback Modes](../runtime/vns-settings-modes.md)** — text speed, volumes, skip mode, auto-play, UI hidden, click-reveal, key bindings
- **[Localization](../runtime/vns-localization.md)** — locale-aware script loading, extracted source-text catalogs, UI string localization, multi-language project structure

### Internals

- **[Parsing Internals](../internals/vns-parsing.md)** — parse pipeline, regex patterns, conditional lowering, error model
- **[Java + JES Cross Development](../integration/java-jes-cross-development.md)** — hybrid architecture, VNS↔JES↔Java patterns

### Architecture & Coordination

- **[VNS ↔ JES Architecture](../integration/vns-jes-architecture.md)** — scene stack coordination, interop routing, bridge lifecycle, timeline runner data flow

### Practical Guides

- **[Tutorial: Building a Complete VN](../guides/vns-tutorial.md)** — full step-by-step project from setup to endings and JES minigame integration
- **[Best Practices & Common Pitfalls](../guides/vns-best-practices.md)** — maintainability patterns, naming conventions, and common mistakes
- **[Debugging & Troubleshooting](../guides/vns-debugging.md)** — parse/runtime diagnostics, common failure modes, and debugging techniques
- **[Project Organization & Scaling](../guides/vns-project-organization.md)** — directory conventions, include strategies, and route management for larger projects

---

## Command Quick Reference

All commands use `[command args]` form. See [Commands Reference](../language/vns-commands.md) for the complete catalog with examples.

| Category | Commands |
|----------|----------|
| **Scene** | `[bg]`, `[transition]` |
| **Flow** | `[jump]`, `[end]`, `[goto]`, `[call]`, `[return]` |
| **Characters** | `[show]`, `[hide]`, `[move]`, `[char ... ]` |
| **Audio** | `[bgm]`, `[sfx]`, `[voice]`, `[bgm_stop]`, `[bgm_fadeout]`, `[bgm_crossfade]`, `[audio_stop_all]` |
| **Effects** | `[wait]`, `[screen shake]`, `[screen flash]`, `[transition]` |
| **Variables** | `[set]`, `[inc]`, `[dec]`, `[flag]`, `[unflag]`, `[clear]` |
| **Conditions** | `[if]`, `[elif]`, `[else]`, `[endif]`, `[if ... goto ...]` |
| **Settings** | `[textspeed]`, `[autodelay]`, `[volume]`, `[skip]`, `[auto]` |
| **UI** | `[ui]`, `[history]`, `[visualizer]`, `[hud]` |
| **Save** | `[save]`, `[quickload]` |
| **Navigation** | `[menu]`, `[settings]`, `[mainmenu]`, `[load]` |
| **Interop** | `[call]`, `[jes]`, `[java]`, `[jes_push]`, `[jes_call]`, `timeline { }` |

---

## Parser Strictness

VNS parser is intentionally strict. Parse errors are thrown for:

- Unknown commands
- Malformed command args
- Duplicate labels
- Undefined referenced labels
- Invalid condition syntax
- Unmatched `if/elif/else/endif` blocks
- Unrecognized non-empty syntax lines

This strictness is surfaced in editor diagnostics and enables CI confidence for narrative content.

---

## Variable Placeholders in Text

Dynamic text is a common need in visual novels. Authors can surface VN variables in dialogue, choice text, and HUD messages using the `${varName}` placeholder syntax. Variables are resolved at runtime with their current values.

```vns
[set player_name "Alice"]
[set score 42]

narrator: Welcome back, ${player_name}!
narrator: Your current score is ${score}.

[hud Score: ${score}]
```

**Behavior:**
- Missing variables resolve to **empty string** (no error).
- Interpolation happens once per render — no nesting (`${${nested}}` does not work).
- VNS also supports ICU-style formatting for plurals, selection, and number formatting: `{varName, plural, one{# item} other{# items}}`.

For advanced formatting (plurals, gender selection, number formatting), see **[Text Formatting & ICU](../language/vns-text-formatting.md)**.

---

## Full Example

```vns
@scenario tutorial
@character narrator "Narrator"
@character hero "Hero"
@background room assets/backgrounds/room.png
@charimg hero neutral assets/characters/hero/neutral.png
@charimg hero happy assets/characters/hero/happy.png

@var score = 0

@label start
[bg room]
[bgm assets/audio/bgm/tutorial.ogg]
[show hero center neutral]
narrator: Welcome, ${playerName}.
hero: Let's begin.

> Play minigame -> minigame
> Skip ahead [if debug] -> ending

@label minigame
[jes push game/minigames/aim.jes label after_game with stage=1]

@label after_game
narrator: You returned with score ${score}.
[if score >= 100 goto good]
[jump bad]

@label good
narrator: {score, plural, one{# point} other{# points}} — great result!
[screen flash 0.5 200 255 215 0]
[end]

@label bad
narrator: Try again.
[end]

@label ending
narrator: Thanks for playing.
[end]
```

---

## Related Docs

- [Documentation Index](../../../INDEX.md)
- [Runtime Guide](../../../runtime/core/runtime.md)
- [Editor Guide](../../../editor/core/editor.md)
- [JES Scripting](../../jes/overview/jes-scripting.md)
- [Timeline Scripting](../../timeline/overview/timeline-scripting.md)
- [VNS ↔ JES Architecture](../integration/vns-jes-architecture.md)
- [VNS Tutorial](../guides/vns-tutorial.md)

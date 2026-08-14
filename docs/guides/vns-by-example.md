# VNS By Example

A progressive tutorial series that builds increasingly complex VNS scripts — from a single dialogue line to multi-route stories, reactive interfaces, phone sequences, and JES integration.

Each chapter is a self-contained document covering one topic in depth with full examples, command references, and design patterns.

Read chapters 1–4 in order if VNS is new to you. After that, follow the topics your project needs; chapters 8–12 assume you are comfortable with variables, labels, and branching.

Source reference:
- Parser: `modules/core/src/main/java/com/jvn/core/vn/script/VnScriptParser.java`
- Runtime: `modules/core/src/main/java/com/jvn/core/vn/VnScene.java`
- State: `modules/core/src/main/java/com/jvn/core/vn/VnState.java`
- Interop: `modules/core/src/main/java/com/jvn/core/vn/DefaultVnInterop.java`

---

## Prerequisites

- JVN project built and running ([Getting Started](getting-started.md))
- Basic familiarity with what VNS is ([VNS Overview](../scripting/vns/overview/vns-scripting.md))

---

## Chapters

### Beginner

| # | Chapter | What You Learn |
|---|---------|---------------|
| 1 | [Hello World](vns-by-example/01-hello-world.md) | `@scenario`, `@character`, dialogue lines, `@label`, `[end]` |
| 2 | [Characters and Backgrounds](vns-by-example/02-characters-and-backgrounds.md) | `@charimg`, `@background`, `[show]`/`[hide]`, `[bg]`, positions, expressions, display slots, display presets |
| 3 | [Choices and Branching](vns-by-example/03-choices-and-branching.md) | Choice blocks, `[jump]`, `[goto]`, `[if ... goto]`, story routing |

### Intermediate

| # | Chapter | What You Learn |
|---|---------|---------------|
| 4 | [Variables and Conditions](vns-by-example/04-variables-and-conditions.md) | `[set]`/`[inc]`/`[dec]`/`[flag]`, `@var`, `[if]`/`[elif]`/`[else]`/`[endif]`, `${interpolation}` |
| 5 | [Audio and Transitions](vns-by-example/05-audio-and-transitions.md) | `[bgm]`, `[sfx]`, `[voice]`, `[transition]`, crossfade, volume control |
| 6 | [Screen Effects and Timing](vns-by-example/06-effects-and-timing.md) | `[particles]`, `[weather]`, `[screen shake]`, `[screen flash]`, `[wait]`, `[textspeed]`, `[ui]`, `[hud]` |
| 7 | [Character Motion](vns-by-example/07-character-motion.md) | `[move]`, `[char global]`, easing, custom positions, layering, choreography |

### Advanced

| # | Chapter | What You Learn |
|---|---------|---------------|
| 8 | [Script Structure](vns-by-example/08-script-structure.md) | `[gosub]`/`[return]`, `@include`, multi-file projects, arc navigation |
| 9 | [Advanced Variables](vns-by-example/09-advanced-variables.md) | `[mul]`/`[div]`/`[toggle]`, `[persistent]`, arithmetic expressions, save data |
| 10 | [JES and Java Integration](vns-by-example/10-jes-and-java-integration.md) | `[jes push]`, `[jes replace]`, inline timelines, `[java]`, `[call]` interop |
| 11 | [Reactive UI with Facets](vns-by-example/11-reactive-ui-and-facets.md) | `.facet` layouts, live bindings, `[screen show]`/`[screen call]`, conditions, return values |
| 12 | [Phone Storytelling](vns-by-example/12-phone-storytelling.md) | Contacts, chats, media messages, unread state, calls, persistence |

---

## What's Next

- [VNS Commands Reference](../scripting/vns/language/vns-commands.md) — complete command catalog
- [VNS Variables & Conditions](../scripting/vns/language/vns-variables.md) — full expression syntax
- [Characters & Sprites](../scripting/vns/presentation/vns-characters.md) — character display details
- [Audio Commands](../scripting/vns/presentation/vns-audio.md) — audio system reference
- [JVN Facets](../scripting/ui/facets.md) — complete reactive interface reference
- [VNS Interop](../scripting/vns/integration/vns-interop.md) — phone commands and external integration
- [JES By Example](jes-by-example.md) — the same progressive tutorial series for JES scenes
- [Cookbook](cookbook.md) — practical recipes for common patterns

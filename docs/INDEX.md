# JVN Documentation Index

Complete documentation for the Java Vector Nexus engine.

---

## Getting Started

- [Getting Started Guide](getting-started.md) — first-time setup, build, and run
- [Project Setup: New Project Wizard](project-setup/new-project-wizard.md) — scaffolding a new VN project
- [Project Setup: Version Control](project-setup/version-control.md) — Git integration

---

## Architecture

- [Overview](architecture/overview.md) — high-level engine map
- [System Architecture](architecture/system-architecture.md) — modules, boot sequence, data flows
- [2D Engine](architecture/2d-engine.md) — Scene2D, physics, JES runtime
- [Performance](architecture/performance.md) — build and runtime optimization
- [Native Library Audit](architecture/native-library-audit.md) — native-math integration status

---

## VNS Scripting (Visual Novel Script)

- [VNS Overview](scripting/vns/vns-scripting.md) — landing page and quick start
- [Directives & Declarations](scripting/vns/vns-directives.md) — `@scenario`, `@character`, `@background`, `@charimg`, `@charlayer`, `@charpreset`, `@label`, `@var`, `@define`, `@include`
- [Dialogue & Text](scripting/vns/vns-dialogue.md) — dialogue forms, text effects, inline markup
- [Choices & Branching](scripting/vns/vns-choices.md) — multi-line choices, inline choices, conditional choices, branching patterns
- [Commands Reference](scripting/vns/vns-commands.md) — complete command catalog with examples
- [Audio Commands](scripting/vns/vns-audio.md) — BGM, SFX, voice, crossfade, advanced audio control
- [Characters & Sprites](scripting/vns/vns-characters.md) — character system, layered sprites, presets, motion, global positioning
- [Variables & Conditions](scripting/vns/vns-variables.md) — set/inc/dec/flag, conditions, if/elif/else/endif, interpolation, ICU formatting
- [Transitions & Screen Effects](scripting/vns/vns-transitions.md) — transitions, screen shake, flash, UI control
- [Subroutines & Flow Control](scripting/vns/vns-flow-control.md) — labels, jumps, gosub/return, script switching
- [Interop & Integration](scripting/vns/vns-interop.md) — JES interop, Java calls, inline timelines, menu commands
- [Text Formatting & ICU](scripting/vns/vns-text-formatting.md) — variable interpolation, plurals, select, number formatting
- [Parsing Internals](scripting/vns/vns-parsing.md) — parser pipeline, regex, error model
- [Java + JES Cross Development](scripting/vns/java-jes-cross-development.md) — hybrid architecture patterns

---

## JES Scripting (Engine Script)

- [JES Overview](scripting/jes/jes-scripting.md) — language guide and quick start
- [JES Parsing Internals](scripting/jes/jes-parsing.md) — tokenizer, parser, strict validation
- [Component Reference](scripting/jes/components.md) — per-component property maps

---

## Timeline

- [Timeline Scripting](scripting/timeline/timeline-scripting.md) — arc/link DSL, editor graph, validation

---

## Runtime

- [Runtime Guide](runtime/runtime.md) — CLI options, launch patterns, asset lookup
- [Interop Guide](runtime/interop.md) — provider routing, default + runtime providers
- [Save System](runtime/save-system.md) — schema, migration, atomic writes, autosave

---

## Menu System

- [Menu Profiles](menu-profiles/menu-profiles.md) — registry, screens, layouts, styles, actions, validation
- [Title Screen & Menu Presentation](project-setup/title-screen.md) — theme + profile layers

---

## Editor

- [Editor Guide](editor/editor.md) — layout, editing modes, keyboard shortcuts
- [Puppeteer Animation Editor](editor/puppeteer.md) — keyframe animation, timeline registry, VNS integration
- [Action Editor Design](editor/action-editor-design.md) — architecture and component breakdown
- [Puppeteer Audit](editor/puppeteer-audit.md) — hardening audit and expansion roadmap
- [Help Center](editor/help-center.md) — in-app documentation browser

---

## Project Setup

- [New Project Wizard](project-setup/new-project-wizard.md) — wizard sections, generated layout
- [Title Screen](project-setup/title-screen.md) — theme and menu config
- [Text Effects](project-setup/text-effects.md) — inline dialogue markup tags
- [Version Control](project-setup/version-control.md) — Git + Git LFS workflows

---

## Supplementary

- [Cookbook & Recipes](cookbook.md) — common patterns and end-to-end examples

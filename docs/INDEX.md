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
- [Layered Character Presets](scripting/vns/vns-layered-charpresets.md) — practical guide: `@charlayer` + `@charpreset` pipeline, asset organization, cross-character refs, editor tooling
- [Variables & Conditions](scripting/vns/vns-variables.md) — set/inc/dec/flag, conditions, if/elif/else/endif, interpolation, ICU formatting
- [Transitions & Screen Effects](scripting/vns/vns-transitions.md) — transitions, screen shake, flash, UI control
- [Subroutines & Flow Control](scripting/vns/vns-flow-control.md) — labels, jumps, gosub/return, script switching
- [Interop & Integration](scripting/vns/vns-interop.md) — JES interop, Java calls, inline timelines, menu commands
- [Text Formatting & ICU](scripting/vns/vns-text-formatting.md) — variable interpolation, plurals, select, number formatting
- [Scene Lifecycle & State](scripting/vns/vns-scene-lifecycle.md) — VnScene node loop, VnState, node types, preflight, character visuals, screen effects, HUD
- [Save System](scripting/vns/vns-save-system.md) — named slots, autosave, quick save/load, schema migration, JSON format, atomic writes, thumbnails
- [Rollback & History](scripting/vns/vns-rollback-history.md) — rollback stack, forward/backward, dialogue backlog, capture/restore
- [Settings & Playback Modes](scripting/vns/vns-settings-modes.md) — text speed, volumes, skip mode, auto-play, UI hidden, click-reveal, key bindings
- [Localization](scripting/vns/vns-localization.md) — locale-aware script loading, UI string localization, multi-language structure
- [Parsing Internals](scripting/vns/vns-parsing.md) — parser pipeline, regex, error model
- [Java + JES Cross Development](scripting/vns/java-jes-cross-development.md) — hybrid architecture patterns

---

## JES Scripting (Engine Script)

- [JES Overview](scripting/jes/jes-scripting.md) — landing page, quick start, quick reference tables
- [Scenes & Entities](scripting/jes/jes-scenes-entities.md) — scene structure, entity declarations, lifecycle, merging, save/load
- [Component Reference](scripting/jes/components.md) — all 12 component types with full property tables
- [Timeline & Actions](scripting/jes/jes-timeline.md) — 22 timeline actions: move, rotate, scale, fade, camera, audio, combat, parallel, loop, labels
- [Input Bindings](scripting/jes/jes-input.md) — keyboard mappings, built-in actions, continuous movement, custom handlers
- [Camera System](scripting/jes/jes-camera.md) — position, zoom, shake, follow with dead zones, parallax scrolling
- [Physics & Collision](scripting/jes/jes-physics.md) — rigid bodies, circles, boxes, sensors, triggers, restitution
- [Tilemaps & Maps](scripting/jes/jes-tilemaps.md) — tilesets, tile layers, collision layers, trigger layers, pathfinding
- [AI System](scripting/jes/jes-ai.md) — chase, patrol, guard, flee, line-of-sight, A* pathfinding
- [RPG Stats & Combat](scripting/jes/jes-rpg.md) — Stats, Inventory, Equipment, Items, damage/heal, death callbacks
- [UI Widgets](scripting/jes/jes-ui-widgets.md) — Button2D, Slider2D, HUD patterns
- [VN Bridge & Java Hooks](scripting/jes/jes-bridge.md) — call handlers, VNS↔JES scene stack, return data, launch properties
- [Parsing Internals](scripting/jes/jes-parsing.md) — tokenizer, parser, AST, strict validation

---

## Timeline

- [Timeline Overview](scripting/timeline/timeline-scripting.md) — landing page, quick start, key concepts
- [Story Arcs & Links DSL](scripting/timeline/timeline-story-arcs.md) — arc declarations, link syntax, clusters, validation, story patterns, editor features
- [Puppeteer Animation Timelines](scripting/timeline/timeline-animation.md) — TimelineData model, keyframe interpolation, audio cues, TimelineRunner, TimelineRegistry, VNS integration

---

## Runtime

- [Runtime Guide](runtime/runtime.md) — CLI options, launch patterns, asset lookup
- [Interop Guide](runtime/interop.md) — provider routing, default + runtime providers
- [Save System](runtime/save-system.md) — schema, migration, atomic writes, autosave
- [Audio System](runtime/audio-system.md) — BGM/SFX/Voice channels, backends, crossfade, spectrum, format support
- [Asset Management](runtime/asset-management.md) — AssetCatalog, filesystem vs classpath, path resolution, conventions
- [VN Settings Reference](runtime/vn-settings.md) — all settings fields, defaults, ranges, persistence, settings store

---

## Menu & Layout System

### Menu Reference

- [Menu Profiles Overview](scripting/menus-submenus/menu-profiles.md) — landing page, quick start, directory structure, loader discovery, action types
- [Menu Screens](scripting/menus-submenus/menu-screens.md) — `.menu` files, item declarations, actions, bounds, slot previews, inheritance
- [Menu Styles](scripting/menus-submenus/menu-styles.md) — `.style` files, colors, fonts, shadows, button skins, title/hints, backgrounds
- [Title Screen & Menu Presentation](project-setup/title-screen.md) — theme + profile layers

### Layout Guides

- [Text-First Layout Workflow](scripting/layout/text-first-layout-workflow.md) — beginner guide, golden iteration loop, migration from visual-first
- [Layout DSL Cookbook](scripting/layout/layout-dsl-cookbook.md) — 10 full recipes, complete key reference, runtime checklists
- [Dialogue Layout & Style](scripting/layout/dialogue-layout.md) — textbox geometry, name box, choice buttons, textbox action buttons, character framing
- [Menu Layouts](scripting/layout/menu-layouts.md) — `.layout` files, list positioning, line height, text alignment, built-in layouts
- [Menu Button Layouts](scripting/layout/menu-button-layouts.md) — per-button positional layouts, explicit bounds, resolution hints, Bounds Studio
- [Menu Registry & File Discovery](scripting/layout/menu-registry.md) — `menu.registry`, file search paths, fallback, auto-discovery
- [Menu Actions & Navigation](scripting/layout/menu-actions.md) — all 10 action types, aliases, custom actions, navigation flow
- [Menu Inheritance & Composition](scripting/layout/menu-inheritance.md) — `extends` for screens/layouts/styles, chains, circular detection
- [Choice Buttons](scripting/layout/choice-buttons.md) — positioning, 4-state colors, borders, fonts, image assets
- [Textbox Action Buttons](scripting/layout/textbox-action-buttons.md) — Auto/Skip/Log/Save buttons, positioning, image skins
- [Character Framing & Sprites](scripting/layout/character-framing.md) — height factor, baseline, textbox interaction
- [Colors & Theming](scripting/layout/colors-theming.md) — hex format, alpha, all color keys, 4 palette recipes
- [Fonts & Typography](scripting/layout/fonts-typography.md) — all font keys, cross-platform availability, examples
- [Save & Load Screens](scripting/layout/save-load-screens.md) — slot templates, thumbnails, placeholder/frame assets
- [Settings Screen](scripting/layout/settings-screen.md) — `{value}` placeholders, built-in settings, section headers
- [Assets & Backgrounds](scripting/layout/assets-backgrounds.md) — all asset keys, path conventions, fallback behavior
- [Validation & Diagnostics](scripting/layout/validation-diagnostics.md) — every diagnostic message, causes, fixes
- [Layout Editor Tools](scripting/layout/layout-editor-tools.md) — Layout Studio, Bounds Studio, registry editor, screen cards
- [Scala DSL Reference](scripting/layout/scala-dsl.md) — type-safe Scala builders for styles, layouts, and button layouts

---

## Editor

- [Editor Guide](editor/editor.md) — layout, editing modes, keyboard shortcuts
- [Puppeteer Overview & Architecture](editor/puppeteer.md) — data pipeline, JES/VNS relationship, snapshot resolution, registry bridge
- [Puppeteer Editor Guide](editor/puppeteer-editor-guide.md) — complete usage: launching, UI panels, keyframes, 12 presets, 26 easing types, audio cues, camera, groups, layer ordering, orbit tool, onion skinning, shortcuts
- [Puppeteer JES DSL Reference](editor/puppeteer-jes-dsl.md) — exported timeline syntax: move, rotate, scale, fade, pivot, cameraMove, cameraZoom, playAudio, wait, parallel, easing values, export modes, VNS/JES integration
- [Sidebar Utilities Overview](editor/sidebar-utilities.md) — landing page for all 14 sidebar panels
  - [Project Explorer](editor/sidebar-project-explorer.md) — file tree, create/rename/delete, run project
  - [Story Timeline](editor/sidebar-story-timeline.md) — multi-arc story graph, arcs, links, clusters, validation
  - [Inspector](editor/sidebar-inspector.md) — entity property editing for Sprite2D, Label2D, Panel2D, physics, particles
  - [Puppeteer Launcher](editor/sidebar-puppeteer-launcher.md) — live VNS scene snapshot, 12 command patterns, one-click launch
  - [VNS Diagnostics](editor/sidebar-vns-diagnostics.md) — live error/warning list, click-to-jump
  - [Label Flow Map](editor/sidebar-label-flow-map.md) — visual label-to-label directed graph
  - [Asset Browser](editor/sidebar-asset-browser.md) — asset discovery, preview, drag-and-drop, type filter
  - [Layout Launcher](editor/sidebar-layout-launcher.md) — status dashboard and launch for layout/style/screen editors
  - [Menu Flow Editor](editor/sidebar-menu-flow-editor.md) — visual menu-to-menu navigation wiring, wire mode
  - [Layered Image Visualizer](editor/sidebar-layered-image-visualizer.md) — layered sprite exploration, 6 export formats, presets
  - [Image Attributes Tool](editor/sidebar-image-attributes-tool.md) — attribute-based character image assembly, profiles
  - [Image Tint Tool](editor/sidebar-image-tint-tool.md) — color tinting/grading with tint, saturation, contrast
  - [Version Control](editor/sidebar-version-control.md) — Git panel: init, commit, push, pull, branch, stash, remote setup
  - [Help Center](editor/sidebar-help-center.md) — in-app Markdown documentation browser, quick access, F1 shortcut
- [Action Editor Design](editor/action-editor-design.md) — architecture and component breakdown
- [Puppeteer Audit](editor/puppeteer-audit.md) — hardening audit and expansion roadmap
- [Help Center](editor/help-center.md) — in-app documentation browser

---

## Project Setup

- [New Project Wizard](project-setup/new-project-wizard.md) — wizard sections, generated layout
- [Project Structure Conventions](project-setup/project-structure.md) — directory layout, naming, asset organization, team patterns
- [Title Screen](project-setup/title-screen.md) — theme and menu config
- [Text Effects](project-setup/text-effects.md) — inline dialogue markup tags
- [Version Control](project-setup/version-control.md) — Git + Git LFS workflows
- [Localization Workflow](project-setup/localization.md) — locale-aware scripts, UI strings, multi-language setup
- [Deployment & Packaging](project-setup/deployment.md) — building for distribution, asset bundling, platform targets

---

## Architecture

- [Overview](architecture/overview.md) — high-level engine map
- [System Architecture](architecture/system-architecture.md) — modules, boot sequence, data flows
- [2D Engine](architecture/2d-engine.md) — Scene2D, physics, JES runtime
- [Performance](architecture/performance.md) — build and runtime optimization
- [Native Library Audit](architecture/native-library-audit.md) — native-math integration status
- [Debugging & Profiling](architecture/debugging.md) — diagnostics, logging, performance investigation

---

## Supplementary

- [Cookbook & Recipes](cookbook.md) — common patterns and end-to-end examples

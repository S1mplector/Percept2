# JVN Documentation Index

Complete documentation for the Java Vector Nexus engine.

---

## Getting Started

- [Getting Started Guide](guides/getting-started.md) — first-time setup, build, and run
- [Project Setup: New Project Wizard](project-setup/onboarding/new-project-wizard.md) — scaffolding a new VN project
- [Project Setup: Version Control](project-setup/collaboration/version-control.md) — Git integration

---

## Architecture

- [Overview](architecture/core/overview.md) — high-level engine map, core capabilities, recommended reading paths
- [System Architecture](architecture/core/system-architecture.md) — modules, Engine update loop, delta smoothing, fixed timestep, SceneManager stack, Input system, boot sequence, data flows
- [2D Engine](architecture/core/2d-engine.md) — Entity2D properties, Camera2D smoothing/bounds/transforms, PhysicsWorld2D broadphase/raycasts/callbacks, Scene2DBase render pipeline, parallax scrolling
- [Performance](architecture/quality/performance.md) — build and runtime optimization
- [Native Library Audit](architecture/native/native-library-audit.md) — native-math integration status
- [Ren'Py UI Parity Roadmap](architecture/core/ui-parity-roadmap.md) — concrete patch plan for `screens.rpy` / `gui.rpy` migration comfort

---

## VNS Scripting (Visual Novel Script)

- [VNS Overview](scripting/vns/overview/vns-scripting.md) — landing page and quick start
- [Directives & Declarations](scripting/vns/language/vns-directives.md) — `@scenario`, `@character`, `@background`, `@charimg`, `@charlayer`, `@charpreset`, `@position`, `@label`, `@var`, `@define`, `@include`
- [Dialogue & Text](scripting/vns/language/vns-dialogue.md) — dialogue forms, text effects, inline markup
- [Choices & Branching](scripting/vns/language/vns-choices.md) — multi-line choices, inline choices, conditional choices, branching patterns
- [Commands Reference](scripting/vns/language/vns-commands.md) — complete command catalog with examples
- [Audio Commands](scripting/vns/presentation/vns-audio.md) — BGM, SFX, voice, crossfade, advanced audio control
- [Characters & Sprites](scripting/vns/presentation/vns-characters.md) — character system, layered sprites, presets, motion, global positioning
- [Layered Character Presets](scripting/vns/presentation/vns-layered-charpresets.md) — practical guide: `@charlayer` + `@charpreset` pipeline, asset organization, cross-character refs, editor tooling
- [Variables & Conditions](scripting/vns/language/vns-variables.md) — set/inc/dec/flag, conditions, if/elif/else/endif, interpolation, ICU formatting
- [Transitions & Screen Effects](scripting/vns/presentation/vns-transitions.md) — transitions, screen shake, flash, UI control
- [Subroutines & Flow Control](scripting/vns/flow/vns-flow-control.md) — labels, jumps, gosub/return, script switching
- [Interop & Integration](scripting/vns/integration/vns-interop.md) — JES interop, Java calls, inline timelines, menu commands
- [Text Formatting & ICU](scripting/vns/language/vns-text-formatting.md) — variable interpolation, plurals, select, number formatting
- [Scene Lifecycle & State](scripting/vns/runtime/vns-scene-lifecycle.md) — VnScene node loop, VnState, node types, preflight, character visuals, screen effects, HUD
- [Save System](scripting/vns/runtime/vns-save-system.md) — named slots, autosave, quick save/load, schema migration, JSON format, atomic writes, thumbnails
- [Rollback & History](scripting/vns/runtime/vns-rollback-history.md) — rollback stack, forward/backward, dialogue backlog, capture/restore
- [Settings & Playback Modes](scripting/vns/runtime/vns-settings-modes.md) — text speed, volumes, skip mode, auto-play, UI hidden, click-reveal, key bindings
- [Localization](scripting/vns/runtime/vns-localization.md) — locale-aware script loading, UI string localization, multi-language structure
- [Parsing Internals](scripting/vns/internals/vns-parsing.md) — parser pipeline, regex, error model
- [Java + JES Cross Development](scripting/vns/integration/java-jes-cross-development.md) — hybrid architecture patterns
- [VNS ↔ JES Architecture](scripting/vns/integration/vns-jes-architecture.md) — scene stack coordination, interop routing, timeline runners, proxy entities, bridge lifecycle
- [Tutorial: Building a Complete VN](scripting/vns/guides/vns-tutorial.md) — step-by-step walkthrough building a multi-scene story with choices, variables, audio, and JES integration
- [Best Practices & Common Pitfalls](scripting/vns/guides/vns-best-practices.md) — naming conventions, structural patterns, common mistakes, performance tips
- [Debugging & Troubleshooting](scripting/vns/guides/vns-debugging.md) — parse errors, runtime issues, visual/audio glitches, interop failures, diagnostic tools
- [Project Organization & Scaling](scripting/vns/guides/vns-project-organization.md) — directory conventions, include strategies, multi-route management, team workflows

---

## JES Scripting (Engine Script)

- [JES Overview](scripting/jes/overview/jes-scripting.md) — landing page, quick start, quick reference tables
- [Scenes & Entities](scripting/jes/scene/jes-scenes-entities.md) — scene structure, entity declarations, lifecycle, merging, save/load
- [Component Reference](scripting/jes/scene/components.md) — all 12 component types with full property tables
- [Timeline & Actions](scripting/jes/timeline/jes-timeline.md) — 22 timeline actions: move, rotate, scale, fade, camera, audio, combat, parallel, loop, labels
- [Input Bindings](scripting/jes/systems/jes-input.md) — keyboard mappings, built-in actions, continuous movement, custom handlers
- [Camera System](scripting/jes/systems/jes-camera.md) — position, zoom, shake, follow with dead zones, parallax scrolling
- [Physics & Collision](scripting/jes/systems/jes-physics.md) — rigid bodies, circles, boxes, sensors, triggers, restitution
- [Tilemaps & Maps](scripting/jes/systems/jes-tilemaps.md) — tilesets, tile layers, collision layers, trigger layers, pathfinding
- [AI System](scripting/jes/gameplay/jes-ai.md) — chase, patrol, guard, flee, line-of-sight, A* pathfinding
- [RPG Stats & Combat](scripting/jes/gameplay/jes-rpg.md) — Stats, Inventory, Equipment, Items, damage/heal, death callbacks
- [UI Widgets](scripting/jes/gameplay/jes-ui-widgets.md) — Button2D, Slider2D, HUD patterns
- [VN Bridge & Java Hooks](scripting/jes/integration/jes-bridge.md) — call handlers, VNS↔JES scene stack, return data, launch properties
- [Parsing Internals](scripting/jes/internals/jes-parsing.md) — tokenizer, parser, AST, strict validation

---

## Timeline

- [Timeline Overview](scripting/timeline/overview/timeline-scripting.md) — landing page, quick start, key concepts
- [Story Arcs & Links DSL](scripting/timeline/story/timeline-story-arcs.md) — arc declarations, link syntax, clusters, validation, story patterns, editor features
- [Puppeteer Animation Timelines](scripting/timeline/animation/timeline-animation.md) — TimelineData model, keyframe interpolation, audio cues, TimelineRunner, TimelineRegistry, VNS integration
- [Hand-Coding Timelines](scripting/timeline/animation/timeline-hand-coding.md) — writing timeline animations by hand, time cursor model, easing guide, 18 annotated examples, reusable templates

---

## Runtime

- [Runtime Guide](runtime/core/runtime.md) — CLI options, launch patterns, asset lookup
- [Interop Guide](runtime/core/interop.md) — provider routing, default + runtime providers
- [Save System](runtime/systems/save-system.md) — schema, migration, atomic writes, autosave
- [Audio System](runtime/systems/audio-system.md) — BGM/SFX/Voice channels, backends, crossfade, spectrum, format support
- [Asset Management](runtime/systems/asset-management.md) — AssetCatalog, filesystem vs classpath, path resolution, conventions
- [VN Settings Reference](runtime/systems/vn-settings.md) — all settings fields, defaults, ranges, persistence, settings store

---

## Menu & Layout System

### Menu Reference

- [Menu Profiles Overview](scripting/ui/menus/menu-profiles.md) — landing page, quick start, directory structure, loader discovery, action types
- [Menu Screens](scripting/ui/menus/menu-screens.md) — `.menu` files, item declarations, actions, bounds, slot previews, inheritance
- [Menu Styles](scripting/ui/menus/menu-styles.md) — `.style` files, colors, fonts, shadows, button skins, title/hints, backgrounds
- [Title Screen & Menu Presentation](project-setup/content/title-screen.md) — theme + profile layers

### Layout Guides

- [Text-First Layout Workflow](scripting/ui/layout/workflow/text-first-layout-workflow.md) — beginner guide, golden iteration loop, migration from visual-first
- [Layout DSL Cookbook](scripting/ui/layout/reference/layout-dsl-cookbook.md) — 10 full recipes, complete key reference, runtime checklists
- [Dialogue Layout & Style](scripting/ui/layout/components/dialogue-layout.md) — textbox geometry, name box, choice buttons, textbox action buttons, character framing
- [Menu Layouts](scripting/ui/layout/structure/menu-layouts.md) — `.layout` files, list positioning, line height, text alignment, built-in layouts
- [Menu Button Layouts](scripting/ui/layout/structure/menu-button-layouts.md) — per-button positional layouts, explicit bounds, resolution hints, Bounds Studio
- [Menu Registry & File Discovery](scripting/ui/layout/structure/menu-registry.md) — `menu.registry`, file search paths, fallback, auto-discovery
- [Menu Actions & Navigation](scripting/ui/layout/structure/menu-actions.md) — all 10 action types, aliases, custom actions, navigation flow
- [Menu Inheritance & Composition](scripting/ui/layout/structure/menu-inheritance.md) — `extends` for screens/layouts/styles, chains, circular detection
- [Custom Layout Scenarios](scripting/ui/layout/tooling/custom-scenarios.md) — multi-button textbox, per-screen backgrounds, per-item fonts, auto-width name box, sidebar menus, chapter select, confirmation dialogs
- [Choice Buttons](scripting/ui/layout/components/choice-buttons.md) — positioning, 4-state colors, borders, fonts, image assets
- [Textbox Action Buttons](scripting/ui/layout/components/textbox-action-buttons.md) — Auto/Skip/Log/Save buttons, positioning, image skins
- [Character Framing & Sprites](scripting/ui/layout/components/character-framing.md) — height factor, baseline, textbox interaction
- [Colors & Theming](scripting/ui/layout/styling/colors-theming.md) — hex format, alpha, all color keys, 4 palette recipes
- [Fonts & Typography](scripting/ui/layout/styling/fonts-typography.md) — all font keys, cross-platform availability, examples
- [Save & Load Screens](scripting/ui/layout/screens/save-load-screens.md) — slot templates, thumbnails, placeholder/frame assets
- [Settings Screen](scripting/ui/layout/screens/settings-screen.md) — `{value}` placeholders, built-in settings, section headers
- [Assets & Backgrounds](scripting/ui/layout/styling/assets-backgrounds.md) — all asset keys, path conventions, fallback behavior
- [Validation & Diagnostics](scripting/ui/layout/tooling/validation-diagnostics.md) — every diagnostic message, causes, fixes
- [Layout Editor Tools](scripting/ui/layout/tooling/layout-editor-tools.md) — Layout Studio, Bounds Studio, registry editor, screen cards
- [Scala DSL Reference](scripting/ui/layout/reference/scala-dsl.md) — type-safe Scala builders for styles, layouts, and button layouts

---

## Editor

- [Editor Guide](editor/core/editor.md) — layout, editing modes, keyboard shortcuts
- [Puppeteer Overview & Architecture](editor/puppeteer/puppeteer.md) — data pipeline, JES/VNS relationship, snapshot resolution, registry bridge
- [Puppeteer Editor Guide](editor/puppeteer/puppeteer-editor-guide.md) — complete usage: launching, UI panels, keyframes, 12 presets, 26 easing types, audio cues, camera, groups, layer ordering, orbit tool, onion skinning, shortcuts
- [Puppeteer JES DSL Reference](editor/puppeteer/puppeteer-jes-dsl.md) — exported timeline syntax: move, rotate, scale, fade, pivot, cameraMove, cameraZoom, playAudio, wait, parallel, easing values, export modes, VNS/JES integration
- [Sidebar Utilities Overview](editor/sidebars/overview/sidebar-utilities.md) — landing page for all 14 sidebar panels
  - [Project Explorer](editor/sidebars/left/sidebar-project-explorer.md) — file tree, create/rename/delete, run project
  - [Story Timeline](editor/sidebars/left/sidebar-story-timeline.md) — multi-arc story graph, arcs, links, clusters, validation
  - [Inspector](editor/sidebars/right/sidebar-inspector.md) — entity property editing for Sprite2D, Label2D, Panel2D, physics, particles
  - [Puppeteer Launcher](editor/sidebars/right/sidebar-puppeteer-launcher.md) — live VNS scene snapshot, 12 command patterns, one-click launch
  - [VNS Diagnostics](editor/sidebars/right/sidebar-vns-diagnostics.md) — live error/warning list, click-to-jump
  - [Label Flow Map](editor/sidebars/right/sidebar-label-flow-map.md) — visual label-to-label directed graph
  - [Asset Browser](editor/sidebars/right/sidebar-asset-browser.md) — asset discovery, preview, drag-and-drop, type filter
  - [Layout Launcher](editor/sidebars/right/sidebar-layout-launcher.md) — status dashboard and launch for layout/style/screen editors
  - [Menu Flow Editor](editor/sidebars/right/sidebar-menu-flow-editor.md) — visual menu-to-menu navigation wiring, wire mode
  - [Layered Image Visualizer](editor/sidebars/right/sidebar-layered-image-visualizer.md) — layered sprite exploration, 6 export formats, presets
  - [Image Attributes Tool](editor/sidebars/right/sidebar-image-attributes-tool.md) — attribute-based character image assembly, profiles
  - [Image Tint Tool](editor/sidebars/right/sidebar-image-tint-tool.md) — color tinting/grading with tint, saturation, contrast
  - [Version Control](editor/sidebars/right/sidebar-version-control.md) — Git panel: init, commit, push, pull, branch, stash, remote setup
  - [Help Center](editor/sidebars/right/sidebar-help-center.md) — in-app Markdown documentation browser, quick access, F1 shortcut
- [Action Editor Design](editor/core/action-editor-design.md) — architecture and component breakdown
- [Puppeteer Audit](editor/puppeteer/puppeteer-audit.md) — hardening audit and expansion roadmap
- [Help Center](editor/core/help-center.md) — in-app documentation browser

---

## Project Setup

- [New Project Wizard](project-setup/onboarding/new-project-wizard.md) — wizard sections, generated layout
- [Project Structure Conventions](project-setup/onboarding/project-structure.md) — directory layout, naming, asset organization, team patterns
- [Title Screen](project-setup/content/title-screen.md) — theme and menu config
- [Text Effects](project-setup/content/text-effects.md) — inline dialogue markup tags
- [Version Control](project-setup/collaboration/version-control.md) — Git + Git LFS workflows
- [Localization Workflow](project-setup/content/localization.md) — locale-aware scripts, UI strings, multi-language setup
- [Deployment & Packaging](project-setup/release/deployment.md) — building for distribution, asset bundling, platform targets

---

## Architecture

- [Overview](architecture/core/overview.md) — high-level engine map, core capabilities, recommended reading paths
- [System Architecture](architecture/core/system-architecture.md) — modules, Engine update loop, delta smoothing, fixed timestep, SceneManager stack, Input system, boot sequence, data flows
- [2D Engine](architecture/core/2d-engine.md) — Entity2D properties, Camera2D smoothing/bounds/transforms, PhysicsWorld2D broadphase/raycasts/callbacks, Scene2DBase render pipeline, parallax scrolling
- [Performance](architecture/quality/performance.md) — build and runtime optimization
- [Native Library Audit](architecture/native/native-library-audit.md) — native-math integration status
- [Debugging & Profiling](architecture/quality/debugging.md) — diagnostics, logging, performance investigation
- [Ren'Py UI Parity Roadmap](architecture/core/ui-parity-roadmap.md) — concrete patch plan for `screens.rpy` / `gui.rpy` migration comfort

---

## Supplementary

- [Cookbook & Recipes](guides/cookbook.md) — common patterns and end-to-end examples

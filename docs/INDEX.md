# JVN Documentation

This index is meant to guide you, the reader. Start with the path that matches what you are trying to do, then drop into the deeper reference sections as needed.

## Start Here

- [Getting Started Guide](guides/getting-started.md) — build the engine, run the editor, and get your environment working
- [Choose Your Path in JVN](guides/choose-your-path.md) — decide whether you should start in VNS, JES, Puppeteer, menus, or editor tooling
- [Common JVN File Types](guides/common-file-types.md) — quick orientation for `.vns`, `.jes`, `.menu`, `.layout`, `.style`, timelines, and staging files
- [Architecture Overview](architecture/core/overview.md) — understand how JVN is split across editor, runtime, VNS, JES, menus, and native systems
- [Editor Guide](editor/core/editor.md) — learn the main editor layout, modes, and shortcuts
- [Editor And Launcher Settings](editor/core/settings.md) — app preferences, launcher handoff, run defaults, and sidebar defaults

## Pick A Path

### I want to make a visual novel in VNS

1. [VNS Overview](scripting/vns/overview/vns-scripting.md)
2. [Directives & Declarations](scripting/vns/language/vns-directives.md)
3. [Dialogue & Text](scripting/vns/language/vns-dialogue.md)
4. [Choices & Branching](scripting/vns/language/vns-choices.md)
5. [Commands Reference](scripting/vns/language/vns-commands.md)
6. [Tutorial: Building a Complete VN](scripting/vns/guides/vns-tutorial.md)

### I want gameplay scenes or systems in JES

1. [JES Overview](scripting/jes/overview/jes-scripting.md)
2. [Scenes & Entities](scripting/jes/scene/jes-scenes-entities.md)
3. [Component Reference](scripting/jes/scene/components.md)
4. [Timeline & Actions](scripting/jes/timeline/jes-timeline.md)
5. [VN Bridge & Java Hooks](scripting/jes/integration/jes-bridge.md)

### I want to animate scenes with Puppeteer

1. [Puppeteer Overview & Architecture](editor/puppeteer/puppeteer.md)
2. [Puppeteer Editor Guide](editor/puppeteer/puppeteer-editor-guide.md)
3. [Puppeteer JES DSL Reference](editor/puppeteer/puppeteer-jes-dsl.md)
4. [Puppeteer Launcher](editor/sidebars/right/sidebar-puppeteer-launcher.md)
5. [Puppeteer Animation Timelines](scripting/timeline/animation/timeline-animation.md)

### I want to build menus and UI layouts

1. [Menu Profiles Overview](scripting/ui/menus/menu-profiles.md)
2. [Menu Screens](scripting/ui/menus/menu-screens.md)
3. [Menu Styles](scripting/ui/menus/menu-styles.md)
4. [Dialogue Layout & Style](scripting/ui/layout/components/dialogue-layout.md)
5. [Text-First Layout Workflow](scripting/ui/layout/workflow/text-first-layout-workflow.md)

### I want to understand the editor and its tools

1. [Editor Guide](editor/core/editor.md)
2. [Welcome Center](editor/core/welcome-center.md)
3. [Run Console](editor/core/run-console.md)
4. [Editor And Launcher Settings](editor/core/settings.md)
5. [Sidebar Utilities Overview](editor/sidebars/overview/sidebar-utilities.md)
6. [Help Center](editor/core/help-center.md)

### I want runtime, packaging, and deployment details

1. [Runtime Guide](runtime/core/runtime.md)
2. [Asset Management](runtime/systems/asset-management.md)
3. [Save System](runtime/systems/save-system.md)
4. [Audio System](runtime/systems/audio-system.md)
5. [Build System](project-setup/release/build-system.md)
6. [Deployment & Packaging](project-setup/release/deployment.md)

### I want architecture, internals, and debugging

1. [Architecture Overview](architecture/core/overview.md)
2. [System Architecture](architecture/core/system-architecture.md)
3. [2D Engine](architecture/core/2d-engine.md)
4. [Debugging & Profiling](architecture/quality/debugging.md)
5. [Performance](architecture/quality/performance.md)

## Editor And Tooling

### Core Editor

- [Editor Guide](editor/core/editor.md) — layout, editing modes, keyboard shortcuts
- [Editor And Launcher Settings](editor/core/settings.md) — app preferences, launcher handoff, run defaults, and sidebar defaults
- [Welcome Center](editor/core/welcome-center.md) — startup dashboard and environment health
- [Run Console](editor/core/run-console.md) — build output, filters, counters, runtime monitoring
- [Scene Graph View](editor/core/scene-graph.md) — entity list, filtering, rename/delete, fit-to-selection
- [Tilemap Editor](editor/core/tilemap-editor.md) — tile painting, layers, tileset preview
- [Action Editor Design](editor/core/action-editor-design.md) — action editor architecture and component breakdown
- [Help Center](editor/core/help-center.md) — in-app docs browser and quick-access routes

### Puppeteer

- [Puppeteer Overview & Architecture](editor/puppeteer/puppeteer.md)
- [Puppeteer Editor Guide](editor/puppeteer/puppeteer-editor-guide.md)
- [Puppeteer JES DSL Reference](editor/puppeteer/puppeteer-jes-dsl.md)
- [Puppeteer Audit](editor/puppeteer/puppeteer-audit.md)

### Sidebar Utilities

- [Sidebar Utilities Overview](editor/sidebars/overview/sidebar-utilities.md)
- [Project Explorer](editor/sidebars/left/sidebar-project-explorer.md)
- [Story Timeline](editor/sidebars/left/sidebar-story-timeline.md)
- [Inspector](editor/sidebars/right/sidebar-inspector.md)
- [Puppeteer Launcher](editor/sidebars/right/sidebar-puppeteer-launcher.md)
- [VNS Diagnostics](editor/sidebars/right/sidebar-vns-diagnostics.md)
- [Label Flow Map](editor/sidebars/right/sidebar-label-flow-map.md)
- [Asset Browser](editor/sidebars/right/sidebar-asset-browser.md)
- [Layout Launcher](editor/sidebars/right/sidebar-layout-launcher.md)
- [Phone Assets](editor/sidebars/right/sidebar-phone-assets-tool.md)
- [Menu Flow Editor](editor/sidebars/right/sidebar-menu-flow-editor.md)
- [Layered Image Visualizer](editor/sidebars/right/sidebar-layered-image-visualizer.md)
- [Image Attributes Tool](editor/sidebars/right/sidebar-image-attributes-tool.md)
- [Scene Lighting Studio](editor/sidebars/right/sidebar-image-tint-tool.md)
- [Version Control](editor/sidebars/right/sidebar-version-control.md)
- [Help Center](editor/sidebars/right/sidebar-help-center.md)
- [Storyboard Overlay](editor/sidebars/right/sidebar-storyboard-overlay.md)
- [Text Editor](editor/sidebars/right/sidebar-script-editor.md)

### Editor File Formats

- [.layersetup Files](editor/tools/layersetup-files.md)

## Scripting

### VNS

- [VNS Overview](scripting/vns/overview/vns-scripting.md)
- [Directives & Declarations](scripting/vns/language/vns-directives.md)
- [Dialogue & Text](scripting/vns/language/vns-dialogue.md)
- [Choices & Branching](scripting/vns/language/vns-choices.md)
- [Commands Reference](scripting/vns/language/vns-commands.md)
- [Variables & Conditions](scripting/vns/language/vns-variables.md)
- [Text Formatting & ICU](scripting/vns/language/vns-text-formatting.md)
- [Audio Commands](scripting/vns/presentation/vns-audio.md)
- [Characters & Sprites](scripting/vns/presentation/vns-characters.md)
- [Layered Character Presets](scripting/vns/presentation/vns-layered-charpresets.md)
- [Transitions & Screen Effects](scripting/vns/presentation/vns-transitions.md)
- [Subroutines & Flow Control](scripting/vns/flow/vns-flow-control.md)
- [Interop & Integration](scripting/vns/integration/vns-interop.md)
- [Java + JES Cross Development](scripting/vns/integration/java-jes-cross-development.md)
- [VNS ↔ JES Architecture](scripting/vns/integration/vns-jes-architecture.md)
- [Scene Lifecycle & State](scripting/vns/runtime/vns-scene-lifecycle.md)
- [Save System](scripting/vns/runtime/vns-save-system.md)
- [Rollback & History](scripting/vns/runtime/vns-rollback-history.md)
- [Settings & Playback Modes](scripting/vns/runtime/vns-settings-modes.md)
- [Localization](scripting/vns/runtime/vns-localization.md)
- [Parsing Internals](scripting/vns/internals/vns-parsing.md)
- [Tutorial: Building a Complete VN](scripting/vns/guides/vns-tutorial.md)
- [Best Practices & Common Pitfalls](scripting/vns/guides/vns-best-practices.md)
- [Debugging & Troubleshooting](scripting/vns/guides/vns-debugging.md)
- [Project Organization & Scaling](scripting/vns/guides/vns-project-organization.md)

### JES

- [JES Overview](scripting/jes/overview/jes-scripting.md)
- [Scenes & Entities](scripting/jes/scene/jes-scenes-entities.md)
- [Component Reference](scripting/jes/scene/components.md)
- [Timeline & Actions](scripting/jes/timeline/jes-timeline.md)
- [Input Bindings](scripting/jes/systems/jes-input.md)
- [Camera System](scripting/jes/systems/jes-camera.md)
- [Physics & Collision](scripting/jes/systems/jes-physics.md)
- [Tilemaps & Maps](scripting/jes/systems/jes-tilemaps.md)
- [AI System](scripting/jes/gameplay/jes-ai.md)
- [RPG Stats & Combat](scripting/jes/gameplay/jes-rpg.md)
- [UI Widgets](scripting/jes/gameplay/jes-ui-widgets.md)
- [VN Bridge & Java Hooks](scripting/jes/integration/jes-bridge.md)
- [Parsing Internals](scripting/jes/internals/jes-parsing.md)

### Timeline

- [Timeline Overview](scripting/timeline/overview/timeline-scripting.md)
- [Story Arcs & Links DSL](scripting/timeline/story/timeline-story-arcs.md)
- [Puppeteer Animation Timelines](scripting/timeline/animation/timeline-animation.md)
- [Hand-Coding Timelines](scripting/timeline/animation/timeline-hand-coding.md)

### Menus And Layout

#### Menus

- [Menu Profiles Overview](scripting/ui/menus/menu-profiles.md)
- [Menu Screens](scripting/ui/menus/menu-screens.md)
- [Menu Styles](scripting/ui/menus/menu-styles.md)

#### Layout

- [Text-First Layout Workflow](scripting/ui/layout/workflow/text-first-layout-workflow.md)
- [Layout DSL Cookbook](scripting/ui/layout/reference/layout-dsl-cookbook.md)
- [Dialogue Layout & Style](scripting/ui/layout/components/dialogue-layout.md)
- [Menu Layouts](scripting/ui/layout/structure/menu-layouts.md)
- [Menu Button Layouts](scripting/ui/layout/structure/menu-button-layouts.md)
- [Menu Registry & File Discovery](scripting/ui/layout/structure/menu-registry.md)
- [Menu Actions & Navigation](scripting/ui/layout/structure/menu-actions.md)
- [Menu Inheritance & Composition](scripting/ui/layout/structure/menu-inheritance.md)
- [Custom Layout Scenarios](scripting/ui/layout/tooling/custom-scenarios.md)
- [Choice Buttons](scripting/ui/layout/components/choice-buttons.md)
- [Textbox Action Buttons](scripting/ui/layout/components/textbox-action-buttons.md)
- [Character Framing & Sprites](scripting/ui/layout/components/character-framing.md)
- [Colors & Theming](scripting/ui/layout/styling/colors-theming.md)
- [Fonts & Typography](scripting/ui/layout/styling/fonts-typography.md)
- [Save & Load Screens](scripting/ui/layout/screens/save-load-screens.md)
- [Settings Screen](scripting/ui/layout/screens/settings-screen.md)
- [Help Screen](scripting/ui/layout/screens/help-screen.md)
- [Assets & Backgrounds](scripting/ui/layout/styling/assets-backgrounds.md)
- [Validation & Diagnostics](scripting/ui/layout/tooling/validation-diagnostics.md)
- [Layout Editor Tools](scripting/ui/layout/tooling/layout-editor-tools.md)
- [Scala DSL Reference](scripting/ui/layout/reference/scala-dsl.md)

## Runtime, Project Setup, And Release

### Runtime

- [Runtime Guide](runtime/core/runtime.md)
- [Interop Guide](runtime/core/interop.md)
- [Save System](runtime/systems/save-system.md)
- [Audio System](runtime/systems/audio-system.md)
- [Asset Management](runtime/systems/asset-management.md)
- [VN Settings Reference](runtime/systems/vn-settings.md)

### Project Setup

- [New Project Wizard](project-setup/onboarding/new-project-wizard.md)
- [Project Structure Conventions](project-setup/onboarding/project-structure.md)
- [Title Screen](project-setup/content/title-screen.md)
- [Text Effects](project-setup/content/text-effects.md)
- [Localization Workflow](project-setup/content/localization.md)
- [Version Control](project-setup/collaboration/version-control.md)
- [Build System](project-setup/release/build-system.md)
- [Deployment & Packaging](project-setup/release/deployment.md)

## Architecture And Internals

- [Architecture Overview](architecture/core/overview.md)
- [System Architecture](architecture/core/system-architecture.md)
- [2D Engine](architecture/core/2d-engine.md)
- [Performance](architecture/quality/performance.md)
- [Debugging & Profiling](architecture/quality/debugging.md)
- [Ren'Py UI Parity Roadmap](architecture/core/ui-parity-roadmap.md)

## Tutorials (By Example)

- [JES By Example](guides/jes-by-example.md) — 10-chapter progressive JES tutorial (scenes → physics → VNS bridge)
- [VNS By Example](guides/vns-by-example.md) — 10-chapter progressive VNS tutorial (dialogue → variables → JES integration)

## Supplementary

- [Choose Your Path in JVN](guides/choose-your-path.md) — onboarding route selection
- [Common JVN File Types](guides/common-file-types.md) — file-role quick reference
- [Cookbook & Recipes](guides/cookbook.md) — practical patterns and end-to-end examples
- [JES ↔ VNS Integration Cookbook](guides/integration-cookbook.md) — detailed code examples for every integration direction

# Ren'Py UI Parity Roadmap

Concrete patch plan for making JVN comfortable for teams migrating from heavily customized Ren'Py projects.

This document is based on a direct audit of a customized local Ren'Py teaser project. The goal is not generic feature parity. The goal is seamless migration for teams already shipping Ren'Py-style customized dialogue, choice, menu, history, save/load, and alternate presentation screens.

## Problem Statement

JVN already has:

- a dialogue layout/style DSL
- menu profiles, layouts, styles, and save/load templates
- history and save/load runtime support
- editor-side dialogue/menu layout tools

What it did not yet have at the start of this roadmap was a complete "screen stack parity" story comparable to Ren'Py's `screens.rpy` + `gui.rpy` workflow.

That gap shows up in three ways:

1. Some presentation controls Ren'Py teams expect are still missing from the dialogue layout DSL.
2. Some runtime screens are still hardcoded renderer overlays instead of data-driven themed screens.
3. Alternate presentation modes such as NVL and bubble dialogue needed to become first-class JVN runtime concepts.

## Audit Evidence

The audited teaser uses:

- custom `say`, `choice`, `main_menu`, `save`, `load`, `preferences`, `history`, `help`, `nvl`, and `bubble` screens
- extensive `gui.rpy` theme variables for dialogue box geometry, name placement, text alignment, choice layout, save slot layout, and typography

This means that "the story runs" is not enough. The migration bar is "the team's existing UI instincts map cleanly onto JVN".

## Patch Areas

## Screen-By-Screen Patch Matrix

This is the concrete patch list for the Ren'Py screen families observed in the teaser.

### `say`

Current JVN surface:

- `dialogue.layout`
- [`VnRenderer.java`](../../../fx/src/main/java/com/jvn/fx/vn/VnRenderer.java)
- [`DialogueLayoutEditorView.java`](../../../editor/src/main/java/com/jvn/editor/ui/DialogueLayoutEditorView.java)

Patch requirements:

- Add horizontal text alignment controls for name, dialogue, and choice text.
- Add stronger typography controls parity with common Ren'Py `gui.*` usage.
- Add explicit title/name alignment docs mapping from Ren'Py `xalign`.
- Keep dialogue presentation fully data-driven through layout/style files.

Status:

- started

### `choice`

Current JVN surface:

- `dialogue.layout` choice section
- [`choice-buttons.md`](../../scripting/ui/layout/components/choice-buttons.md)
- [`VnRenderer.java`](../../../fx/src/main/java/com/jvn/fx/vn/VnRenderer.java)

Patch requirements:

- Add text alignment parity for padded choice content.
- Add more per-state control over typography, spacing, and border styling.
- Ensure layout editor preview matches runtime choice alignment and spacing exactly.

Status:

- started

### `main_menu`

Current JVN surface:

- menu profiles
- menu layouts
- menu styles

Patch requirements:

- Add a documented Ren'Py `main_menu` to JVN menu-profile mapping.
- Expose title alignment, subtitle spacing, footer/hint placement, and hero-art composition patterns in the menu DSL.
- Ensure first-run project templates include a polished menu baseline that can be themed without code edits.

Implemented so far:

- `titleAlign`, `hintsAlign`, and `hintsX` in [`MenuLayoutSpec.java`](../../../core/src/main/java/com/jvn/core/menu/config/MenuLayoutSpec.java)
- `subtitleText` on menu screens and `subtitleGap` on menu layouts
- runtime title/footer placement in [`MenuRenderer.java`](../../../fx/src/main/java/com/jvn/fx/menu/MenuRenderer.java)
- editor support in [`MenuLayoutVisualEditor.java`](../../../editor/src/main/java/com/jvn/editor/ui/MenuLayoutVisualEditor.java)

Status:

- started

### `save` / `load`

Current JVN surface:

- menu save/load screens
- save/load routing in [`FxLauncher.java`](../../../fx/src/main/java/com/jvn/fx/FxLauncher.java)
- preview save/load routing in [`VnPreviewView.java`](../../../editor/src/main/java/com/jvn/editor/ui/VnPreviewView.java)

Patch requirements:

- Remove renderer-owned save/load overlay rendering for VN mode.
- Route save/load presentation through the same menu/layout/style system as standalone menu screens.
- Expose slot title, timestamp, metadata, hint/footer, and empty-slot styling as data, not constants.

Status:

- completed

### `preferences`

Current JVN surface:

- settings menu support
- settings-screen docs

Patch requirements:

- Expand spacing, alignment, and section-title controls so settings screens can match heavily customized Ren'Py layouts.
- Add a concrete migration example from Ren'Py preference widgets to JVN settings menu definitions.

Implemented so far:

- shared menu title/footer alignment controls from the main-menu slice
- section-header rendering via `item.<id>.renderAs=section` or `role=section` in [`MenuRenderer.java`](../../../fx/src/main/java/com/jvn/fx/menu/MenuRenderer.java)
- updated examples in [`settings-screen.md`](../../scripting/ui/layout/screens/settings-screen.md)
- static text blocks via `renderAs=body` / `paragraph` / `note`
- variable-height rows via `rowSpan` / `rows`

Status:

- started

### `history`

Current JVN surface:

- history runtime support
- themed history scene in [`HistoryMenuScene.java`](../../../core/src/main/java/com/jvn/core/menu/HistoryMenuScene.java)
- history rendering in [`MenuRenderer.java`](../../../fx/src/main/java/com/jvn/fx/menu/MenuRenderer.java)

Patch requirements:

- Replace hardcoded overlay rendering with a themed screen/profile.
- Expose entry spacing, page title alignment, background card styling, scrollbar appearance, and footer hint layout.
- Ensure history works both as an overlay and as a menu-style screen.

Status:

- completed

### `help`

Current JVN surface:

- menu system can host static/help screens

Patch requirements:

- Provide a documented canonical help/profile pattern for VN projects.
- Add consistent title/body/footer layout options so teams are not forced into ad hoc menu hacks.

Implemented so far:

- wrapped static text blocks via `renderAs=body` / `note`
- variable-height rows for auto-laid-out help content
- canonical help-screen example in [`help-screen.md`](../../scripting/ui/layout/screens/help-screen.md)

Status:

- started

### `nvl`

Current JVN surface:

- none as a first-class presentation mode

Patch requirements:

- Add runtime NVL mode with stacked dialogue entries.
- Add layout DSL for speaker column, body column, entry gap, page size, and background styling.
- Add editor preview support.

Status:

- completed

### `bubble`

Current JVN surface:

- none as a first-class presentation mode

Patch requirements:

- Add runtime bubble dialogue mode with anchor presets and per-speaker placement.
- Add bubble frame assets, text padding, pointer placement, and follow-target behavior.
- Add authoring and preview tools in the editor.

Status:

- completed

### `gui.rpy` Variable Migration

Current JVN surface:

- dialogue layout DSL
- menu layout/style DSL

Patch requirements:

- Add a migration table from high-value Ren'Py `gui.*` variables to JVN keys.
- Keep the focus on direct runtime/editor parity, not a one-off audit importer.
- Expand automated coverage so the current dialogue/menu layout DSL remains a concrete support target for common Ren'Py `gui.*` values.

Status:

- not started

### 1. Dialogue Layout DSL Parity

Patch the VN dialogue layout/style system so it covers the same day-to-day knobs Ren'Py teams use in `gui.rpy`.

Required patches:

- Add text alignment controls that mirror Ren'Py `xalign` semantics.
  - `nameTextXAlign`
  - `dialogueTextXAlign`
  - `choiceTextXAlign`
- Add stronger mapping docs from Ren'Py `gui.*` variables to JVN `.layout` keys.
- Preserve unknown-but-supported style keys when editing through the Dialogue Layout Editor.

Status:

- `in progress`
- First slice implemented in this batch: `nameTextXAlign`, `dialogueTextXAlign`, `choiceTextXAlign`

Files involved:

- [`VnUiStyleSpec.java`](../../../core/src/main/java/com/jvn/core/vn/ui/VnUiStyleSpec.java)
- [`VnUiLayoutLoader.java`](../../../core/src/main/java/com/jvn/core/vn/ui/VnUiLayoutLoader.java)
- [`VnRenderer.java`](../../../fx/src/main/java/com/jvn/fx/vn/VnRenderer.java)
- [`DialogueLayoutEditorView.java`](../../../editor/src/main/java/com/jvn/editor/ui/DialogueLayoutEditorView.java)

### 2. Data-Driven Runtime Screen Parity

JVN still renders some VN UX as fixed overlays in code. Ren'Py teams expect these to be themeable screens.

Required patches:

- Replace hardcoded history overlay rendering with a data-driven screen/profile.
- Replace hardcoded save-slot overlay rendering with the same menu/layout/style system used by save/load menus.
- Expose screen-local typography, spacing, and title/hint alignment instead of relying on renderer constants.

Current hardcoded targets:

- history overlay in [`VnRenderer.java`](../../../fx/src/main/java/com/jvn/fx/vn/VnRenderer.java)
- save/load slot overlay in [`VnRenderer.java`](../../../fx/src/main/java/com/jvn/fx/vn/VnRenderer.java)

### 3. Alternate Dialogue Presentation Modes

Ren'Py teams use more than one dialogue presentation model.

Required patches:

- Add first-class NVL presentation mode.
  - stacked entries
  - configurable list length
  - name/text column geometry
- Add first-class bubble dialogue mode.
  - bubble anchor presets
  - per-speaker bubble placement rules
  - bubble frame assets / style controls

This must be a runtime concept, not an ad hoc script hack.

### 4. Menu Screen Parity

Main menu, save, load, preferences, help, and history should feel like one configurable screen family.

Required patches:

- Normalize title, hints, slot list, section list, and footer styling across menu screens.
- Add missing settings/help/history presentation controls where the current menu/style DSL is thinner than the dialogue layout DSL.
- Document a Ren'Py `screens.rpy` to JVN menu-profile migration path.

Relevant existing surface:

- [`menu-profiles.md`](../../scripting/ui/menus/menu-profiles.md)
- [`menu-screens.md`](../../scripting/ui/menus/menu-screens.md)
- [`save-load-screens.md`](../../scripting/ui/layout/screens/save-load-screens.md)
- [`settings-screen.md`](../../scripting/ui/layout/screens/settings-screen.md)

### 5. Optional Migration Tooling

Manual translation from `gui.rpy` and `screens.rpy` is useful, but it is not required for functional parity.

Required patches:

- Add a Ren'Py UI audit/import tool in the editor.
- Parse common `gui.rpy` variables and generate:
  - `config/ui/dialogue.layout`
  - `config/menu/layouts/*.layout`
  - `config/menu/styles/*.style`
  - `config/menu/menus/*.menu`
- Emit diagnostics for unsupported Ren'Py constructs rather than failing silently.

This should be a migration assistant, not a full transpiler.

Status:

- deferred

## Recommended Execution Order

1. Dialogue Layout DSL parity
2. History/save overlay conversion to data-driven screens
3. Menu screen parity cleanup
4. NVL mode
5. Bubble mode
6. Optional Ren'Py UI import assistant

This order is deliberate:

- It starts with the smallest high-value mapping layer.
- It then removes the biggest hardcoded UI surfaces.
- It leaves alternate modes until the main layout/config foundation is stronger.
- Import tooling is optional once the runtime/editor surface matches in functionality.

## Started In This Batch

Implemented:

- `nameTextXAlign`
- `dialogueTextXAlign`
- `choiceTextXAlign`

Why this first:

- It maps directly to common Ren'Py `gui.rpy` usage.
- It fits JVN's current layout DSL cleanly.
- It improves migration fidelity without introducing a second UI system.

## Out Of Scope For This Document

This roadmap is specifically about screen/layout parity.

It does not cover:

- phone/chat runtime parity
- ATL/Puppeteer animation parity
- layered image migration

Those are separate migration tracks and should stay separate so the implementation order remains coherent.

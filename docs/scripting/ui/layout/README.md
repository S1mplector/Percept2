# In-Game UI And Menu Authoring

JVN's in-game interface is authored as plain-text, versionable property files. The runtime and
visual editors consume the same files; there is no editor-only menu format.

## Choose The File You Need

| You want to change | File | Primary guide |
|---|---|---|
| Dialogue panel, choices, NVL, bubbles, or textbox actions | `config/ui/dialogue.layout` | [Dialogue layout](components/dialogue-layout.md) |
| Menu screen contents and navigation | `config/menu/menus/<id>.menu` | [Menu actions](structure/menu-actions.md) |
| Menu positioning and spacing | `config/menu/layouts/<id>.layout` | [Menu layouts](structure/menu-layouts.md) |
| Colors, type, backgrounds, and button assets | `config/menu/styles/<id>.style` | [Colors and theming](styling/colors-theming.md) |
| Menu discovery and the initial screen | `config/menu/registry/menu.registry` | [Menu registry](structure/menu-registry.md) |

All five formats use Java properties syntax: one `key=value` declaration per line, with `#` for
comments. Paths are project-relative and should use forward slashes.

## How A Menu Is Resolved

```text
menu.registry
    selects a .menu screen
        ├── layout=<id>        → layouts/<id>.layout
        ├── defaultItemStyle   → styles/<id>.style
        └── item.*.action      → runtime action or another menu target
```

A screen contains content and behavior, a layout contains geometry, and a style contains visual
presentation. Keeping those responsibilities separate lets several screens share one layout or
theme without copying configuration.

## Recommended Workflow

1. Open **Layout Editors** in the editor sidebar.
2. Resolve any registry or cross-file warnings shown on its cards.
3. Open a screen, layout, style, or dialogue file in **Layout Studio**.
4. Work in code, visual, or split mode. Both surfaces edit the same DSL text.
5. Use drag handles, Bounds Studio, asset pickers, and presets for spatial work.
6. Save and run the project to verify navigation, input, font availability, and final rendering.

The visual previews are suitable for authoring geometry, color, bounds, and assets. The running
game remains authoritative for behavior: actions, screen transitions, responsive behavior, and
platform font rendering.

## Minimal Working Menu

Register one screen, layout, and style:

```properties
# config/menu/registry/menu.registry
defaultMenu=main
menus=main
layouts=default
styles=default
```

Define its content and actions:

```properties
# config/menu/menus/main.menu
titleText=My Game
layout=default
defaultItemStyle=default
items=start,settings,quit
item.start.label=Start
item.start.action=start
item.settings.label=Settings
item.settings.action=settings_menu
item.quit.label=Quit
item.quit.action=quit
```

Keep geometry and presentation independent:

```properties
# config/menu/layouts/default.layout
listYStart=0.40
lineHeight=52
listWidthFactor=0.32
listXCenter=0.78
textAlign=left
```

```properties
# config/menu/styles/default.style
itemColor=#333333
itemSelectedColor=#000000
itemDisabledColor=#999999
itemSelectedPrefix=—
itemFontFamily=SansSerif
itemFontSize=24
backgroundAsset=assets/menu/background.png
```

The exact accepted action names and target requirements are documented in
[Menu Actions](structure/menu-actions.md).

## Contract And Diagnostics

The runtime loader defines accepted keys, ranges, inheritance, and fallback behavior. Layout Studio
uses those loaders where possible and supplements them with line-aware diagnostics and cross-file
checks. Diagnostics include duplicate or unknown keys, malformed values, missing assets, missing
registry entries, unresolved layout/style references, and navigation targets that do not exist.

Do not rely on implicit clamping as an authoring technique. If the editor reports that a value was
adjusted, write the effective in-range value into the file.

## Visual Editors

- **Dialogue Layout Editor** — standard/NVL/bubble previews, draggable and resizable regions,
  choice styling, textbox buttons, runtime preview, and undoable Standard VN or Minimal Monochrome
  presets.
- **Menu Screen Editor** — ordered items, built-in actions, targets, enabled states, per-item bounds,
  save/load slot fields, and interactive selection preview.
- **Menu Layout Editor** — title/list/hint positioning, alignment, spacing, scrolling, and drag-based
  geometry editing, with Standard and Minimal Right-Side presets.
- **Menu Style Editor** — typography, state colors, opacity, shadows, backgrounds, button assets,
  and undoable Standard or Minimal Monochrome presets.
- **Unified Menu Editor** — the screen, referenced layout, and referenced style in one workspace.
- **Bounds Studio** — precise rectangles and polygon bounds over reference artwork.

See [Layout Editor Tools](tooling/layout-editor-tools.md) for the complete editor workflow.

## Common Tasks

| Task | Change |
|---|---|
| Move all menu entries | `listYStart` or `listXCenter` in the referenced `.layout` |
| Change spacing | `lineHeight` in the referenced `.layout` |
| Change the selected state | `itemSelectedColor`, prefix, or selected asset in `.style` |
| Open another screen | `item.<id>.action=open_menu` plus `item.<id>.target=<screen>` |
| Add a screen | Create `.menu`, register its id, and point an action target to it |
| Put choices beside a character | Change `choiceXCenter` and `choiceWidthFactor` in `dialogue.layout` |
| Add quick save/history controls | Add `textBoxButton.ids` and per-button fields in `dialogue.layout` |
| Use custom artwork as buttons | Set normal/hover/selected/disabled asset paths in `.style` |

## Failure Checklist

If a menu does not appear or an edit seems ignored:

1. Confirm the screen, layout, and style IDs are present in `menu.registry`.
2. Confirm `layout=` and `defaultItemStyle=` refer to existing files with matching IDs.
3. Check Layout Editors for orange cross-file warnings.
4. Check the file for duplicate keys; Java properties uses the last declaration.
5. Use forward slashes and project-relative paths for assets.
6. Save every participating file before running the project.
7. Test navigation in runtime; a visual preview does not execute actions.

## Reference

- [Text-first workflow](workflow/text-first-layout-workflow.md)
- [Complete DSL cookbook](reference/layout-dsl-cookbook.md)
- [Validation and diagnostics](tooling/validation-diagnostics.md)
- [Choice buttons](components/choice-buttons.md)
- [Textbox action buttons](components/textbox-action-buttons.md)
- [Inheritance](structure/menu-inheritance.md)
- [Assets and backgrounds](styling/assets-backgrounds.md)
- [Save and load screens](screens/save-load-screens.md)
- [Settings screen](screens/settings-screen.md)

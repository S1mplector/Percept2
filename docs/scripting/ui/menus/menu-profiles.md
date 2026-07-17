# Menu Profiles

Menu profiles are JVN's data-driven menu system for main/load/save/settings/custom menu screens, plus the dialogue UI layout system. Everything is defined in plain properties files — no code changes needed to customize menus and dialogue presentation.

Core classes:
- `modules/core/src/main/java/com/jvn/core/menu/config/MenuProfile.java`
- `modules/core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`
- `modules/core/src/main/java/com/jvn/core/menu/config/MenuProfileValidator.java`
- `modules/core/src/main/java/com/jvn/core/vn/ui/VnUiLayoutLoader.java`

---

## Who This Is For

Use menu profiles if you want to build or restyle:

- main menus
- load/save/settings screens
- custom menu flows
- dialogue UI layout and textbox presentation

This is the right system when you want project-specific UI without writing Java code.

## What You Will Learn

This page gives you the beginner map for:

- which files make up a menu profile
- how screens, layouts, styles, and dialogue UI fit together
- what a minimal working config looks like
- which deeper docs to read for structure vs appearance vs tooling

## Read This Next

- New to JVN overall: [Choose Your Path in JVN](../../../guides/choose-your-path.md)
- Need file-level orientation: [Common JVN File Types](../../../guides/common-file-types.md)
- Want the practical workflow first: [Text-First Layout Workflow](../layout/workflow/text-first-layout-workflow.md)

---

## Sub-Document Reference

### Menu System

- **[Menu Screens](menu-screens.md)** — `.menu` files, item declarations, actions, navigation, bounds, slot previews, inheritance, custom actions
- **[Reactive Overlay Screens](reactive-screens.md)** — `.screen` overlays driven by `VnState` variables and VNS `[screen show/call]`
- **[Menu Layouts](../layout/structure/menu-layouts.md)** — `.layout` files, list positioning, line height, text alignment, title placement, built-in layouts
- **[Menu Styles](menu-styles.md)** — `.style` files, item colors/fonts/shadows, button skins, title/hints styling, backgrounds
- **[Button Layouts](../layout/structure/menu-button-layouts.md)** — per-button positional layouts, explicit bounds, and resolution hints

### Dialogue UI

- **[Dialogue Layout & Style](../layout/components/dialogue-layout.md)** — textbox geometry, name box, dialogue text, choice buttons, textbox action buttons, fonts, colors, character framing

---

## Directory Structure

```text
config/
├── menu/
│   ├── registry/menu.registry      # Registry: declares IDs and defaults
│   ├── menus/
│   │   ├── main.menu               # Main menu screen
│   │   ├── load.menu               # Load game screen
│   │   ├── save.menu               # Save game screen
│   │   ├── settings.menu           # Settings screen
│   │   └── extras.menu             # Custom screens
│   ├── layouts/
│   │   ├── default.layout          # Default list layout
│   │   └── slots.layout            # Save/load slot layout
│   ├── styles/
│   │   ├── default.style           # Default visual style
│   │   └── neon.style              # Custom style
│   └── buttons/
│       └── main_buttons.properties # Button layout for main menu
└── ui/
    └── dialogue.layout             # Dialogue textbox layout + style
```

---

## Quick Start

### 1. Registry

```properties
# config/menu/registry/menu.registry
defaultMenu=main
menus=main,load,save,settings
layouts=default,slots
styles=default
```

### 2. Screen

```properties
# config/menu/menus/main.menu
titleText=My Game
hintsText=↑↓ Navigate    Enter Select    Esc Quit
layout=default
defaultItemStyle=default
items=start,load,settings,quit

item.start.label=New Game
item.start.action=run_script:scripts/story/prologue.vns

item.load.label=Load Game
item.load.action=load_menu

item.settings.label=Settings
item.settings.action=settings_menu

item.quit.label=Quit
item.quit.action=quit
```

### 3. Layout

```properties
# config/menu/layouts/default.layout
listYStart=0.34
lineHeight=68
listWidthFactor=0.44
textAlign=center
hintsBottomMargin=36
titleY=0.14
```

### 4. Style

```properties
# config/menu/styles/default.style
itemColor=#DCE6F8
itemSelectedColor=#FFE8A3
itemDisabledColor=#7D8CA8
itemSelectedPrefix=▶ 
itemFontFamily=Segoe UI
itemFontSize=28
titleColor=#F2F7FF
titleFontSize=56
backgroundAsset=assets/backgrounds/title.png
```

---

## Loader Discovery

`MenuProfileLoader` loads configuration in this order:

1. **Registry** — `menu.registry` (declares IDs and default screen)
2. **Declared IDs** — menus/layouts/styles listed in registry
3. **Discovered files** — auto-discovered from config directories
4. **Fallback defaults** — built-in `MenuProfile.defaults()`

Registry search paths:
- `config/menu/registry/menu.registry`
- `config/menu/menu.registry`
- `config/menu/registry.properties`
- `menu.registry`

---

## Inheritance

All three file types support `extends=<parentId>`:

```properties
# config/menu/styles/neon_soft.style
extends=neon
itemSelectedColor=#8CFF66
```

Only explicitly set properties override the parent. Circular inheritance is detected and reported.

---

## Action Type Reference

| Type | Aliases | Description |
|------|---------|-------------|
| `NEW_GAME` | `new`, `start`, `start_game` | Start a new VN game |
| `LOAD_MENU` | `load`, `continue` | Navigate to load screen |
| `SAVE_MENU` | `save` | Navigate to save screen |
| `SETTINGS_MENU` | `settings`, `options` | Navigate to settings |
| `MAIN_MENU` | `main`, `title` | Return to main menu |
| `OPEN_MENU` | `submenu`, `menu` | Open a named sub-menu |
| `RUN_SCRIPT` | `script`, `play_script` | Run a VNS script |
| `BACK` | `return` | Go back to previous screen |
| `QUIT` | `exit` | Exit the application |
| `NOOP` | `none` | No action |

---

## Validation

```java
List<String> issues = MenuProfileValidator.validate(profile);
```

Detects: missing default screen, empty screens, unknown layout/style refs, `OPEN_MENU` with missing target, `RUN_SCRIPT` without target.

---

## Editor Support

JVN's Layout Studio edits the runtime source formats directly:

| File type | Surface | Features |
|-----------|--------|----------|
| `.menu` | Layout Studio | Source template, item diagnostics, asset helpers |
| `.layout` | Layout Studio | Geometry template and range diagnostics |
| `.style` | Layout Studio | Style template and asset helpers |
| `.buttonlayout` | Source editor | Explicit normalized or pixel bounds |
| `dialogue.layout` | Layout Studio | Runtime-loader diagnostics and dialogue templates |
| `menu.registry` | Layout Editors sidebar/source editor | Cross-file validation and direct properties editing |

Layout Studio supports source undo/redo and writes the properties text atomically.

---

## Recommended Authoring Pattern

1. Start with `default` layout and style.
2. Define `main`, `load`, `save`, `settings` screens first.
3. Add custom screens (`extras`, `credits`) using `OPEN_MENU`.
4. Use `extends` for style variants instead of duplicating.
5. Keep action targets explicit and stable.
6. Validate profile before release.

---

## Related Docs

- [Documentation Index](../../../INDEX.md)
- [VNS Scripting](../../vns/overview/vns-scripting.md) — runtime story flow
- [Editor Guide](../../../editor/core/editor.md) — editing modes
- [Title Screen & Menu Presentation](../../../project-setup/content/title-screen.md)

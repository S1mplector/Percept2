# Menu Profiles

Menu profiles are JVN's data-driven menu system for main/load/save/settings/custom menu screens, plus the dialogue UI layout system. Everything is defined in plain properties files — no code changes needed to customize menus and dialogue presentation.

Core classes:
- `core/src/main/java/com/jvn/core/menu/config/MenuProfile.java`
- `core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`
- `core/src/main/java/com/jvn/core/menu/config/MenuProfileValidator.java`
- `core/src/main/java/com/jvn/core/vn/ui/VnUiLayoutLoader.java`

---

## Sub-Document Reference

### Menu System

- **[Menu Screens](menu-screens.md)** — `.menu` files, item declarations, actions, navigation, bounds, slot previews, inheritance, custom actions
- **[Menu Layouts](menu-layouts.md)** — `.layout` files, list positioning, line height, text alignment, title placement, built-in layouts
- **[Menu Styles](menu-styles.md)** — `.style` files, item colors/fonts/shadows, button skins, title/hints styling, backgrounds
- **[Button Layouts](menu-button-layouts.md)** — per-button positional layouts, explicit bounds, resolution hints, Bounds Studio editor

### Dialogue UI

- **[Dialogue Layout & Style](dialogue-layout.md)** — textbox geometry, name box, dialogue text, choice buttons, textbox action buttons, fonts, colors, character framing

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

JVN editor provides dedicated visual editors for all config types:

| File type | Editor | Features |
|-----------|--------|----------|
| `.menu` | Menu Screen Visual Editor | Item table, action combos, bounds inspector, slot preview config |
| `.layout` | Menu Layout Visual Editor | Slider controls, live preview, dynamic item count |
| `.style` | Menu Style Visual Editor | ColorPickers, font selectors, asset pickers |
| `.buttonlayout` | Bounds Studio | Visual drag/draw button placement tool |
| `dialogue.layout` | Dialogue Layout Editor | Collapsible sections, resize handles, Bounds Studio |
| `menu.registry` | Inline Registry Editor | TextField editing with validation |

All editors support **Ctrl+Z / Ctrl+Y** undo/redo and sync bidirectionally with properties text.

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

- [Documentation Index](../INDEX.md)
- [VNS Scripting](../scripting/vns/vns-scripting.md) — runtime story flow
- [Editor Guide](../editor/editor.md) — editing modes
- [Title Screen & Menu Presentation](../project-setup/title-screen.md)

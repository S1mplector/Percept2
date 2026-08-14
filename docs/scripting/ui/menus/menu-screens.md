# Menu Screens

Complete reference for defining menu screens — the `.menu` properties format, item declarations, actions, navigation, bounds, slot previews, and inheritance.

Model: `modules/core/src/main/java/com/jvn/core/menu/config/MenuScreenSpec.java`
Items: `modules/core/src/main/java/com/jvn/core/menu/config/MenuItemSpec.java`
Actions: `modules/core/src/main/java/com/jvn/core/menu/config/MenuActionSpec.java`
Loader: `modules/core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`

---

## Overview

A menu screen defines one navigable page in the JVN menu system (main menu, load, save, settings, or custom screens). Each screen has a title, hint text, item list, layout reference, and style reference.

---

## File Location

Screens are stored as `.menu` files:

```text
config/menu/menus/main.menu
config/menu/menus/load.menu
config/menu/menus/save.menu
config/menu/menus/settings.menu
config/menu/menus/extras.menu
```

Also recognized:

```text
config/menu/main.menu
config/menu/main.properties
```

---

## Screen Properties

```properties
titleText=My Game
subtitleText=A quieter supporting line
hintsText=Select: Enter    Back: Esc
layout=default
defaultItemStyle=default
wrapSelection=true
# backgroundAsset=assets/backgrounds/my_screen_bg.png
```

| Property | Default | Description |
|----------|---------|-------------|
| `titleText` | — | Title displayed at top of screen |
| `subtitleText` | — | Optional subtitle/tagline shown below the title |
| `hintsText` | — | Hint text at bottom of screen |
| `layout` / `layoutId` | `"default"` | Layout ID for positioning |
| `defaultItemStyle` | `"default"` | Default style ID for items |
| `wrapSelection` | true | Wrap cursor from last to first item and vice versa |
| `backgroundAsset` | — | Per-screen background image. Overrides the style-level background when set. |

### Per-Screen Backgrounds

When `backgroundAsset` is set on a screen, it takes priority over the style’s
`backgroundAsset` and `backgroundColor`. This lets different screens share the same
style (fonts, colors) while having unique backgrounds:

```properties
# main.menu — custom title screen background
backgroundAsset=assets/backgrounds/title_screen.png

# settings.menu — different background
backgroundAsset=assets/backgrounds/settings_bg.png

# extras.menu — no backgroundAsset, falls back to style’s background
```

Priority: screen `backgroundAsset` > style `backgroundAsset` > style `backgroundColor` > engine default.

---

## Item Declarations

Items are the selectable entries on the screen. Declare them with a CSV `items` list:

```properties
items=start,load,settings,quit
```

Then define each item with the `item.<id>.` prefix:

```properties
item.start.label=New Game
item.start.action=new_game

item.load.label=Load Game
item.load.action=load_menu

item.settings.label=Settings
item.settings.action=settings_menu

item.quit.label=Quit
item.quit.action=quit
```

If `items` is omitted, IDs are auto-discovered from `item.<id>.*` keys.

### Item Properties

| Property | Default | Description |
|----------|---------|-------------|
| `label` | — | Display text |
| `style` | screen's `defaultItemStyle` | Style ID for this item |
| `icon` | — | Icon asset path |
| `enabled` | true | Whether the item is selectable |
| `action` | `"noop"` | Action to execute on selection |
| `target` | — | Action target (e.g., script path, menu ID) |
| `bgAsset` | — | Normal background image |
| `bgSelectedAsset` | — | Selected state background |
| `bgDisabledAsset` | — | Disabled state background |
| `boundsX` | — | Hit area left (fraction ≤1, pixels >1) |
| `boundsY` | — | Hit area top |
| `boundsWidth` | — | Hit area width |
| `boundsHeight` | — | Hit area height |
| `fontFamily` | — | Per-item font family override (e.g., `Georgia`, `Segoe UI`) |
| `fontWeight` | — | Per-item font weight override (`NORMAL`, `BOLD`, `SEMI_BOLD`) |
| `fontSize` | — | Per-item font size override (positive integer) |

### Per-Item Font Overrides

Individual items can override the style’s font properties. Each field is independent —
you can set just `fontSize` and inherit `fontFamily` and `fontWeight` from the style.

```properties
# Section header in a distinct font
item.header.label=── Audio Settings ──
item.header.action=noop
item.header.enabled=false
item.header.fontWeight=BOLD
item.header.fontSize=18

# Standard item inherits the style font
item.bgm_vol.label=BGM Volume: {value}
```

Resolution order: per-item field > style field > engine default.

### Extras

Any `item.<id>.<key>` property not in the known set is preserved as an **extra** and accessible at runtime via `MenuItemSpec.extras()`. This enables custom data without modifying the spec.

### Static Text Blocks

For help screens, lore pages, disclaimers, and explanatory settings text, items can render as
non-button text blocks instead of selectable buttons.

Supported render modes:

| Extra | Effect |
|-------|--------|
| `renderAs=section` | Divider/header row |
| `renderAs=body` | Wrapped paragraph block |
| `renderAs=paragraph` | Alias for `body` |
| `renderAs=text` | Alias for `body` |
| `renderAs=note` | Wrapped paragraph inside a subtle card |
| `renderAs=card` | Alias for `note` |

Useful body extras:

| Extra | Description |
|-------|-------------|
| `rowSpan` / `rows` | Number of menu rows this block occupies |
| `bodyAlign` | `left`, `center`, or `right` |
| `bodyPaddingX` | Horizontal padding inside the text block |
| `bodyPaddingY` | Vertical padding inside the text block |
| `bodyLineHeight` | Explicit line height in pixels |

Example:

```properties
item.controls_header.label=Controls
item.controls_header.action=noop
item.controls_header.enabled=false
item.controls_header.renderAs=section

item.controls_body.label=Click or press Enter to advance dialogue. Esc backs out of menus. F5 saves and F9 loads during gameplay.
item.controls_body.action=noop
item.controls_body.enabled=false
item.controls_body.renderAs=body
item.controls_body.rowSpan=3
item.controls_body.bodyAlign=left
item.controls_body.bodyPaddingY=6
```

Notes:
- Text blocks are non-interactive and excluded from hover hit-testing.
- `rowSpan` only affects auto-laid-out items. Explicit `boundsX/Y/Width/Height` still win.
- Use per-item `fontFamily`, `fontWeight`, and `fontSize` to give body blocks their own typography.

---

## Actions

### Action Syntax

Actions can be specified two ways:

**Split fields:**

```properties
item.start.action=run_script
item.start.target=scripts/story/prologue.vns
```

**Shorthand (colon separator):**

```properties
item.start.action=run_script:scripts/story/prologue.vns
```

### Action Types

| Type | Aliases | Description |
|------|---------|-------------|
| `NEW_GAME` | `new`, `new_game`, `start`, `start_game` | Start a new VN game |
| `LOAD_MENU` | `load`, `load_menu`, `continue` | Navigate to load screen |
| `SAVE_MENU` | `save`, `save_menu` | Navigate to save screen |
| `SETTINGS_MENU` | `settings`, `settings_menu`, `options` | Navigate to settings |
| `HISTORY_MENU` | `history`, `history_menu`, `backlog`, `toggle_history` | Open dialogue history |
| `TOGGLE_SKIP` | `skip`, `toggle_skip` | Toggle skip playback |
| `TOGGLE_AUTO` | `auto`, `toggle_auto`, `auto_play` | Toggle auto playback |
| `MAIN_MENU` | `main`, `main_menu`, `title`, `title_menu` | Return to main menu |
| `OPEN_MENU` | `open_menu`, `submenu`, `menu` | Open a named sub-menu |
| `RUN_SCRIPT` | `run_script`, `script`, `start_script`, `play_script` | Run a VNS script |
| `BACK` | `back`, `return` | Go back to previous screen |
| `QUIT` | `quit`, `exit` | Exit the application |
| `GALLERY` | `gallery`, `cg`, `cg_gallery` | Open the built-in CG gallery |
| `MUSIC_ROOM` | `music`, `music_room`, `sound_room`, `jukebox` | Open the built-in music room |
| `NOOP` | `noop`, `no_op`, `none` | No action |

### Custom Actions

Unknown action strings are preserved as `NOOP` type but with the raw action key accessible via `MenuActionSpec.actionKey()`. Use `isCustomAction()` to detect them:

```properties
item.credits.action=show_credits
item.credits.target=credits_scene
```

Register a custom handler at runtime to respond:

```java
engine.setMenuActionHandler((actionKey, target) -> {
    if ("show_credits".equals(actionKey)) {
        showCreditsScene(target);
        return true; // handled
    }
    return false; // let default handling continue
});
```

---

## Item Bounds

For custom button placement (instead of list-based layout), define bounds per item:

```properties
item.start.boundsX=0.18
item.start.boundsY=0.40
item.start.boundsWidth=0.52
item.start.boundsHeight=0.10
```

### Coordinate Rules

- Values **≤ 1.0** are treated as **fractions** of the menu draw area
- Values **> 1.0** are treated as **absolute pixels**
- All four bounds must be set together

### Polygon Hit-Test

For non-rectangular clickable shapes:

```properties
item.start.boundsPoints=0.00,0.30;0.08,0.00;0.92,0.00;1.00,0.30;1.00,1.00;0.00,1.00
```

Points are normalized (0–1) relative to the item's bounding rectangle. Requires at least 3 points.

---

## Save/Load Slot Previews

For save/load screens, items can display inline screenshot thumbnails:

```properties
item.save_slot.slotPreviewEnabled=true
item.save_slot.slotPreviewPlaceholderAsset=assets/ui/slot_placeholder.png
item.save_slot.slotPreviewFrameAsset=assets/ui/slot_frame.png
item.save_slot.slotPreviewX=0.02
item.save_slot.slotPreviewY=0.05
item.save_slot.slotPreviewWidth=0.25
item.save_slot.slotPreviewHeight=0.90
```

| Property | Description |
|----------|-------------|
| `slotPreviewEnabled` | Enable inline thumbnail preview |
| `slotPreviewPlaceholderAsset` | Image when no save screenshot exists |
| `slotPreviewFrameAsset` | Frame overlay on top of the thumbnail |
| `slotPreviewX/Y/Width/Height` | Preview region within the item bounds |

### Template Item IDs

Save/load screens use special template item IDs:

- **Load screen:** `save_slot`, `slot`, `entry` — template for each save row
- **Save screen:** `new_slot`, `new_save`, `new` — the "create new save" row; existing saves use `save_slot`

---

## Inheritance

Screens support `extends` for inheriting from a parent screen:

```properties
# config/menu/menus/extras.menu
extends=main
titleText=Extras
items=gallery,music,credits

item.gallery.label=Gallery
item.gallery.action=show_gallery

item.music.label=Music Player
item.music.action=show_music

item.credits.label=Credits
item.credits.action=show_credits
```

Only explicitly set properties override the parent. Layout, style, items, and all other fields fall back to the parent.

---

## Complete Example: Main Menu

```properties
# config/menu/menus/main.menu
titleText=Echoes of Time
hintsText=↑↓ Navigate    Enter Select    Esc Quit
layout=default
defaultItemStyle=default
wrapSelection=true
backgroundAsset=assets/backgrounds/title_screen.png

items=new_game,continue,extras,settings,quit

item.new_game.label=New Game
item.new_game.action=run_script:scripts/story/prologue.vns
item.new_game.icon=assets/ui/icons/new.png
item.new_game.bgAsset=assets/ui/menu/btn_primary.png
item.new_game.bgSelectedAsset=assets/ui/menu/btn_primary_sel.png

item.continue.label=Continue
item.continue.action=load_menu
item.continue.icon=assets/ui/icons/load.png

item.extras.label=Extras
item.extras.action=open_menu:extras
item.extras.icon=assets/ui/icons/star.png

item.settings.label=Settings
item.settings.action=settings_menu
item.settings.icon=assets/ui/icons/gear.png

item.quit.label=Quit
item.quit.action=quit
item.quit.icon=assets/ui/icons/exit.png
```

## Complete Example: Load Menu

```properties
# config/menu/menus/load.menu
titleText=Load Game
hintsText=↑↓ Navigate    Enter Load    Esc Back
layout=compact
defaultItemStyle=default
wrapSelection=true

items=save_slot

item.save_slot.label=Empty Slot
item.save_slot.action=noop
item.save_slot.slotPreviewEnabled=true
item.save_slot.slotPreviewPlaceholderAsset=assets/ui/slot_empty.png
item.save_slot.slotPreviewFrameAsset=assets/ui/slot_frame.png
item.save_slot.slotPreviewX=0.02
item.save_slot.slotPreviewY=0.05
item.save_slot.slotPreviewWidth=0.22
item.save_slot.slotPreviewHeight=0.90
```

---

## Related Docs

- [Menu Profiles Overview](menu-profiles.md)
- [Menu Layouts](../layout/structure/menu-layouts.md)
- [Menu Styles](menu-styles.md)
- [Button Layouts](../layout/structure/menu-button-layouts.md)
- [Custom Layout Scenarios](../layout/tooling/custom-scenarios.md)
- [Dialogue Layout & Style](../layout/components/dialogue-layout.md)

# Menu Profiles

Menu profiles are JVN's data-driven menu system for main/load/save/settings/custom menu screens.

Core classes:
- model: `core/src/main/java/com/jvn/core/menu/config/MenuProfile.java`
- loader: `core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`
- validator: `core/src/main/java/com/jvn/core/menu/config/MenuProfileValidator.java`

## Why Use Menu Profiles

They separate menu behavior/presentation from code:

- define screen list and default screen
- define reusable layouts and styles
- define per-item actions and targets
- support inheritance via `extends`
- keep everything as plain properties files

## Directory Structure

```text
config/menu/
|-- registry/menu.registry
|-- theme/menu.theme
|-- menus/
|   |-- main.menu
|   |-- load.menu
|   |-- save.menu
|   `-- settings.menu
|-- layouts/
|   `-- default.layout
`-- styles/
    `-- default.style
```

## Loader Discovery Rules

`MenuProfileLoader` loads:

1. `menu.registry` (if present)
2. declared `menus/layouts/styles` IDs from registry
3. discovered files under config directories
4. fallback defaults from `MenuProfile.defaults()`

Supported registry path candidates include:
- `config/menu/registry/menu.registry`
- `config/menu/menu.registry`
- `config/menu/registry.properties`
- `menu.registry`

## `menu.registry` Reference

```properties
defaultMenu=main
menus=main,load,save,settings,extras
layouts=default,compact
styles=default,neon
```

Fields:
- `defaultMenu`: startup screen id
- `menus`: comma-separated menu IDs
- `layouts`: layout IDs
- `styles`: style IDs

## Screen Files (`*.menu`)

Example:

```properties
titleText=My Game
hintsText=Select: Enter    Back: Esc
layout=default
defaultItemStyle=default
wrapSelection=true
items=start,load,settings,quit

item.start.label=Start
item.start.action=run_script:scripts/story/prologue.vns

item.load.action=load_menu
item.settings.action=settings_menu
item.quit.action=quit
```

Supported item fields:
- `label`
- `style`
- `icon`
- `enabled`
- `action`
- `target`
- `bgAsset`
- `bgSelectedAsset`
- `bgDisabledAsset`
- `boundsX`
- `boundsY`
- `boundsWidth`
- `boundsHeight`
- `slotPreviewEnabled`
- `slotPreviewPlaceholderAsset`
- `slotPreviewFrameAsset`
- `slotPreviewX`
- `slotPreviewY`
- `slotPreviewWidth`
- `slotPreviewHeight`

`bounds*` mapping rules:
- values `<= 1` are treated as normalized fractions (relative to menu draw area)
- values `> 1` are treated as pixels
- all four `boundsX/Y/Width/Height` should be set together

`slotPreview*` mapping rules:
- used by save/load menu rows for inline thumbnail preview
- values `<= 1` are normalized to the menu row bounds
- values `> 1` are treated as pixels inside the row
- set all four `slotPreviewX/Y/Width/Height` together when overriding

### Action Parsing

Actions accept either:
- split fields: `action=run_script` + `target=scripts/story/prologue.vns`
- shorthand: `action=run_script:scripts/story/prologue.vns`

Action aliases are normalized by `MenuActionType.parse`.

## Layout Files (`*.layout`)

Example:

```properties
listYStart=0.35
lineHeight=40
listWidthFactor=1.0
textAlign=center
hintsBottomMargin=20
titleY=60
```

Fields map to `MenuLayoutSpec`:
- `listYStart`
- `lineHeight`
- `listWidthFactor`
- `textAlign`
- `hintsBottomMargin`
- `titleY` (optional override)

## Style Files (`*.style`)

Example:

```properties
# Item text colors
itemColor=#cccccc
itemSelectedColor=#ffd700
itemHoverColor=#ffe066
itemDisabledColor=#808080

# Item prefixes
itemPrefix=  
itemSelectedPrefix=> 
itemDisabledPrefix=- 

# Item font
itemFontFamily=Arial
itemFontWeight=BOLD
itemFontSize=20

# Item text effects
itemShadowColor=#00000088
itemShadowOffsetX=2
itemShadowOffsetY=2
itemOpacity=1.0

# Button skins (all four states)
buttonAsset=assets/ui/menu/button.png
buttonSelectedAsset=assets/ui/menu/button_selected.png
buttonHoverAsset=assets/ui/menu/button_hover.png
buttonDisabledAsset=assets/ui/menu/button_disabled.png
buttonTextPaddingX=18
buttonTextPaddingY=0

# Title styling
titleColor=#ffffff
titleFontFamily=Georgia
titleFontWeight=BOLD
titleFontSize=36
titleShadowColor=#000000

# Hints styling
hintsColor=#aaaaaa
hintsFontFamily=Arial
hintsFontSize=14

# Background
backgroundAsset=assets/ui/menu/bg.png
backgroundColor=#1a1a2e
backgroundOpacity=0.9
```

Fields map to `MenuStyleSpec`. All fields are optional and inherit from parent styles via `extends`.

## Inheritance (`extends`)

`*.menu`, `*.layout`, and `*.style` support `extends=<parentId>`.

Example:

```properties
# styles/neon_soft.style
extends=neon
itemSelectedColor=#8cff66
```

## Built-in Scene Integration

Menu profile data is consumed by:
- `MainMenuScene` (`main` and custom screen IDs)
- `LoadMenuScene` (`load`)
- `SaveMenuScene` (`save`)
- `SettingsScene` (`settings`)

This means all major menu scenes now share one config model.

## Runtime Action Parity Notes

Action handling is now consistent across `main`, `load`, `save`, and `settings` contexts:

- `OPEN_MENU` and `MAIN_MENU` push configured menu screens through `MainMenuScene`.
- `RUN_SCRIPT` and `NEW_GAME` start VN runtime scenes with current settings propagated.
- `LOAD_MENU` and `SETTINGS_MENU` can be triggered from non-main screens.
- `SAVE_MENU` can be triggered when an active `VnScene` exists (otherwise it is ignored safely).
- `QUIT` and `BACK` work in all menu contexts.

For load/save profiles, template item IDs are still important:
- load slot template action: `item.save_slot.*` (aliases: `slot`, `entry`)
- save slot template action: `item.new_slot.*` for the top "new save" row
- existing save rows use `item.save_slot.*` (aliases: `slot`, `entry`)

Per-item icon rendering:
- `item.<id>.icon=assets/ui/...` is now rendered by the FX menu renderer in all menu scenes.

Save/load inline preview rendering:
- `item.save_slot.slotPreviewEnabled=true` enables per-row embedded thumbnails.
- `item.new_slot.slotPreviewEnabled=true` enables preview on the save menu’s "new save" row.
- `slotPreviewPlaceholderAsset` is used when no sidecar thumbnail exists.
- `slotPreviewFrameAsset` overlays a frame skin over the thumbnail region.

## Action Type Reference

`MenuActionType` values:
- `NEW_GAME`
- `LOAD_MENU`
- `SAVE_MENU`
- `SETTINGS_MENU`
- `MAIN_MENU`
- `OPEN_MENU`
- `RUN_SCRIPT`
- `BACK`
- `QUIT`
- `NOOP`

## Validation

Use `MenuProfileValidator.validate(profile)` to detect:
- missing default screen
- empty screens
- unknown layout/style refs
- `OPEN_MENU` missing or unknown target
- `RUN_SCRIPT` without target

## Editor Support

JVN editor has dedicated visual editors for:
- `config/menu/menus/*.menu`
- `config/menu/layouts/*.layout`
- `config/menu/styles/*.style`

These editors sync to properties text and preserve extra keys where possible.

## Recommended Authoring Pattern

1. Start with one `default` layout and style.
2. Define `main/load/save/settings` screens first.
3. Add custom screens (`extras`, `credits`, etc.) using `OPEN_MENU`.
4. Keep action targets explicit and stable.
5. Validate profile in tests/tools before shipping.

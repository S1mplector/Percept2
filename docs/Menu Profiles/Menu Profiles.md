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
|-- menu.registry
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
itemColor=#cccccc
itemSelectedColor=#ffd700
itemDisabledColor=#808080
itemPrefix=  
itemSelectedPrefix=> 
itemDisabledPrefix=- 
itemFontFamily=Arial
itemFontWeight=BOLD
itemFontSize=20
```

Fields map to `MenuStyleSpec`.

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

These editors sync to properties text and preserve extra keys where possible.

## Recommended Authoring Pattern

1. Start with one `default` layout and style.
2. Define `main/load/save/settings` screens first.
3. Add custom screens (`extras`, `credits`, etc.) using `OPEN_MENU`.
4. Keep action targets explicit and stable.
5. Validate profile in tests/tools before shipping.

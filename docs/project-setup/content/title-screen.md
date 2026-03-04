# Title Screen and Menu Presentation

JVN menu presentation is driven by two complementary systems:

1. **Theme layer** (`menu.theme`) for shared visual styling.
2. **Menu profile layer** (`menu.registry`, `*.menu`, `*.layout`, `*.style`) for dynamic screen/content/action structure.

This page covers both.

## Theme File (`config/menu/theme/menu.theme`)

Theme loader class:
- `fx/src/main/java/com/jvn/fx/menu/MenuTheme.java`

Lookup order:
- `config/menu/theme/menu.theme`
- `config/menu/menu.theme`
- `config/menu.theme`
- `menu.theme`

### Common visual keys

```properties
backgroundColor=#0A0C12
titleColor=#FFFFFF
itemColor=#CCCCCC
itemSelectedColor=#FFD700
hintColor=rgba(200,200,200,0.8)
accentColor=#FFD700
```

### Font keys

```properties
titleFontFamily=Arial
titleFontWeight=BOLD
titleFontSize=32

itemFontFamily=Arial
itemFontWeight=NORMAL
itemFontSize=20

hintFontFamily=Arial
hintFontWeight=NORMAL
hintFontSize=14
```

### Layout/prefix keys

```properties
titleY=60
listYStart=0.35
lineHeight=40
itemPrefix=  
itemSelectedPrefix=> 
hintsText=Select: Enter    Back: Esc
```

### Label override keys

```properties
titleText=My Visual Novel
label.new=New Game
label.load=Load
label.settings=Settings
label.quit=Quit
```

### Title assets

```properties
backgroundImage=game/images/title_bg.png
logoImage=game/images/logo.png
logoX=0.5
logoY=0.15
logoScale=1.0
logoShadow=true
bgm=game/audio/title_theme.ogg
bgmVolume=0.7
```

## Dynamic Menu Profiles (Recommended)

Profile loader:
- `core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`

Use this structure:

```text
config/menu/
|-- registry/menu.registry
|-- theme/menu.theme
|-- menus/*.menu
|-- layouts/*.layout
`-- styles/*.style
```

If `menu.registry` is missing, JVN auto-discovers menu/layout/style files.

## `menu.registry`

Example:

```properties
defaultMenu=main
menus=main,load,save,settings,extras
layouts=default
styles=default,neon
```

## `*.menu` Screen Files

Define menu items and actions.

```properties
titleText=My Game
layout=default
defaultItemStyle=default
wrapSelection=true
items=start,extras,quit

item.start.action=run_script:scripts/story/prologue.vns
item.extras.action=open_menu
item.extras.target=extras
item.quit.action=quit
```

## `*.layout` Files

Define geometry and alignment for menu list/title/hints.

```properties
listYStart=0.35
lineHeight=40
listWidthFactor=1.0
textAlign=center
hintsBottomMargin=20
titleY=60
```

## `*.style` Files

Define style overrides and prefixes.

```properties
itemColor=#cccccc
itemSelectedColor=#ffd700
itemDisabledColor=#808080
itemPrefix=  
itemSelectedPrefix=> 
itemDisabledPrefix=- 
```

Style inheritance is supported:

```properties
extends=default
itemSelectedColor=#8cff66
```

## Menu Actions

Action parsing supports aliases and `action:target` shorthand.

Core action types:
- `new_game`
- `run_script`
- `load_menu`
- `save_menu`
- `settings_menu`
- `main_menu`
- `open_menu`
- `back`
- `quit`
- `noop`

## Built-in Scene IDs

Menu profile screens are consumed by these scenes:
- `main`
- `load`
- `save`
- `settings`

`load`, `save`, and `settings` now use the same profile system as main menu.

## Editor Support

In editor, these files have dedicated visual tooling:
- `config/menu/menus/*.menu`
- `config/menu/layouts/*.layout`

They remain plain properties files on disk for source control and manual review.

## Minimal Recommended Setup

For new projects, start with:
- one default layout
- one default style
- `main`, `load`, `save`, `settings` menu files

Then add custom menus (e.g. `extras`, `gallery`, `credits`) via `open_menu` actions.

## Related Docs

- [Menu Profiles Overview](../../scripting/ui/menus/menu-profiles.md)
- [Menu Screens](../../scripting/ui/menus/menu-screens.md)
- [Menu Styles](../../scripting/ui/menus/menu-styles.md)
- [Menu Layouts](../../scripting/ui/layout/structure/menu-layouts.md)
- [Editor Guide](../../editor/core/editor.md)
- [Runtime Guide](../../runtime/core/runtime.md)

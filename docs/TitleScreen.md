# Title Screen System

The JVN Engine provides a fully customizable title screen system that allows you to create main menus for your visual novels.

## Configuration File

Create a file named `menu.theme` at `config/menu/menu.theme` in your project. This properties file controls all aspects of the title screen (legacy `scripts/menu.theme` is still supported).

For richer menu behavior (multiple screens, per-item actions, custom layouts, per-button styles), add:

- `config/menu/menu.registry`
- `config/menu/menus/*.menu`
- `config/menu/layouts/*.layout`
- `config/menu/styles/*.style`

The engine loads these from `config/menu/` at runtime and falls back to built-in defaults if files are missing.

### Title Screen Assets

```properties
# Background image (classpath path or absolute filesystem path)
backgroundImage=game/images/title_bg.png

# Logo/title image (replaces text title when set)
logoImage=game/images/logo.png

# Logo positioning (fractions of screen size)
logoX=0.5          # Horizontal position (0.5 = centered)
logoY=0.15         # Vertical position from top
logoScale=1.0      # Scale multiplier
logoShadow=true    # Draw drop shadow behind logo

# Background music (loops automatically)
bgm=game/audio/title_bgm.ogg
bgmVolume=0.7      # Volume 0.0-1.0
```

### Colors

Colors can be specified as:
- Hex: `#RRGGBB` or `#AARRGGBB`
- RGB: `rgb(r,g,b)` or `rgba(r,g,b,a)` where values are 0-255 or 0.0-1.0

```properties
backgroundColor=#0A0C12
titleColor=#FFFFFF
itemColor=#CCCCCC
itemSelectedColor=#FFD700
hintColor=rgba(200,200,200,0.8)
accentColor=#FFD700
```

### Fonts

```properties
titleFontFamily=Arial
titleFontWeight=BOLD        # NORMAL, BOLD, LIGHT, etc.
titleFontSize=32

itemFontFamily=Arial
itemFontWeight=NORMAL
itemFontSize=20

hintFontFamily=Arial
hintFontWeight=NORMAL
hintFontSize=14
```

### Layout

```properties
# Title Y position (<=1 = fraction of height, >1 = pixels)
titleY=60

# Where menu items start (fraction of screen height)
listYStart=0.35

# Spacing between menu items (pixels)
lineHeight=40

# Prefixes for menu items
itemPrefix=  
itemSelectedPrefix=> 
```

### Custom Labels

Override the default localized labels:

```properties
titleText=My Visual Novel
label.new=New Game
label.load=Continue
label.settings=Options
label.quit=Exit

# Custom hints at bottom
hintsText=Enter: Select    Esc: Quit
```

### Advanced Menu Profiles

`config/menu/menu.registry`
```properties
defaultMenu=main
menus=main,load,save,settings,extras
layouts=default
styles=default,neon
```

`config/menu/menus/main.menu`
```properties
titleText=My Game
layout=default
defaultItemStyle=default
items=start,extras,quit
item.start.action=run_script:scripts/story/prologue.vns
item.extras.action=open_menu
item.extras.target=extras
item.quit.action=quit
```

`config/menu/menus/settings.menu`
```properties
titleText=Settings
layout=default
defaultItemStyle=default
items=text_speed,bgm_volume,sfx_volume,voice_volume,auto_play_delay,skip_unread,skip_after_choices,physics_fixed_step,physics_max_substeps,physics_default_friction,input_profile,back
item.back.action=back
```

`load`, `save`, and `settings` are special screen ids consumed by built-in menu scenes.

You can compose profiles with inheritance:

```properties
# config/menu/styles/neon_soft.style
extends=neon
itemSelectedColor=#8cff66
```

If `menu.registry` is omitted, JVN auto-discovers files under:
- `config/menu/menus/*.menu`
- `config/menu/layouts/*.layout`
- `config/menu/styles/*.style`

## Programmatic Configuration

You can also configure the title screen programmatically:

```java
MainMenuScene menu = new MainMenuScene(engine, settings, saveManager, "main.vns", audio);
menu.setTitleBgm("game/audio/title.ogg", 0.7);
engine.scenes().push(menu);
```

## Asset Paths

Assets can be loaded from:
1. **Classpath** - Relative to resources root (e.g., `game/images/logo.png`)
2. **Filesystem** - Absolute paths or relative to working directory

## Example Configuration

```properties
# ===========================================
# My Visual Novel - Title Screen Theme
# ===========================================

# Assets
backgroundImage=game/images/title_bg.png
logoImage=game/images/logo.png
logoY=0.12
logoScale=0.8
bgm=game/audio/title_theme.ogg
bgmVolume=0.6

# Dark elegant theme
backgroundColor=#0D0D14
titleColor=#E8E8E8
itemColor=#A0A0A0
itemSelectedColor=#FFD700
accentColor=#FFD700

# Modern sans-serif fonts
titleFontFamily=Segoe UI
titleFontSize=28
itemFontFamily=Segoe UI
itemFontSize=18

# Compact layout
listYStart=0.45
lineHeight=36
itemSelectedPrefix=▸ 

# Custom labels
label.new=Begin Story
label.load=Continue
label.settings=Options
label.quit=Exit Game
```

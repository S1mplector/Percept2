# UI By Example — A Complete Menu Profile

Build the four files that define JVN's main menu: registry, screen, geometry, and presentation.

**Difficulty:** Intermediate
**Time:** 25 minutes
**Concepts:** menu registry, `.menu`, `.layout`, `.style`, discovery, separation of responsibilities

---

## Project Structure

```text
config/menu/
├── registry/menu.registry
├── menus/main.menu
├── layouts/default.layout
└── styles/default.style
```

### 1. Register the sources

```properties
# config/menu/registry/menu.registry
defaultMenu=main
menus=main
layouts=default
styles=default
```

The registry selects the initial screen and gives cross-file validation an explicit project map.

### 2. Define content and behavior

```properties
# config/menu/menus/main.menu
titleText=Signal at Platform Nine
subtitleText=A Java Vector Nexus story
hintsText=Arrows: Navigate    Enter: Select
layout=default
defaultItemStyle=default
wrapSelection=true

items=start,load,settings,extras,quit

item.start.label=Begin
item.start.action=run_script
item.start.target=scripts/story/prologue.vns

item.load.label=Continue
item.load.action=load_menu

item.settings.label=Settings
item.settings.action=settings_menu

item.extras.label=Extras
item.extras.action=open_menu
item.extras.target=extras

item.quit.label=Quit
item.quit.action=quit
```

`items=` controls order. Each ID is stable and may be referenced by styles, tooling, or custom runtime behavior.

### 3. Define geometry

```properties
# config/menu/layouts/default.layout
listYStart=0.38
lineHeight=62
listWidthFactor=0.36
listXCenter=0.78
textAlign=left
titleY=0.14
titleX=0.08
titleAlign=left
hintsX=0.08
hintsAlign=left
hintsBottomMargin=34
maxVisibleItems=8
```

### 4. Define presentation

```properties
# config/menu/styles/default.style
itemColor=#E2E8F0
itemSelectedColor=#FCD34D
itemDisabledColor=#64748B
itemSelectedPrefix=▶
itemFontFamily=SansSerif
itemFontSize=25

titleColor=#F8FAFC
titleFontFamily=SansSerif
titleFontSize=50
hintsColor=#94A3B8
hintsFontSize=15

backgroundColor=#020617
backgroundAsset=assets/ui/menu/title-background.png
```

---

## Resolution Flow

```text
menu.registry
  -> main.menu
       -> layout=default          -> default.layout
       -> defaultItemStyle=default -> default.style
       -> item actions            -> runtime navigation
```

If a referenced source is missing, the runtime reports a diagnostic and falls back where possible. A fallback keeps the game runnable; it does not prove the authored source was applied.

---

## Add the Referenced Screens

The example points to `extras`, plus built-in load and settings destinations. Before shipping, create or register every intended screen:

```properties
# revised menu.registry
defaultMenu=main
menus=main,load,save,settings,extras
layouts=default,submenu,slots
styles=default,submenu,slot
```

JVN also auto-discovers supported files, but explicit registry entries make project intent easier to validate and review.

---

## Key Takeaways

1. The registry declares IDs and the initial screen.
2. `.menu` owns content and actions.
3. `.layout` owns list and title geometry.
4. `.style` owns typography, colors, backgrounds, and skins.
5. Prefer reusable layout and style IDs over copying values into every screen.

---

## Next

Connect several screens in [Navigation, Scripts, and Custom Actions](06-navigation-and-actions.md).

[Back to UI By Example](../ui-by-example.md)

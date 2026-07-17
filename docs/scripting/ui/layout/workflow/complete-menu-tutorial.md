# Build a Complete Menu System

This tutorial builds a small but production-shaped menu system entirely from source. It includes a
main menu, settings and chapter-selection screens, reusable layout and style files, navigation, and
a runtime verification checklist.

## Result

```text
main
  ├─ Start       -> start
  ├─ Chapters    -> chapter_select
  ├─ Settings    -> settings
  └─ Quit        -> quit

chapter_select
  ├─ Prologue    -> scripts/story/prologue_sample.vns
  └─ Back
```

## 1. Register the sources

Create `config/menu/registry/menu.registry`:

```properties
defaultMenu=main
menus=main,chapter_select,settings
layouts=default,submenu
styles=default,submenu
```

Registry IDs match filenames without extensions. Keep this list explicit so missing files become
obvious in the Layout Editors sidebar.

## 2. Create the main screen

Create `config/menu/menus/main.menu`:

```properties
titleText=Lavender
subtitleText=A Java Vector Nexus project
hintsText=Arrow keys or pointer • Enter to select
layout=default
defaultItemStyle=default
wrapSelection=true
items=start,chapters,settings,quit

item.start.label=Start
item.start.action=start

item.chapters.label=Chapter Select
item.chapters.action=open_menu
item.chapters.target=chapter_select

item.settings.label=Settings
item.settings.action=settings_menu

item.quit.label=Quit
item.quit.action=quit
```

`open_menu` requires a target. Built-in actions such as `start`, `settings_menu`, and `quit` do not.

## 3. Create chapter selection

Create `config/menu/menus/chapter_select.menu`:

```properties
titleText=Chapter Select
layout=submenu
defaultItemStyle=submenu
items=prologue,back

item.prologue.label=Prologue
item.prologue.action=run_script
item.prologue.target=scripts/story/prologue_sample.vns

item.back.label=Back
item.back.action=back
```

The script target is project-relative. Verify its filename and case on disk.

## 4. Add geometry

Create `config/menu/layouts/default.layout`:

```properties
listYStart=0.42
listXCenter=0.78
listWidthFactor=0.32
lineHeight=54
textAlign=left
titleY=0.14
titleX=0.08
titleAlign=left
hintsBottomMargin=26
hintsAlign=left
hintsX=0.08
subtitleGap=12
```

Create `config/menu/layouts/submenu.layout`:

```properties
extends=default
listYStart=0.32
listXCenter=0.50
listWidthFactor=0.46
textAlign=center
titleX=0.50
titleAlign=center
hintsAlign=center
hintsX=0.50
```

Fractional positions are viewport-relative. `lineHeight` and type sizes are pixels, so test them at
both ends of the supported resolution range.

## 5. Add presentation

Create `config/menu/styles/default.style`:

```properties
itemColor=#303030
itemHoverColor=#111111
itemSelectedColor=#000000
itemDisabledColor=#909090
itemSelectedPrefix=—
itemFontFamily=SansSerif
itemFontWeight=NORMAL
itemFontSize=25
titleColor=#111111
titleFontFamily=SansSerif
titleFontWeight=BOLD
titleFontSize=48
hintsColor=#555555
hintsFontFamily=SansSerif
hintsFontSize=15
backgroundColor=#F7F7F4
backgroundOpacity=1.0
# backgroundAsset=assets/backgrounds/menu.png
```

Create `config/menu/styles/submenu.style`:

```properties
extends=default
itemFontSize=23
titleFontSize=40
```

To use artwork, uncomment `backgroundAsset` and copy the project-relative path with Layout Studio's
asset utilities. Do not use an absolute filesystem path.

## 6. Add settings

Create `config/menu/menus/settings.menu` from Layout Studio's standard screen template or copy the
project's generated settings screen. Register it as `settings`, use the `submenu` layout/style, and
retain the engine-supported settings actions appropriate to the project. The `settings_menu` action
on the main screen routes to the runtime settings screen.

## 7. Validate the sources

In the editor:

1. Open **Layout Editors** and refresh.
2. Resolve every missing registry member, layout/style reference, and navigation target.
3. Open each file in Layout Studio and resolve its line diagnostics.
4. Use the Key Reference list when adding a declaration; it is populated from runtime loader keys.
5. Save all participating sources.

Do not treat “no diagnostics” as proof that the UI works. It proves only that the sources satisfy
the parsers and known cross-file constraints.

## 8. Run the real system

Use **Save and Run Runtime** (`Ctrl/Cmd+Enter`) and verify:

- the runtime starts on `main`;
- selection wraps only where configured;
- Chapter Select opens and Back returns to the correct screen;
- the Prologue action starts the expected VNS file;
- settings can be entered and exited;
- pointer and keyboard/controller focus select the same item;
- all text states remain readable over the background;
- the smallest and largest supported viewports remain usable;
- asset paths work on a case-sensitive filesystem.

Commit the working sources together. A menu change often spans the registry, screen, layout, and
style, and reviewing that complete diff is safer than committing isolated fragments.

## Next steps

- [Dialogue layout](../components/dialogue-layout.md)
- [Save and load screens](../screens/save-load-screens.md)
- [Settings screens](../screens/settings-screen.md)
- [Menu actions](../structure/menu-actions.md)
- [Validation and diagnostics](../tooling/validation-diagnostics.md)

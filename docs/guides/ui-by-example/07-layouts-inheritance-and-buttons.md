# UI By Example — Layouts, Inheritance, and Bespoke Buttons

Create reusable menu variants and break out of uniform row layouts when the art direction needs explicit control placement.

**Difficulty:** Intermediate
**Time:** 25 minutes
**Concepts:** `extends`, resolution order, list layouts, explicit item bounds, button-layout files, polygon hit areas

---

## Inherit Instead of Copying

Create a submenu layout that changes only what differs from the main screen:

```properties
# config/menu/layouts/submenu.layout
extends=default
listXCenter=0.32
listYStart=0.28
listWidthFactor=0.46
textAlign=left
titleX=0.10
titleY=0.11
hintsX=0.10
```

Create a style variant:

```properties
# config/menu/styles/submenu.style
extends=default
itemFontSize=22
itemSelectedColor=#93C5FD
titleFontSize=42
backgroundAsset=assets/ui/menu/submenu-background.png
```

Then use them from a screen:

```properties
# config/menu/menus/extras.menu
titleText=Extras
layout=submenu
defaultItemStyle=submenu
items=gallery,music,credits,back
```

Explicit child properties override the parent. Screen, layout, and style files all support `extends=<parent-id>`. Circular chains are rejected with diagnostics.

---

## Override One Item

```properties
item.reset.label=Reset All Data
item.reset.style=danger
item.reset.action=open_menu:confirm_reset
```

Per-item style and font fields win over the screen's default style. Use this for a genuinely distinct semantic role, not arbitrary one-off decoration.

---

## Place Items Explicitly

An item can leave the automatic row flow by defining all four bounds:

```properties
item.start.boundsX=0.58
item.start.boundsY=0.38
item.start.boundsWidth=0.30
item.start.boundsHeight=0.09
```

Values at or below `1.0` are fractions of the menu draw area; larger values are pixels. Partial bounds are invalid.

Add a non-rectangular hit area only when the visual shape requires it:

```properties
item.start.boundsPoints=0.00,0.30;0.08,0.00;0.92,0.00;1.00,0.30;1.00,1.00;0.00,1.00
```

Polygon points are normalized inside the item's rectangle.

---

## Reusable Button Layout Files

For a composition dominated by explicitly placed controls, store the geometry in a button layout:

```properties
# config/menu/buttons/main_buttons.properties
menuId=main
resolution=1280x720
menuType=main
button.ids=start,continue,settings,quit

button.start.boundsX=0.58
button.start.boundsY=0.38
button.start.boundsW=0.30
button.start.boundsH=0.09

button.continue.boundsX=0.58
button.continue.boundsY=0.49
button.continue.boundsW=0.30
button.continue.boundsH=0.08

button.settings.boundsX=0.58
button.settings.boundsY=0.59
button.settings.boundsW=0.14
button.settings.boundsH=0.07

button.quit.boundsX=0.74
button.quit.boundsY=0.59
button.quit.boundsW=0.14
button.quit.boundsH=0.07
```

Button-layout sources are useful for art-directed title screens. The ordinary `.menu` item remains the behavioral authority; do not create competing action declarations in multiple places.

---

## Auto Layout Versus Explicit Bounds

| Use automatic rows when... | Use explicit bounds when... |
|---|---|
| Content length or item count may change | The background artwork defines exact control locations |
| Localization must expand freely | Every supported locale has been tested in fixed regions |
| Keyboard/controller order should follow the list | Focus order and visual order are deliberately coordinated |
| The screen is settings, save/load, or a long list | The screen is a compact art-directed title or hub |

Explicit bounds buy composition control at the cost of more viewport and localization testing.

---

## Key Takeaways

1. Extend screens, layouts, and styles to express variants without duplication.
2. A child overrides only explicitly declared properties.
3. Define all four bounds when placing a menu item manually.
4. Keep visible geometry and pointer polygons aligned.
5. Prefer automatic lists for dynamic content and explicit geometry for fixed art direction.

---

## Next

Apply the menu system to production screens in [Settings, Save/Load, and Help](08-settings-save-load-and-help.md).

[Back to UI By Example](../ui-by-example.md)

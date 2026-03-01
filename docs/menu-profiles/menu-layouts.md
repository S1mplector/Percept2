# Menu Layouts

Complete reference for menu layout files — controlling item list positioning, line height, text alignment, title placement, and hints margins.

Model: `core/src/main/java/com/jvn/core/menu/config/MenuLayoutSpec.java`
Loader: `core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`

---

## Overview

A menu layout controls the **spatial arrangement** of items on a menu screen — where the item list starts, how tall each row is, how wide the list area is, and where the title and hints are placed. Layouts are separate from styles (colors, fonts, assets) so you can mix and match.

---

## File Location

```text
config/menu/layouts/default.layout
config/menu/layouts/compact.layout
config/menu/layouts/slots.layout
```

Also recognized:

```text
config/menu/layouts/default.properties
config/menu/default.layout
```

---

## Properties

```properties
listYStart=0.35
lineHeight=40
listWidthFactor=1.0
textAlign=center
hintsBottomMargin=20
titleY=60
```

| Property | Default | Description |
|----------|---------|-------------|
| `listYStart` | 0.35 | Y position where the item list begins (fraction of viewport, 0–1) |
| `lineHeight` | 40 | Height of each item row in pixels |
| `listWidthFactor` | 1.0 | Width of the item list as a fraction of the menu draw area (0.1–1.0) |
| `textAlign` | `"center"` | Text alignment: `"center"`, `"left"`, or `"right"` |
| `hintsBottomMargin` | 20 | Distance from the bottom edge to the hints text (pixels) |
| `titleY` | — | Y position of the title text (pixels from top). Optional; if omitted, uses a default based on layout |

---

## Built-in Layouts

### `default`

The standard main menu layout with centered items.

```properties
# Built-in defaults
listYStart=0.34
lineHeight=68
listWidthFactor=0.44
textAlign=center
hintsBottomMargin=36
titleY=0.14
```

### `submenu`

A secondary screen layout (settings, extras) with left-aligned items and a wider list.

```properties
listYStart=0.24
lineHeight=62
listWidthFactor=0.64
textAlign=left
hintsBottomMargin=30
titleY=0.11
```

### `slots`

Optimized for save/load screens with taller rows for slot previews.

```properties
listYStart=0.20
lineHeight=74
listWidthFactor=0.58
textAlign=left
hintsBottomMargin=30
titleY=0.10
```

---

## Inheritance

Layouts support `extends` to inherit from a parent layout:

```properties
# config/menu/layouts/compact.layout
extends=default
lineHeight=36
listWidthFactor=0.50
```

Only explicitly set properties override the parent. All others fall back.

Circular inheritance is detected and reported as a diagnostic.

---

## Examples

### Centered Main Menu

```properties
# config/menu/layouts/centered.layout
listYStart=0.40
lineHeight=60
listWidthFactor=0.40
textAlign=center
hintsBottomMargin=30
titleY=80
```

Result:
- Items start at 40% down the screen
- Each item is 60px tall
- The list occupies 40% of the screen width, centered
- Title at 80px from top

### Left-Aligned Settings

```properties
# config/menu/layouts/settings.layout
extends=submenu
listYStart=0.20
lineHeight=52
listWidthFactor=0.70
textAlign=left
```

### Wide Slot List

```properties
# config/menu/layouts/wide_slots.layout
extends=slots
listWidthFactor=0.80
lineHeight=80
titleY=40
```

---

## How Layout Relates to Other Config

```text
Menu Screen (.menu)
  ├── layout=<layoutId>     →  Menu Layout (.layout)
  ├── defaultItemStyle=<id> →  Menu Style (.style)
  └── items=...
       └── item.<id>.style=<id> →  Menu Style (.style)
```

A screen references a layout by ID. The layout controls geometry; the style controls visuals.

---

## Diagnostic Messages

The loader produces diagnostics for:

- `"Invalid number for 'lineHeight' in ..."` — non-numeric value
- `"Circular layout inheritance detected at '...'"` — `extends` loop
- `"Layout '...' extends missing layout '...'"` — parent not found

---

## Related Docs

- [Menu Profiles Overview](menu-profiles.md)
- [Menu Screens](menu-screens.md)
- [Menu Styles](menu-styles.md)
- [Button Layouts](menu-button-layouts.md)

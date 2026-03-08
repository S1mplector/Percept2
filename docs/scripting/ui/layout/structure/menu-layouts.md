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
titleAlign=center
hintsBottomMargin=20
hintsAlign=center
titleY=60
# listXCenter=0.5
# titleX=0.5
# hintsX=0.5
# maxVisibleItems=8
```

| Property | Default | Description |
|----------|---------|-------------|
| `listYStart` | 0.35 | Y position where the item list begins (fraction of viewport, 0–1) |
| `lineHeight` | 40 | Height of each item row in pixels |
| `listWidthFactor` | 1.0 | Width of the item list as a fraction of the menu draw area (0.1–1.0) |
| `textAlign` | `"center"` | Text alignment: `"center"`, `"left"`, or `"right"` |
| `titleAlign` | `"center"` | Title alignment when `titleX` is not set |
| `hintsBottomMargin` | 20 | Distance from the bottom edge to the hints text (pixels) |
| `hintsAlign` | `"center"` | Hints/footer alignment when `hintsX` is not set |
| `titleY` | — | Y position of the title text (pixels from top). Optional; if omitted, uses a default based on layout |
| `listXCenter` | — | Horizontal center of the item list (fraction of viewport, 0–1). Optional; overrides `textAlign`-based positioning when set. |
| `titleX` | — | Horizontal center of the title text (fraction of viewport, 0–1). Optional; overrides default centered title. |
| `hintsX` | — | Horizontal center of the hints/footer text (fraction of viewport, 0–1). Optional; overrides `hintsAlign`. |
| `maxVisibleItems` | — | Maximum number of items to show at once (positive integer). Optional; if omitted, all items are visible. |

### Advanced Positioning: `listXCenter`, `titleX`, and `hintsX`

By default, the list is positioned using `textAlign` and `listWidthFactor`. Setting
`listXCenter` provides explicit horizontal centering — the list is centered on the
specified fraction of the viewport, regardless of `textAlign`. The list is still clamped
to stay within screen bounds.

Similarly, `titleX` positions the title text’s center at a specific viewport fraction
instead of the default centered behavior.

`hintsX` does the same for the footer/hints line, which is useful for help and settings
screens that place instructions under a left or right content column.

```properties
# Position the list at 30% from the left edge
listXCenter=0.3
titleX=0.3
hintsX=0.3
```

### Scrollable Lists: `maxVisibleItems`

When `maxVisibleItems` is set, only that many items are displayed simultaneously. If the
menu has more items than the limit, the renderer scrolls the visible window as the
selection moves. This is useful for long lists like save slots or chapter selects.

```properties
# Show at most 8 items; scroll if there are more
maxVisibleItems=8
```

---

## Built-in Layouts

### `default`

The standard main menu layout with centered items.

```properties
# Built-in defaults
listYStart=0.38
lineHeight=62
listWidthFactor=0.36
textAlign=center
titleAlign=center
hintsBottomMargin=32
hintsAlign=center
titleY=0.16
```

### `submenu`

A secondary screen layout (extras, gallery) with left-aligned items and a wider list.

```properties
listYStart=0.26
lineHeight=56
listWidthFactor=0.52
textAlign=left
titleAlign=left
hintsBottomMargin=28
hintsAlign=left
titleY=0.13
```

### `settings`

A dedicated settings screen layout with tighter spacing for option rows.

```properties
listYStart=0.16
lineHeight=50
listWidthFactor=0.56
textAlign=left
titleAlign=left
hintsBottomMargin=24
hintsAlign=left
titleY=0.07
maxVisibleItems=8
```

### `slots`

Optimized for save/load screens with taller rows for slot previews.

```properties
listYStart=0.22
lineHeight=68
listWidthFactor=0.54
textAlign=left
titleAlign=left
hintsBottomMargin=28
hintsAlign=left
titleY=0.12
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
titleAlign=center
hintsBottomMargin=30
hintsAlign=center
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
# Inherits from the built-in settings preset
extends=settings
listYStart=0.18
lineHeight=48
listWidthFactor=0.60
textAlign=left
maxVisibleItems=10
```

### Off-Center Menu (Sidebar Style)

```properties
# config/menu/layouts/sidebar.layout
listYStart=0.20
lineHeight=58
listWidthFactor=0.30
textAlign=left
listXCenter=0.18
titleX=0.18
titleAlign=left
titleY=0.08
hintsBottomMargin=24
hintsAlign=left
hintsX=0.18
```

Result:
- Items are placed in a narrow column on the left side of the screen
- Title aligns with the item list instead of centering on the screen
- Hints align with the same column instead of centering on the viewport
- Ideal for layouts where artwork occupies the right half

### Wide Slot List

```properties
# config/menu/layouts/wide_slots.layout
extends=slots
listWidthFactor=0.80
lineHeight=80
titleY=40
```

### Chapter Select with Scroll

```properties
# config/menu/layouts/chapters.layout
listYStart=0.22
lineHeight=54
listWidthFactor=0.60
textAlign=left
titleY=0.08
maxVisibleItems=6
```

Result:
- Only 6 chapters visible at a time
- Selection scrolls through remaining chapters
- Clean look regardless of total chapter count

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
- `"Invalid number for 'listXCenter' in ..."` — non-numeric value for optional field
- `"Invalid number for 'maxVisibleItems' in ..."` — non-positive integer
- `"Circular layout inheritance detected at '...'"` — `extends` loop
- `"Layout '...' extends missing layout '...'"` — parent not found

---

## Related Docs

- [Menu Profiles Overview](../../menus/menu-profiles.md)
- [Menu Screens](../../menus/menu-screens.md)
- [Menu Styles](../../menus/menu-styles.md)
- [Button Layouts](menu-button-layouts.md)

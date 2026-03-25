# Menu Styles

Complete reference for menu style files — item colors, fonts, text effects, button skins, title/hints styling, and backgrounds.

Model: `core/src/main/java/com/jvn/core/menu/config/MenuStyleSpec.java`
Loader: `core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`

---

## Overview

A menu style controls the **visual appearance** of menu items — colors, fonts, shadows, button images, title/hints presentation, and screen backgrounds. Styles are separate from layouts (geometry) so you can mix and match freely.

---

## File Location

```text
config/menu/styles/default.style
config/menu/styles/neon.style
config/menu/styles/dark.style
```

Also recognized:

```text
config/menu/styles/default.properties
config/menu/default.style
```

---

## Item Text Colors

```properties
itemColor=#CCCCCC
itemSelectedColor=#FFD700
itemHoverColor=#FFE066
itemDisabledColor=#808080
```

| Property | Description |
|----------|-------------|
| `itemColor` | Normal item text color |
| `itemSelectedColor` | Selected/focused item text color |
| `itemHoverColor` | Hovered item text color |
| `itemDisabledColor` | Disabled item text color |

Colors use hex format: `#RGB`, `#RRGGBB`, or `#RRGGBBAA` (with alpha).

---

## Item Prefixes

Optional text prefixes prepended to item labels for visual cues:

```properties
itemPrefix=  
itemSelectedPrefix=> 
itemDisabledPrefix=- 
```

| Property | Description |
|----------|-------------|
| `itemPrefix` | Prefix for normal items (e.g., two spaces for indent) |
| `itemSelectedPrefix` | Prefix for selected item (e.g., `"> "`) |
| `itemDisabledPrefix` | Prefix for disabled items (e.g., `"- "`) |

---

## Item Font

```properties
itemFontFamily=Arial
itemFontWeight=BOLD
itemFontSize=20
```

| Property | Default | Description |
|----------|---------|-------------|
| `itemFontFamily` | — | Font family name |
| `itemFontWeight` | — | Font weight: `NORMAL`, `BOLD` |
| `itemFontSize` | — | Font size in pixels |

---

## Item Text Effects

```properties
itemShadowColor=#00000088
itemShadowOffsetX=2
itemShadowOffsetY=2
itemOpacity=1.0
```

| Property | Description |
|----------|-------------|
| `itemShadowColor` | Drop shadow color (with alpha) |
| `itemShadowOffsetX` | Shadow horizontal offset (pixels) |
| `itemShadowOffsetY` | Shadow vertical offset (pixels) |
| `itemOpacity` | Item opacity (0–1) |

---

## Button Skins

Image assets for button backgrounds in each state:

```properties
buttonAsset=assets/ui/menu/button.png
buttonSelectedAsset=assets/ui/menu/button_selected.png
buttonHoverAsset=assets/ui/menu/button_hover.png
buttonDisabledAsset=assets/ui/menu/button_disabled.png
buttonTextPaddingX=18
buttonTextPaddingY=0
```

| Property | Description |
|----------|-------------|
| `buttonAsset` | Normal state background image |
| `buttonSelectedAsset` | Selected state background image |
| `buttonHoverAsset` | Hovered state background image |
| `buttonDisabledAsset` | Disabled state background image |
| `buttonTextPaddingX` | Horizontal text padding inside button (pixels) |
| `buttonTextPaddingY` | Vertical text padding inside button (pixels) |

When button assets are provided, they are drawn as the item background. Text is rendered on top with the configured padding.

---

## Title Styling

```properties
titleColor=#FFFFFF
titleFontFamily=Georgia
titleFontWeight=BOLD
titleFontSize=36
titleShadowColor=#000000
```

| Property | Description |
|----------|-------------|
| `titleColor` | Title text color |
| `titleFontFamily` | Title font family |
| `titleFontWeight` | Title font weight |
| `titleFontSize` | Title font size (pixels) |
| `titleShadowColor` | Title drop shadow color |

---

## Hints Styling

```properties
hintsColor=#AAAAAA
hintsFontFamily=Arial
hintsFontWeight=NORMAL
hintsFontSize=14
```

| Property | Default | Description |
|----------|---------|-------------|
| `hintsColor` | — | Hints text color |
| `hintsFontFamily` | — | Hints font family |
| `hintsFontWeight` | `NORMAL` | Hints font weight: `NORMAL`, `BOLD`, `SEMI_BOLD` |
| `hintsFontSize` | — | Hints font size (pixels) |

---

## Background

```properties
backgroundAsset=assets/ui/menu/bg.png
backgroundColor=#1A1A2E
backgroundOpacity=0.9
```

| Property | Description |
|----------|-------------|
| `backgroundAsset` | Background image asset path |
| `backgroundColor` | Fallback background color (hex) |
| `backgroundOpacity` | Background opacity (0–1) |

---

## Inheritance

Styles support `extends` to inherit from a parent style:

```properties
# config/menu/styles/neon_soft.style
extends=neon
itemSelectedColor=#8CFF66
titleColor=#66FF99
```

Only explicitly set properties override the parent. Circular inheritance is detected and reported.

---

## Complete Example: Dark Theme

```properties
# config/menu/styles/dark.style

# Items
itemColor=#B0B0C0
itemSelectedColor=#FFD700
itemHoverColor=#FFE066
itemDisabledColor=#505060
itemPrefix=    
itemSelectedPrefix=  ▸ 
itemFontFamily=Segoe UI
itemFontWeight=NORMAL
itemFontSize=22
itemShadowColor=#000000AA
itemShadowOffsetX=1
itemShadowOffsetY=1
itemOpacity=1.0

# Button skins
buttonAsset=assets/ui/menu/dark_btn.png
buttonSelectedAsset=assets/ui/menu/dark_btn_sel.png
buttonHoverAsset=assets/ui/menu/dark_btn_hover.png
buttonDisabledAsset=assets/ui/menu/dark_btn_disabled.png
buttonTextPaddingX=24
buttonTextPaddingY=4

# Title
titleColor=#FFFFFF
titleFontFamily=Georgia
titleFontWeight=BOLD
titleFontSize=40
titleShadowColor=#000000CC

# Hints
hintsColor=#707080
hintsFontFamily=Segoe UI
hintsFontSize=14

# Background
backgroundAsset=assets/ui/menu/dark_bg.png
backgroundColor=#0A0A18
backgroundOpacity=1.0
```

## Complete Example: Neon Theme

```properties
# config/menu/styles/neon.style

itemColor=#00CCFF
itemSelectedColor=#FF00FF
itemHoverColor=#FF66FF
itemDisabledColor=#336666
itemSelectedPrefix=>> 
itemFontFamily=Courier New
itemFontWeight=BOLD
itemFontSize=20
itemShadowColor=#FF00FF44
itemShadowOffsetX=0
itemShadowOffsetY=2

titleColor=#00FFCC
titleFontFamily=Courier New
titleFontWeight=BOLD
titleFontSize=42
titleShadowColor=#00FFCC44

hintsColor=#006666
hintsFontFamily=Courier New
hintsFontSize=13

backgroundColor=#000000
backgroundOpacity=1.0
```

---

## Per-Item Style Override

Individual items can reference a different style via `item.<id>.style`:

```properties
# In the .menu file
item.quit.style=danger

# config/menu/styles/danger.style
extends=default
itemColor=#FF4444
itemSelectedColor=#FF0000
```

---

## Related Docs

- [Menu Profiles Overview](menu-profiles.md)
- [Menu Screens](menu-screens.md)
- [Menu Layouts](../layout/structure/menu-layouts.md)
- [Button Layouts](../layout/structure/menu-button-layouts.md)
- [Dialogue Layout & Style](../layout/components/dialogue-layout.md)

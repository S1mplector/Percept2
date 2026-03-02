# Fonts & Typography

Complete guide to configuring fonts across JVN dialogue and menu DSLs — font families, weights, sizes, where each font key applies, cross-platform considerations, and practical examples.

---

## Overview

JVN uses system fonts for all text rendering. Font settings are spread across two DSL systems: the dialogue layout (`config/ui/dialogue.layout`) for VN text, and menu styles (`config/menu/styles/*.style`) for menu text. Each subsystem has independent font controls so you can use different typefaces for dialogue, choice buttons, menu items, titles, and hints.

---

## All Font Keys

### dialogue.layout

| Key | Applies to | Type |
|-----|-----------|------|
| `nameTextFontFamily` | Speaker name in the name box | String |
| `nameTextFontSize` | Speaker name size | Integer (> 0) |
| `dialogueTextFontFamily` | Dialogue body text | String |
| `dialogueTextFontSize` | Dialogue body text size | Integer (> 0) |
| `choiceFontFamily` | Choice button text | String |
| `choiceFontSize` | Choice button text size | Integer (> 0) |

### Menu .style

| Key | Applies to | Type |
|-----|-----------|------|
| `itemFontFamily` | Menu item labels | String |
| `itemFontWeight` | Menu item weight | String |
| `itemFontSize` | Menu item size | Integer (> 0) |
| `titleFontFamily` | Screen title text | String |
| `titleFontWeight` | Screen title weight | String |
| `titleFontSize` | Screen title size | Integer (> 0) |
| `hintsFontFamily` | Hints bar text | String |
| `hintsFontSize` | Hints bar text size | Integer (> 0) |

---

## Font Family

The `fontFamily` value is the **display name** of a font installed on the system. It must match exactly (case-insensitive on most platforms).

```properties
# Common cross-platform choices
dialogueTextFontFamily=Segoe UI
nameTextFontFamily=Georgia
choiceFontFamily=Segoe UI

itemFontFamily=Segoe UI
titleFontFamily=Georgia
hintsFontFamily=Segoe UI
```

### Cross-Platform Font Availability

| Font | Windows | macOS | Linux |
|------|---------|-------|-------|
| Segoe UI | Yes | No | No |
| Arial | Yes | Yes | Usually |
| Georgia | Yes | Yes | Usually |
| Times New Roman | Yes | Yes | Usually |
| Courier New | Yes | Yes | Usually |
| Helvetica Neue | No | Yes | No |
| San Francisco | No | Yes (system) | No |
| Noto Sans | Bundled | Bundled | Common |

### Fallback Behavior

If a specified font isn't found, the JVM falls back to its default logical fonts:

- **Serif** fallback: Times New Roman or similar
- **Sans-serif** fallback: Arial, Helvetica, or similar
- **Monospace** fallback: Courier New or similar

The engine does **not** log a warning for missing fonts -- it silently falls back. Always test on your target platform.

### Safe Cross-Platform Choices

For projects targeting all platforms:

```properties
# Option 1: Widely available
dialogueTextFontFamily=Arial
nameTextFontFamily=Georgia

# Option 2: Java logical names (always available)
dialogueTextFontFamily=SansSerif
nameTextFontFamily=Serif

# Option 3: Noto family (bundle with your project)
dialogueTextFontFamily=Noto Sans
nameTextFontFamily=Noto Serif
```

---

## Font Weight

Font weight is only available in menu styles (`itemFontWeight`, `titleFontWeight`). Dialogue fonts don't have a weight property -- they use the default weight of the font family.

| Value | Description |
|-------|-------------|
| `NORMAL` | Regular/Book weight |
| `BOLD` | Bold weight |
| `SEMI_BOLD` | Semi-bold (between normal and bold) |

```properties
itemFontWeight=SEMI_BOLD
titleFontWeight=BOLD
```

### Tips

- Not all fonts have a semi-bold variant. If unavailable, the renderer may fall back to normal or bold.
- For dialogue text emphasis, use VNS inline markup `{b}bold{/b}` instead of a font weight property.

---

## Font Size

Font size is specified in **points** (not pixels). A larger value means larger text.

```properties
# Dialogue
nameTextFontSize=18
dialogueTextFontSize=22
choiceFontSize=20

# Menu
itemFontSize=28
titleFontSize=56
hintsFontSize=18
```

### Size Guidelines

| Context | Recommended Range | Notes |
|---------|------------------|-------|
| Dialogue body | 16--24 | Must be readable during fast text reveal |
| Speaker name | 16--22 | Slightly smaller than or equal to dialogue |
| Choice buttons | 16--22 | Must fit within button height |
| Menu items | 20--32 | Larger for main menu, smaller for settings |
| Menu title | 36--64 | Prominent but not overwhelming |
| Hints text | 12--18 | Small, unobtrusive |

### Size and Layout Interaction

Font size must work with the available space:

- **Choice buttons:** `choiceFontSize` must fit within `choiceHeight`. A 24pt font in a 30px button will clip.
- **Menu items:** `itemFontSize` must fit within `lineHeight`. A 40pt font in a 50px row looks cramped.
- **Name box:** `nameTextFontSize` must fit within `nameBoxHeight` considering `nameTextBaselineOffset`.

Rule of thumb: `lineHeight` or `buttonHeight` should be at least 1.5x the font size.

---

## Examples

### Example 1: Clean Modern UI

```properties
# dialogue.layout
nameTextFontFamily=Segoe UI
nameTextFontSize=18
dialogueTextFontFamily=Segoe UI
dialogueTextFontSize=20
choiceFontFamily=Segoe UI
choiceFontSize=18
```

```properties
# default.style
itemFontFamily=Segoe UI
itemFontWeight=SEMI_BOLD
itemFontSize=26
titleFontFamily=Segoe UI
titleFontWeight=BOLD
titleFontSize=48
hintsFontFamily=Segoe UI
hintsFontSize=16
```

### Example 2: Literary/Classic

```properties
# dialogue.layout
nameTextFontFamily=Georgia
nameTextFontSize=20
dialogueTextFontFamily=Georgia
dialogueTextFontSize=22
choiceFontFamily=Georgia
choiceFontSize=18
```

```properties
# default.style
itemFontFamily=Georgia
itemFontWeight=NORMAL
itemFontSize=24
titleFontFamily=Georgia
titleFontWeight=BOLD
titleFontSize=52
hintsFontFamily=Georgia
hintsFontSize=14
```

### Example 3: Terminal/Hacker

```properties
# dialogue.layout
nameTextFontFamily=Courier New
nameTextFontSize=16
dialogueTextFontFamily=Courier New
dialogueTextFontSize=16
choiceFontFamily=Courier New
choiceFontSize=14
```

```properties
# default.style
itemFontFamily=Courier New
itemFontWeight=BOLD
itemFontSize=18
titleFontFamily=Courier New
titleFontWeight=BOLD
titleFontSize=36
hintsFontFamily=Courier New
hintsFontSize=12
```

### Example 4: Mixed Fonts (Different for Each Role)

```properties
# dialogue.layout
nameTextFontFamily=Georgia
nameTextFontSize=18
dialogueTextFontFamily=Segoe UI
dialogueTextFontSize=20
choiceFontFamily=Segoe UI
choiceFontSize=18
```

```properties
# default.style
itemFontFamily=Segoe UI
itemFontWeight=SEMI_BOLD
itemFontSize=28
titleFontFamily=Georgia
titleFontWeight=BOLD
titleFontSize=56
hintsFontFamily=Arial
hintsFontSize=14
```

Using a serif font for titles/names and sans-serif for body text is a common pattern.

### Example 5: Large Text (Accessibility)

```properties
# dialogue.layout
nameTextFontSize=24
dialogueTextFontSize=28
choiceFontSize=24

# Adjust layout to accommodate larger text
textBoxHeight=0.30
choiceHeight=60
choiceGap=12
nameBoxHeight=48
dialogueTextTopPadding=50
```

```properties
# default.style
itemFontSize=32
titleFontSize=64
hintsFontSize=20
```

---

## Typography Across the Full UI

A typical game uses fonts in these locations:

```text
┌─────────────────────────────────────────┐
│  Menu Title    (titleFontFamily/Size)   │
│                                         │
│  ▶ New Game    (itemFontFamily/Size)    │
│    Load Game                            │
│    Settings                             │
│    Quit                                 │
│                                         │
│  Enter: Select   Esc: Quit              │
│  (hintsFontFamily/Size)                 │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│  [Character sprites]                    │
│                                         │
│  ┌─ Name Box ─────────────────────┐     │
│  │ Aria  (nameTextFontFamily)     │     │
│  └────────────────────────────────┘     │
│  ┌─ Textbox ──────────────────────────┐ │
│  │ I can't believe what happened...   │ │
│  │ (dialogueTextFontFamily/Size)      │ │
│  │                                    │ │
│  │                      [AUTO] [SKIP] │ │
│  └────────────────────────────────────┘ │
│                                         │
│  ┌─ Choice 1 ─────────────────────────┐ │
│  │ Go to the forest                   │ │
│  │ (choiceFontFamily/Size)            │ │
│  └────────────────────────────────────┘ │
│  ┌─ Choice 2 ─────────────────────────┐ │
│  │ Stay here                          │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

---

## Font Size and Resolution

Font sizes are in **points**, which are resolution-independent on most JVM renderers. However:

- On HiDPI displays, fonts may appear physically smaller. Test at your target DPI.
- Menu layout values like `lineHeight` are in **pixels** and don't scale with DPI. You may need larger `lineHeight` values on higher-resolution displays.
- Dialogue layout fractional values (`textBoxY`, `textBoxWidth`) scale naturally with viewport size.

---

## Runtime Validation Checklist

- [ ] All text renders in the expected font family (not a fallback serif)
- [ ] Font sizes are readable at the target resolution
- [ ] Font weight (bold/semi-bold) is visually distinguishable from normal
- [ ] Speaker name fits within the name box
- [ ] Dialogue text doesn't clip at the textbox edges
- [ ] Choice text fits within choice button height
- [ ] Menu items fit within line height
- [ ] Title text is prominent but not oversized
- [ ] Hints text is legible but unobtrusive
- [ ] Text renders correctly on the target OS (test cross-platform if needed)
- [ ] VNS inline markup ({b}, {i}) works with the chosen font family

---

## Common Mistakes

**Font not found -- silent fallback:**
The engine doesn't warn about missing fonts. If text looks different than expected, the font may not be installed. Test on a clean system.

**Font size too large for container:**
A 30pt font in a 40px `choiceHeight` clips. Ensure container height is at least 1.5x font size.

**Using a decorative font for body text:**
Fancy fonts are hard to read at small sizes. Use decorative fonts only for titles.

**Inconsistent font families:**
Using 4+ different font families creates visual chaos. Stick to 2 max: one for headings, one for body.

**Confusing font weight with font family:**
`itemFontWeight=BOLD` is not the same as using a "Bold" font family. The weight property adjusts the existing family. Don't use `itemFontFamily=Segoe UI Bold` -- use `itemFontFamily=Segoe UI` with `itemFontWeight=BOLD`.

---

## Related Docs

- [Dialogue Layout & Style](dialogue-layout.md)
- [Menu Styles](../menus-submenus/menu-styles.md)
- [Colors & Theming](colors-theming.md)
- [Choice Buttons](choice-buttons.md)
- [VNS Dialogue & Text](../vns/vns-dialogue.md)

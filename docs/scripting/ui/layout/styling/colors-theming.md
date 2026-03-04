# Colors & Theming

Complete guide to the JVN color system — hex format, alpha transparency, state-based colors, building cohesive themes across dialogue and menu DSLs, and practical palette recipes.

---

## Overview

Every color value in JVN layout and style files uses **hex format**. Colors appear in dialogue layout (textbox, name box, choices), menu styles (items, titles, hints, backgrounds), and all four interactive states (normal, hover, selected, disabled). This guide covers the format, every color key across all DSL types, theming strategies, and ready-to-use palettes.

---

## Color Format

JVN accepts three hex formats:

| Format | Example | Description |
|--------|---------|-------------|
| `#RGB` | `#F00` | Short form (expanded to `#FF0000`) |
| `#RRGGBB` | `#FF0000` | Standard 6-digit hex |
| `#RRGGBBAA` | `#FF000088` | 8-digit hex with alpha channel |

### Alpha Channel

The alpha byte (`AA`) controls transparency:

| Alpha | Hex | Meaning |
|-------|-----|---------|
| 255 | `FF` | Fully opaque |
| 204 | `CC` | ~80% opaque |
| 170 | `AA` | ~67% opaque |
| 128 | `80` | 50% transparent |
| 68 | `44` | ~27% opaque |
| 0 | `00` | Fully transparent (invisible) |

```properties
# Solid black
textBoxColor=#000000

# Semi-transparent black (80% opaque)
textBoxColor=#000000CC

# Very transparent black (25% opaque)
textBoxColor=#00000040

# Fully invisible
textBoxColor=#00000000
```

---

## All Color Keys by DSL Type

### dialogue.layout — Textbox Colors

| Key | Purpose |
|-----|---------|
| `textBoxColor` | Textbox background fill |
| `nameBoxColor` | Name plate background fill |
| `nameTextColor` | Speaker name text color |
| `dialogueTextColor` | Dialogue body text color |

### dialogue.layout — Choice Button Colors (4 states)

| State | Background | Text | Border |
|-------|-----------|------|--------|
| Normal | `choiceBackgroundColor` | `choiceTextColor` | `choiceBorderColor` |
| Hover | `choiceHoverColor` | `choiceHoverTextColor` | `choiceHoverBorderColor` |
| Selected | `choiceSelectedColor` | `choiceSelectedTextColor` | `choiceSelectedBorderColor` |
| Disabled | `choiceDisabledColor` | `choiceDisabledTextColor` | `choiceDisabledBorderColor` |

That's **12 color keys** for choices alone.

### Menu .style — Item Colors (4 states)

| Key | Purpose |
|-----|---------|
| `itemColor` | Normal item text color |
| `itemSelectedColor` | Selected/focused item text |
| `itemHoverColor` | Mouse-hovered item text |
| `itemDisabledColor` | Disabled item text |
| `itemShadowColor` | Drop shadow color |

### Menu .style — Title, Hints, Background

| Key | Purpose |
|-----|---------|
| `titleColor` | Title text color |
| `titleShadowColor` | Title drop shadow |
| `hintsColor` | Hints bar text color |
| `backgroundColor` | Screen background solid fill |

---

## Opacity Keys

Some elements have dedicated opacity controls (0.0--1.0) separate from alpha:

| Key | File | Description |
|-----|------|-------------|
| `textBoxOpacity` | dialogue.layout | Textbox overlay opacity |
| `itemOpacity` | menu .style | Menu item opacity |
| `backgroundOpacity` | menu .style | Menu background opacity |

Opacity and alpha **multiply**. If `textBoxColor=#000000CC` (alpha ~80%) and `textBoxOpacity=0.5`, the effective opacity is ~40%.

---

## Building Themes

A theme is a cohesive set of colors applied across all UI elements. The most efficient approach is to pick a **palette of 5--7 colors** and map them to specific roles.

### Palette Roles

| Role | Usage |
|------|-------|
| **Background** | Screen fill, textbox fill |
| **Surface** | Choice buttons, name box |
| **Primary** | Selected text, active highlights |
| **Secondary** | Normal text, borders |
| **Muted** | Disabled text, inactive elements |
| **Accent** | Hover highlights, special emphasis |
| **Text** | Primary readable text on surfaces |

### Example: Dark Blue Theme

Palette:
- Background: `#0A0A1A`
- Surface: `#1A1A3A`
- Primary: `#FFD700` (gold)
- Secondary: `#B8C4D8` (blue-grey)
- Muted: `#4A5568`
- Accent: `#FF6B6B` (coral)
- Text: `#E8E8F0`

```properties
# dialogue.layout
textBoxColor=#0A0A1ADD
nameBoxColor=#1A1A3AEE
nameTextColor=#FFD700
dialogueTextColor=#E8E8F0

choiceBackgroundColor=#1A1A3A
choiceHoverColor=#2A2A5A
choiceSelectedColor=#3A3A7A
choiceDisabledColor=#0A0A1A
choiceTextColor=#B8C4D8
choiceHoverTextColor=#FFD700
choiceSelectedTextColor=#FFFFFF
choiceDisabledTextColor=#4A5568
choiceBorderColor=#2A2A5A
choiceHoverBorderColor=#5A5A8A
choiceSelectedBorderColor=#8A8ACE
choiceDisabledBorderColor=#1A1A2A
```

```properties
# menu style
itemColor=#B8C4D8
itemSelectedColor=#FFD700
itemHoverColor=#E8E8F0
itemDisabledColor=#4A5568
itemShadowColor=#00000088
titleColor=#FFD700
titleShadowColor=#000000CC
hintsColor=#4A5568
backgroundColor=#0A0A1A
backgroundOpacity=1.0
```

### Example: Light Pastel Theme

Palette:
- Background: `#FFF5F0`
- Surface: `#FFE8E0`
- Primary: `#E85D75` (rose)
- Secondary: `#4A4A6A` (dark lavender)
- Muted: `#B0B0C8`
- Accent: `#D04060`
- Text: `#3A3A5A`

```properties
# dialogue.layout
textBoxColor=#FFF5F0EE
nameBoxColor=#FFE8E0FF
nameTextColor=#E85D75
dialogueTextColor=#3A3A5A

choiceBackgroundColor=#FFF5F0
choiceHoverColor=#FFE8E0
choiceSelectedColor=#FFD0C0
choiceDisabledColor=#F0F0F0
choiceTextColor=#4A4A6A
choiceHoverTextColor=#E85D75
choiceSelectedTextColor=#D04060
choiceDisabledTextColor=#B0B0C8
choiceBorderColor=#E0D0C8
choiceHoverBorderColor=#E8A090
choiceSelectedBorderColor=#D08070
choiceDisabledBorderColor=#D0D0D0
```

```properties
# menu style
itemColor=#4A4A6A
itemSelectedColor=#E85D75
itemHoverColor=#6A6A8A
itemDisabledColor=#B0B0C8
titleColor=#E85D75
hintsColor=#8888AA
backgroundColor=#FFF5F0
backgroundOpacity=1.0
```

### Example: Neon/Cyberpunk Theme

Palette:
- Background: `#000000`
- Surface: `#0A0A1A`
- Primary: `#FF00FF` (magenta)
- Secondary: `#00CCFF` (cyan)
- Muted: `#336666`
- Accent: `#00FFCC` (teal)
- Text: `#CCFFFF`

```properties
# dialogue.layout
textBoxColor=#0A0A1AEE
nameBoxColor=#0A0A1AFF
nameTextColor=#00FFCC
dialogueTextColor=#CCFFFF

choiceBackgroundColor=#0A0A1A
choiceHoverColor=#1A0A2A
choiceSelectedColor=#2A0A3A
choiceDisabledColor=#050510
choiceTextColor=#00CCFF
choiceHoverTextColor=#FF00FF
choiceSelectedTextColor=#FF66FF
choiceDisabledTextColor=#336666
choiceBorderColor=#00CCFF44
choiceHoverBorderColor=#FF00FF88
choiceSelectedBorderColor=#FF00FFCC
choiceDisabledBorderColor=#33666644
```

```properties
# menu style
itemColor=#00CCFF
itemSelectedColor=#FF00FF
itemHoverColor=#FF66FF
itemDisabledColor=#336666
itemShadowColor=#FF00FF44
titleColor=#00FFCC
titleShadowColor=#00FFCC44
hintsColor=#006666
backgroundColor=#000000
backgroundOpacity=1.0
```

### Example: Sepia/Vintage Theme

```properties
# dialogue.layout
textBoxColor=#2A1A0ADD
nameBoxColor=#3A2A1AEE
nameTextColor=#D4A574
dialogueTextColor=#E8D5B8

choiceBackgroundColor=#2A1A0A
choiceHoverColor=#3A2A1A
choiceSelectedColor=#4A3A2A
choiceDisabledColor=#1A1008
choiceTextColor=#C8A888
choiceHoverTextColor=#E8C8A8
choiceSelectedTextColor=#FFE8C8
choiceDisabledTextColor=#6A5A4A
choiceBorderColor=#4A3A2A
choiceHoverBorderColor=#6A5A4A
choiceSelectedBorderColor=#8A7A6A
choiceDisabledBorderColor=#2A2218
```

---

## State Color Guidelines

### Dialogue Choice States

Design all four states as a progression of intensity:

```text
Disabled  →  Normal  →  Hover  →  Selected
(dimmest)                         (brightest)
```

**Dark themes:** brighten the background and text progressively.
**Light themes:** darken or saturate the background progressively.

### Menu Item States

Menu items only have text colors (no per-item backgrounds unless using button assets):

```text
Disabled (#808080)  →  Normal (#CCCCCC)  →  Hover (#EEEEEE)  →  Selected (#FFD700)
```

The selected prefix (`itemSelectedPrefix`) provides an additional visual cue beyond color.

---

## Contrast and Readability

### Minimum Contrast Guidelines

- **Text on background:** Ensure at least 4.5:1 contrast ratio for body text
- **Disabled text:** Can have lower contrast (2:1) to indicate non-interactivity
- **Selected state:** Should be clearly distinguishable from normal state

### Testing Contrast

Run your project and check:
1. Can you read normal text easily?
2. Can you tell which item is selected at a glance?
3. Can you distinguish disabled items from normal items?
4. On a bright monitor and a dim monitor, is everything still readable?

---

## Sharing Colors Across Files

The properties format doesn't support variables, so you can't define `PRIMARY=#FFD700` once and reuse it. Instead:

1. **Document your palette** in a comment at the top of each file:
   ```properties
   # Theme: Dark Blue
   # Primary: #FFD700  Secondary: #B8C4D8  Muted: #4A5568
   ```

2. **Use inheritance** to share across style variants:
   ```properties
   # base.style defines the palette
   # dark_variant.style extends=base and overrides only what differs
   ```

3. **Keep a palette reference file** (not loaded by engine) for your team:
   ```properties
   # config/menu/palette-reference.txt (not a .style file)
   # bg=#0A0A1A  surface=#1A1A3A  primary=#FFD700  ...
   ```

---

## Runtime Validation Checklist

- [ ] All color values render as expected (no white/black fallbacks)
- [ ] Alpha transparency is visible (semi-transparent elements show content behind)
- [ ] All four choice states are visually distinct
- [ ] All four menu item states are visually distinct
- [ ] Text is readable against its background in every state
- [ ] Disabled elements are clearly non-interactive
- [ ] The overall palette feels cohesive across dialogue and menu screens
- [ ] Shadow colors are visible but subtle
- [ ] Background opacity works as expected

---

## Common Mistakes

**Missing alpha causes unexpected opacity:**
`#000000` is solid black. If you wanted semi-transparent, use `#000000AA`.

**Color looks wrong -- RGB vs BGR:**
JVN uses standard RGB order: `#RRGGBB`. `#FF0000` is red, not blue.

**Invisible text:**
Text color matches background. Always check contrast.

**Forgot a state:**
Setting `choiceTextColor` but forgetting `choiceSelectedTextColor` means the selected state uses whatever the engine default is, which may clash with your theme.

**Alpha stacking:**
A semi-transparent textbox on a semi-transparent background makes the combined area more transparent than intended. Test the full stack.

---

## Related Docs

- [Dialogue Layout & Style](dialogue-layout.md)
- [Choice Buttons](choice-buttons.md)
- [Menu Styles](../menus-submenus/menu-styles.md)
- [Fonts & Typography](fonts-typography.md)
- [Assets & Backgrounds](assets-backgrounds.md)

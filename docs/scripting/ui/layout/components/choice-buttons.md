# Choice Buttons

Complete guide to configuring VN dialogue choice buttons — positioning, sizing, colors, borders, fonts, button images, and state-based styling.

Layout spec: `modules/core/src/main/java/com/jvn/core/vn/ui/VnUiLayoutSpec.java`
Style spec: `modules/core/src/main/java/com/jvn/core/vn/ui/VnUiStyleSpec.java`
Loader: `modules/core/src/main/java/com/jvn/core/vn/ui/VnUiLayoutLoader.java`

---

## Overview

Choice buttons appear during VNS branching dialogue when the player must select from options. They are configured in the `dialogue.layout` file alongside the textbox and name box. Choice buttons support four visual states: normal, hover, selected, and disabled, each with independent colors, text colors, border colors, and optional image assets.

---

## Geometry Keys

All choice geometry is defined in `config/ui/dialogue.layout`:

```properties
choiceXCenter=0.5
choiceYStart=-1
choiceWidthFactor=0.6
choiceHeight=50
choiceGap=10
choiceTextXPadding=20
```

| Key | Default | Range | Description |
|-----|---------|-------|-------------|
| `choiceXCenter` | `0.5` | 0.0–1.0 | Horizontal center (fraction of viewport) |
| `choiceYStart` | `-1.0` | -1 or 0.0–1.0 | Top of first choice (-1 = auto-center) |
| `choiceWidthFactor` | `0.6` | 0.1–1.0 | Width as fraction of viewport |
| `choiceHeight` | `50.0` | ≥ 14 | Height per button (pixels) |
| `choiceGap` | `10.0` | ≥ 0 | Vertical gap between buttons (pixels) |
| `choiceTextXPadding` | `20.0` | ≥ 0 | Horizontal text padding inside button (pixels) |

### How Positioning Works

Choices are laid out as a vertical stack. The engine calculates:

1. **Total height** = `(count × choiceHeight) + ((count - 1) × choiceGap)`
2. **Left edge** = `(viewportWidth × choiceXCenter) - (choiceWidth / 2)`
3. **Top edge**:
   - If `choiceYStart = -1` → auto-centered: `(viewportHeight - totalHeight) / 2`
   - If `choiceYStart ≥ 0` → fixed: `viewportHeight × choiceYStart`

---

## Examples: Positioning

### Centered on Screen (Default)

```properties
choiceXCenter=0.5
choiceYStart=-1
choiceWidthFactor=0.6
choiceHeight=50
choiceGap=10
```

Choices appear centered both horizontally and vertically.

### Offset to the Right

```properties
choiceXCenter=0.7
choiceYStart=-1
choiceWidthFactor=0.4
```

Choices shift to the right side of the screen. Useful when a character sprite is on the left.

### Pinned to Upper Third

```properties
choiceXCenter=0.5
choiceYStart=0.2
choiceWidthFactor=0.5
```

Choices appear starting at 20% from the top, left auto-centered.

### Full-Width Choices

```properties
choiceWidthFactor=1.0
choiceXCenter=0.5
```

Choices span the entire viewport width.

### Compact Choices (Small Buttons)

```properties
choiceWidthFactor=0.35
choiceHeight=36
choiceGap=6
choiceTextXPadding=12
```

Smaller buttons for games with many choices.

### Tall Choices (Paragraph Text)

```properties
choiceHeight=80
choiceGap=12
choiceTextXPadding=24
```

Taller buttons for choices that contain long descriptive text.

---

## Button State Colors

Four states, each with background, text, and border colors:

```properties
# ── Background colors ──
choiceBackgroundColor=#2A2A4A
choiceHoverColor=#3A3A6A
choiceSelectedColor=#4A4A8A
choiceDisabledColor=#1A1A2A

# ── Text colors ──
choiceTextColor=#FFFFFF
choiceHoverTextColor=#FFE8A3
choiceSelectedTextColor=#FFD700
choiceDisabledTextColor=#666666

# ── Border colors ──
choiceBorderColor=#555588
choiceHoverBorderColor=#7777AA
choiceSelectedBorderColor=#9999CC
choiceDisabledBorderColor=#333355
```

### State Descriptions

| State | When | Typical Look |
|-------|------|-------------|
| **Normal** | Default, no interaction | Neutral background, readable text |
| **Hover** | Mouse cursor over button | Brighter, inviting |
| **Selected** | Keyboard focus on button | Highlighted, prominent |
| **Disabled** | Choice is not selectable | Dimmed, muted |

### Color Format

All colors use hex: `#RGB`, `#RRGGBB`, or `#RRGGBBAA` (with alpha channel).

```properties
# Solid white
choiceTextColor=#FFFFFF

# Semi-transparent black background
choiceBackgroundColor=#00000088

# Fully transparent (invisible)
choiceDisabledColor=#00000000
```

---

## Examples: Color Themes

### Dark Blue Theme

```properties
choiceBackgroundColor=#1A1A3A
choiceHoverColor=#2A2A5A
choiceSelectedColor=#3A3A7A
choiceDisabledColor=#0A0A1A
choiceTextColor=#C8D0E8
choiceHoverTextColor=#FFE8A3
choiceSelectedTextColor=#FFFFFF
choiceDisabledTextColor=#4A4A6A
choiceBorderColor=#3A3A6A
choiceHoverBorderColor=#5A5A8A
choiceSelectedBorderColor=#7A7AAA
choiceDisabledBorderColor=#2A2A3A
```

### Light/Pastel Theme

```properties
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

### Transparent Overlay

```properties
choiceBackgroundColor=#00000066
choiceHoverColor=#00000099
choiceSelectedColor=#000000CC
choiceDisabledColor=#00000033
choiceTextColor=#FFFFFF
choiceHoverTextColor=#FFD700
choiceSelectedTextColor=#FFD700
choiceDisabledTextColor=#888888
choiceBorderColor=#FFFFFF44
choiceHoverBorderColor=#FFFFFF88
choiceSelectedBorderColor=#FFFFFFCC
choiceDisabledBorderColor=#FFFFFF22
```

### No Background (Text Only)

```properties
choiceBackgroundColor=#00000000
choiceHoverColor=#FFFFFF11
choiceSelectedColor=#FFFFFF22
choiceDisabledColor=#00000000
choiceTextColor=#AAAAAA
choiceHoverTextColor=#FFFFFF
choiceSelectedTextColor=#FFD700
choiceDisabledTextColor=#555555
choiceBorderColor=#00000000
choiceHoverBorderColor=#00000000
choiceSelectedBorderColor=#00000000
choiceDisabledBorderColor=#00000000
```

---

## Border & Corner Geometry

```properties
choiceCornerRadius=10
choiceBorderWidth=2
choiceTextBaselineOffset=5
choiceTextXAlign=0.0
```

| Key | Default | Range | Description |
|-----|---------|-------|-------------|
| `choiceCornerRadius` | `10.0` | 0–96 | Rounded corner radius (pixels) |
| `choiceBorderWidth` | `2.0` | 0–12 | Border thickness (pixels) |
| `choiceTextBaselineOffset` | `5.0` | -120 to 120 | Vertical text offset within button |
| `choiceTextXAlign` | `0.0` | 0–1 | Horizontal alignment inside the padded choice text area (`0.0` left, `0.5` center, `1.0` right) |

### Examples

**Sharp rectangles:**
```properties
choiceCornerRadius=0
choiceBorderWidth=1
```

**Pill-shaped buttons:**
```properties
choiceCornerRadius=25
choiceBorderWidth=0
```

**Thick bordered cards:**
```properties
choiceCornerRadius=4
choiceBorderWidth=4
```

**No border:**
```properties
choiceBorderWidth=0
```

---

## Font Settings

```properties
choiceFontFamily=Segoe UI
choiceFontSize=20
```

| Key | Default | Description |
|-----|---------|-------------|
| `choiceFontFamily` | *(system default)* | Font family name |
| `choiceFontSize` | *(system default)* | Font size (> 0) |

### Examples

```properties
# Monospace for a terminal/hacker theme
choiceFontFamily=Courier New
choiceFontSize=16

# Serif for a literary feel
choiceFontFamily=Georgia
choiceFontSize=18

# Large readable text
choiceFontFamily=Segoe UI
choiceFontSize=24
```

---

## Button Image Assets

Instead of (or in addition to) color fills, you can use images for each state:

```properties
choiceButtonAsset=assets/ui/choice_normal.png
choiceButtonHoverAsset=assets/ui/choice_hover.png
choiceButtonSelectedAsset=assets/ui/choice_selected.png
choiceButtonDisabledAsset=assets/ui/choice_disabled.png
choiceButtonBoundsPoints=0,0;1,0;1,1;0,1
```

| Key | Description |
|-----|-------------|
| `choiceButtonAsset` | Normal state background image |
| `choiceButtonHoverAsset` | Hover state background image |
| `choiceButtonSelectedAsset` | Selected/focused state image |
| `choiceButtonDisabledAsset` | Disabled state image |
| `choiceButtonBoundsPoints` | (optional) Hit-test polygon (normalized to button rect) |

### Asset Behavior

- Images are stretched to fill the button rect (`choiceWidthFactor × choiceHeight`)
- Background colors still render behind the image (visible if image has transparency)
- Text renders on top of the image with `choiceTextXPadding` offset
- If an asset path is invalid, the engine logs a warning and falls back to color fill

### Example: Image-Based Choices

```properties
# Geometry
choiceWidthFactor=0.55
choiceHeight=60
choiceGap=8
choiceTextXPadding=30

# Images
choiceButtonAsset=assets/ui/choice/default.png
choiceButtonHoverAsset=assets/ui/choice/hover.png
choiceButtonSelectedAsset=assets/ui/choice/selected.png

# Text on top of images
choiceTextColor=#FFFFFF
choiceHoverTextColor=#FFD700
choiceSelectedTextColor=#FFD700
choiceFontFamily=Segoe UI
choiceFontSize=18

# No border needed with images
choiceBorderWidth=0
choiceCornerRadius=0
```

### Custom Hit-Test Shape

For non-rectangular button images, define a polygon:

```properties
# Hexagonal hit area
choiceButtonBoundsPoints=0.05,0.5;0.15,0;0.85,0;0.95,0.5;0.85,1;0.15,1
```

Points are normalized (0–1) relative to the button rect. Minimum 3 points.

---

## VNS Integration

Choices appear when a VNS script uses the choice syntax:

```vns
narrator: What do you want to do?

> Go to the forest -> forest_path
> Visit the town -> town_path
> Stay here -> stay
```

Each `>` line becomes a choice button. The layout properties in `dialogue.layout` control how they appear.

### Disabled Choices (Conditional)

```vns
@if has_key
> Unlock the door -> unlock
@endif
> Try to force it open -> force
> Walk away -> leave
```

Choices hidden by `@if` don't appear at all. For choices that appear but are disabled (greyed out), handle this through the VN runtime API.

---

## Complete Example

```properties
# config/ui/dialogue.layout (choice section)

# ── Position & Size ──
choiceXCenter=0.5
choiceYStart=-1
choiceWidthFactor=0.55
choiceHeight=52
choiceGap=8
choiceTextXPadding=24

# ── Colors: Dark RPG theme ──
choiceBackgroundColor=#1E1E2EDD
choiceHoverColor=#2E2E4EDD
choiceSelectedColor=#3E3E6EDD
choiceDisabledColor=#12121EAA
choiceTextColor=#C0C8E0
choiceHoverTextColor=#FFE8A3
choiceSelectedTextColor=#FFD700
choiceDisabledTextColor=#505068
choiceBorderColor=#3A3A5A
choiceHoverBorderColor=#5A5A8A
choiceSelectedBorderColor=#8A8ACE
choiceDisabledBorderColor=#1A1A2A

# ── Geometry ──
choiceCornerRadius=6
choiceBorderWidth=2
choiceTextBaselineOffset=0

# ── Font ──
choiceFontFamily=Segoe UI
choiceFontSize=18
```

---

## Runtime Validation Checklist

- [ ] Choice buttons appear when a VNS choice is reached
- [ ] Buttons are centered (or positioned) as configured
- [ ] Button width and height match expectations
- [ ] Gap between buttons is visible and consistent
- [ ] Text doesn't clip against button edges (check `choiceTextXPadding`)
- [ ] Normal state: correct background, text, and border colors
- [ ] Hover state: colors change when mouse moves over a button
- [ ] Selected state: colors change when navigating with keyboard
- [ ] Disabled state: dimmed colors, button is not activatable
- [ ] If using image assets: images load, display correctly, and state-switch works
- [ ] Corner radius is visible on all buttons
- [ ] Border width is consistent
- [ ] Font family and size render correctly
- [ ] With many choices (5+), buttons don't overflow the screen
- [ ] `choiceTextBaselineOffset` aligns text vertically within the button

---

## Common Mistakes

**Choices overflow the screen:**
Too many choices with large `choiceHeight` and `choiceGap`. Reduce sizes or use `choiceYStart=0.1` to start higher.

**Text clipped at edges:**
`choiceTextXPadding` is too small for the font size. Increase padding.

**Colors look wrong:**
Check alpha channel. `#FF0000` is solid red; `#FF000088` is semi-transparent red.

**Image stretching looks bad:**
Design button images at the exact aspect ratio of `choiceWidthFactor * viewportWidth` : `choiceHeight`.

**choiceYStart=0 puts buttons at the very top:**
This is a fraction, not pixels. `0.0` = top edge. Use `0.2` or higher, or `-1` for auto-center.

---

## Related Docs

- [Dialogue Layout & Style](dialogue-layout.md)
- [Character Framing](character-framing.md)
- [Textbox Action Buttons](textbox-action-buttons.md)
- [Colors & Theming](../styling/colors-theming.md)
- [VNS Choices](../../../vns/language/vns-choices.md)

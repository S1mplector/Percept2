# Dialogue Layout & Style

Complete reference for configuring the VN dialogue UI — textbox geometry, name box, dialogue text, choice buttons, textbox action buttons, fonts, colors, and character framing.

Layout spec: `core/src/main/java/com/jvn/core/vn/ui/VnUiLayoutSpec.java`
Style spec: `core/src/main/java/com/jvn/core/vn/ui/VnUiStyleSpec.java`
Loader: `core/src/main/java/com/jvn/core/vn/ui/VnUiLayoutLoader.java`
Action buttons: `core/src/main/java/com/jvn/core/vn/ui/VnUiActionButtonSpec.java`

---

## Overview

The dialogue layout system controls the visual appearance of VN dialogue scenes — the textbox, name box, dialogue text area, choice buttons, and optional action buttons overlaid on the textbox. All values are defined in a `.layout` properties file.

---

## File Location

Default search order (first match wins):

1. Path configured via `dialogueLayout` key in `jvn.project`
2. `config/ui/dialogue.layout`
3. `config/vn/dialogue.layout`
4. `dialogue.layout`

### Configuring via jvn.project

```properties
# jvn.project
dialogueLayout=config/ui/my_custom_dialogue.layout
```

---

## Textbox Geometry

The textbox is the main dialogue panel at the bottom of the screen.

```properties
# Position and size (normalized 0–1 of viewport)
textBoxX=0
textBoxY=0.75
textBoxWidth=1.0
textBoxHeight=0.25

# Internal padding (pixels)
textBoxPadding=20
```

| Property | Default | Range | Description |
|----------|---------|-------|-------------|
| `textBoxX` | 0.0 | 0–1 | Left edge (fraction of viewport width) |
| `textBoxY` | 0.75 | 0–1 | Top edge (fraction of viewport height) |
| `textBoxWidth` | 1.0 | 0.05–1 | Width (fraction of viewport) |
| `textBoxHeight` | 0.25 | 0.05–1 | Height (fraction of viewport) |
| `textBoxPadding` | 20 | ≥ 0 | Internal padding in pixels |

The textbox is clamped so `x + width ≤ 1.0` and `y + height ≤ 1.0`.

### Textbox Visual Style

```properties
textBoxAsset=assets/ui/textbox.png
textBoxColor=#1a1a2eCC
textBoxOpacity=0.9
textBoxBoundsPoints=0.02,0;0.98,0;1,0.1;1,1;0,1;0,0.1
```

| Property | Default | Description |
|----------|---------|-------------|
| `textBoxAsset` | — | Image asset for the textbox background |
| `textBoxColor` | — | Fallback color if no asset (hex with optional alpha) |
| `textBoxOpacity` | — | Opacity override (0–1) |
| `textBoxBoundsPoints` | — | Polygon hit-test shape (normalized `x,y;x,y;...`) |

---

## Name Box

The name box displays the speaking character's name, positioned relative to the textbox.

```properties
# Position relative to textbox (pixels)
nameBoxXOffset=20
nameBoxYOffset=-40

# Size (pixels)
nameBoxWidth=200
nameBoxHeight=40

# Text position within name box (pixels)
nameTextXOffset=10
nameTextBaselineOffset=25

# Dynamic width (optional)
nameBoxAutoWidth=false
```

| Property | Default | Description |
|----------|---------|-------------|
| `nameBoxXOffset` | 20 | Horizontal offset from textbox left edge |
| `nameBoxYOffset` | -40 | Vertical offset from textbox top (negative = above) |
| `nameBoxWidth` | 200 | Name box width in pixels (also acts as minimum when auto-width is on) |
| `nameBoxHeight` | 40 | Name box height in pixels |
| `nameTextXOffset` | 10 | Text X offset inside name box |
| `nameTextBaselineOffset` | 25 | Text baseline Y offset inside name box |
| `nameBoxAutoWidth` | false | When `true`, the name box width expands to fit the speaker's name text. The fixed `nameBoxWidth` becomes the minimum width. |

### Name Box Auto-Width

When `nameBoxAutoWidth=true`, the renderer measures the speaker's name text at runtime
and expands the name box to fit. The formula is:

```
effective width = max(nameBoxWidth, measuredTextWidth + nameTextXOffset × 2)
```

This ensures short names (e.g., "Al") still use the minimum `nameBoxWidth`, while longer
names (e.g., "Professor Henderson") expand the box gracefully. The name box image or
color fill stretches to the computed width.

### Name Box Visual Style

```properties
nameBoxAsset=assets/ui/namebox.png
nameBoxColor=#2a2a4eDD
nameBoxOpacity=1.0
nameTextColor=#FFE8A3
nameTextFontFamily=Georgia
nameTextFontSize=20
nameTextFontWeight=BOLD
nameBoxBoundsPoints=0,0;1,0;1,1;0,1
```

| Property | Default | Description |
|----------|---------|-------------|
| `nameBoxAsset` | — | Image asset for the name box background |
| `nameBoxColor` | — | Fallback color if no asset (hex with optional alpha) |
| `nameBoxOpacity` | — | Name box opacity override (0–1) |
| `nameTextColor` | — | Name text color (hex) |
| `nameTextFontFamily` | — | Font family for the name text |
| `nameTextFontSize` | — | Font size for the name text (> 0) |
| `nameTextFontWeight` | — | Font weight: `NORMAL` or `BOLD` |
| `nameBoxBoundsPoints` | — | Polygon hit-test shape (normalized `x,y;x,y;...`) |

---

## Dialogue Text

Controls the text area within the textbox where dialogue lines are rendered.

```properties
# Padding inside textbox for dialogue text (pixels)
dialogueTextHorizontalPadding=20
dialogueTextTopPadding=40
dialogueTextRightPadding=20
dialogueTextBottomPadding=10
```

| Property | Default | Description |
|----------|---------|-------------|
| `dialogueTextHorizontalPadding` | 20 | Left padding |
| `dialogueTextTopPadding` | 40 | Top padding (below name box area) |
| `dialogueTextRightPadding` | 20 | Right padding (defaults to horizontal if omitted) |
| `dialogueTextBottomPadding` | 10 | Bottom padding |

### Dialogue Text Visual Style

```properties
dialogueTextColor=#FFFFFF
dialogueTextFontFamily=Segoe UI
dialogueTextFontSize=18
dialogueTextFontWeight=NORMAL
dialogueTextBoundsPoints=0,0;1,0;1,1;0,1
```

| Property | Default | Description |
|----------|---------|-------------|
| `dialogueTextColor` | — | Dialogue body text color (hex) |
| `dialogueTextFontFamily` | — | Font family for dialogue text |
| `dialogueTextFontSize` | — | Font size for dialogue text (> 0) |
| `dialogueTextFontWeight` | — | Font weight: `NORMAL` or `BOLD` |
| `dialogueTextBoundsPoints` | — | Custom bounds polygon for the dialogue text area |

---

## Choice Buttons

Choice buttons appear when the player must select from dialogue options.

```properties
# Position and sizing (normalized/pixels)
choiceXCenter=0.5
choiceYStart=-1
choiceWidthFactor=0.6
choiceHeight=50
choiceGap=10
choiceTextXPadding=20
```

| Property | Default | Description |
|----------|---------|-------------|
| `choiceXCenter` | 0.5 | Horizontal center (fraction of viewport, 0–1) |
| `choiceYStart` | -1 | Top of first choice (-1 = auto-center vertically) |
| `choiceWidthFactor` | 0.6 | Width as fraction of viewport (0.1–1) |
| `choiceHeight` | 50 | Height per choice button (pixels) |
| `choiceGap` | 10 | Vertical gap between choices (pixels) |
| `choiceTextXPadding` | 20 | Text padding inside choice button |

### Choice Visual Style

```properties
# Button assets (4 states)
choiceButtonAsset=assets/ui/choice_normal.png
choiceButtonHoverAsset=assets/ui/choice_hover.png
choiceButtonSelectedAsset=assets/ui/choice_selected.png
choiceButtonDisabledAsset=assets/ui/choice_disabled.png
choiceButtonBoundsPoints=0,0;1,0;1,1;0,1

# Background colors (fallback if no assets)
choiceBackgroundColor=#2a2a4eCC
choiceHoverColor=#3a3a6eCC
choiceSelectedColor=#4a4a8eCC
choiceDisabledColor=#1a1a2e88

# Text colors (4 states)
choiceTextColor=#FFFFFF
choiceHoverTextColor=#FFE8A3
choiceSelectedTextColor=#FFF5CC
choiceDisabledTextColor=#808080

# Border colors (4 states)
choiceBorderColor=#4a4a8e
choiceHoverBorderColor=#6a6aae
choiceSelectedBorderColor=#8a8ace
choiceDisabledBorderColor=#3a3a5e

# Geometry
choiceCornerRadius=8
choiceBorderWidth=2
choiceTextBaselineOffset=0

# Font
choiceFontFamily=Segoe UI
choiceFontSize=18
choiceFontWeight=NORMAL
```

> **Font weight values:** All `*FontWeight` properties accept `NORMAL` or `BOLD`.

---

## Character Framing

Controls how character sprites are positioned and scaled in the dialogue scene.

```properties
characterHeightFactor=0.85
characterBaselineY=0.95
```

| Property | Default | Description |
|----------|---------|-------------|
| `characterHeightFactor` | — | Character height as fraction of viewport height |
| `characterBaselineY` | — | Baseline Y position (fraction of viewport) |

---

## Textbox Action Buttons

Clickable buttons overlaid on the textbox (e.g., auto-play, skip, log, save).

```properties
# Declare button IDs
textBoxButton.ids=auto,skip,log,save

# Auto button
textBoxButton.auto.label=Auto
textBoxButton.auto.action=mode
textBoxButton.auto.target=auto
textBoxButton.auto.enabled=true
textBoxButton.auto.asset=assets/ui/btn_auto.png
textBoxButton.auto.hoverAsset=assets/ui/btn_auto_hover.png
textBoxButton.auto.x=0.82
textBoxButton.auto.y=0.02
textBoxButton.auto.width=0.08
textBoxButton.auto.height=0.15

# Skip button
textBoxButton.skip.label=Skip
textBoxButton.skip.action=mode
textBoxButton.skip.target=skip
textBoxButton.skip.x=0.90
textBoxButton.skip.y=0.02
textBoxButton.skip.width=0.08
textBoxButton.skip.height=0.15

# Log button
textBoxButton.log.label=Log
textBoxButton.log.action=history
textBoxButton.log.target=toggle
textBoxButton.log.x=0.82
textBoxButton.log.y=0.20
textBoxButton.log.width=0.08
textBoxButton.log.height=0.15

# Save button
textBoxButton.save.label=Save
textBoxButton.save.action=save
textBoxButton.save.target=quick
textBoxButton.save.x=0.90
textBoxButton.save.y=0.20
textBoxButton.save.width=0.08
textBoxButton.save.height=0.15
```

### Button Properties

| Property | Default | Description |
|----------|---------|-------------|
| `label` | button ID | Display text |
| `action` | `"noop"` | Action type (e.g., `mode`, `history`, `save`, `ui`) |
| `target` | — | Action target (e.g., `auto`, `skip`, `toggle`, `quick`) |
| `enabled` | true | Whether the button is active |
| `asset` | — | Normal state image |
| `hoverAsset` | — | Hover state image |
| `disabledAsset` | — | Disabled state image |
| `boundsPoints` | — | Polygon hit-test points (normalized to button rect) |
| `x` | 0 | Left edge (fraction of textbox, 0–1) |
| `y` | 0 | Top edge (fraction of textbox, 0–1) |
| `width` | 0.12 | Width (fraction of textbox, 0.01–1) |
| `height` | 0.25 | Height (fraction of textbox, 0.01–1) |

Button coordinates are **relative to the textbox rectangle**, not the viewport.

---

## Complete Example

```properties
# === Textbox ===
textBoxX=0.05
textBoxY=0.72
textBoxWidth=0.90
textBoxHeight=0.26
textBoxPadding=16
textBoxAsset=assets/ui/textbox_ornate.png
textBoxOpacity=0.95

# === Name Box ===
nameBoxXOffset=24
nameBoxYOffset=-38
nameBoxWidth=220
nameBoxHeight=38
nameTextXOffset=12
nameTextBaselineOffset=24
nameBoxAutoWidth=true
nameBoxAsset=assets/ui/namebox_ornate.png
nameTextColor=#FFE8A3
nameTextFontFamily=Georgia
nameTextFontSize=18
nameTextFontWeight=BOLD
# nameBoxOpacity=1.0

# === Dialogue Text ===
dialogueTextHorizontalPadding=24
dialogueTextTopPadding=36
dialogueTextRightPadding=24
dialogueTextBottomPadding=12
dialogueTextColor=#F0F0F0
dialogueTextFontFamily=Noto Sans
dialogueTextFontSize=16
# dialogueTextFontWeight=NORMAL

# === Choices ===
choiceXCenter=0.5
choiceYStart=-1
choiceWidthFactor=0.55
choiceHeight=48
choiceGap=8
choiceTextXPadding=16
choiceButtonAsset=assets/ui/choice.png
choiceButtonHoverAsset=assets/ui/choice_hover.png
choiceBackgroundColor=#2a2a4eCC
choiceHoverColor=#3a3a6eCC
choiceTextColor=#FFFFFF
choiceHoverTextColor=#FFE8A3
choiceBorderColor=#4a4a8eAA
choiceCornerRadius=6
choiceBorderWidth=1
choiceFontFamily=Noto Sans
choiceFontSize=16
# choiceFontWeight=NORMAL

# === Character Framing ===
characterHeightFactor=0.82
characterBaselineY=0.95

# === Textbox Buttons ===
textBoxButton.ids=auto,skip,log
textBoxButton.auto.label=AUTO
textBoxButton.auto.action=mode
textBoxButton.auto.target=auto
textBoxButton.auto.x=0.85
textBoxButton.auto.y=0.03
textBoxButton.auto.width=0.06
textBoxButton.auto.height=0.12
textBoxButton.skip.label=SKIP
textBoxButton.skip.action=mode
textBoxButton.skip.target=skip
textBoxButton.skip.x=0.92
textBoxButton.skip.y=0.03
textBoxButton.skip.width=0.06
textBoxButton.skip.height=0.12
textBoxButton.log.label=LOG
textBoxButton.log.action=history
textBoxButton.log.target=toggle
textBoxButton.log.x=0.85
textBoxButton.log.y=0.18
textBoxButton.log.width=0.06
textBoxButton.log.height=0.12
```

---

## Text Mapping & Localization

Dialogue text supports runtime variable interpolation via `VnTextFormatter`. Text keys
defined in `config/locales/*.properties` are resolved at runtime:

```properties
# config/locales/en.properties
greeting=Hello, {name}!
farewell=Goodbye, {name}. Until we meet again.
```

See [Localization Workflow](../../../../project-setup/content/localization.md) for full details.

---

## Runtime Behavior

The `VnRenderer` (JavaFX) consumes all style properties at load time via `applyUiStyle`:

- **Font weights** (`nameTextFontWeight`, `dialogueTextFontWeight`, `choiceFontWeight`) are
  resolved to `FontWeight` values. If omitted, name defaults to `BOLD`, dialogue and choice
  default to `NORMAL`.
- **Name box opacity** (`nameBoxOpacity`) is applied as a global alpha multiplier when drawing
  the name box background. Defaults to `1.0` (fully opaque).
- **Name box auto-width** (`nameBoxAutoWidth`) causes the renderer to measure the current
  speaker's name text each frame and expand the name box width to fit. The fixed `nameBoxWidth`
  is used as the minimum width, so the box never shrinks below that baseline.
- All other font, color, and asset properties are applied identically.

---

## Editor Support

The dialogue layout is edited visually in the **Dialogue Layout Editor**:

- **Collapsible sections** for textbox, name box, text, choices, buttons
- **Resize handles** for textbox bounds
- **Font weight** selectors for name text, dialogue text, and choice text (`NORMAL` / `BOLD`)
- **Name box opacity** slider (0–1)
- **Name box auto-width** checkbox — toggles dynamic name box sizing
- **ColorPicker** for all color fields
- **Bounds Studio** for textbox button placement (visual drag/draw tool)
- **Live preview** canvas
- **Ctrl+Z / Ctrl+Y** undo/redo

---

## Related Docs

- [Menu Profiles Overview](../../menus/menu-profiles.md)
- [Menu Screens](../../menus/menu-screens.md)
- [Menu Styles](../../menus/menu-styles.md)
- [VNS Dialogue & Text](../../../vns/language/vns-dialogue.md)
- [VNS Characters & Sprites](../../../vns/presentation/vns-characters.md)

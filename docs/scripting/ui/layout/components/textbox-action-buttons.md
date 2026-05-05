# Textbox Action Buttons

Complete guide to configuring clickable action buttons overlaid on the VN dialogue textbox — Auto, Skip, Log, Save, and custom buttons.

Loader: `modules/core/src/main/java/com/jvn/core/vn/ui/VnUiLayoutLoader.java`
Spec: `modules/core/src/main/java/com/jvn/core/vn/ui/VnUiActionButtonSpec.java`

---

## Overview

Textbox action buttons are small clickable controls rendered on top of the dialogue text box. They provide quick access to common VN functions — auto-play, skip, backlog, quick-save — without navigating to a menu. Buttons are declared in the same `dialogue.layout` file that controls the textbox geometry.

---

## File Location

Textbox action buttons are defined in the same file as the dialogue layout:

- `config/ui/dialogue.layout` (primary)
- `config/vn/dialogue.layout` (fallback)
- `dialogue.layout` (fallback)

---

## Declaration Syntax

Buttons use a `textBoxButton.` prefix (or `button.` prefix in newer files):

```properties
# Declare button IDs
textBoxButton.ids=auto,skip,log,save

# Per-button properties
textBoxButton.auto.label=AUTO
textBoxButton.auto.action=mode
textBoxButton.auto.target=auto
textBoxButton.auto.x=0.82
textBoxButton.auto.y=0.02
textBoxButton.auto.width=0.08
textBoxButton.auto.height=0.15
```

### Alternative Prefix

The `button.` prefix is also accepted:

```properties
button.ids=auto,skip,log
button.auto.label=AUTO
button.auto.action=mode
button.auto.target=auto
```

---

## Button Properties

| Property | Default | Description |
|----------|---------|-------------|
| `label` | button ID | Display text rendered on the button |
| `action` | `"noop"` | Action type (see table below) |
| `target` | — | Action target (depends on action type) |
| `enabled` | `true` | Whether the button is active |
| `x` | `0` | Left edge (fraction of textbox width, 0–1) |
| `y` | `0` | Top edge (fraction of textbox height, 0–1) |
| `width` | `0.12` | Width (fraction of textbox width, 0.01–1) |
| `height` | `0.25` | Height (fraction of textbox height, 0.01–1) |
| `asset` | — | Normal state image asset path |
| `hoverAsset` | — | Hover state image asset path |
| `disabledAsset` | — | Disabled state image asset path |
| `boundsPoints` | — | Polygon hit-test points (normalized to button rect) |

**Coordinate system:** All position and size values are **fractions of the textbox rectangle**, not the viewport. `x=0.82` means 82% from the left edge of the textbox.

---

## Action Types

| Action | Target | Description |
|--------|--------|-------------|
| `mode` | `auto` | Toggle auto-play mode |
| `mode` | `skip` | Toggle skip mode |
| `history` | `toggle` | Toggle backlog overlay |
| `history` | `show` | Show backlog overlay |
| `history` | `hide` | Hide backlog overlay |
| `save` | `quick` | Quick save to most recent slot |
| `save` | `menu` | Open save menu |
| `ui` | `hide` | Hide the textbox (click to restore) |
| `noop` | — | No action (decorative) |

---

## Examples

### Example 1: Standard Auto/Skip/Log Buttons

Three buttons in the top-right corner of the textbox:

```properties
textBoxButton.ids=auto,skip,log

# Auto-play toggle
textBoxButton.auto.label=AUTO
textBoxButton.auto.action=mode
textBoxButton.auto.target=auto
textBoxButton.auto.x=0.82
textBoxButton.auto.y=0.03
textBoxButton.auto.width=0.06
textBoxButton.auto.height=0.12

# Skip toggle
textBoxButton.skip.label=SKIP
textBoxButton.skip.action=mode
textBoxButton.skip.target=skip
textBoxButton.skip.x=0.89
textBoxButton.skip.y=0.03
textBoxButton.skip.width=0.06
textBoxButton.skip.height=0.12

# Backlog toggle
textBoxButton.log.label=LOG
textBoxButton.log.action=history
textBoxButton.log.target=toggle
textBoxButton.log.x=0.82
textBoxButton.log.y=0.18
textBoxButton.log.width=0.06
textBoxButton.log.height=0.12
```

### Example 2: Full Button Bar

Six buttons across the bottom of the textbox:

```properties
textBoxButton.ids=auto,skip,log,save,load,hide

textBoxButton.auto.label=Auto
textBoxButton.auto.action=mode
textBoxButton.auto.target=auto
textBoxButton.auto.x=0.55
textBoxButton.auto.y=0.82
textBoxButton.auto.width=0.07
textBoxButton.auto.height=0.14

textBoxButton.skip.label=Skip
textBoxButton.skip.action=mode
textBoxButton.skip.target=skip
textBoxButton.skip.x=0.63
textBoxButton.skip.y=0.82
textBoxButton.skip.width=0.07
textBoxButton.skip.height=0.14

textBoxButton.log.label=Log
textBoxButton.log.action=history
textBoxButton.log.target=toggle
textBoxButton.log.x=0.71
textBoxButton.log.y=0.82
textBoxButton.log.width=0.07
textBoxButton.log.height=0.14

textBoxButton.save.label=Save
textBoxButton.save.action=save
textBoxButton.save.target=quick
textBoxButton.save.x=0.79
textBoxButton.save.y=0.82
textBoxButton.save.width=0.07
textBoxButton.save.height=0.14

textBoxButton.load.label=Load
textBoxButton.load.action=save
textBoxButton.load.target=menu
textBoxButton.load.x=0.87
textBoxButton.load.y=0.82
textBoxButton.load.width=0.07
textBoxButton.load.height=0.14

textBoxButton.hide.label=Hide
textBoxButton.hide.action=ui
textBoxButton.hide.target=hide
textBoxButton.hide.x=0.93
textBoxButton.hide.y=0.82
textBoxButton.hide.width=0.06
textBoxButton.hide.height=0.14
```

### Example 3: Image-Based Buttons

Using custom button artwork instead of text labels:

```properties
textBoxButton.ids=auto,skip,log

textBoxButton.auto.label=
textBoxButton.auto.action=mode
textBoxButton.auto.target=auto
textBoxButton.auto.asset=assets/ui/btn_auto.png
textBoxButton.auto.hoverAsset=assets/ui/btn_auto_hover.png
textBoxButton.auto.disabledAsset=assets/ui/btn_auto_off.png
textBoxButton.auto.x=0.84
textBoxButton.auto.y=0.04
textBoxButton.auto.width=0.05
textBoxButton.auto.height=0.10

textBoxButton.skip.label=
textBoxButton.skip.action=mode
textBoxButton.skip.target=skip
textBoxButton.skip.asset=assets/ui/btn_skip.png
textBoxButton.skip.hoverAsset=assets/ui/btn_skip_hover.png
textBoxButton.skip.x=0.90
textBoxButton.skip.y=0.04
textBoxButton.skip.width=0.05
textBoxButton.skip.height=0.10

textBoxButton.log.label=
textBoxButton.log.action=history
textBoxButton.log.target=toggle
textBoxButton.log.asset=assets/ui/btn_log.png
textBoxButton.log.hoverAsset=assets/ui/btn_log_hover.png
textBoxButton.log.x=0.94
textBoxButton.log.y=0.04
textBoxButton.log.width=0.05
textBoxButton.log.height=0.10
```

### Example 4: Minimal — Just Auto and Skip

```properties
textBoxButton.ids=auto,skip

textBoxButton.auto.label=A
textBoxButton.auto.action=mode
textBoxButton.auto.target=auto
textBoxButton.auto.x=0.92
textBoxButton.auto.y=0.04
textBoxButton.auto.width=0.035
textBoxButton.auto.height=0.10

textBoxButton.skip.label=S
textBoxButton.skip.action=mode
textBoxButton.skip.target=skip
textBoxButton.skip.x=0.96
textBoxButton.skip.y=0.04
textBoxButton.skip.width=0.035
textBoxButton.skip.height=0.10
```

### Example 5: No Textbox Buttons

Simply don't declare any button IDs:

```properties
# No textBoxButton.ids line → no buttons rendered
```

---

## Positioning Guide

The textbox action button coordinate system:

- **x=0, y=0** = top-left corner of the textbox
- **x=1, y=1** = bottom-right corner of the textbox
- Button `width` and `height` are also fractions of the textbox

### Common Layouts

**Top-right row:**
```properties
# Button 1         Button 2         Button 3
# x=0.80           x=0.87           x=0.94
# y=0.03           y=0.03           y=0.03
# w=0.06           w=0.06           w=0.05
# h=0.12           h=0.12           h=0.12
```

**Bottom-right row:**
```properties
# Same x values but y=0.82, h=0.14
```

**Vertical stack on right edge:**
```properties
# x=0.92 for all
# y=0.05, 0.20, 0.35, 0.50 (stacked vertically)
# w=0.06, h=0.12
```

---

## Custom Hit-Test Shapes

For non-rectangular button images, define a polygon:

```properties
textBoxButton.auto.boundsPoints=0.1,0;0.9,0;1,0.5;0.9,1;0.1,1;0,0.5
```

Points are normalized (0–1) relative to the button's bounding rectangle. Minimum 3 points. The engine uses this polygon for mouse hit-testing instead of the rectangular bounds.

---

## Disabled Buttons

Buttons can be disabled at startup or conditionally by runtime code:

```properties
# Disabled by default (e.g., feature not yet implemented)
textBoxButton.save.enabled=false
textBoxButton.save.disabledAsset=assets/ui/btn_save_disabled.png
```

Disabled buttons:
- Render with the `disabledAsset` if provided
- Don't respond to mouse clicks
- Are visually distinct from enabled buttons

---

## Runtime Validation Checklist

- [ ] All declared buttons appear on the textbox
- [ ] Buttons are positioned correctly within the textbox rectangle
- [ ] Button labels render (if using text labels)
- [ ] Button images render (if using assets)
- [ ] Auto button toggles auto-play mode
- [ ] Skip button toggles skip mode
- [ ] Log button opens/closes the backlog overlay
- [ ] Save button triggers a quick save (or opens save menu)
- [ ] Hide button hides the textbox
- [ ] Hover state images swap on mouse-over
- [ ] Disabled buttons are visually distinct and non-clickable
- [ ] Buttons don't overlap each other
- [ ] Buttons don't obscure important dialogue text
- [ ] At different resolutions, buttons scale with the textbox

---

## Common Mistakes

**Buttons outside the textbox:**
If `x + width > 1.0` or `y + height > 1.0`, the button extends beyond the textbox. Ensure coordinates fit within the 0–1 range.

**Buttons overlap dialogue text:**
Position buttons in areas where text won't reach — typically the top-right corner or bottom edge. Adjust `dialogueTextRightPadding` to leave room.

**Wrong prefix:**
Using `button.` when the file uses `textBoxButton.` or vice versa. Check which prefix your file uses and be consistent.

**Forgetting the IDs declaration:**
```properties
# Wrong — properties exist but IDs are not declared
textBoxButton.auto.label=AUTO
textBoxButton.auto.action=mode

# Correct — declare IDs first
textBoxButton.ids=auto
textBoxButton.auto.label=AUTO
textBoxButton.auto.action=mode
```

---

## Related Docs

- [Dialogue Layout & Style](dialogue-layout.md)
- [Choice Buttons](choice-buttons.md)
- [Character Framing](character-framing.md)
- [VNS Settings & Modes](../../../vns/runtime/vns-settings-modes.md)

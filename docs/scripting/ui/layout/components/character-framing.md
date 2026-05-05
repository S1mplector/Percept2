# Character Framing & Sprites

Complete guide to configuring how character sprites are positioned and scaled in VN dialogue scenes — height factor, baseline positioning, and interaction with the dialogue textbox.

Style spec: `modules/core/src/main/java/com/jvn/core/vn/ui/VnUiStyleSpec.java`
Loader: `modules/core/src/main/java/com/jvn/core/vn/ui/VnUiLayoutLoader.java`

---

## Overview

Character framing controls how character sprites appear on screen relative to the viewport and the dialogue textbox. Two keys -- `characterHeightFactor` and `characterBaselineY` -- determine the vertical scaling and grounding of all character sprites in dialogue scenes.

These keys are defined in the same `config/ui/dialogue.layout` file as the textbox and choice button settings.

---

## Keys

```properties
characterHeightFactor=0.85
characterBaselineY=0.95
```

| Key | Type | Default | Range | Description |
|-----|------|---------|-------|-------------|
| `characterHeightFactor` | Double | *(none)* | 0.1--3.0 | Character sprite height as fraction of viewport height |
| `characterBaselineY` | Double | *(none)* | -0.5--2.0 | Baseline Y position where sprites are grounded (fraction of viewport) |

Both keys are **optional**. When omitted, the engine uses its internal defaults for sprite scaling and positioning.

---

## How Framing Works

### characterHeightFactor

Controls the vertical size of character sprites relative to the viewport:

- `0.5` = character fills 50% of the viewport height
- `0.85` = character fills 85% of the viewport height
- `1.0` = character fills the entire viewport height
- `1.5` = character is 150% of the viewport height (top cropped off-screen)

The engine scales the sprite proportionally -- width scales with height to preserve aspect ratio.

### characterBaselineY

Controls the vertical anchor point -- where the character's "feet" sit:

- `0.0` = top of the screen
- `0.75` = at the top edge of a standard textbox (textBoxY=0.75)
- `0.95` = near the bottom of the screen (slight margin)
- `1.0` = bottom edge of the viewport

The sprite is drawn with its bottom edge at `viewport_height * characterBaselineY`.

---

## Examples

### Standard VN (Characters Above Textbox)

Characters fill most of the screen height, feet just above the textbox:

```properties
textBoxY=0.75
textBoxHeight=0.25

characterHeightFactor=0.85
characterBaselineY=0.95
```

Result: Characters are tall (85% of screen), grounded near the bottom. Their lower body overlaps the textbox slightly (textbox is semi-transparent, so this is intentional).

### Characters Fully Above Textbox

No overlap with the textbox:

```properties
textBoxY=0.75
textBoxHeight=0.25

characterHeightFactor=0.70
characterBaselineY=0.75
```

Result: Characters are 70% screen height, feet exactly at the textbox top edge. No overlap.

### Tall Characters (Cropped at Top)

For close-up, dramatic framing:

```properties
characterHeightFactor=1.2
characterBaselineY=1.0
```

Result: Characters are 120% of screen height -- heads cropped at the top. Feet at the very bottom. Creates an intimate, imposing feel.

### Small Characters (Distant)

For wide establishing shots or chibi-style:

```properties
characterHeightFactor=0.40
characterBaselineY=0.85
```

Result: Small characters in the lower portion of the screen.

### Centered Characters (ADV Style)

For full-screen text boxes where characters float in the middle:

```properties
textBoxX=0.05
textBoxY=0.05
textBoxWidth=0.9
textBoxHeight=0.9

characterHeightFactor=0.60
characterBaselineY=0.70
```

Result: Characters are moderately sized and centered vertically behind the text overlay.

### Waist-Up Portraits

Common in mobile VNs:

```properties
characterHeightFactor=0.55
characterBaselineY=0.95
```

Result: Only the upper body is visible. The lower portion is "off-screen" below the viewport.

---

## Interaction with Textbox

Character sprites render **behind** the dialogue textbox. The textbox's `textBoxOpacity` and `textBoxColor` determine how visible characters are through the textbox area:

```properties
# Semi-transparent textbox -- characters visible through it
textBoxColor=#000000AA
textBoxOpacity=0.7
characterHeightFactor=0.90
characterBaselineY=0.95
```

```properties
# Opaque textbox -- characters only visible above it
textBoxColor=#1A1A2EFF
textBoxOpacity=1.0
characterHeightFactor=0.70
characterBaselineY=0.75
```

### Recommended Combinations

| Style | textBoxY | textBoxOpacity | heightFactor | baselineY | Notes |
|-------|----------|----------------|-------------|-----------|-------|
| Classic VN | 0.75 | 0.85 | 0.85 | 0.95 | Characters peek through textbox |
| No overlap | 0.75 | 1.0 | 0.70 | 0.75 | Clean separation |
| Dramatic | 0.80 | 0.70 | 1.10 | 1.0 | Large sprites, slight crop |
| Mobile | 0.70 | 0.90 | 0.55 | 0.90 | Compact, waist-up |
| ADV | 0.05 | 0.92 | 0.60 | 0.70 | Full-screen text overlay |

---

## Interaction with VNS Show Command

The VNS `[show]` command positions characters at named positions (left, center, right, etc.). `characterHeightFactor` and `characterBaselineY` apply globally to all positions:

```vns
[show hero center]
[show villain right]
```

Both `hero` and `villain` render at the configured `characterHeightFactor` height, with feet at `characterBaselineY`.

### Layering

When multiple characters are on screen, their left-to-right order determines default z-order. The VNS `[show]` command supports explicit layer control:

```vns
[show hero center happy 2]
[show villain right angry 1]
```

Layer order is independent of character framing -- framing controls size/position, layering controls draw order.

---

## Per-Character Overrides

The `characterHeightFactor` and `characterBaselineY` values apply to **all** characters uniformly. There is no per-character override in the layout file.

To achieve different sizes per character, use sprite assets of different inherent sizes. A character with a taller source image will appear proportionally taller when scaled by the same factor.

**Workaround for mixed sizes:** Design character sprites with built-in padding. A "short" character can have empty space above their head in the source image, so when scaled to the same height factor, they appear shorter than other characters.

---

## Complete Example

```properties
# config/ui/dialogue.layout -- character framing section

# ── Textbox (for context) ──
textBoxX=0.0
textBoxY=0.75
textBoxWidth=1.0
textBoxHeight=0.25
textBoxColor=#0A0A1ADD
textBoxOpacity=0.88

# ── Character Framing ──
# Characters fill 85% of screen height
characterHeightFactor=0.85

# Feet positioned near the bottom (overlapping textbox slightly)
characterBaselineY=0.95
```

---

## Runtime Validation Checklist

- [ ] Character sprites appear at the expected size relative to the viewport
- [ ] Characters are grounded at the expected vertical position
- [ ] With multiple characters on screen, they all use the same scaling
- [ ] Characters don't clip awkwardly at the top of the screen (if heightFactor > 1.0, this is intentional)
- [ ] The relationship between character feet and textbox top looks correct
- [ ] Semi-transparent textbox shows characters behind it
- [ ] Opaque textbox cleanly separates from characters above
- [ ] At different window sizes, character proportions remain consistent
- [ ] Sprite position names (left, center, right) distribute correctly

---

## Common Mistakes

**Characters too small:**
`characterHeightFactor=0.3` makes characters tiny. For standard VN, use 0.7--0.9.

**Characters floating above the ground:**
`characterBaselineY` is too low (e.g., 0.5). Increase it toward 0.9--1.0.

**Characters hidden behind opaque textbox:**
If `textBoxOpacity=1.0` and `characterBaselineY > textBoxY`, the lower portion of characters is invisible. Either reduce opacity or raise the baseline above the textbox.

**Unexpected cropping:**
`characterHeightFactor=1.5` means the top 33% of the character is above the viewport. This is valid for dramatic effect but surprising if unintentional.

---

## Related Docs

- [Dialogue Layout & Style](dialogue-layout.md)
- [Choice Buttons](choice-buttons.md)
- [VNS Characters & Sprites](../../../vns/presentation/vns-characters.md)
- [VNS Transitions](../../../vns/presentation/vns-transitions.md)

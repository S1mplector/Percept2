# Sidebar — Image Tint Tool

Standalone color tinting and grading utility for character and background images. Apply tint, saturation, and contrast adjustments with live preview.

Source: `editor/src/main/java/com/jvn/editor/ui/ImageTintToolView.java`

---

## Overview

The Image Tint Tool lets you experiment with color grading for VN scenes. Select a character image and/or background image, adjust tint color, strength, saturation, and contrast, then preview the result in real-time. Save named setups for reuse and export tint profiles for integration with the runtime.

Inspired by Ren'Py's Image Tint Tool, adapted for JVN's asset pipeline.

- **Default side:** Right
- **Tab name:** Image Tint Tool
- **Implements:** `ImageToolPanel` (shared interface for all three image tools)
- **State file:** `.jvn/image-tint-tool.properties`

---

## UI Layout

```text
┌──────────────────────────────────────┐
│  Image Tint Tool                     │
│  Character: [▼ hero_neutral      ]   │
│  Background: [▼ park             ]   │
├──────────────────────────────────────┤
│  ┌──────────────────────────────┐    │
│  │                              │    │
│  │    Tinted Preview            │    │
│  │    (drag to pan, scroll      │    │
│  │     to zoom, dbl-click       │    │
│  │     to reset)                │    │
│  │                              │    │
│  └──────────────────────────────┘    │
├──────────────────────────────────────┤
│  ▼ Tint Controls                     │
│  Tint Color: [■ #ffd4a0] (picker)   │
│  Tint Strength: [========== ] 30     │
│  Saturation:    [=====      ] 0      │
│  Contrast:      [=====      ] 0      │
├──────────────────────────────────────┤
│  Setups: [▼ Select...] [Save]        │
│  [Load] [Delete]                     │
├──────────────────────────────────────┤
│  [Export Profile] [Fullscreen]       │
└──────────────────────────────────────┘
```

---

## Tint Controls

| Control | Range | Default | Description |
|---------|-------|---------|-------------|
| **Tint Color** | ColorPicker | White (`#ffffff`) | The color to blend with the image |
| **Tint Strength** | 0 – 100 | 30 | Blend intensity: 0 = no tint, 100 = full color overlay |
| **Saturation** | -100 – 100 | 0 | Saturation adjustment: -100 = full grayscale, 0 = original, +100 = oversaturated |
| **Contrast** | -100 – 100 | 0 | Contrast adjustment: -100 = flat gray, 0 = original, +100 = maximum contrast |

### How Tinting Works

The tint is applied as a per-pixel blend:

1. **Tint blend** — each pixel is linearly interpolated toward the tint color by the strength percentage
2. **Saturation** — the pixel's saturation is adjusted (desaturation moves toward luminance gray)
3. **Contrast** — the pixel's distance from mid-gray is scaled by the contrast factor

All operations are applied in order and previewed in real-time on the canvas.

---

## Image Selection

### Character Tag

ComboBox listing all character image tags discovered in the project. Selecting a tag loads the character's default expression image.

### Background Tag

ComboBox listing all background images discovered in the project. Selecting a background loads it as the base layer behind the character.

Both ComboBoxes are populated by scanning project asset directories and parsing `@charpreset` entries from VNS scripts.

---

## Preview Canvas

The preview area shows the tinted result:

| Interaction | Result |
|-------------|--------|
| **Drag** | Pan the image |
| **Scroll** | Zoom in/out |
| **Double-click** | Reset view (center and fit) |

The preview composites:
1. Background image (if selected) at full size
2. Character image overlaid at its configured position
3. Tint/saturation/contrast applied to the entire composition

---

## Saved Setups

Save complete tint configurations under named labels for reuse:

| Action | Description |
|--------|-------------|
| **Save** | Prompts for a name, stores character tag, background tag, tint color, strength, saturation, contrast |
| **Load** | Restores all settings from a saved setup |
| **Delete** | Removes a saved setup |

Setups are stored in `.jvn/image-tint-tool.properties` and persist across sessions.

---

## Per-Background Persistence

Tint settings are automatically remembered per background tag. When you switch between backgrounds, the tint controls restore to the last values used with that background. This allows different scenes to have different color grading without manually re-entering values.

---

## Export

### Tint Profile Export

Exports the current tint configuration as a properties-style string:

```properties
tintColor=#ffd4a0
tintStrength=30
saturation=-20
contrast=10
```

### Full Setup Export

Exports the complete setup including image selections:

```properties
characterTag=hero
backgroundTag=sunset_beach
tintColor=#ffd4a0
tintStrength=30
saturation=-20
contrast=10
```

---

## Fullscreen Mode

Click the fullscreen button to expand the tint tool to fill the entire editor window. Useful for detailed color grading work with a larger preview. Click again to return to the sidebar.

Only one image tool (Layered Image Visualizer, Image Attributes Tool, or Image Tint Tool) can be fullscreen at a time.

---

## State Persistence

All state is stored in `.jvn/image-tint-tool.properties`:

| State | Persisted |
|-------|-----------|
| Selected character tag | ✓ |
| Selected background tag | ✓ |
| Tint color | ✓ |
| Tint strength | ✓ |
| Saturation | ✓ |
| Contrast | ✓ |
| Per-background tint values | ✓ |
| Saved setups | ✓ |
| Viewport position/zoom | ✓ |

---

## Catalog Scanning

When `refreshCatalog()` is called:

1. Scans project image directories for character and background assets
2. Parses VNS scripts for `@charpreset` entries to discover expression variants
3. Populates the character and background ComboBoxes
4. Restores persisted selections and tint values

---

## Use Cases

| Scenario | Settings |
|----------|----------|
| **Night scene** | Tint: dark blue `#1a237e`, Strength: 40, Saturation: -30, Contrast: -10 |
| **Sunset warmth** | Tint: warm orange `#ff8a65`, Strength: 25, Saturation: 10, Contrast: 5 |
| **Flashback** | Tint: sepia `#d7ccc8`, Strength: 50, Saturation: -60, Contrast: -15 |
| **Dream sequence** | Tint: soft purple `#ce93d8`, Strength: 35, Saturation: -20, Contrast: -20 |
| **High tension** | Tint: red `#ef5350`, Strength: 15, Saturation: 20, Contrast: 25 |

---

## Related Docs

- [Sidebar Utilities Overview](../overview/sidebar-utilities.md) — all 14 sidebar panels
- [Layered Image Visualizer](sidebar-layered-image-visualizer.md) — layer-based sprite composition
- [Image Attributes Tool](sidebar-image-attributes-tool.md) — attribute-based image assembly
- [Asset Browser](sidebar-asset-browser.md) — general asset discovery

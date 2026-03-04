# Sidebar — Layered Image Visualizer

Explores layered sprite sets (characters with separate body, eyes, mouth, hair layers), composites them in real-time, and exports VNS script snippets.

Source: `editor/src/main/java/com/jvn/editor/ui/LayeredImageVisualizerView.java`

---

## Overview

The Layered Image Visualizer scans the project for multi-layer character sprite directories, lets you pick options per layer group (eyes, mouth, brow, hair, etc.), previews the composited result live, and exports ready-to-paste VNS script code. It supports presets, shortforms, randomization, and game-framing preview.

- **Default side:** Right
- **Tab name:** Layered Image Visualizer
- **Implements:** `ImageToolPanel` (shared interface for all three image tools)
- **State file:** `.jvn/layered-image-visualizer.properties`

---

## Concepts

| Concept | Description |
|---------|-------------|
| **Set** | A directory of layered images grouped by naming convention (typically one per character) |
| **Layer group** | A category of interchangeable layers (e.g., eyes, mouth, brow, hair, body, outfit, accessory) |
| **Layer option** | A specific image file within a group (e.g., `eyes_happy.png`, `eyes_angry.png`) |
| **Expression** | The combination of selected layer options forms a composite expression ID |
| **Shortform** | A named alias for a layer combination (e.g., `happy = eyes=neutral mouth=happy`) |
| **Preset** | A saved snapshot of all layer selections for a specific set |

---

## UI Layout

```text
┌──────────────────────────────────────┐
│  Layered Image Visualizer            │
│  Set: [▼ hero    ] Filter [_______]  │
├──────────────────────────────────────┤
│  ┌──────────────────────────────┐    │
│  │                              │    │
│  │    Composited Preview        │    │
│  │    (drag to pan, scroll      │    │
│  │     to zoom, dbl-click       │    │
│  │     to reset)                │    │
│  │                              │    │
│  └──────────────────────────────┘    │
│  Focus X [===] Focus Y [===]         │
│  Crop [===]    Zoom [===]            │
│  Tag: hero  Expression: happy        │
│  Format: [▼ @charimg + [show]]       │
│  [Copy Snippet] [Copy Expression]    │
├──────────────────────────────────────┤
│  Tool Row:                           │
│  [Randomize] [Defaults] [Clear]      │
│  [◀ Swap] [Swap ▶] [Reset View]     │
│  [Match Game Framing] [Fullscreen]   │
├──────────────────────────────────────┤
│  ┌───────────┬────────┬───────────┐  │
│  │ Attributes│ Typed  │ Shortforms│  │
│  ├───────────┴────────┴───────────┤  │
│  │  ☑ eyes:    [▼ happy        ]  │  │
│  │  ☐ mouth:   [▼ smile        ]  │  │
│  │  ☑ brow:    [▼ neutral      ]  │  │
│  │  ☐ hair:    [▼ default      ]  │  │
│  │  ☐ body:    [▼ school_uniform] │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

---

## Preview Canvas

The main preview area composites all selected layers into a single image:

| Interaction | Result |
|-------------|--------|
| **Drag** | Pan the composited image |
| **Scroll** | Zoom in/out |
| **Double-click** | Reset view (center and zoom to fit) |

### View Controls

| Slider | Description |
|--------|-------------|
| **Focus X** | Horizontal focus point offset |
| **Focus Y** | Vertical focus point offset |
| **Crop** | Crop factor (how much of the image to show) |
| **Zoom** | Additional zoom multiplier |

### Match Game Framing

The "Match Game Framing" button switches the preview to simulate how the character would appear in the actual VN runtime, applying `heightFactor` and `baselineY` values from the project's character configuration.

---

## Script Controls

| Element | Description |
|---------|-------------|
| **Tag** | The character tag used in export (derived from directory name, editable) |
| **Expression** | The computed expression string from current layer selections |
| **Format** | ComboBox selecting the export snippet format |
| **Copy Snippet** | Copies the formatted script snippet to clipboard |
| **Copy Expression** | Copies just the expression string to clipboard |

---

## Snippet Export Formats

| Format | Example Output |
|--------|---------------|
| **`@charimg + [show]`** | `@charimg hero happy assets/char/hero/base.png\|eyes_happy.png\|mouth_smile.png`<br>`[show hero center happy]` |
| **`@charimg only`** | `@charimg hero happy assets/char/hero/base.png\|eyes_happy.png\|mouth_smile.png` |
| **`@charpreset + [show]`** | `@charpreset hero happy $eyes=happy $mouth=smile`<br>`[show hero center happy]` |
| **`@charpreset only`** | `@charpreset hero happy $eyes=happy $mouth=smile` |
| **`[show] only`** | `[show hero center happy]` |
| **Recipe comments** | `# hero happy: eyes=happy mouth=smile brow=neutral` |

---

## Tool Row Actions

| Button | Description |
|--------|-------------|
| **Randomize** | Picks a random option per group. If "active groups only" checkbox is set, only randomizes checked groups. |
| **Defaults** | Resets all groups to their first option |
| **Clear** | Deselects all layer options (shows nothing) |
| **◀ Swap** | Cycles all marked (checked) groups one option backward |
| **Swap ▶** | Cycles all marked (checked) groups one option forward |
| **Reset View** | Resets pan/zoom/focus/crop to defaults |
| **Match Game Framing** | Toggles runtime character framing preview |
| **Fullscreen** | Expands the panel to fill the entire editor window |

---

## Tabs

### Attributes Tab

Per-group layer selectors:
- Each group shows a **checkbox** (marks the group for swap/randomize operations) and a **ComboBox** of available options
- Groups are sorted by their natural order (body, face, eyes, brow, mouth, hair, outfit, accessory, etc.)
- A filter text field narrows the group list

### Typed Tab

Free-text attribute input field:
- Syntax: `eyes=happy mouth=smile` or `eyes_happy mouth_smile`
- Optional **real-time preview** toggle — updates the composited image as you type
- Pressing Enter applies the typed expression

### Shortforms Tab

Named aliases for layer combinations:
- Define: `happy = eyes=neutral mouth=happy brow=neutral`
- **Save** shortform button
- **Delete** shortform button
- **Apply** — clicking a shortform name applies it to the current selections
- Shortforms are persisted in the state file per set

---

## Group Token Aliases

The visualizer normalizes layer group names from directory/file naming conventions:

| Input Tokens | Normalized Group |
|-------------|-----------------|
| `eye`, `eyes` | `eyes` |
| `mouth`, `lip`, `lips` | `mouth` |
| `brow`, `eyebrow`, `eyebrows` | `brow` |
| `outfit`, `clothes` | `outfit` |
| `accessory`, `accessories`, `acc` | `accessory` |
| `base`, `body`, `hair`, `face` | (kept as-is) |

This means a directory named `hero_eyebrows_angry.png` will be categorized under the `brow` group.

---

## Presets

Save and load named presets that remember all layer selections for a specific set:

- **Save Preset** — prompts for a name, stores current selections
- **Load Preset** — applies saved selections
- **Delete Preset** — removes a saved preset
- Presets are stored per-set in `.jvn/layered-image-visualizer.properties`

---

## State Persistence

All state is persisted per-set in `.jvn/layered-image-visualizer.properties`:

| State | Persisted |
|-------|-----------|
| Selected set | ✓ |
| Per-group selections | ✓ |
| Active group checkboxes | ✓ |
| View controls (focus, crop, zoom) | ✓ |
| Filter text | ✓ |
| Shortforms | ✓ |
| Presets | ✓ |
| Export format selection | ✓ |

State is restored when the panel is reopened or the project is reloaded.

---

## File Operations Toolbar

Below the tool row, a second row provides file-level export and import actions:

| Button | Icon Color | Description |
|--------|------------|-------------|
| **Export PNG** | Blue | Composites all visible layers and saves a flattened PNG file |
| **Export Setup** | Green | Saves the current layer selection as a `.layersetup` file |
| **Import Setup** | Yellow | Loads a `.layersetup` file and restores its layer selections |
| **Copy Charpreset** | Purple | Copies a `@charpreset` snippet to the clipboard, ready to paste into a `.vns` script |

See [.layersetup Files](../../tools/layersetup-files.md) for the full file format reference and workflow documentation.

---

## Fullscreen Mode

Click the fullscreen button to expand the visualizer to fill the entire editor window. This is shared across all three image tools (`ImageToolPanel` interface):

- Click fullscreen → panel fills the editor
- Click again → returns to sidebar
- Only one image tool can be fullscreen at a time

---

## Catalog Scanning

When `refreshCatalog()` is called:

1. Scans the project's image asset directories
2. Discovers layered sprite sets by directory structure
3. Parses file names to extract group names and option names
4. Groups files by set → group → option
5. Populates the set ComboBox and group selectors
6. Restores persisted selections if available

---

## Related Docs

- [.layersetup Files](../../tools/layersetup-files.md) — file format, export/import workflow, charpreset quick-export
- [Sidebar Utilities Overview](../overview/sidebar-utilities.md) — all 14 sidebar panels
- [Image Attributes Tool](sidebar-image-attributes-tool.md) — attribute-based image assembly
- [Image Tint Tool](sidebar-image-tint-tool.md) — color tinting and grading
- [Asset Browser](sidebar-asset-browser.md) — general asset discovery
- [Puppeteer Launcher](sidebar-puppeteer-launcher.md) — uses character image data for scene snapshots

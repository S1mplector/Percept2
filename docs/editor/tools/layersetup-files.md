# .layersetup Files

Portable, human-readable snapshots of a layer selection from the **Layered Image Visualizer**.

Source: `editor/src/main/java/com/jvn/editor/ui/LayeredImageVisualizerView.java`

---

## What is a .layersetup file?

A `.layersetup` file records which image is currently selected for every layer group in the visualizer. It is a plain-text key=value format that can be version-controlled, shared with teammates, or kept alongside your character assets as a reference preset.

> **Editor-only artifact** — `.layersetup` files are *not* consumed by the JVN runtime. They exist solely for the editor's Layered Image Visualizer.

---

## File Format

```properties
# JVN Layered Image Visualizer Setup
#
# This file records a layer selection from the Layered Image Visualizer.
# To restore this configuration, open the visualizer in the JVN editor
# and click the Import button (folder icon in the file-ops toolbar),
# then choose this .layersetup file.

set=assets/demo
characterId=lavender
expression=base_closed_day_smile

layer.base=assets/demo/characters/lavender/base/lavender_test_sprite_base.png
# label: base
layer.eyes=assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_closed.png
# label: closed
layer.field=assets/demo/backgrounds/field/glorious_ricefield_day.png
# label: day
layer.mouth=assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
# label: smile
```

### Keys

| Key | Required | Description |
|-----|----------|-------------|
| `set` | optional | The layer set ID (typically a directory path like `assets/demo`) |
| `characterId` | optional | The character tag used for snippet export |
| `expression` | optional | The composite expression string |
| `layer.<group>` | **yes** | Relative path to the selected image for each layer group |
| `# label: <name>` | no | Human-readable label comment (ignored on import) |

Lines starting with `#` are comments and are ignored during import. Blank lines are also ignored.

---

## How to Export

1. Open the **Layered Image Visualizer** sidebar in the editor.
2. Select a layer set and configure your desired layer options per group.
3. In the **file-ops toolbar** (below the tool row), click the **save icon** (green).
4. Choose a destination and filename (defaults to `<set_id>.layersetup`).

The file will contain the current set, character ID, expression, and every `layer.<group>=<path>` assignment.

---

## How to Import

1. Open the **Layered Image Visualizer** sidebar.
2. Make sure the correct layer set is selected (the import will match group names against the current set's groups).
3. In the **file-ops toolbar**, click the **folder icon** (yellow).
4. Select the `.layersetup` file.

The visualizer will:
- Parse each `layer.<group>=<value>` entry.
- Match each group name to the current set's group selectors (with fuzzy matching via `sanitizeId`).
- For each matched group, find the layer option whose relative path matches the stored value.
- Apply all matched selections and update the preview.

A status message will report how many layers were successfully applied.

---

## Quick Charpreset Export

Instead of exporting to a file, you can copy a **`@charpreset`** snippet directly to the clipboard:

1. Configure your layers as desired.
2. Click the **copy icon** (purple) in the file-ops toolbar.
3. The generated `@charpreset` snippet is now on your clipboard, ready to paste into a `.vns` script.

Example output:

```vns
@charlayer lavender eyes_closed assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_closed.png
@charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
@charpreset lavender base_closed_day_smile $eyes_closed | $mouth_smile
```

This is equivalent to selecting **"@charpreset only"** in the snippet format ComboBox and clicking **Copy Snippet**, but as a one-click action.

---

## Use Cases

- **Preset sharing** — commit `.layersetup` files alongside your character assets so other team members can quickly load known-good expressions.
- **Expression catalog** — export one `.layersetup` per expression to build a reference library.
- **Script authoring** — use the charpreset quick-export to copy paste-ready `@charpreset` declarations directly into your VNS scripts.
- **QA snapshots** — export both the `.layersetup` and a composited PNG for visual regression tracking.

---

## Related Docs

- [Layered Image Visualizer](../sidebars/right/sidebar-layered-image-visualizer.md) — full sidebar documentation
- [Image Attributes Tool](../sidebars/right/sidebar-image-attributes-tool.md) — attribute-based image assembly
- [Scene Lighting Studio](../sidebars/right/sidebar-image-tint-tool.md) — scene lighting, tinting, and grading

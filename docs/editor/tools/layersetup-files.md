# .layersetup Files

Portable, human-readable snapshots of a layer selection from the **Layered Image Visualizer**.

Source: `modules/editor/src/main/java/com/jvn/editor/ui/LayeredImageVisualizerView.java`

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
# and click Import Setup in the Export section,
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
3. In the main **Export** section, click **Layer Setup** for the configured export folder or **Setup As** to choose a destination.
4. The default filename follows the current export base name and ends in `.layersetup`.

The file will contain the current set, character ID, expression, and every `layer.<group>=<path>` assignment.

---

## How to Import

1. Open the **Layered Image Visualizer** sidebar.
2. Make sure the correct layer set is selected (the import will match group names against the current set's groups).
3. In the main **Export** section, click **Import Setup**.
4. Select the `.layersetup` file.

The visualizer will:
- Parse each `layer.<group>=<value>` entry.
- Match each group name to the current set's group selectors (with fuzzy matching via `sanitizeId`).
- For each matched group, find the layer option whose relative path matches the stored value.
- Apply all matched selections and update the preview.

A status message will report how many layers were successfully applied.

---

## Runtime Charpreset Export

For runtime script work, prefer exporting a **charpreset snippet** instead of a
`.layersetup` file. The snippet contains the `@charlayer` declarations plus the
`@charpreset` declaration that VNS can parse directly.

1. Configure your layers as desired.
2. In the main **Export** section, click **Copy Charpreset** to copy it, or
   **Save Charpreset** / **Charpreset As** to write a `.vns` snippet file.
3. Paste or include the generated declarations in a `.vns` script.

Example output:

```vns
@charlayer lavender eyes_closed assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_closed.png
@charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png
@charpreset lavender base_closed_day_smile $eyes_closed | $mouth_smile
```

This is equivalent to selecting **"@charpreset only"** in the snippet format
ComboBox and clicking **Copy Snippet**, but the main Export section makes it the
obvious runtime-facing path. Saved snippet files use names like
`lavender_happy_charpreset.vns`.

---

## Use Cases

- **Preset sharing** — commit `.layersetup` files alongside your character assets so other team members can quickly load known-good expressions.
- **Expression catalog** — export one `.layersetup` per expression to build a reference library.
- **Script authoring** — use the charpreset export controls to copy or save paste-ready `@charpreset` declarations directly for VNS scripts.
- **QA snapshots** — export both the `.layersetup` and a composited PNG for visual regression tracking.

---

## Related Docs

- [Layered Image Visualizer](../sidebars/right/sidebar-layered-image-visualizer.md) — full sidebar documentation
- [Image Attributes Tool](../sidebars/right/sidebar-image-attributes-tool.md) — attribute-based image assembly
- [Scene Lighting Studio](../sidebars/right/sidebar-image-tint-tool.md) — scene lighting, grading, occlusion, and stage export

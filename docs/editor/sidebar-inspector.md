# Sidebar — Inspector

Property editor for the currently selected entity in a JES scene viewport. Displays different fields depending on entity type, with full undo/redo support.

Source: `editor/src/main/java/com/jvn/editor/ui/InspectorView.java`

---

## Overview

The Inspector panel shows editable properties for whatever entity is selected in the JES visual editor canvas. It dynamically rebuilds its UI when the selection changes, showing only the fields relevant to the selected entity's type.

- **Default side:** Right
- **Tab name:** Inspector
- **Selection source:** Click an entity on the JES scene canvas, or select from the entity tree

---

## Common Fields (All Entity Types)

Every entity shows at minimum:

| Field | Type | Description |
|-------|------|-------------|
| **Selection label** | Read-only | Shows the entity class name (e.g., "Selected: Sprite2D") |
| **x** | Number | Horizontal position in scene coordinates |
| **y** | Number | Vertical position in scene coordinates |

All numeric fields are editable text fields. Press **Enter** to commit a value. Changes are pushed through the `CommandStack` for undo/redo support (`Ctrl+Z` / `Ctrl+Y`).

---

## Entity-Specific Fields

### Sprite2D

Standard image entity — the most common entity type in JES scenes.

| Field | Type | Description |
|-------|------|-------------|
| **image** | Text | Asset path to the sprite image (e.g., `assets/char/hero.png`) |
| **width** | Number | Display width in pixels |
| **height** | Number | Display height in pixels |
| **alpha** | Number | Opacity (0.0 = invisible, 1.0 = fully opaque) |
| **originX** | Number | Horizontal origin point for rotation and scaling |
| **originY** | Number | Vertical origin point for rotation and scaling |

### Label2D

Text display entity for in-scene labels, dialogue, or UI text.

| Field | Type | Description |
|-------|------|-------------|
| **text** | Text | The display string (press Enter to apply) |
| **size** | Number | Font size in points |
| **bold** | Checkbox | Toggle bold weight |
| **align** | ComboBox | Text alignment: `LEFT`, `CENTER`, `RIGHT` |
| **color** | ColorPicker | Text color with full RGBA support |
| **alpha** | Number | Opacity (0.0 – 1.0) |

### Panel2D

Rectangular container entity used for backgrounds, frames, and layout regions.

| Field | Type | Description |
|-------|------|-------------|
| **width** | Number | Panel width in pixels |
| **height** | Number | Panel height in pixels |

### PhysicsBodyEntity2D

Entity with a rigid body attached, used in physics-enabled JES scenes.

| Field | Type | Description |
|-------|------|-------------|
| **mass** | Number | Body mass (affects collision response) |
| **restitution** | Number | Bounce factor (0.0 = no bounce, 1.0 = perfect bounce) |
| **static** | Checkbox | If checked, the body is immovable (infinite mass) |
| **sensor** | Checkbox | If checked, the body detects overlaps but has no collision response |
| **vx** | Number | Current horizontal velocity |
| **vy** | Number | Current vertical velocity |

### ParticleEmitter2D

Particle system entity for visual effects (sparks, smoke, rain, etc.).

| Field | Type | Description |
|-------|------|-------------|
| **emissionRate** | Number | Particles emitted per second |
| **minLife** | Number | Minimum particle lifetime (seconds) |
| **maxLife** | Number | Maximum particle lifetime (seconds) |
| **minSize** | Number | Minimum initial particle size |
| **maxSize** | Number | Maximum initial particle size |
| **endSizeScale** | Number | Scale factor applied at end of particle life |
| **minSpeed** | Number | Minimum initial speed |
| **maxSpeed** | Number | Maximum initial speed |
| **minAngle** | Number | Minimum emission angle (degrees) |
| **maxAngle** | Number | Maximum emission angle (degrees) |
| **gravityY** | Number | Downward gravity force |
| **startColor** | ColorPicker | Particle color at birth (RGBA) |
| **endColor** | ColorPicker | Particle color at death (RGBA) |
| **texture** | Text | Path to particle texture asset |
| **additive** | Checkbox | Enable additive blending (glow effect) |

---

## Undo / Redo

All property changes in the Inspector are pushed through the editor's `CommandStack`:

- Each change creates a `FunctionalCommand` capturing the old and new values
- **Ctrl+Z** undoes the last property change
- **Ctrl+Y** / **Ctrl+Shift+Z** redoes the last undone change
- Multiple rapid changes to the same field create separate undo entries

If no `CommandStack` is available (e.g., during initialization), changes are applied directly without undo history.

---

## No-Selection State

When nothing is selected, the Inspector shows a single label: **"No selection"**. Select an entity on the canvas or in the entity tree to populate the panel.

---

## Integration with Scene Viewport

- Clicking an entity on the JES canvas sets the Inspector selection via `inspectorView.setSelection(entity)`
- The Inspector also receives the active `JesScene2D` via `setScene()` for context
- Property changes are immediately reflected on the canvas (live preview)
- The status bar is updated with change descriptions (e.g., "Updated text", "Updated image path")

---

## Related Docs

- [Sidebar Utilities Overview](sidebar-utilities.md) — all 14 sidebar panels
- [Editor Guide](editor.md) — main editor layout and modes
- [Puppeteer Editor Guide](puppeteer-editor-guide.md) — Puppeteer animation editing (uses its own KeyframeEditor for animation properties)

# Scene Graph View

Entity list panel for JES scenes, providing a filterable, type-badged overview of all entities in the active scene with rename, delete, and fit-to-selection actions.

Source: `modules/editor/src/main/java/com/jvn/editor/ui/SceneGraphView.java`

---

## Overview

The Scene Graph View is an embedded panel (typically docked in the left sidebar or integrated into the JES viewport area) that lists every `Entity2D` in the currently loaded `JesScene2D`. It is the primary way to browse, select, and manage entities when editing JES scenes.

---

## Features

### Entity List

Each entity is displayed with:

- **Name** — the entity's scene-unique identifier
- **Type badge** — a colored letter indicating the entity type:

| Badge | Color | Type |
|-------|-------|------|
| **S** | Gold (`#d29922`) | `Sprite2D` |
| **L** | Blue (`#58a6ff`) | `Label2D` |
| **P** | Green (`#2ea043`) | `Panel2D` |
| **B** | Red (`#e16e6e`) | `PhysicsBodyEntity2D` |
| **E** | Purple (`#a371f7`) | `ParticleEmitter2D` |
| **?** | Gray (`#8b949e`) | Unknown/other entity type |

### Filter

A text field at the top performs live case-insensitive substring filtering across entity names. The list updates as you type.

### Selection

- **Single-click** selects the entity and fires the `onSelected` callback, which typically updates the Inspector panel with the entity's properties
- **Double-click** selects and invokes `onFit`, which centers the viewport camera on the selected entity

### Context Menu

Right-click any entity for:

| Action | Description |
|--------|-------------|
| **Rename** | Opens a text input dialog to change the entity's name in the scene |
| **Delete** | Removes the entity from the scene immediately |
| **Fit Selection** | Centers the viewport on the selected entity |

All context menu actions are disabled when no entity is selected.

---

## Integration

The Scene Graph View is wired via `setContext()`:

- **scene** — the `JesScene2D` to display
- **onSelected** — callback when an entity is clicked (drives Inspector updates)
- **onFit** — callback when an entity is double-clicked (drives camera fit)
- **setStatus** — callback for status bar messages (e.g., "Renamed player → hero")

Call `refresh()` after scene mutations to rebuild the entity list.

---

## Related Docs

- [Inspector](../sidebars/right/sidebar-inspector.md) — entity property editing
- [JES Scenes & Entities](../../scripting/jes/scene/jes-scenes-entities.md) — scene structure
- [Editor Guide](editor.md) — overall editor layout

# Tilemap Editor

Visual tile-painting tool for editing JES tilemap layers. Lets you select maps and layers from a parsed `.jes` scene file, paint tiles on a grid canvas, and pick tiles from a tileset palette.

Source: `editor/src/main/java/com/jvn/editor/ui/TilemapEditorView.java`

---

## Overview

The Tilemap Editor opens when a `.jes` file containing `map` declarations is loaded for visual editing. It parses the JES AST to discover all maps, layers, and tilesets, then renders an interactive grid for tile placement.

---

## UI Layout

### Toolbar

| Control | Description |
|---------|-------------|
| **Map selector** | ComboBox listing all `map` declarations in the scene |
| **Layer selector** | ComboBox listing layers within the selected map (tile layers, collision layers, trigger layers) |
| **Tile index spinner** | Numeric spinner to select the active tile index for painting |
| **Save** | Writes the modified tile data back to the `.jes` file |
| **Status label** | Shows current map dimensions, layer name, and cursor position |

### Main Canvas

- Displays the active tile layer as a colored grid
- **Left-click** to paint the selected tile index at the cursor position
- **Right-click** to erase (set tile to 0)
- Grid lines separate each cell
- Non-zero tiles are filled with a computed color based on tile index
- Cell size defaults to 24px, scaled by `gridScale`

### Tileset Palette

- Renders the tileset image associated with the current map's `tileset` declaration
- Displays a grid overlay dividing the tileset into individual tiles
- **Click** a tile in the palette to select it as the active painting index
- Selected tile is highlighted with a colored border
- Scale defaults to 2× for visibility

---

## Workflow

1. Open a `.jes` file containing map declarations
2. Select a map from the **Map** dropdown
3. Select a layer from the **Layer** dropdown
4. Click tiles in the **tileset palette** to choose a tile
5. **Left-click** on the grid canvas to paint
6. **Right-click** to erase
7. Click **Save** to write changes back to the `.jes` source file

---

## Data Model

The editor reads and writes JES AST nodes:

| AST Node | Purpose |
|----------|---------|
| `MapDecl` | Map declaration with dimensions (cols × rows) |
| `MapLayerDecl` | Layer within a map — stores the 2D tile index array |
| `TilesetDecl` | Tileset reference — image path, tile width/height, columns |

Tile data is stored as a flat `int[][]` array (`tiles[row][col]`), where `0` represents an empty cell.

---

## Save Format

When saving, the editor serializes the modified tile array back into the JES map layer syntax within the original `.jes` file, preserving surrounding scene declarations and formatting.

---

## Related Docs

- [Tilemaps & Maps](../../scripting/jes/systems/jes-tilemaps.md) — tileset format, tile layers, collision layers, trigger layers
- [JES Scenes & Entities](../../scripting/jes/scene/jes-scenes-entities.md) — scene structure and map declarations
- [Editor Guide](editor.md) — editing modes overview

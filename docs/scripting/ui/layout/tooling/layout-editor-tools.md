# Layout Editor Tools

Complete guide to the JVN editor's layout-related visual tools — Layout Studio code mode, visual preview toggle, Bounds Studio, registry editor panel, screen cards, and how each tool fits the text-first workflow.

---

## Overview

The JVN editor provides code and visual surfaces over the same runtime DSL files. Direct text editing
is efficient for exact values and reviewable diffs; visual editing is the primary surface for bounds,
dragging, colors, assets, and combined previews. Layout Studio opens in code mode and can switch to
visual or split mode without introducing an editor-only format.

---

## Layout Studio

The main entry point for layout editing in the editor. Accessed from the sidebar's Layout Launcher.

### Code Mode (Default)

Layout Studio opens in **code mode** by default. This is a text editor tab showing the raw `.layout`, `.style`, `.menu`, or `dialogue.layout` file. You edit properties directly as `key=value` lines.

**Features:**
- Syntax-aware text editing with properties format support
- Undo/redo (Ctrl+Z / Ctrl+Y)
- Saves directly to the file on disk
- Save + Run Runtime from the Layout Studio toolbar
- Ctrl/Cmd+Enter shortcut for Save + Run Runtime
- Immediate feedback — save and run the project to validate

### Visual Preview (Toggle)

The visual preview is an **optional toggle**, not the primary editing surface. It renders an approximation of how the layout looks at runtime.

**What the preview shows:**
- Approximate item positions for menu layouts
- Textbox and name box rectangles for dialogue layouts
- Color fills and font rendering
- Dynamic item count preview

**What still requires runtime validation:**
- Actions, navigation, and input behavior
- Platform font availability and final rasterization
- Animation and transition timing
- Responsive behavior at every supported resolution

**When to use the preview:**
- Quick sanity check of position/size changes
- Getting a rough sense of proportions before running
- Comparing before/after of a layout tweak

**When NOT to use the preview:**
- As the final validation — always run the project
- For pixel-perfect positioning — runtime is authoritative
- For testing actions or navigation flow

---

## Dialogue Layout Editor

Visual editor for `config/ui/dialogue.layout`. Provides collapsible sections for each area of the dialogue UI.

### Sections

- **Textbox Geometry** — sliders/fields for textBoxX, Y, Width, Height, Padding
- **Name Box** — offset, size, and text positioning fields
- **Dialogue Text** — padding fields for the text area
- **Choice Buttons** — center, start, width, height, gap, padding
- **Textbox Action Buttons** — button list, per-button fields, Bounds Studio launcher
- **Visual Style** — color fields with ColorPicker, font fields, asset paths

### Features

- **Resize handles** on the preview canvas for textbox bounds
- **ColorPicker** for all color fields (click the color swatch next to any color text field)
- **Ctrl+Z / Ctrl+Y** undo/redo across all fields
- **Dynamic choice count** preview (adjust how many sample choices are shown)
- **Bounds Studio** button for textbox action button placement
- **Undoable presets** for Standard VN and Minimal Monochrome layouts
- **Runtime VN preview** using the current unsaved editor state

### Authoring Usage

Use code mode for exact values and visual mode for composition. Presets are complete starting points
and replace the current layout, but the operation is undoable. Runtime preview is the final check for
text flow and presentation behavior.

---

## Menu Screen Visual Editor

Visual editor for `.menu` files. Shows items in a table with per-item property editing.

### Features

- **Item table** with columns for label, action, target, style, enabled, icon
- **"Show Advanced" toggle** — hides 15 advanced columns by default (bounds, slot preview, etc.)
- **Action combo box** — dropdown with all built-in action types
- **Item Bounds inspector** — fields for boundsX/Y/Width/Height
- **Bounds Studio button** — opens the visual bounds drawing tool for item placement
- **Responsive preview canvas** — shows approximate item layout
- **Ctrl+Z / Ctrl+Y** undo/redo

### Text-First Usage

The `.menu` file is the source of truth. Use this editor to:
1. Get an overview of all items and their actions
2. Quickly toggle the "enabled" checkbox
3. Use Bounds Studio for custom item positioning

---

## Menu Layout Visual Editor

Visual editor for `.layout` files.

### Features

- **Slider controls** for listYStart, lineHeight, listWidthFactor, hintsBottomMargin, titleY
- **Text alignment selector** (left/center/right)
- **Dynamic item count preview** — adjust how many sample items appear
- **Fixed title width** display
- **Live preview** canvas showing approximate layout at current values
- **Ctrl+Z / Ctrl+Y** undo/redo
- **Standard and Minimal Right-Side presets** for a fast, undoable starting point

### Authoring Usage

Use the layout editor when the relationship between title, list, hints, width, alignment, and
scrolling matters more than any single value. Dragging the title or list updates the same properties
that code mode exposes, and custom properties remain available for compatible extensions.

---

## Menu Style Visual Editor

Visual editor for `.style` files.

### Features

- **ColorPicker** for item colors, title color, hints color, background color
- **Font family and size** fields
- **Font weight** selectors for items, title, and hints (`NORMAL` / `BOLD` / `SEMI_BOLD`)
- **Button asset path** fields
- **Shadow offset** fields
- **Opacity slider**
- **Ctrl+Z / Ctrl+Y** undo/redo
- **Standard and Minimal Monochrome presets** that retain the current background asset

### Authoring Usage

Use this editor to compare state colors, typography, shadows, opacity, and button textures together.
Asset paths support project browsing and the preview loads supported images from the project.

---

## Bounds Studio

A specialized visual tool for placing buttons and items at exact coordinates. Opens as a modal dialog from the Menu Screen Visual Editor or Dialogue Layout Editor.

### Three Modes

**Select Mode:**
- Click to select an existing button/item bounds
- Drag to move
- Corner handles to resize
- See coordinates update in real-time

**Rectangle Mode:**
- Click and drag to draw a new rectangular bounds
- Release to create
- Coordinates are shown during drawing

**Point-Nail Mode:**
- Click to place individual corner points
- The tool generates a bounding rectangle from the placed points
- Useful for precise placement from a reference image

### Features

- **Background asset image** display (load a screenshot or mockup)
- **Grid overlay** for alignment
- **Color-coded bounds** with labels showing button IDs
- **Coordinate readout** showing exact position and size values
- **Copy coordinates** to clipboard for pasting into text files

### When to Use Bounds Studio

Bounds Studio is most useful for:
- Placing buttons at exact pixel positions on a background image
- Non-list menu layouts (scattered, radial, or grid button arrangements)
- Textbox action button placement (small buttons in a small area)
- Custom button layouts with background art

For standard list-based menus, you typically don't need Bounds Studio — the list layout handles positioning automatically.

---

## Inline Registry Editor

A panel in the Layout Launcher sidebar for editing `menu.registry` directly.

### Features

- **Editable TextFields** for defaultMenu, menus, layouts, styles
- **Save Registry** button — writes changes to `config/menu/registry/menu.registry`
- **Open File** button — opens the registry file in the code editor
- **Validation** — highlights issues (missing files, unregistered screens)

### Authoring Usage

Use this panel to see discovery and wiring as a system rather than four isolated lists. The source
file remains directly editable through **Open File**.

---

## Screen Item Cards

The Layout Launcher sidebar shows cards for each menu screen, layout, and style discovered in the project.

### Card Features

- **Layout/style reference** with validation color (blue = valid, orange = missing)
- **Quick-assign ComboBoxes** for layout and style (write directly to the `.menu` file)
- **Navigation flow indicators** ("Navigates to: load, settings")
- **Validation warnings** with orange border on cards with issues
- **Clone/Duplicate button** — copies file content and auto-registers in registry

### Validation Warnings Shown

- Layout/style not found in project
- Navigation target not found
- Screen not registered in menu.registry
- Screen registered but file doesn't exist

### Authoring Usage

Screen cards are an authoring dashboard. Use them to:
1. See the overall menu structure at a glance
2. Spot wiring issues (orange warnings)
3. Use Quick-assign to change layout/style references without switching files
4. Clone a screen as a starting point for a new one

---

## Unified Menu Editor

A combined editor view that shows the menu screen, its layout, and its style together with a shared preview.

### Features

- **Combined preview tab** showing the screen with its actual layout and style applied
- **Tab navigation** between Screen, Layout, and Style sub-editors
- **Project root propagation** — all sub-editors share the same project context

### Authoring Usage

Use this workspace when a change spans content, geometry, and presentation. Each tab writes its
corresponding source file; the combined preview shows their interaction before runtime validation.

---

## Editor Tools vs. Runtime: What to Trust

| Question | Trust |
|----------|-------|
| "Is the value correct?" | The text file |
| "Does it look right?" | The runtime |
| "Is the hex color correct?" | The ColorPicker |
| "Are bounds positioned right?" | Bounds Studio (approximate), then runtime (authoritative) |
| "Does navigation work?" | The runtime only |
| "Is the font correct?" | The runtime only |
| "Are asset paths and images loading?" | Visual preview, then runtime for final verification |

---

## Recommended Tool Usage by Task

| Task | Best Tool |
|------|-----------|
| Change a numeric value | Text editor |
| Change a color | Text editor (or ColorPicker for discovery) |
| Change a font | Text editor |
| Add a new menu item | Text editor |
| Place buttons at exact positions | Bounds Studio |
| Check menu wiring | Screen cards in Layout Launcher |
| Quick-assign layout/style to screen | Screen card ComboBoxes |
| Clone a menu screen | Clone button on screen card |
| Validate everything works | Run the project |

---

## Keyboard Shortcuts

| Shortcut | Action | Available In |
|----------|--------|-------------|
| Ctrl+Z | Undo | All visual editors |
| Ctrl+Y | Redo | All visual editors |
| Ctrl+S | Save | All editors |
| Ctrl+Enter | Save + Run Runtime | Layout Studio windows |
| Ctrl+R | Run project | Global |

---

## Related Docs

- [Text-First Layout Workflow](../workflow/text-first-layout-workflow.md)
- [Layout DSL Cookbook](../reference/layout-dsl-cookbook.md)
- [Dialogue Layout & Style](../components/dialogue-layout.md)
- [Menu Button Layouts](../structure/menu-button-layouts.md)
- [Validation & Diagnostics](validation-diagnostics.md)

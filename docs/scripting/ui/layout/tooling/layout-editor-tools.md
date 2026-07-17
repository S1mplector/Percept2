# Layout Authoring Tools

JVN deliberately uses a text-first layout workflow. The editor improves the source-writing loop;
it does not maintain a second UI model or a simulated renderer.

## Layout Editors sidebar

The sidebar is the project-level control surface. It scans the active project and groups dialogue,
screen, layout, style, and registry files.

Use it to:

- create or clone correctly located DSL files;
- register screens, layouts, and styles;
- assign a layout or style to a screen;
- find unresolved navigation targets and missing references;
- open a source in Layout Studio;
- inspect and edit `menu.registry` without losing sight of the whole menu graph.

Orange warnings describe cross-file problems. Fix them before tuning appearance: a perfectly valid
style cannot render when its screen references another ID.

## Layout Studio

Layout Studio is a focused source editor for `.menu`, `.layout`, and `.style` files and
`dialogue.layout`.

### Source editing

- properties-aware highlighting;
- normal editor undo and redo;
- dirty-state tracking and close confirmation;
- atomic file replacement on save;
- `Ctrl/Cmd+S` to save;
- `Ctrl/Cmd+Enter` to save and run the active project.

The file on disk is always the source of truth. **Reload** discards the editor buffer only after
confirmation when it is dirty.

### Source templates

The right utility panel offers complete, commented templates appropriate to the open file:

| File | Templates |
|---|---|
| `dialogue.layout` | Standard dialogue; Minimal monochrome |
| Menu `.layout` | Standard menu; Minimal right-side menu |
| Menu `.style` | Standard menu; Minimal monochrome |
| `.menu` | Standard menu screen |

Applying a template requires confirmation because it replaces the current buffer. For the minimal
style template, Layout Studio retains the current `backgroundAsset` value when it can parse one.
Templates do not save automatically; inspect the diff and save deliberately.

### Key reference and guides

The Key Reference list is populated from the runtime menu loader's accepted-key catalog. Choose a
key and use **Copy Key** to place a `key=` declaration on the clipboard. Screen item fields are
shown as `item.<id>.<field>` so the required scope is explicit. Dialogue keys come from the
regression-tested standard template plus textbox-action declarations.

**Copy Template** copies a complete starting source without replacing the buffer. **Open Layout
Guide** opens the guide for the active file type locally when the JVN repository is available, or
falls back to the stable documentation on GitHub.

### Diagnostics

Diagnostics appear on the relevant source lines. They cover malformed properties, duplicate and
unknown keys, invalid numbers, incomplete bounds groups, and values outside supported ranges.
Dialogue properties are also passed through the runtime's `VnUiLayoutLoader`, preventing a
separate editor interpretation from drifting away from runtime behavior.

Project-level relationships cannot be proven from one file. Use the sidebar alongside line
diagnostics for registry membership, referenced layouts/styles, menu targets, and missing assets.

### Asset utilities

The right panel can browse an existing project asset, import an external asset, convert it to a
project-relative path, reveal it, copy its path, or apply it to a selected DSL key. Screen item keys
accept an item ID so Layout Studio can write `item.<id>.*` declarations.

Prefer paths such as `assets/ui/button.png`. Avoid absolute paths and backslashes: they make a
project machine-specific.

## Runtime validation

The removed visual previews were approximations. Save and Run Runtime is now the single validation
path for rendering and behavior. In the running project, check:

1. the initial menu and every navigation target;
2. normal, hover, selected, and disabled states;
3. keyboard, pointer, and controller input used by the project;
4. bundled fonts and assets on case-sensitive filesystems;
5. dialogue, choices, NVL, bubble mode, and textbox actions where enabled;
6. the smallest and largest supported viewport.

Keep source changes small so each runtime pass answers one question. Fractional coordinates are
viewport-relative; pixel measurements such as font size and line height need explicit resolution
testing.

## Choosing the right surface

| Question | Surface |
|---|---|
| Which key controls this? | [DSL cookbook](../reference/layout-dsl-cookbook.md) |
| Is this declaration valid? | Layout Studio diagnostics |
| Is the menu graph connected? | Layout Editors sidebar |
| Does an asset path resolve? | Asset utilities, then runtime |
| Does it look and behave correctly? | Runtime |
| What changed? | Source diff/version control |

## Related documentation

- [Text-first workflow](../workflow/text-first-layout-workflow.md)
- [Validation and diagnostics](validation-diagnostics.md)
- [Dialogue layout](../components/dialogue-layout.md)
- [Menu layouts](../structure/menu-layouts.md)
- [Menu actions](../structure/menu-actions.md)

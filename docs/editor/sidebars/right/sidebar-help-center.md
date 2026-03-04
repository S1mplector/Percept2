# Sidebar — Help Center

In-app documentation browser that discovers, indexes, and previews Markdown documentation files from the workspace and project.

Source: `editor/src/main/java/com/jvn/editor/ui/HelpCenterView.java`

---

## Overview

The Help Center provides immediate access to all project documentation without leaving the editor. It scans `docs/` directories in both the workspace root and project root, indexes Markdown files by title, and presents them in a filterable, searchable list with a built-in preview pane.

- **Default side:** Right
- **Tab name:** Help
- **Shortcut:** Press **F1** from anywhere in the editor to jump to the Help tab

---

## UI Layout

```text
┌─────────────────┬────────────────────────┐
│  Filter [______] │  Editor Guide          │
│  [Refresh]       │  docs/editor/core/editor.md │
│                  │  [Open in Editor]      │
│  Quick Access    │  [Reveal File]         │
│  [README]        │  [Copy Path]           │
│  [Overview]      │                        │
│  [Editor]        │  Tip: press F1 from    │
│  [VNS] [JES]    │  anywhere to jump here  │
│  [Runtime]       │                        │
│  [Menus]         │  ┌────────────────────┐│
│                  │  │                    ││
│  Quick Commands  │  │  (Markdown preview ││
│  [Build]         │  │   read-only view)  ││
│  [Run Editor]    │  │                    ││
│  [Run Runtime]   │  │                    ││
│                  │  │                    ││
│  ─────────────── │  │                    ││
│  Documents       │  └────────────────────┘│
│  ┌─────────────┐ │                        │
│  │ Editor Guide│ │                        │
│  │ Workspace   │ │                        │
│  │ editor.md   │ │                        │
│  ├─────────────┤ │                        │
│  │ Puppeteer   │ │                        │
│  │ Workspace   │ │                        │
│  │ puppeteer.md│ │                        │
│  └─────────────┘ │                        │
│  12 docs indexed │                        │
└─────────────────┴────────────────────────┘
```

The panel uses a `SplitPane` (34% left, 66% right):
- **Left:** Filter, quick access buttons, document list, stats
- **Right:** Title, path, action buttons, tip, Markdown content preview

---

## Quick Access

Pre-configured buttons for common documentation entry points:

### Row 1 — Core Docs

| Button | Target Path |
|--------|-------------|
| **README** | `README.md` |
| **Overview** | `docs/INDEX.md` |
| **Editor** | `docs/editor/core/editor.md` |

### Row 2 — Scripting & Runtime

| Button | Target Path |
|--------|-------------|
| **VNS** | `docs/VNS Scripting/VNS Scripting.md` |
| **JES** | `docs/JES Scripting/JES Scripting.md` |
| **Runtime** | `docs/runtime/core/runtime.md` |
| **Menus** | `docs/project-setup/content/title-screen.md` |

Quick access buttons are disabled (grayed out) when their target file doesn't exist in the current workspace.

### Quick Commands

Copy-to-clipboard buttons for common terminal commands:

| Button | Command |
|--------|---------|
| **Build** | `./gradlew build` |
| **Run Editor** | `./gradlew :editor:run` |
| **Run Runtime** | `./gradlew :runtime:run` |

Clicking a command button copies it to the system clipboard for pasting into a terminal.

---

## Document List

A `ListView` showing all discovered Markdown files, each displayed as:
- **Title** — extracted from the first `# Heading` in the file (within the first 100 lines)
- **Source label** — "Workspace" or "Project" indicating where the file was found
- **Relative path** — path relative to the source root

### Interactions

| Action | Result |
|--------|--------|
| **Click** | Shows the document preview in the right pane |
| **Double-click** | Opens the file in the editor as a full tab |
| **Enter** | Same as double-click |

---

## Filter

The filter text field performs real-time case-insensitive search across:
- Document title
- Relative file path
- Source label

The filtered list updates instantly as you type.

---

## Preview Pane

The right side shows a read-only `TextArea` with the raw Markdown content of the selected document. Above it:

| Element | Description |
|---------|-------------|
| **Title** | Document title (from `# Heading`) |
| **Path** | Full relative path |
| **Open in Editor** | Opens the document as a full editor tab |
| **Reveal File** | Opens the containing directory in the OS file manager |
| **Copy Path** | Copies the file path to the clipboard |
| **Tip** | "Press F1 from anywhere in the editor to jump back here." |

---

## Document Indexing

### Scan Sources

The Help Center scans two root directories:

1. **Workspace root** — the JVN repository root (contains framework documentation)
2. **Project root** — the currently opened game project (contains project-specific docs)

### Scan Process

1. Recursively walk `docs/` subdirectories in each root
2. Collect all `.md` files
3. Extract the title from the first `# Heading` line (within the first 100 lines)
4. Deduplicate by path (workspace docs and project docs may overlap)
5. Sort alphabetically by title
6. Update the stats label (e.g., "12 docs indexed")

### Title Extraction

The scanner reads up to `TITLE_SCAN_LINE_LIMIT` (100) lines from each Markdown file, looking for a line starting with `# `. The first match becomes the document's title. If no heading is found, the filename is used as the title.

---

## Refresh

The **Refresh** button triggers a full re-index:
1. Clears the current index
2. Rescans both workspace and project roots
3. Reapplies the current filter
4. Selects the first document if nothing was selected
5. Updates quick access button states

Refresh is also called automatically when `setWorkspaceRoot()` or `setProjectRoot()` is called.

---

## Integration

| Callback | Description |
|----------|-------------|
| `setOnOpenDoc(Consumer<File>)` | Called when a document is opened (double-click or Open in Editor button). EditorApp wires this to `openFile()`. |
| `setWorkspaceRoot(File)` | Sets the JVN workspace root for framework doc discovery |
| `setProjectRoot(File)` | Sets the game project root for project-specific doc discovery |

---

## F1 Shortcut

The editor registers a global F1 shortcut that:
1. Ensures the Help tab exists in the sidebar
2. Selects the Help tab
3. Focuses the Help Center panel

This provides instant access to documentation from anywhere in the editor.

---

## Related Docs

- [Sidebar Utilities Overview](../overview/sidebar-utilities.md) — all 14 sidebar panels
- [Editor Guide](../../core/editor.md) — main editor layout and modes

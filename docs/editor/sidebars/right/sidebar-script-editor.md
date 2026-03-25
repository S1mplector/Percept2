# Sidebar — Script Editor

A focused VNS script explorer and launcher panel. Browse project scripts in a tree view, inspect label outlines and include dependencies, search across all scripts, and pop out a dedicated tabbed code editor window.

Source: `editor/src/main/java/com/jvn/editor/ui/ScriptEditorLauncherView.java`

---

## Overview

The Script Editor sidebar is designed to behave like a small IDE explorer for VNS scripts. It scans the project's scripts directory, builds a workspace model with label and include analysis, and provides quick access to open, create, rename, delete, and search scripts.

- **Default side:** Right (hidden by default)
- **Tab name:** Script Editor
- **Panel chooser entry:** Script Editor

---

## Features

| Feature | Description |
|---------|-------------|
| **Project tree** | Hierarchical tree of all `.vns` scripts under the project's scripts directory |
| **Filter** | Live case-insensitive filter across filenames, paths, and labels |
| **Search in scripts** | Debounced full-text search across all script file contents (300ms debounce) |
| **Label outline** | Shows all labels defined in the selected script |
| **Include graph** | "Includes" and "Included By" lists for the selected script |
| **Open in editor** | Double-click or Enter to open in the main editor tab |
| **Pop Out IDE** | Launches a dedicated tabbed VNS code editor window |
| **New Script** | Create a new `.vns` file with a starter template |
| **Rename / Delete** | Right-click context menu or keyboard shortcuts (F2, Delete) |
| **Reveal** | Open the selected file's containing folder in the system file manager |
| **Workspace stats** | Script count, folder count, and total label count |

---

## UI Sections

### 1. Header Card

- **Project path** and **scripts root** path
- **Stats row** — three mini cards showing script count, folder count, and label count
- **Action buttons:**
  - **Open in Editor** — opens the selected script in the main editor
  - **Pop Out IDE** — launches the dedicated editor window
  - **New Script** — creates a new `.vns` file
  - **Refresh** — rescans the workspace
  - **Reveal** — opens the file in the system file manager

### 2. Explorer Filter & Search

- **Explorer Filter** — filters the tree view by filename, path, or label name
- **Search in Scripts** — full-text content search across all `.vns` files; results appear as clickable entries with line numbers

### 3. Project Explorer Tree

A tree view showing the scripts directory hierarchy:

- **Folder nodes** — with script count badges
- **Script nodes** — individual `.vns` files
- **Color-coded icons** — folders in gold, files in gray
- **Double-click** or **Enter** to open in the main editor
- **Context menu** — Open, Open in Pop-Out, New Script Here, Rename, Delete, Copy Path, Reveal

### 4. Selection Inspector

When a script is selected, the bottom panel shows:

- **Selection title** — filename
- **Path** — relative path from project root
- **Metadata** — file size, last modified date, line count, label count
- **Label Outline** — clickable list of all labels (jumps to line on click)
- **Includes** — scripts that this file includes
- **Included By** — scripts that include this file

---

## Pop-Out IDE Window

The **Pop Out IDE** button opens a standalone code editor window with:

- **Tabbed editing** — multiple scripts open as tabs
- **VNS syntax highlighting** — full syntax-aware editor via `VnsCodeEditor`
- **Configurable font size** — inherits editor preferences
- **Status bar** — current file path and diagnostics

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| **Enter** | Open selected script in main editor |
| **F2** | Rename selected script |
| **Delete / Backspace** | Delete selected script (with confirmation) |
| **F5** | Refresh workspace |

---

## Workspace Model

The sidebar builds a `WorkspaceSnapshot` by scanning the scripts directory:

- Discovers all `.vns` files recursively
- Parses each file to extract labels and include directives
- Builds an include dependency graph (includes / included-by)
- Computes per-folder script counts
- All analysis runs on the JavaFX application thread (files are typically small)

---

## Related Docs

- [Sidebar Utilities Overview](../overview/sidebar-utilities.md) — all sidebar panels
- [VNS Diagnostics](sidebar-vns-diagnostics.md) — live error/warning diagnostics for VNS scripts
- [Label Flow Map](sidebar-label-flow-map.md) — visual label-to-label flow graph
- [Editor Guide](../../core/editor.md) — VNS editing modes and preview

# Sidebar — Project Explorer

The file-tree panel for navigating, creating, and managing project files and directories.

Source: `editor/src/main/java/com/jvn/editor/ui/ProjectExplorerView.java`

---

## Overview

The Project Explorer is the primary navigation panel in the JVN editor. It displays a recursive tree of the currently opened project directory, with real-time filtering, file creation scaffolding, and a one-click project run button.

- **Default side:** Left
- **Tab name:** Project
- **Always open:** Yes (the Project tab cannot be closed)

---

## UI Elements

| Element | Description |
|---------|-------------|
| **Header** | "Project" label |
| **Filter** | Text field for real-time file/directory name filtering |
| **File tree** | Recursive `TreeView<File>` with expand/collapse |
| **Run button** | Appears on the project root node — launches JVN runtime |

---

## File Tree

- Files and directories are sorted alphabetically (case-insensitive)
- Hidden files (names starting with `.`) are excluded
- Directories show as expandable nodes
- Files show as leaf nodes
- **Double-click** a file to open it in the editor (or system default for non-editable types)
- The tree root shows the project directory name with a **Run** button inline

### Filtering

The filter text field matches against file and directory names in real-time:
- Matching is case-insensitive
- A directory is shown if any descendant matches the filter
- The filter clears by deleting its contents

---

## Context Menu

Right-click any item in the tree to access:

| Action | Description |
|--------|-------------|
| **Open** | Open the selected file in the editor |
| **New JES Script...** | Prompts for a name, creates `<name>.jes` with a scene template: `scene "name" { }` |
| **New VNS Script...** | Prompts for a name, creates `<name>.vns` with a label template: `@label start` |
| **New Java Class...** | Prompts for a class name, creates a `.java` file in the project source tree with package and class stub |
| **New Folder...** | Prompts for a name, creates a subdirectory under the selected directory |
| **Rename...** | Renames the selected file or directory via a text input dialog |
| **Delete** | Deletes the selected file or directory with a confirmation prompt |
| **Reveal in Finder** | Opens the containing directory in the OS file manager (macOS Finder, Windows Explorer, etc.) |

### File Creation Details

- **New JES Script** creates the file in the currently selected directory (or project root if nothing is selected)
- **New VNS Script** behaves the same way, placing the file in the nearest directory context
- **New Java Class** scans the project for a `src/main/java` directory and places the file appropriately
- After creation, the new file is automatically opened in the editor

---

## Run Button

The **Run** button appears only on the project root tree node. Clicking it:

1. Sets the project directory as the runtime working directory
2. Selects the Project tab
3. Invokes the project runner (`doRunProject`), which launches the JVN runtime with the project configuration

The button has a tooltip: "Run this project in JVN Runtime".

---

## Keyboard & Mouse Shortcuts

| Action | Shortcut |
|--------|----------|
| Open file | Double-click |
| Open context menu | Right-click |

---

## Integration

- **File open callback** — when a file is opened, the editor determines the appropriate tab type (JES visual editor, VNS text editor, Java editor, etc.)
- **Project open callback** — when Run is clicked, the editor opens the project and starts the runtime
- Files that are not directly editable (images, audio, etc.) are opened with the system default application via `Desktop.getDesktop().open()`

---

## Related Docs

- [Sidebar Utilities Overview](../overview/sidebar-utilities.md) — all 14 sidebar panels
- [Editor Guide](../../core/editor.md) — main editor layout and modes
- [Asset Browser](../right/sidebar-asset-browser.md) — asset-focused browsing with preview

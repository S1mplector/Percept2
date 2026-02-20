# Editor

The JVN Editor is the main authoring environment for VN scripts, menu/config assets, timeline flow, and runtime launch.

Launch command:

```bash
./gradlew :editor:run
```

## Layout Overview

### Startup welcome dashboard

- The center area opens with a `Welcome` tab by default.
- Shows:
  - JVN logo + editor version
  - recent projects (with one-click open)
  - workspace/project health checks (Java, Gradle wrapper, Git/Git LFS, optional artifacts, missing project files)
- Quick actions:
  - `New Project`
  - `Open Project`
  - `Refresh Health`

### Top bar

- Menus: `File`, `Edit`, `Code`, `Project`, `Version Control`, `Help`
- Main actions: `Open`, `Save`, `Undo`, `Redo`, `Apply Code`, `Run`
- Status + performance strip (CPU/GPU/RAM/FPS)

### Left panel: Project Explorer

- Project tree with filter box
- Root-level **Run** button for full project runtime launch
- Context menu actions:
  - open
  - reveal in file manager
  - new JES/VNS/Java/folder
  - rename
  - delete

### Center panel: Tabbed editors

Files open into typed tabs via `FileEditorTab` dispatch:

- VNS (`.vns`) -> VNS code editor + VN preview
- JES (`.jes`) -> JES code editor + viewport preview
- Timeline (`.timeline`) -> timeline graph + timeline code editor
- Menu/theme/layout config -> dedicated studio editors (Design / Code / Split modes)
- General text/code formats -> text editor

### Side panels (+ chooser system)

- Left defaults to `Project` tab.
- Right starts empty; use `+` to open a panel chooser.
- Available addable panels on **both** sides:
  - `Project`
  - `Timeline`
  - `VNS Diagnostics`
  - `Label Flow`
  - `Assets`
  - `Version Control`
  - `Help`
  - `Inspector`

## Project Run Behavior

Project run from editor:

1. Reads `jvn.project` from project root.
2. For VN projects (`type=vn`), launches runtime with:
   - `--assets <projectRoot>`
   - normalized `--script <entryVns>`
3. Executes Gradle with isolated user home:
   - `.jvn-gradle-user-home`
   - `--no-daemon`, `--console=plain`, VFS watch disabled

This design avoids shared global lock issues and makes editor-run more consistent across machines.

## Supported Editing Modes

### VNS editor

- syntax highlighting
- strict parser diagnostics (same parser as runtime)
- lint for:
  - undefined labels
  - missing assets/background ids
  - unreachable labels/blocks
- quick fixes via context menu on diagnostics:
  - create missing label
  - replace label with existing one
  - replace missing asset path from discovered assets
  - remove unreachable block
- side-panel integrations:
  - `VNS Diagnostics` panel with filter + click-to-jump lines
  - `Label Flow` graph panel for label/jump/choice flow

### JES editor

- syntax highlighting
- parser lint diagnostics
- live apply into preview viewport

### Timeline editor

- graph editing + text DSL editing
- arc/link validation against script existence and labels
- drag-and-drop `.vns` files to create arcs quickly
- cluster filtering/collapse and auto layout

### Visual config editors

- `config/ui/dialogue.layout` -> `DialogueLayoutEditorView`
  - drag textbox/namebox/choice layout in preview
  - import textbox assets and tune text/name bounds
- `config/menu/menus/*.menu` -> `MenuScreenVisualEditor`
  - edit menu items/actions/styles/targets with table + preview
  - import per-item button assets (`bgAsset`, selected/disabled variants)
  - map per-item click/render bounds (`boundsX/Y/Width/Height`) by table or drag
- `config/menu/layouts/*.layout` -> `MenuLayoutVisualEditor`
  - edit list/title/hint geometry with draggable preview guides
- `config/menu/styles/*.style` -> `MenuStyleVisualEditor`
  - edit reusable item typography/colors/prefixes
  - define shared button skins + text padding offsets

All visual editors sync back to plain text properties content, preserving source-control-friendly files.

## New Project Wizard

The wizard creates a layered VN project scaffold with:

- `config/settings`, `config/timeline`, `config/ui`, `config/menu/...`
- `scripts/story`, `scripts/common`, `scripts/system`
- structured `assets/` subfolders for backgrounds/characters/audio/ui/fonts
- `jvn.project` manifest with entry script and config paths
- optional Git repository + Git LFS defaults + initial commit

See full wizard documentation:
- `docs/Project Setup/New Project Wizard.md`

## Keyboard Shortcuts

- `Cmd/Ctrl+O` -> Open Project
- `Cmd/Ctrl+Shift+O` -> Open JES file
- `Cmd/Ctrl+Alt+O` -> Open VNS file
- `Cmd/Ctrl+S` -> Save
- `Cmd/Ctrl+Shift+S` -> Save As
- `Cmd/Ctrl+W` -> Close Tab
- `Cmd/Ctrl+Enter` -> Apply Code
- `F11` -> Toggle editor fullscreen (for split preview+editor tabs)
- `Cmd/Ctrl+Z` -> Undo
- `Cmd/Ctrl+Shift+Z` -> Redo
- `F1` -> Open/select Help Center
- `Cmd/Ctrl+Shift+G` -> Open/select Version Control
- `Cmd/Ctrl+Shift+H` -> Open/select Welcome tab

## Recommended Team Workflow

1. Keep story logic in VNS with clear label naming.
2. Keep UI tuning in config files edited through visual tools when practical.
3. Use timeline graph for arc-level narrative structure.
4. Run from project tree root button frequently for full end-to-end checks.
5. Keep source control clean with the Version Control panel (`Cmd/Ctrl+Shift+G`).
6. Use Help Center (`F1`) for quick docs lookup without leaving editor.

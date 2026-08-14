# Editor And Launcher Settings

JVN has two application preference windows:

- **Editor Settings** controls the main authoring environment.
- **Launcher Settings** controls the standalone project launcher and how it hands projects to the editor/runtime.

These preferences are separate from in-game VN settings such as text speed, volumes, auto-play, and skip mode. VN settings are documented in [VN Settings Reference](../../runtime/systems/vn-settings.md).

## Where Settings Are Stored

Editor and launcher preferences are shared through:

```text
~/.jvn-editor/editor-preferences.properties
```

The same store is used by both entry points so a launcher choice, such as spawned editor theme, is available when the editor starts.

## Opening Settings

### Editor

Open **Editor Settings** from:

- `Edit -> Editor Settings`
- `Panels -> Settings -> Editor Settings`
- `Navigate -> Editor Settings`
- `Tools -> Editor Settings`
- `Window -> Editor Settings`
- the Workspace Hub settings action
- `Cmd/Ctrl+,`

The editor settings panel can open as a tab or as its own window.

### Launcher

Open **Launcher Settings** from:

- launcher **Settings**
- `Edit -> Settings...`
- `Cmd/Ctrl+,`

## Finding A Setting

Both settings windows include a **Filter settings...** field. Type one or more words to narrow the visible sections. Each word must match the section's title, description, or setting keywords.

## Editor Settings

### Appearance

| Setting | What It Does | Default |
|---------|--------------|---------|
| Editor Theme | Switches the editor between dark and light theme. | Dark |
| Code Text Size | Changes the code editor font size. | 13 |

### VNS Authoring

| Setting | What It Does | Default |
|---------|--------------|---------|
| Wrap long VNS lines by default | Enables line wrapping in every open VNS tab and for VNS tabs opened later. | Off |
| Show the VNS script minimap | Shows the script overview and its diagnostic/timeline markers beside VNS source. | On |

Saving either preference applies it to open VNS tabs immediately. The VNS toolbar's line-wrap
button (`Cmd/Ctrl+Shift+W`) can still change wrapping temporarily for the current editor session.

### Runtime

| Setting | What It Does | Default |
|---------|--------------|---------|
| Max FPS | Caps editor preview/render timing. `0` follows the display rate. | 0 |
| Show runtime performance HUD when launching projects | Adds runtime performance HUD output when launching VN projects from the editor. | On |
| Save dirty files before project runs | Saves modified editor tabs before running the project. | On |
| Confirm before running a project from the editor | Shows a confirmation dialog before editor project launch. | Off |
| Skip Gradle tests for default project runs | Uses `-x test` for Gradle run commands when `jvn.project` does not define explicit `args`. | On |

### Startup

| Setting | What It Does | Default |
|---------|--------------|---------|
| Show Workspace Hub tab on startup | Opens the Workspace Hub when the editor starts. | On |
| Load sidebar extensions only when opened | Defers sidebar tool construction until needed to reduce startup memory work. | On |

### File Opening

| Setting | What It Does | Default |
|---------|--------------|---------|
| Default Text Editor | Chooses JVN Editor, the system default app, or a custom command for opening files externally. | JVN Editor |
| Custom Command | Command template used when Default Text Editor is set to Custom Command. | Empty |

Custom command templates support:

```text
{file}     absolute path to the selected file
{project}  absolute path to the current project
```

Example:

```text
code --reuse-window {file}
```

### Default Sidebar Panels

Each editable sidebar tool can be configured with:

- **Default Placement**: `LEFT`, `RIGHT`, or `HIDDEN`
- **Show In Chooser**: whether the tool appears in the `+` panel chooser

These settings change future editor layouts and chooser behavior. The pinned Project tab remains protected by the editor.

## Launcher Settings

### Appearance

| Setting | What It Does | Default |
|---------|--------------|---------|
| Launcher Theme | Switches the launcher between dark and light theme. | Dark |
| Editor Theme | Theme passed to editor processes launched from the launcher. | Dark |

### Startup Project

| Setting | What It Does | Default |
|---------|--------------|---------|
| Restore the last selected project on startup | Re-selects the last launcher project when the launcher opens. | On |
| Startup Project | Stored project path used by restore-on-startup. | Empty |

Use **Use Current** to stage the currently selected launcher project as the startup project. Use **Clear** to remove the stored path.

### Editor Handoff

| Setting | What It Does | Default |
|---------|--------------|---------|
| Default Text Editor | Chooses how file opens leave the launcher. | JVN Editor |
| Custom Command | Command template for external custom editor launch. | Empty |
| Keep launcher open after opening the editor | Leaves the launcher visible after spawning the editor. | Off |
| Confirm before opening a project in the editor | Shows a confirmation dialog before editor handoff. | Off |

### Run Behavior

| Setting | What It Does | Default |
|---------|--------------|---------|
| Confirm before running a project from the launcher | Shows a confirmation dialog before launcher project run. | Off |
| Show runtime performance HUD when launching projects | Adds runtime performance HUD output when launching VN projects from the launcher. | On |
| Skip Gradle tests for default project runs | Uses `-x test` for Gradle run commands when `jvn.project` does not define explicit `args`. | On |

## Gradle Run Args

For Gradle projects, `jvn.project` can define explicit `args`:

```properties
type=gradle
path=:runtime
task=run
args=-x test --info
```

When `args` exists, the manifest wins and the settings window does not override it.

When `args` is missing:

- **Skip Gradle tests for default project runs** on -> default args are `-x test`
- **Skip Gradle tests for default project runs** off -> default args are empty

## Buttons

Both settings windows use the same button pattern:

- **Reload** discards unsaved form edits and reloads the stored preferences.
- **Save** writes the current form values and applies them to the running app where possible.
- **Defaults** restores default values in the form. Use **Save** to persist them.

## Related Docs

- [Editor Guide](editor.md)
- [Welcome Center](welcome-center.md)
- [Run Console](run-console.md)
- [Sidebar Utilities Overview](../sidebars/overview/sidebar-utilities.md)
- [VN Settings Reference](../../runtime/systems/vn-settings.md)

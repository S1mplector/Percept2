# Run Console

Integrated build and runtime output console that filters verbose Gradle noise and presents color-coded, searchable engine messages.

Source: `editor/src/main/java/com/jvn/editor/ui/RunConsoleView.java`

---

## Overview

The Run Console opens as a bottom panel when a project is launched from the editor (via the Project Explorer **Run** button or `Project > Run`). It attaches to the Gradle build process, streams stdout in real time, and classifies each line for smart filtering and coloring.

---

## Engine States

The console tracks a state machine displayed in the status bar:

| State | Color | Meaning |
|-------|-------|---------|
| **BUILDING** | Blue | Gradle build in progress |
| **STARTING** | Yellow | Build succeeded, engine is initializing |
| **RUNNING** | Green | Engine is running normally |
| **STOPPED** | Gray | Process exited cleanly (exit code 0) |
| **FAILED** | Red | Process exited with a non-zero exit code |

---

## Toolbar Actions

| Button | Description |
|--------|-------------|
| **Run** | Re-run the last build (enabled when stopped or failed) |
| **Stop** | Kill the running process |
| **Copy** | Copy the last traceback/error block to clipboard |
| **Clear** | Clear all output |
| **Build Output** | Toggle — show/hide suppressed Gradle noise lines |
| **Auto-scroll** | Toggle — auto-scroll to latest output |
| **Wrap** | Toggle — word-wrap long lines |

---

## Filtering & Search

### Smart Line Classification

Lines are classified automatically using regex patterns:

| Category | Pattern | Behavior |
|----------|---------|----------|
| **Engine messages** | Lines starting with `[JVN]`, `[Engine]`, `[Scene]`, `[Audio]`, `[Script]`, `[VN]`, `[Menu]`, `[Runtime]`, `[Init]`, `[Asset]`, `[Error]`, `[WARN]`, `[INFO]` | Always shown, highlighted in light blue |
| **Errors** | Lines matching `Exception`, `Error`, `FAILED`, `fatal:`, `at line N` | Shown in red, increment error counter |
| **Warnings** | Lines matching `Warning`, `WARN`, `deprecated`, `could not` | Shown in orange, increment warning counter |
| **Gradle noise** | Lines matching `> Task`, `> Configure`, `BUILD SUCCESSFUL`, `Deprecated Gradle`, download progress, etc. | Suppressed by default (toggle with **Build Output**) |

### Log Level Filter

ComboBox with four filter modes:

- **All** — show everything (respecting Build Output toggle)
- **Engine** — only engine lifecycle messages
- **Errors** — only error lines
- **Warnings** — only warning lines

### Search

The search field performs a live substring match across all buffered lines and highlights matches. All raw output is retained in memory for re-filtering.

---

## Status Bar

Bottom strip showing:

| Element | Description |
|---------|-------------|
| **State badge** | Current engine state with color |
| **Elapsed time** | Time since process start |
| **Line count** | Total lines received |
| **Error count** | Running error counter (red when > 0) |
| **Warning count** | Running warning counter (orange when > 0) |

---

## Color Scheme

| Line Type | Color |
|-----------|-------|
| Error | `#f38ba8` (pink-red) |
| Warning | `#f0b673` (orange) |
| Engine message | `#dbe4f0` (light blue-white) |
| Info | `#8ab4f8` (blue) |
| Normal | `#b5bfd0` (gray-blue) |
| Gradle noise | `#6b7381` (dim gray) |

---

## Menu Bar

The console includes a mini menu bar with:

- **Console** menu — Clear, Copy All, Save to File, Close
- **View** menu — toggle Build Output, Auto-scroll, Word Wrap
- Keyboard accelerators for common actions

---

## Related Docs

- [Editor Guide](editor.md) — project run behavior
- [Runtime Guide](../../runtime/core/runtime.md) — CLI options and launch patterns

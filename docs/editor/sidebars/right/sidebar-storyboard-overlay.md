# Sidebar — Storyboard Overlay

Ghost storyboard frames over the active JES or VNS preview for staging reference while placing characters, tuning framing, or checking shot continuity against board art.

Source: `editor/src/main/java/com/jvn/editor/ui/StoryboardOverlayView.java`

---

## Overview

The Storyboard Overlay is a sidebar panel that scans a storyboard folder, lists available frames, and composites the selected frame over the editor's live preview with adjustable opacity. It is designed for teams working with pre-production storyboards who need to match engine staging to board art.

- **Default side:** Right
- **Tab name:** Storyboard Overlay
- **Panel chooser entry:** Storyboard Overlay
- **Also accessible via:** `View > Panels > Storyboard Overlay` or `Tools > Storyboard Overlay`

---

## Features

| Feature | Description |
|---------|-------------|
| **Auto-detect folder** | Scans the project for a `storyboard/`, `storyboards/`, or `boards/` directory automatically |
| **Manual folder override** | Text field or Browse button to point at any directory |
| **Frame list** | Filterable list of discovered image files with thumbnail previews |
| **Quick filter** | Live substring filter across frame filenames |
| **Opacity slider** | Adjustable 5%–100% overlay opacity (default 35%) |
| **Enable/disable toggle** | Checkbox to show or hide the overlay without losing selection |
| **Follow active scene** | Tracks the active JES or VNS file and finds the most probable storyboard frame match |
| **Best-match jump** | Jumps directly to the strongest storyboard candidate for the current script context |
| **Previous / Next** | Step through frames sequentially while working |
| **Reveal frame** | Opens the selected frame in the OS so artists can jump straight to the source file |
| **Preview thumbnail** | Shows the selected frame with path, dimensions, and list position below the list |
| **Viewport-fitted compositing** | Overlay scales to match the project viewport from `jvn.project`, so 1:1 boards line up with the engine preview |

---

## UI Sections

### 1. Source Configuration

- **Folder field** — shows the active storyboard directory (auto-detected or manual)
- **Browse** — opens a directory chooser
- **Auto** — clears the manual override and returns to automatic discovery
- **Refresh** — rescans the folder for new/changed frames

### 2. Frame Browser

- **Filter field** — live case-insensitive substring match on filenames
- **Frame list** — scrollable list with filename-first rows and the parent folder path underneath
- Click to select; the selected frame is composited over the preview

### 3. Preview & Controls

- **Preview image** — larger thumbnail of the selected frame
- **Path label** — absolute path to the selected image
- **Metadata label** — image dimensions, viewport fit status, and selected frame index
- **Previous / Next** buttons — navigate frames sequentially
- **Jump To Match** — snap to the strongest scene-aware storyboard candidate
- **Reveal Frame** — open the selected image file outside the editor

### 4. Overlay Settings

- **Show overlay in preview** — checkbox to enable/disable compositing
- **Follow active scene** — automatically reselects the best frame match when the active JES/VNS tab changes
- **Opacity slider** — 5% to 100% with live numeric readout

---

## Folder Discovery

When no manual folder is set, the scanner looks for these directories under the project root:

1. `storyboard/`
2. `storyboards/`
3. `boards/`
4. `assets/storyboard/`

If none are found, the panel falls back to the project's `assets/` or `images/` directory so you can still point it at reference material.

Supported image formats: `.png`, `.jpg`, `.jpeg`, `.bmp`, `.gif`, `.webp`

---

## State Persistence

All settings are saved to `.jvn/storyboard-overlay.properties`:

| Key | Description |
|-----|-------------|
| `folder` | Manual folder path (empty for auto-detect) |
| `filter` | Last-used filter text |
| `enabled` | Overlay enabled state |
| `followActive` | Whether the panel should auto-follow the active JES/VNS scene |
| `opacity` | Opacity percentage (5–100) |
| `selected` | Last selected frame filename |

State is restored automatically when the panel reopens.

---

## Integration

The overlay communicates with the preview via a `StoryboardOverlayState` callback and now also receives the active script file from the editor so it can compute probable storyboard matches:

- **Image** — the selected `Image` object (or null when disabled)
- **Opacity** — the current opacity value (0.05–1.0)
- **Enabled** — whether the overlay should be drawn

The preview renderer receives this state and composites the overlay image behind the scene content at the configured opacity, scaled to fit the project's target resolution.

---

## Notes

- The overlay is **editor-only** and does not ship in runtime builds
- Image loading is cached per session to avoid redundant disk I/O
- Folder scanning runs on a background task to keep the UI responsive
- The overlay respects the project viewport resolution for correct alignment

---

## Related Docs

- [Sidebar Utilities Overview](../overview/sidebar-utilities.md) — all sidebar panels
- [Editor Guide](../../core/editor.md) — main editor layout
- [VNS Preview Virtual Viewport](../../core/editor.md#vns-preview-virtual-viewport) — resolution-aware preview rendering

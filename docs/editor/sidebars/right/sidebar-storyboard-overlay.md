# Sidebar — Storyboard Overlay

Ghost storyboard frames over the active JES or VNS preview for staging reference while placing characters, tuning framing, or checking shot continuity against board art.

Source: `modules/editor/src/main/java/com/jvn/editor/ui/StoryboardOverlayView.java`

---

## Overview

The Storyboard Overlay is a sidebar panel that scans a storyboard folder, lists available frames, and composites the selected frame over the editor's live preview with adjustable opacity. It is designed for teams working with pre-production storyboards who need to match engine staging to board art.

- **Default side:** Right
- **Tab name:** Storyboard Overlay
- **Panel chooser entry:** Storyboard Overlay
- **Also accessible via:**
  - `View > Panels > Visual Tools > Storyboard Overlay`
  - `Navigate > Visual Tools > Storyboard Overlay`
  - `Tools > Layout & UI > Storyboard Overlay`
  - `Window > Open Tool Window > Storyboard Overlay`

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
| **Flush preview compositing** | Overlay maps directly onto the active JES/VNS preview rectangle from `jvn.project`, so matching-size boards sit edge-to-edge with the engine preview |
| **Crop selection** | Drag directly on the frame preview, or open the large crop selector, to use part of a storyboard page as the overlay source |
| **Direct offset drag** | Drag the rendered storyboard image in the active preview to adjust X/Y mapping offsets visually |

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

Keyboard support:

- `Down` from the filter field moves focus into the frame list
- `Enter` from the filter field selects the only remaining result
- `Esc` in the filter field clears the filter
- `Cmd/Ctrl+F` in the frame list jumps back to the filter field
- `Enter` on a selected frame reveals it in the OS
- `Left` / `Right` step to the previous or next frame

### 3. Preview & Controls

- **Preview image** — larger thumbnail of the selected frame
- **Path label** — absolute path to the selected image
- **Metadata label** — image dimensions, viewport fit status, and selected frame index
- **Previous / Next** buttons — navigate frames sequentially
- **Jump To Match** — snap to the strongest scene-aware storyboard candidate
- **Reveal Frame** — open the selected image file outside the editor
- **Crop Mode** — enters explicit crop drawing mode for the selected frame
- **Crop preview** — while crop mode is active, drag over the preview image to draw a dashed crop rectangle
- **Apply Crop** — saves the current crop rectangle and exits crop mode
- **Exit Crop** — leaves crop mode without applying the current draft
- **Show selected crop only** — composites only the saved crop while preserving full-frame preview context when off
- **Full Screen Crop** — opens a large crop selector for more precise source rectangles
- **Clear Crop** — removes the saved crop for the selected frame
- **Preview drag** — drag the storyboard image in the live JES/VNS preview to adjust X and Y offsets

### 4. Overlay Settings

- **Show overlay in preview** — checkbox to enable/disable compositing
- **Follow active scene** — automatically reselects the best frame match when the active JES/VNS tab changes
- **Opacity slider** — 5% to 100% with live numeric readout
- **Mode / Game / Board / Scale / Offset** — advanced mapping controls for fitting storyboard frames whose canvas differs from the project viewport
- **Board dimensions** default to the selected image dimensions until explicitly changed with the fields or setup buttons

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
| `hideUi` | Whether storyboard mode hides VN UI chrome in preview |
| `fitMode` | Overlay placement mode (`FIT`, `FILL`, `STRETCH`, `ORIGINAL`) |
| `runtimeWidth`, `runtimeHeight` | Runtime viewport dimensions used for overlay placement |
| `runtimeSizeExplicit` | Whether runtime dimensions were manually overridden |
| `storyboardWidth`, `storyboardHeight` | Storyboard canvas dimensions used for mapping |
| `storyboardSizeExplicit` | Whether storyboard dimensions were manually overridden |
| `scale`, `offsetX`, `offsetY` | Manual placement transform |
| `crop.*` | Per-frame crop rectangles and crop enabled state |

State is restored automatically when the panel reopens.

---

## Integration

The overlay communicates with the preview via a `StoryboardOverlayState` callback and now also receives the active script file from the editor so it can compute probable storyboard matches:

- **Image** — the selected `Image` object (or null when disabled)
- **Opacity** — the current opacity value (0.05–1.0)
- **Enabled** — whether the overlay should be drawn

The preview renderer receives this state and composites the overlay image over the scene content at the configured opacity. The image is drawn into the same fitted preview rectangle as the active JES or VNS render, so the overlay does not drift into letterbox space or a separate board-page layout.

## Matching Behavior

When **Follow active scene** is enabled, the panel scores available storyboard frames against the active JES or VNS file using filename and path tokens. The goal is not strict naming magic; it is to get the likely frame into view quickly enough that staging work stays fluid.

Use **Jump To Match** when you want to resync the frame list manually without changing the follow behavior.

---

## Notes

- The overlay is **editor-only** and does not ship in runtime builds
- Image loading is cached per session to avoid redundant disk I/O
- Folder scanning runs on a background task to keep the UI responsive
- The overlay respects the project viewport resolution for correct alignment
- Boards with the same dimensions and aspect ratio as the project viewport align without distortion

---

## Related Docs

- [Sidebar Utilities Overview](../overview/sidebar-utilities.md) — all sidebar panels
- [Editor Guide](../../core/editor.md) — main editor layout
- [VNS Preview Virtual Viewport](../../core/editor.md#vns-preview-virtual-viewport) — resolution-aware preview rendering

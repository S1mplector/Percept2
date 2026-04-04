# editor

JavaFX-based visual editor for authoring JVN visual novel and game projects. Entry point: `EditorApp`.

## Features

- **Puppeteer** — animation timeline editor with keyframe interpolation, easing picker, audio cues, camera control, onion skinning, and JES/VNS code export
- **Code Editor** — syntax-highlighted JES/VNS script editing with live preview
- **Sidebar Panels** — Project Explorer, Story Timeline, Inspector, Asset Browser, Label Flow Map, VNS Diagnostics, Layered Image Visualizer, Image Attributes/Tint Tools, Layout Launcher, Menu Flow Editor, Puppeteer Launcher, Version Control (Git), Help Center
- **Layout Studio** — visual editors for dialogue layouts, menu styles, menu screens, and button bounds (Bounds Studio)
- **New Project Wizard** — scaffolding with inline validation, resolution-aware templates, locale stubs

## Key Packages

| Package | Purpose |
|---------|---------|
| `commands/` | Undo/redo command infrastructure |
| `ui/` | All sidebar views, layout editors, wizard, dialogs |
| `ui/actioneditor/` | Puppeteer window, timeline panel, animation preview, keyframe editor, code exporter |
| `vcs/` | Git version control service |

## Dependencies

- `:core`, `:fx`, `:audio`, `:audio-fx`, `:scripting`
- `richtextfx` — syntax-highlighted code editing

## Build & Run

```bash
./gradlew :editor:run
```

## Gradle Tasks

| Task | Purpose |
|------|---------|
| `generateDocsScreenshots` | Capture annotated screenshots for all editor profiles |
| `generatePuppeteerDocsScreenshots` | Puppeteer-specific screenshots |
| `generateSidebarDocsScreenshots` | All sidebar panel screenshots |

## Documentation

- [Editor Guide](../docs/editor/core/editor.md)
- [Puppeteer Overview](../docs/editor/puppeteer/puppeteer.md)
- [Puppeteer Editor Guide](../docs/editor/puppeteer/puppeteer-editor-guide.md)
- [Sidebar Utilities](../docs/editor/sidebars/overview/sidebar-utilities.md)

# Editor — Sidebar Utilities

Complete reference for all 16 documented sidebar panels available in the JVN editor. Each panel has its own dedicated sub-document with full details, examples, and usage guides.

Panels can be added to either the left or right sidebar via the **+** tab. Each panel is independently closable and repositionable.

Source: `editor/src/main/java/com/jvn/editor/EditorApp.java` (panel chooser at lines 1880–1955)

---

## Sub-Document Reference

| # | Panel | Sub-Document | Default Side | Purpose |
|---|-------|-------------|-------------|---------|
| 1 | Project Explorer | **[sidebar-project-explorer.md](../left/sidebar-project-explorer.md)** | Left | File tree, create/rename/delete files, run project |
| 2 | Story Timeline | **[sidebar-story-timeline.md](../left/sidebar-story-timeline.md)** | Left | Multi-arc story graph with arc/link management |
| 3 | Inspector | **[sidebar-inspector.md](../right/sidebar-inspector.md)** | Right | Entity property editing for JES scenes |
| 4 | Puppeteer Launcher | **[sidebar-puppeteer-launcher.md](../right/sidebar-puppeteer-launcher.md)** | Right | VNS scene snapshot and Puppeteer launch |
| 5 | VNS Diagnostics | **[sidebar-vns-diagnostics.md](../right/sidebar-vns-diagnostics.md)** | Right | Live error/warning list for .vns scripts |
| 6 | Label Flow Map | **[sidebar-label-flow-map.md](../right/sidebar-label-flow-map.md)** | Right | Visual label-to-label flow graph for VNS |
| 7 | Asset Browser | **[sidebar-asset-browser.md](../right/sidebar-asset-browser.md)** | Right | Project asset discovery, preview, copy path |
| 8 | Layout Launcher | **[sidebar-layout-launcher.md](../right/sidebar-layout-launcher.md)** | Right | Quick-launch layout/style/screen editors |
| 9 | Phone Assets | **[sidebar-phone-assets-tool.md](../right/sidebar-phone-assets-tool.md)** | Right | Structured editor for phone config, contacts, threads, messages, and asset import |
| 10 | Storyboard Overlay | **[sidebar-storyboard-overlay.md](../right/sidebar-storyboard-overlay.md)** | Right | Transparent board-frame overlays for JES and VNS staging previews |
| 11 | Menu Flow Editor | **[sidebar-menu-flow-editor.md](../right/sidebar-menu-flow-editor.md)** | Right | Visual menu-to-menu navigation wiring |
| 12 | Layered Image Visualizer | **[sidebar-layered-image-visualizer.md](../right/sidebar-layered-image-visualizer.md)** | Right | Layered sprite exploration and snippet export |
| 13 | Image Attributes Tool | **[sidebar-image-attributes-tool.md](../right/sidebar-image-attributes-tool.md)** | Right | Attribute-based character image assembly |
| 14 | Image Tint Tool | **[sidebar-image-tint-tool.md](../right/sidebar-image-tint-tool.md)** | Right | Color tinting and grading for character/background images |
| 15 | Version Control | **[sidebar-version-control.md](../right/sidebar-version-control.md)** | Right | Git operations: commit, push, pull, branch, stash |
| 16 | Help Center | **[sidebar-help-center.md](../right/sidebar-help-center.md)** | Right | In-app documentation browser |

### Adding Panels

1. Click the **+** tab on the left or right sidebar
2. A "New Panel" chooser tab opens listing all available panels, including the phone editor utility
3. Click any panel name to add it as a new tab
4. Close panels by clicking the tab's **×** button (Project tab is always open)

---

## Fullscreen Mode (Image Tools)

The three image tools (Layered Image Visualizer, Image Attributes Tool, Image Tint Tool) share a **fullscreen toggle** that expands the panel to fill the entire editor window. Only one image tool can be fullscreen at a time. All three implement the `ImageToolPanel` interface:

```java
public interface ImageToolPanel {
    void setProjectRoot(File projectRoot);
    void refreshCatalog();
    void setOnToggleFullscreen(Runnable handler);
    void setFullscreenActive(boolean active);
}
```

---

## State Persistence

Several sidebar utilities persist their state in the project's `.jvn/` directory:

| Utility | State File |
|---------|-----------|
| Layered Image Visualizer | `.jvn/layered-image-visualizer.properties` |
| Phone Assets | `.jvn/phone-assets-tool.properties` |
| Image Attributes Tool | `.jvn/image-attributes-tool.properties` |
| Image Tint Tool | `.jvn/image-tint-tool.properties` |
| Story Timeline | `.jvn/story-timeline.txt` |
| Version Control | Git state (`.git/`) |

---

## Related Docs

- [Editor Guide](../../core/editor.md) — main editor layout, editing modes, keyboard shortcuts
- [Puppeteer Editor Guide](../../puppeteer/puppeteer-editor-guide.md) — comprehensive Puppeteer usage
- [Puppeteer JES DSL Reference](../../puppeteer/puppeteer-jes-dsl.md) — exported timeline code syntax

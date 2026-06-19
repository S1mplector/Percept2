# JVN Documentation

This directory is the source of truth for Java Vector Nexus editor, runtime, scripting, packaging, and workflow docs.

Start with the public documentation website at [javavectornexus.com](https://javavectornexus.com)
when you want the searchable, hosted guide tree. Start with the detailed [Documentation Index](INDEX.md)
when editing docs in this repository. It is organized by task first, then by subsystem.

## Fast Routes

| Goal | Start Here |
|------|------------|
| Build and run JVN | [Getting Started Guide](guides/getting-started.md) |
| Understand why JVN is source-first for now | [No Prebuilt Download Yet](guides/getting-started.md#why-there-is-no-prebuilt-download-yet) |
| Use or install the Engine Hub | [JVN Engine Hub](editor/core/engine-hub.md) |
| Decide which layer to use | [Choose Your Path in JVN](guides/choose-your-path.md) |
| Identify project file types | [Common JVN File Types](guides/common-file-types.md) |
| Work inside the editor | [JVN Editor Docs](editor/README.md) |
| Animate shots | [Puppeteer Editor Guide](editor/puppeteer/puppeteer-editor-guide.md) |
| Export lighting/stage presets | [Scene Lighting Studio](editor/sidebars/right/sidebar-image-tint-tool.md) |
| Write VN scripts | [VNS Overview](scripting/vns/overview/vns-scripting.md) |
| Write gameplay scenes | [JES Overview](scripting/jes/overview/jes-scripting.md) |
| Build menus and layouts | [Menu Profiles Overview](scripting/ui/menus/menu-profiles.md) |
| Deploy to mobile/web | [Platform Runtimes](runtime/platforms/README.md) |
| Package a game | [Build And Release Docs](project-setup/release/README.md) |

## Directory Map

| Directory | Coverage |
|-----------|----------|
| `architecture/` | Engine architecture, 2D rendering, performance, debugging, and UI parity notes |
| `editor/` | Engine Hub, editor windows, settings, Puppeteer, sidebars, and editor-owned file formats |
| `guides/` | Onboarding, by-example tutorials, cookbooks, integration recipes, and file-type orientation |
| `project-setup/` | New project wizard, project structure, content setup, collaboration, build, release, and deployment |
| `runtime/` | Runtime behavior, interop, save/load, audio, asset resolution, and VN settings |
| `scripting/` | VNS, JES, timelines, menu profiles, layout DSL, visual UI config, and scripting internals |

## Maintenance Notes

- Generated screenshot docs use `generated-*.md` names and are refreshed by docs screenshot tasks.
- Prefer linking to hub pages from new docs, then deep pages for specific APIs or tools.
- Keep implementation-specific docs anchored to current source file names when possible, especially under `editor/`.

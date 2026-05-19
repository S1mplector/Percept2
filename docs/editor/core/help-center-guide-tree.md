# Help Center Guide Tree

The Help Center guide tree is the in-editor documentation map used by the Help sidebar tool.

Current tool version: `v1.2.2`

## Purpose

The tree is designed for two workflows:

- **Guided onboarding:** new users can start at indexes and getting-started pages before moving into VNS, JES, Puppeteer, UI, runtime, and editor tools.
- **Fast reference lookup:** experienced users can filter by title, path, summary, or heading text and jump directly into the relevant section of a Markdown page.

## Tree Shape

The Help Center indexes Markdown files from the workspace and the active project, then builds this hierarchy:

1. **Guide section** — the broad domain, such as Visual Novel Authoring or Gameplay And JES.
2. **Topic folder** — the workflow or reference family inside that domain, such as Language Reference, Timeline DSL, Right Sidebar Tools, or Build And Release.
3. **Document** — one Markdown file, titled from its first heading.
4. **Heading anchors** — level 2 to level 4 headings inside the document.

Topic folders prevent the sidebar from becoming one long flat list as the docs grow.

## Guide Sections

| Section | Typical contents |
|---------|------------------|
| Start Here | README, docs index, getting started, choosing a path, common file types |
| Visual Novel Authoring | VNS overview, language reference, presentation, runtime systems, integration, internals, VNS by example |
| Gameplay And JES | JES overview, scenes, components, timeline DSL, systems, gameplay modules, integration, JES by example |
| Animation And Timelines | Puppeteer editor docs, timeline scripting, timeline animation, story arcs |
| Menus And UI Layout | Menu profiles, screens, styles, layout structures, components, UI tooling |
| Editor And Tools | Core editor pages, sidebar tool docs, editor file tools |
| Runtime And Project Setup | Runtime core, assets, audio, display, save system, project setup, release docs |
| Architecture And Internals | Engine architecture, performance, debugging, module-level internals |
| Guides And Recipes | General guides, cookbooks, and cross-system recipes |
| Current Project Docs | Markdown found in the active project root |
| Reference And Generated Docs | Generated screenshots, audit notes, and lower-frequency references |

## Search Behavior

The filter matches:

- document title
- relative path
- source label (`Workspace` or `Project`)
- extracted summary
- heading text

When a heading matches but the parent document title does not, the tree still shows the document and expands only the matching heading anchors. Selecting a heading opens the document preview and scrolls to that heading.

## Maintenance Rules

- Put first-time docs under `docs/guides/` or the relevant `docs/scripting/.../overview/` path.
- Put reference docs under the domain they describe instead of a catch-all folder.
- Use clear level 2 and level 3 headings; they become navigable anchors in the tree.
- Keep generated screenshot docs named with `generated-` so they stay in the lower-priority generated/reference area.
- Add project-specific docs under the game project root when they should travel with that game.

## Related Docs

- [Help Center](help-center.md)
- [Sidebar Help Center](../sidebars/right/sidebar-help-center.md)
- [Sidebar Utilities Overview](../sidebars/overview/sidebar-utilities.md)

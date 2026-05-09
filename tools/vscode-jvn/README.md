# JVN Language Tools

<img src="images/jlt_icon.png" width="120" alt="JVN Language Tools logo">

**JVN Language Tools** brings Java Vector Nexus project files into VS Code with readable syntax highlighting, useful snippets, and recognizable file icons for the JVN DSL family.

It is built for writers, scripters, technical artists, and developers who work directly with JVN projects and want VS Code to understand the files they touch every day.

## Why Install It

JVN projects use several small domain-specific languages instead of one giant project format. That keeps game projects flexible, but plain VS Code treats those files as anonymous text.

JLT makes those files easier to scan, review, and edit:

- Story scripts become visibly structured by dialogue, commands, labels, choices, and formatting.
- JES scenes and timelines become easier to read by separating entities, components, actions, literals, and properties.
- Story Map files stand out as route/link planning documents.
- Menu, layout, style, theme, and stage preset files get consistent highlighting instead of plain text.
- Common structures are available as snippets, reducing repetitive typing and small syntax mistakes.
- JVN-specific file icons make project folders easier to navigate at a glance.

## What It Supports

| Area | Icon | Files | What It Helps With |
| --- | --- | --- | --- |
| VNS Story Scripts | <img src="images/dsl/vns.png" width="48" alt="VNS icon"> | `.vns` | Dialogue, labels, choices, VN commands, JES bridge calls |
| JES Scenes | <img src="images/dsl/jes.png" width="48" alt="JES icon"> | `.jes` | Scenes, entities, components, actions, animation timelines |
| Story Maps | <img src="images/dsl/storymap.png" width="48" alt="Story Map icon"> | `.storymap`, `.timeline` | Arcs, route links, script planning, legacy timeline maps |
| Menus | <img src="images/dsl/menu.png" width="48" alt="Menu icon"> | `.menu`, `.registry` | Menu screens, item actions, menu registration |
| Layouts | <img src="images/dsl/layout.png" width="48" alt="Layout icon"> | `.layout` | UI bounds, button placement, screen geometry |
| Styles And Themes | <img src="images/dsl/style.png" width="48" alt="Style icon"> | `.style`, `.theme` | Colors, fonts, skins, shared presentation rules |
| Stage Presets | <img src="images/dsl/stagepreset.png" width="48" alt="Stage Preset icon"> | `.stagepreset` | Lighting, tint, contrast, and staging presets |

## Everyday Use

Use JLT when you want to:

- Review a branch that changes scripts, menus, layouts, or route maps.
- Edit multiple VNS scenes with VS Code search and source control.
- Quickly inspect JES scene structure without launching the full editor.
- Keep menu/style/layout files readable during UI iteration.
- Work on JVN projects from a lightweight external editor.

Use the full JVN editor when you need runtime-aware tools such as live diagnostics, previews, Puppeteer editing, asset pickers, visual layout tools, or build/export validation.

## File Icons

JLT includes an optional **JVN DSL Icons** file icon theme.

To enable it:

1. Open the VS Code Command Palette.
2. Run **Preferences: File Icon Theme**.
3. Select **JVN DSL Icons**.

VS Code does not switch icon themes automatically after installing an extension, so this step is required if you want the custom file icons in the Explorer.

## Snippets

JLT includes snippets for common project structures:

- VNS: scenarios, characters, backgrounds, character show commands, choices, conditionals, JES scene pushes, and timeline calls.
- JES: scene blocks, sprite entities, timelines, move/fade actions, parallel blocks, and audio cues.
- Story Map: arcs, links, and clusters.
- Config: menu items, layout button bounds, style colors, and stage preset skeletons.

Type a snippet prefix in a supported file and use VS Code completion to insert it.

## Design Goal

JLT is intentionally lightweight. It improves project readability and editing speed without running a background analyzer or mutating your project.

Runtime validation remains the job of the JVN editor, where the engine has access to project context, assets, previews, and internal analyzers.

## Current Scope

This version provides:

- Syntax highlighting
- Snippets
- Language-specific brackets/comments/folding
- File associations
- Optional file icon theme

It does not yet provide:

- Parser diagnostics
- Go-to-definition
- Rename symbol
- Asset existence checks
- Label graph validation
- Runtime preview

Those are good candidates for a future language server once JVN exposes a stable analyzer API for external tools.

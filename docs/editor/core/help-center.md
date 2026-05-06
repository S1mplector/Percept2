# Help Center

The editor ships with an in-app Help Center so teams can browse project/workspace documentation without leaving JVN.

Component:
- `modules/editor/src/main/java/com/jvn/editor/ui/HelpCenterView.java`

Current sidebar tool version: `v1.2.1`

## How to Open

- Menu: `Help -> Help Center`
- Shortcut: `F1`
- Right panel tab: `Help`

## What Gets Indexed

### Workspace docs

- top-level Markdown such as `README.md`
- module `README.md` files like `core/README.md`, `editor/README.md`, and similar
- all markdown under `docs/**/*.md`

### Project-local docs (when a project is open)

- project `README.md`
- project `docs/**/*.md`
- other project-local Markdown outside generated/build folders

Docs are tagged by source (`Workspace` vs `Project`) in the guide tree.

## Core Features

- live filter box (`Filter docs...`)
- progressive guide tree with guide sections, topic folders, document nodes, and heading anchors
- heading-aware search across title, path, source, summary, and level 2-4 headings
- inline rendered Markdown preview pane
- `Open in Editor` action for direct tab editing
- `Reveal File` action in OS file manager
- `Copy Path` action
- version chip in the guide tree header so the installed sidebar utility version is visible

## Typical Usage Pattern

1. Press `F1`.
2. Search by topic, file name, or path fragment.
3. Expand the topic folder or matching heading anchor.
4. Read inline first.
5. Open target doc into an editor tab when you need to update it.
6. Refresh the index after adding/removing docs.

## Guide Tree v1.2.1

The `v1.2.1` tree groups docs by domain first, then by topic folder:

- Start Here
- Visual Novel Authoring
- Gameplay And JES
- Animation And Timelines
- Menus And UI Layout
- Editor And Tools
- Runtime And Project Setup
- Architecture And Internals
- Guides And Recipes
- Current Project Docs
- Reference And Generated Docs

See [Help Center Guide Tree](help-center-guide-tree.md) for the complete taxonomy and maintenance rules.

## Why It Matters in Team Environments

- reduces browser/context switching
- helps onboarding by centralizing docs inside the same tool used for content work
- keeps technical reference and script authoring in a single workspace loop

## Maintenance Guidance

- keep docs under `docs/` to maximize Help Center discoverability
- keep top-level `README.md` current, since it is usually the first indexed entry point
- use clear file naming for searchable paths (`Runtime`, `Menu Profiles`, `Save System`, etc.)
- use useful level 2 and level 3 headings because they become searchable tree anchors

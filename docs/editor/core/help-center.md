# Help Center

The editor ships with an in-app Help Center so teams can browse project/workspace documentation without leaving JVN.

Component:
- `modules/editor/src/main/java/com/jvn/editor/ui/HelpCenterView.java`

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
- progressive guide tree with summaries, source chips, and full Markdown coverage
- inline rendered Markdown preview pane
- `Open in Editor` action for direct tab editing
- `Reveal File` action in OS file manager
- `Copy Path` action
- quick-access buttons for common docs (`README`, `Overview`, `Editor`, `VNS`, `JES`, `Runtime`, `Menus`)
- quick command copy buttons (`./jvnw build`, `./jvnw editor`, `./jvnw runtime`)

## Typical Usage Pattern

1. Press `F1`.
2. Search by topic, file name, or path fragment.
3. Read inline first.
4. Open target doc into an editor tab when you need to update it.
5. Refresh the index after adding/removing docs.

## Why It Matters in Team Environments

- reduces browser/context switching
- helps onboarding by centralizing docs inside the same tool used for content work
- keeps technical reference and script authoring in a single workspace loop

## Maintenance Guidance

- keep docs under `docs/` to maximize Help Center discoverability
- keep top-level `README.md` current, since it is usually the first quick-access target
- use clear file naming for searchable paths (`Runtime`, `Menu Profiles`, `Save System`, etc.)

# New Project Wizard

The editor's new project wizard scaffolds a VN-ready project with layered config, scripts, assets, and manifest metadata.

Wizard class:
- `editor/src/main/java/com/jvn/editor/ui/NewProjectWizard.java`

## Goals of Current Wizard

- produce clean, engine-aligned directory layout
- generate editable starter files (not opaque binary state)
- support immediate run from editor project tree
- include menu/dialogue/timeline config that visual editors can open directly

## Wizard Sections

1. **Project Basics**
   - project name
   - author
   - location
   - output path preview (sanitized folder name)

2. **Engine Profile**
   - resolution preset
   - menu theme preset
   - entry script/timeline/dialogue layout preview

3. **Feature Modules**
   - sample prologue script
   - main menu profile pack
   - save/load profile files
   - settings profile
   - history/backlog defaults

4. **Generated Layout Preview**
   - live tree preview before create

5. **Project Notes**
   - optional description for manifest + README

## Generated Directory Structure

```text
<project>/
|-- config/
|   |-- settings/vn.settings
|   |-- timeline/story.timeline
|   |-- ui/dialogue.layout
|   `-- menu/
|       |-- theme/menu.theme
|       |-- registry/menu.registry
|       |-- menus/
|       |   |-- main.menu
|       |   |-- load.menu      (optional)
|       |   |-- save.menu      (optional)
|       |   `-- settings.menu  (optional)
|       |-- layouts/default.layout
|       |-- styles/default.style
|       `-- assets/{buttons,icons}
|-- scripts/
|   |-- story/prologue.vns
|   |-- common/
|   `-- system/
|-- assets/
|   |-- backgrounds/
|   |-- characters/
|   |-- portraits/
|   |-- cg/
|   |-- ui/
|   |-- fonts/
|   `-- audio/{bgm,sfx,voices}
|-- save/
|-- README.md
`-- jvn.project
```

## Manifest (`jvn.project`)

Wizard writes key runtime/editor metadata such as:
- project identity (`name`, `author`, `type`)
- entry points (`entryVns`, `entryLabel`, `timeline`)
- config references (`settingsFile`, `dialogueLayout`, menu paths)
- resolution (`width`, `height`)
- selected module booleans

This manifest is used by editor project-run workflow.

## Generated Starter Content

### Prologue script

By default, wizard creates a richer sample prologue demonstrating:
- labels and branching
- variable set/inc
- condition-gated choices
- transitions and screen effects
- text effects and interpolation

Path:
- `scripts/story/prologue.vns`

### Timeline starter

Creates initial arc:

```text
arc "Prologue" script "scripts/story/prologue.vns" entry "start" at 40,40
```

### Dialogue layout starter

Creates baseline `config/ui/dialogue.layout` values fully compatible with visual editor.

### Menu profile starter

When enabled, creates:
- registry
- default layout/style
- main menu
- optional load/save/settings menu files

## Recommended First Steps After Creation

1. Open project in editor.
2. Edit `scripts/story/prologue.vns`.
3. Tune `config/ui/dialogue.layout` in visual dialogue editor.
4. Customize `config/menu/menus/*.menu` and `config/menu/layouts/*.layout`.
5. Add assets under `assets/`.
6. Run from project explorer root `Run` button.

## Team Usage Notes

- Wizard output is intentionally plain text and source-control friendly.
- Keep `jvn.project` committed; it defines entry/config linkage.
- Use module toggles consistently across team templates to avoid layout drift.

# Project Structure Conventions

Complete guide to JVN project directory layout — where every file type lives, naming conventions, asset organization, team patterns, and how the engine discovers content.

---

## Overview

A JVN project is a self-contained directory with a predictable structure. The engine, editor, and runtime all expect specific subdirectories and file extensions. Following these conventions ensures that auto-discovery, the editor's visual tools, and the runtime's asset resolution all work correctly.

---

## Canonical Directory Layout

```text
MyProject/
├── jvn.project                     # Project manifest (required)
├── README.md                       # Optional project readme
│
├── scripts/                        # VNS and JES scripts
│   ├── story/                      # Main narrative scripts
│   │   ├── prologue.vns
│   │   ├── chapter1.vns
│   │   └── chapter2.vns
│   ├── common/                     # Shared includes (macros, character defs)
│   │   ├── characters.vns
│   │   └── variables.vns
│   └── system/                     # System scripts (menus, credits)
│       └── credits.vns
│
├── config/                         # Configuration files
│   ├── ui/                         # Dialogue UI
│   │   └── dialogue.layout
│   ├── menu/                       # Menu system
│   │   ├── registry/
│   │   │   └── menu.registry
│   │   ├── menus/
│   │   │   ├── main.menu
│   │   │   ├── load.menu
│   │   │   ├── save.menu
│   │   │   └── settings.menu
│   │   ├── layouts/
│   │   │   ├── default.layout
│   │   │   └── submenu.layout
│   │   ├── styles/
│   │   │   ├── default.style
│   │   │   └── submenu.style
│   │   └── theme/
│   │       └── menu.theme
│   ├── settings/
│   │   └── vn.settings
│   └── story/
│       └── story.storymap
│
├── assets/                         # All media assets
│   ├── backgrounds/                # Scene backgrounds
│   ├── characters/                 # Character sprites (by character)
│   ├── portraits/                  # Character portraits/thumbnails
│   ├── cg/                         # Full-screen CG images
│   ├── ui/                         # UI elements
│   │   ├── textbox.png
│   │   ├── namebox.png
│   │   ├── choice/
│   │   └── menu/
│   ├── fonts/                      # Custom font files
│   └── audio/
│       ├── bgm/                    # Background music
│       ├── sfx/                    # Sound effects
│       └── voices/                 # Voice clips (by character)
│
├── save/                           # Save files (gitignored)
│
├── .gitignore                      # Git ignore rules
└── .gitattributes                  # Line ending rules
```

---

## Project Manifest (`jvn.project`)

The `jvn.project` file in the project root is a properties file that tells the editor and runtime how to launch the project:

```properties
name=My Visual Novel
author=Studio Name
type=vn

entryVns=scripts/story/prologue.vns
entryLabel=start
storyMap=config/story/story.storymap

settingsFile=config/settings/vn.settings
dialogueLayout=config/ui/dialogue.layout

width=1920
height=1080

# Feature module flags
module.prologue=true
module.menuProfile=true
module.saveLoad=true
module.settings=true
module.history=true

# VCS toggles
vcs.git.enabled=true
```

### Required Fields

- **`name`** — project display name
- **`type`** — project type (`vn` for visual novel)
- **`entryVns`** — startup VNS script path
  - Example: `entryVns=scripts/story/chapter_01.vns`
  - Runtime uses this when `--script` is not passed.

### Optional Fields

- **`entryLabel`** — starting label within the entry script
- **`author`**, **`width`**, **`height`** — metadata
- **`timeline`**, **`settingsFile`**, **`dialogueLayout`** — config references
- **`module.*`** — feature flags from the New Project Wizard

---

## Scripts Directory

### Organization by Purpose

```text
scripts/
├── story/          # Narrative content (one file per chapter/scene)
├── common/         # Shared definitions (@character, @background, @var)
└── system/         # Non-narrative scripts (credits, tutorials)
```

### Naming Conventions

- Use **snake_case** for filenames: `chapter_1.vns`, `side_quest_a.vns`
- VNS files use `.vns` extension
- JES files use `.jes` extension
- One scenario per file (each file has one `@scenario` directive)

### Common Includes

Shared character definitions, variables, and backgrounds can go in `scripts/common/`:

```vns
# scripts/common/characters.vns
@character narrator "Narrator"
@character hero "Aria"
@character villain "Shade"

@charimg hero neutral assets/characters/aria/neutral.png
@charimg hero happy assets/characters/aria/happy.png
```

Include them in story scripts:

```vns
@include common/characters
```

---

## Config Directory

### `config/ui/` — Dialogue Layout

Single file controlling the VN textbox, name box, choices, and character framing:

```text
config/ui/dialogue.layout
```

### `config/menu/` — Menu System

The menu configuration tree:

```text
config/menu/
├── registry/menu.registry    # Which menus/layouts/styles to load
├── menus/*.menu              # Screen definitions
├── layouts/*.layout          # List geometry
├── styles/*.style            # Visual styling
└── theme/menu.theme          # Legacy theme (optional)
```

**Naming rule:** The filename (minus extension) is the ID. `main.menu` has ID `main`. `default.layout` has ID `default`.

### `config/settings/` — VN Settings

```text
config/settings/vn.settings
```

Default VN playback settings for the project.

### `config/story/` — Story Map

```text
config/story/story.storymap
```

The narrative arc graph.

---

## Assets Directory

### Backgrounds

```text
assets/backgrounds/
├── classroom.png
├── forest_day.png
├── forest_night.png
└── title_screen.jpg
```

Referenced in VNS:

```vns
@background classroom assets/backgrounds/classroom.png
```

### Characters

Organize by character, with subdirectories for expressions:

```text
assets/characters/
├── aria/
│   ├── neutral.png
│   ├── happy.png
│   ├── angry.png
│   └── sad.png
└── shade/
    ├── neutral.png
    └── menacing.png
```

For layered sprites:

```text
assets/characters/
└── aria/
    ├── base/
    │   └── body.png
    ├── eyes/
    │   ├── open.png
    │   ├── closed.png
    │   └── surprised.png
    └── mouth/
        ├── smile.png
        ├── frown.png
        └── neutral.png
```

### Audio

```text
assets/audio/
├── bgm/
│   ├── title_theme.ogg
│   ├── calm_day.ogg
│   └── battle.mp3
├── sfx/
│   ├── click.ogg
│   ├── door_open.wav
│   └── explosion.ogg
└── voices/
    ├── aria/
    │   ├── prologue_001.ogg
    │   └── prologue_002.ogg
    └── narrator/
        └── intro.ogg
```

### UI Elements

```text
assets/ui/
├── textbox.png
├── namebox.png
├── choice/
│   ├── normal.png
│   ├── hover.png
│   ├── selected.png
│   └── disabled.png
├── menu/
│   ├── btn_normal.png
│   ├── btn_selected.png
│   └── bg_main.jpg
└── icons/
    ├── save.png
    └── load.png
```

---

## File Extension Reference

| Extension | Type | Opens In |
|-----------|------|----------|
| `.vns` | VNS script | VNS code editor + preview |
| `.jes` | JES script | JES code editor + viewport |
| `.storymap` | Story map | Story Map graph editor |
| `.timeline` | Legacy story map | Story Map graph editor |
| `.layout` | Menu/dialogue layout | Layout Studio source editor |
| `.style` | Menu style | Layout Studio source editor |
| `.menu` | Menu screen | Layout Studio source editor |
| `.registry` | Menu registry | Text editor (or inline editor) |
| `.theme` | Menu theme | Text editor |
| `.properties` | Config | Text editor |
| `.project` | Manifest | Text editor |

---

## Naming Conventions Summary

| Item | Convention | Example |
|------|-----------|---------|
| Project directory | PascalCase or kebab-case | `MyVisualNovel` |
| Script files | snake_case | `chapter_1.vns` |
| Asset files | snake_case | `forest_night.png` |
| Character directories | lowercase | `assets/characters/aria/` |
| Config IDs | snake_case | `main.menu`, `default.layout` |
| Menu item IDs | snake_case | `new_game`, `save_slot` |
| Labels in VNS | snake_case | `@label chapter_start` |

---

## What Goes Where — Quick Decision Guide

| Content | Location |
|---------|----------|
| Story dialogue and branching | `scripts/story/` |
| Shared character/variable defs | `scripts/common/` |
| Textbox appearance | `config/ui/dialogue.layout` |
| Menu screens and navigation | `config/menu/menus/` |
| Menu visual styling | `config/menu/styles/` |
| Menu list geometry | `config/menu/layouts/` |
| Background images | `assets/backgrounds/` |
| Character sprites | `assets/characters/<name>/` |
| BGM tracks | `assets/audio/bgm/` |
| Sound effects | `assets/audio/sfx/` |
| Voice clips | `assets/audio/voices/<name>/` |
| Button/UI art | `assets/ui/` |
| Save files | `save/` (gitignored) |
| User settings | `~/.jvn/settings.properties` (outside project) |

---

## Team Workflow Tips

1. **Keep `jvn.project` committed** — it defines how the project launches
2. **Gitignore `save/`** — save files are user-specific, not shared
3. **Gitignore `.jvn-gradle-user-home/`** — editor's isolated Gradle cache
4. **Keep binary assets reasonably sized** — compress images and audio before committing
5. **Keep scripts and configs in normal Git** — they're plain text, diff-friendly
6. **Use feature branches** — one branch per chapter, side quest, or UI pass
7. **Separate script changes from layout changes** — makes code review easier
8. **Don't nest projects** — each project should be its own directory

---

## Related Docs

- [New Project Wizard](new-project-wizard.md)
- [Getting Started](../../guides/getting-started.md)
- [Asset Management](../../runtime/systems/asset-management.md)
- [Version Control](../collaboration/version-control.md)
- [Menu Registry & File Discovery](../../scripting/ui/layout/structure/menu-registry.md)

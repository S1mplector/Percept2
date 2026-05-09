# New Project Wizard

The editor's new project wizard scaffolds a VN-ready project with layered config, scripts, assets, and manifest metadata.

Wizard class:
- `modules/editor/src/main/java/com/jvn/editor/ui/NewProjectWizard.java`

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
   - high-resolution presets up to 4K (`3840x2160`)
   - custom width/height override for non-standard targets (ultrawide, handheld, kiosk)
   - menu theme preset
   - entry script/story map/dialogue layout preview

3. **Feature Modules**
   - sample prologue script
   - main menu profile pack
   - save/load profile files
   - settings profile
   - history/backlog defaults

4. **Version Control**
   - initialize Git repository
   - optional initial commit

5. **Generated Layout Preview**
   - live tree preview before create

6. **Project Notes**
   - optional description for manifest + README

---

### Inline Validation

The wizard provides real-time validation feedback on critical fields:

| Field | Validation Rules | Visual Feedback |
|-------|-----------------|----------------|
| **Project name** | Non-empty, alphanumeric with underscores/hyphens | Green border = valid, Red border = error |
| **Location** | Non-empty, path exists | Green border = valid, Red border = error |
| **Output path** | Auto-generated from sanitized name | Gray display (read-only) |

A validation label at the bottom of the wizard shows the current status:
- "Ready to create" — all fields valid, Create button enabled
- "Project name cannot be empty" — specific error message
- "Location must be an existing directory" — specific error message
- "Project name must be alphanumeric" — specific error message

The Create button is disabled when validation fails.

### Tooltips

Comprehensive tooltips guide users through wizard options:

| Control | Tooltip Content |
|---------|-----------------|
| **txtProjectName** | "Enter a name for your project (alphanumeric, underscores, hyphens allowed)" |
| **txtAuthor** | "Your name or team name for project metadata" |
| **txtLocation** | "Parent directory where the project folder will be created" |
| **cmbResolution** | "Target game resolution (affects layout scaling)" |
| **cmbTheme** | "Menu theme preset for generated UI assets" |
| **cmbRuntimeUi** | "Runtime UI style (standard, minimal, or custom)" |
| **cmbAudioBackend** | "Audio system for playback (JavaFX Sound, LWJGL OpenAL, etc.)" |
| **cmbLocale** | "Primary language/locale for the game (editable for custom codes)" |
| **Playback spinners** | "Default player preferences for text speed, auto-advance, and volumes" |
| **Skip checkboxes** | "Enable skip modes for read text and unvisited text" |
| **txtInputProfilePath** | "Optional path to a custom input profile configuration" |

Tooltips appear on hover and provide context for each option.

### Editable Locale ComboBox

The locale ComboBox is editable to support custom locale codes beyond the preset list:

1. Click the locale dropdown to see common presets (en, ja, zh, ko, es, fr, de, etc.)
2. Type a custom locale code (e.g., `pt-BR`, `ru-RU`, `ar-SA`) if your language isn't listed
3. The wizard accepts any valid locale code format

### Unicode Box-Drawing

The structure preview tree uses proper Unicode box-drawing characters for a polished appearance:

```
├── config/
│   ├── settings/
│   │   └── vn.settings
│   ├── ui/
│   │   └── dialogue.layout
│   └── menu/
│       ├── registry/
│       │   └── menu.registry
│       ├── menus/
│       │   ├── main.menu
│       │   ├── load.menu
│       │   └── settings.menu
│       ├── layouts/
│       │   └── default.layout
│       └── styles/
│           └── default.style
```

Conditional entries (like save/load/settings menus) use dynamic connectors to show they're optional based on feature selections.

### Fullscreen Toggle

When the settings menu pack is enabled, the generated `settings.menu` includes a fullscreen toggle item:

```properties
item.fullscreen.label={fullscreen}
item.fullscreen.action=toggle_fullscreen
```

The `{fullscreen}` placeholder is resolved from the locale file, allowing for localized labels.

### Theme Font Weight

Menu theme creation now includes the `hintsFontWeight` property:

```properties
hintsFontWeight=NORMAL
```

This controls the font weight for hint text in menu screens (e.g., "Enter: Confirm" navigation hints).

### Resolution-Aware Dialogue Layout

When creating the dialogue layout, the wizard scales pixel-based values proportionally if the target resolution differs from the 1080p baseline:

| Resolution | Scaling Factor |
|------------|----------------|
| 720p (1280×720) | 0.67× |
| 1080p (1920×1080) | 1.0× (baseline) |
| 1440p (2560×1440) | 1.33× |
| 4K (3840×2160) | 2.0× |

Scaled values include:
- Textbox padding
- Name box dimensions and offsets
- Font sizes (name, dialogue, choice)
- Choice button dimensions and gaps

The generated `dialogue.layout` includes a comment header:
```properties
# Target resolution: 1920x1080
```

### Active Locale Entries

The generated locale stub (`config/locales/<locale>.properties`) now includes active entries based on enabled features:

```properties
# Menu labels (if menu pack enabled)
menu.new_game=New Game
menu.continue=Continue
menu.settings=Settings
menu.quit=Quit

# Settings entries (if settings pack enabled)
settings.fullscreen=Fullscreen
settings.text_speed=Text Speed
settings.auto_delay=Auto Delay

# Dialogue text keys (if sample content enabled)
intro.welcome=Welcome to your new visual novel project!
intro.instructions=Edit scripts/story/prologue.vns to begin your story.
```

Entries are uncommented only for the features you enable, keeping the locale file clean.

## Generated Directory Structure

```text
<project>/
|-- config/
|   |-- settings/vn.settings
|   |-- story/story.storymap
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
|-- .gitignore      (if Git init is enabled)
|-- .gitattributes  (if Git is enabled)
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
- VCS toggles (`vcs.git.*`)

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

### Bundled demo assets

Wizard also copies demo-ready starter assets so first run is visually complete:
- layered character sprites: `assets/demo/characters/lavender/*`
- background images: `assets/demo/backgrounds/field/*`
- sample BGM/SFX used by starter script and menu defaults

Default main-menu style points to the bundled background asset so new projects do not boot into an empty-color title screen.

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
5. Add/replace assets under `assets/` (you can keep or remove `assets/demo`).
6. Run from project explorer root `Run` button.

## Team Usage Notes

- Wizard output is intentionally plain text and source-control friendly.
- Keep `jvn.project` committed; it defines entry/config linkage.
- Use module toggles consistently across team templates to avoid layout drift.
- If Git is enabled, the wizard writes managed blocks into `.gitignore` and `.gitattributes`.
- Re-running Git setup from editor is safe; managed blocks are replaced in place.

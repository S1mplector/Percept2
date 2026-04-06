# Common JVN File Types

This page is for beginners who can already open the editor, but are not yet sure what each JVN file is responsible for.

## Core Project Files

| File | What it does | Usually edited by |
|------|--------------|-------------------|
| `jvn.project` | Project manifest. Defines project type, entry script, viewport, and other top-level settings. | project lead or engine-facing author |
| `README.md` | Optional project readme for your team. | anyone |

## Story And Scene Files

| File | What it does | Usually edited by |
|------|--------------|-------------------|
| `.vns` | Visual novel script files: dialogue, choices, commands, variables, and story flow. | writers, VN designers |
| `.jes` | Low-level scene/gameplay files: entities, components, physics, input, and gameplay timelines. | technical designers, gameplay authors |

## Animation And Timeline Files

| File | What it does | Usually edited by |
|------|--------------|-------------------|
| `scripts/timelines/*.jes` | Registered Puppeteer export files that can be called from VNS or used from JES. | cinematic/technical authors |
| `.clip` | Reusable Puppeteer clip snippets saved from track ranges. | cinematic authors |

## Menu And UI Files

| File | What it does | Usually edited by |
|------|--------------|-------------------|
| `config/menu/registry/menu.registry` | Declares available menu IDs, layouts, and styles. | UI/system author |
| `.menu` | Defines a menu screen, its items, and actions. | UI/system author |
| `.layout` | Defines menu or dialogue geometry and positioning. | UI author |
| `.style` | Defines colors, fonts, assets, and presentation styling. | UI author |
| `config/ui/dialogue.layout` | Main textbox and dialogue UI configuration. | UI author |

## Image And Staging Tool Files

| File | What it does | Usually edited by |
|------|--------------|-------------------|
| `.tintsetup` | Saved Scene Lighting Studio setups for editor reuse. | art or staging author |
| `.stagepreset` | Runtime-ready stage/light preset export for scene reuse. | art or staging author |

## How To Think About Them

- If it controls **story flow**, it is usually a `.vns`.
- If it controls **entities and gameplay systems**, it is usually a `.jes`.
- If it controls **menus or textbox presentation**, it is usually `.menu`, `.layout`, or `.style`.
- If it controls **animation reuse**, it is usually a registered Puppeteer timeline or `.clip`.
- If it controls **lighting/staging reuse**, it is usually `.tintsetup` or `.stagepreset`.

## Safe Beginner Starting Point

If you are not sure where to begin, start here:

1. `jvn.project`
2. your main `.vns` file under `scripts/`
3. `config/ui/dialogue.layout`
4. `config/menu/`

That gives you story, textbox, and menu control before you move into more specialized tools.

## Read This Next

- [Choose Your Path in JVN](choose-your-path.md)
- [Getting Started Guide](getting-started.md)
- [Documentation Index](../INDEX.md)

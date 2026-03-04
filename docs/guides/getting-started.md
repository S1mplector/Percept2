# Getting Started with JVN

This guide walks you through building JVN, creating your first project, and running it.

---

## Prerequisites

- **JDK 21** — toolchain auto-download is enabled, but a local JDK 21 is recommended
- **No global Gradle install required** — `./gradlew` wrapper is included
- **Git** (optional) — for version control integration in the editor
- **CMake + C/C++ toolchain** (optional) — for native-math acceleration builds

---

## Step 1: Build the Engine

Clone the repository and build:

```bash
git clone <repository-url> Java-Vector-Nexus
cd Java-Vector-Nexus
./gradlew build
```

This compiles all modules and runs tests. The build also auto-attempts a `native-math` CMake build when native outputs are missing.

If you don't have CMake/toolchain installed, bypass native builds:

```bash
./gradlew -PskipNativeMathBuild=true build
```

### Targeted builds during development

Instead of full `build` every time, use focused tasks:

```bash
# Compile core + runtime only
./gradlew :core:compileJava :runtime:compileJava

# Compile editor
./gradlew :editor:compileJava

# Run tests
./gradlew :core:test :scripting:test
```

---

## Step 2: Launch the Editor

```bash
./gradlew :editor:run
```

The editor opens with a Welcome dashboard showing:
- Recent projects
- Environment health checks
- Quick actions: **New Project**, **Open Project**

---

## Step 3: Create a New Project

1. Click **New Project** in the Welcome dashboard.
2. Fill in project basics (name, author, location).
3. Choose engine profile (resolution, menu theme).
4. Select feature modules (sample script, menu profiles, save/load).
5. Optionally enable Git version control.
6. Review the generated layout preview.
7. Click **Create**.

The wizard generates a complete project scaffold:

```text
MyProject/
├── config/
│   ├── settings/vn.settings
│   ├── timeline/story.timeline
│   ├── ui/dialogue.layout
│   └── menu/
│       ├── registry/menu.registry
│       ├── menus/{main,load,save,settings}.menu
│       ├── layouts/default.layout
│       └── styles/default.style
├── scripts/
│   └── story/prologue.vns
├── assets/
│   ├── backgrounds/
│   ├── characters/
│   └── audio/{bgm,sfx,voices}
├── jvn.project
└── README.md
```

---

## Step 4: Edit Your First Script

Open `scripts/story/prologue.vns` from the project explorer. You'll see a starter script:

```vns
@scenario prologue
@character narrator "Narrator"

@label start
narrator: Welcome to your new visual novel!
[end]
```

Try adding content:

```vns
@scenario prologue
@character narrator "Narrator"
@character hero "Aria"
@background park assets/backgrounds/park.png
@charimg hero neutral assets/characters/aria/neutral.png
@charimg hero happy assets/characters/aria/happy.png

@label start
[bg park]
[bgm assets/audio/bgm/calm.ogg]
[show hero center neutral]

narrator: A new day begins.
hero: Hello! I'm Aria.

[show hero center happy]
hero: Nice to meet you!

> Tell me more -> learn_more
> Let's get going -> adventure

@label learn_more
hero: I love exploring and meeting new people!
[jump adventure]

@label adventure
narrator: And so the adventure begins...
[end]
```

The editor provides:
- **Syntax highlighting** for VNS
- **Real-time diagnostics** (undefined labels, missing assets)
- **Quick fixes** via context menu on diagnostic markers

---

## Step 5: Run Your Project

Click the **Run** button at the top of the Project Explorer panel.

Or run from terminal:

```bash
./gradlew :runtime:run --args='--assets /path/to/MyProject --script scripts/story/prologue.vns --ui fx --audio auto'
```

### Runtime Controls

| Key | Action |
|-----|--------|
| **Enter / Click** | Advance dialogue / complete text reveal |
| **1-9** | Select choice option |
| **S** | Toggle skip mode |
| **A** | Toggle auto-advance |
| **H** | Hide/show UI |
| **L** | Open history/backlog |
| **Ctrl+S** | Quick save |
| **Ctrl+L** | Quick load |
| **Esc** | Open menu |

---

## Step 6: Configure Presentation

### Dialogue Layout

Open `config/ui/dialogue.layout` in the editor — it opens in the visual **Dialogue Layout Editor** where you can:
- Drag the textbox and name box positions
- Adjust text padding and choice layout
- Import custom textbox assets

### Menu Screens

Open files under `config/menu/menus/` — they open in the **Menu Screen Visual Editor** where you can:
- Edit menu items, actions, and targets
- Assign layout and style references
- Preview the menu layout

### Menu Styles

Open `config/menu/styles/default.style` to customize colors, fonts, and prefixes.

---

## Step 7: Add Assets

Place your assets in the project's `assets/` directory:

```text
assets/
├── backgrounds/       # scene backgrounds
│   ├── classroom.png
│   └── forest.png
├── characters/        # character sprites
│   └── aria/
│       ├── neutral.png
│       ├── happy.png
│       └── angry.png
├── audio/
│   ├── bgm/          # background music
│   ├── sfx/          # sound effects
│   └── voices/       # voice clips
├── ui/                # UI elements
└── fonts/             # custom fonts
```

Reference them in your VNS scripts:

```vns
@background classroom assets/backgrounds/classroom.png
@charimg hero neutral assets/characters/aria/neutral.png
[bgm assets/audio/bgm/school_theme.ogg]
[sfx assets/audio/sfx/door_open.ogg]
```

---

## Step 8: Use the Timeline Graph

Open `config/timeline/story.timeline` to see the narrative structure graph.

- Each **arc** represents a VNS script file and entry label
- **Links** show how arcs connect (branches, returns)
- Drag `.vns` files from the project explorer onto the graph to create arcs
- Use clusters to organize main story vs. side content

---

## Next Steps

- **[VNS Scripting Guide](../scripting/vns/overview/vns-scripting.md)** — complete language reference
- **[Cookbook & Recipes](cookbook.md)** — common patterns and examples
- **[Editor Guide](../editor/core/editor.md)** — full editor features
- **[Menu Profiles](../scripting/ui/menus/menu-profiles.md)** — customizing menus
- **[Save System](../runtime/systems/save-system.md)** — save/load architecture
- **[Puppeteer Animation Editor](../editor/puppeteer/puppeteer.md)** — visual keyframe animation
- **[Documentation Index](../INDEX.md)** — full docs map

# VNS Project Organization & Scaling

How to structure VNS projects for maintainability as they grow — directory conventions, include strategies, shared definitions, multi-route management, asset pipelines, and team workflows.

---

## Directory Structure

### Small Project (1–3 scripts)

For simple kinetic novels or short demos, a flat structure works:

```text
game/
├── scripts/
│   ├── prologue.vns
│   ├── chapter1.vns
│   └── ending.vns
├── backgrounds/
│   ├── park.png
│   └── cafe.png
├── characters/
│   ├── hero_neutral.png
│   └── hero_happy.png
└── audio/
    ├── bgm/
    │   └── calm.ogg
    └── sfx/
        └── click.ogg
```

### Medium Project (5–20 scripts)

Introduce `common/` for shared definitions and group scripts by chapter:

```text
game/
├── scripts/
│   ├── common/
│   │   ├── characters.vns      # all @character + @charimg
│   │   ├── backgrounds.vns     # all @background
│   │   ├── variables.vns       # shared @var declarations
│   │   └── positions.vns       # custom @position declarations
│   ├── prologue.vns
│   ├── chapter1.vns
│   ├── chapter2.vns
│   ├── chapter3.vns
│   └── endings/
│       ├── ending_good.vns
│       └── ending_bad.vns
├── backgrounds/
│   ├── day/
│   │   ├── park.png
│   │   └── cafe.png
│   └── night/
│       ├── park_night.png
│       └── cafe_night.png
├── characters/
│   ├── hero/
│   │   ├── neutral.png
│   │   ├── happy.png
│   │   └── sad.png
│   └── rival/
│       ├── neutral.png
│       └── angry.png
└── audio/
    ├── bgm/
    ├── sfx/
    └── voices/
```

### Large Project (20+ scripts, multiple routes)

Use route directories, per-character asset folders, and layered sprite hierarchies:

```text
game/
├── scripts/
│   ├── common/
│   │   ├── characters.vns
│   │   ├── backgrounds.vns
│   │   ├── variables.vns
│   │   ├── positions.vns
│   │   └── macros.vns          # @define constants
│   ├── prologue/
│   │   ├── prologue_1.vns
│   │   └── prologue_2.vns
│   ├── common_route/
│   │   ├── ch1.vns
│   │   ├── ch2.vns
│   │   └── ch3_branch.vns      # branching point
│   ├── route_aria/
│   │   ├── aria_ch1.vns
│   │   ├── aria_ch2.vns
│   │   └── aria_ending.vns
│   ├── route_kai/
│   │   ├── kai_ch1.vns
│   │   ├── kai_ch2.vns
│   │   └── kai_ending.vns
│   └── extras/
│       ├── gallery.vns
│       └── bonus_scene.vns
├── backgrounds/
│   ├── school/
│   ├── city/
│   ├── nature/
│   └── fantasy/
├── characters/
│   ├── hero/
│   │   ├── layers/             # layered sprite parts
│   │   │   ├── base.png
│   │   │   ├── eyes_neutral.png
│   │   │   ├── eyes_happy.png
│   │   │   ├── mouth_smile.png
│   │   │   └── mouth_frown.png
│   │   └── presets/            # pre-composited fallbacks
│   │       ├── neutral.png
│   │       └── happy.png
│   ├── aria/
│   │   ├── layers/
│   │   └── presets/
│   └── kai/
│       ├── layers/
│       └── presets/
├── audio/
│   ├── bgm/
│   │   ├── main_theme.ogg
│   │   ├── battle.ogg
│   │   └── emotional.ogg
│   ├── sfx/
│   ├── voices/
│   │   ├── hero/
│   │   ├── aria/
│   │   └── kai/
│   └── ambient/
└── locales/
    ├── en/                     # default locale scripts
    ├── ja/
    └── strings/
        ├── en.properties
        └── ja.properties
```

---

## Shared Definitions with `@include`

### Character Header Files

Centralize character declarations so every script references the same definitions:

```vns
# common/characters.vns — shared character header
@character narrator "Narrator"
@character hero "Yuki"
@character aria "Aria Voss"
@character kai "Kai Chen"
@character ??? "???"

# Hero images
@charimg hero neutral assets/characters/hero/neutral.png
@charimg hero happy assets/characters/hero/happy.png
@charimg hero sad assets/characters/hero/sad.png
@charimg hero angry assets/characters/hero/angry.png

# Aria images
@charimg aria neutral assets/characters/aria/neutral.png
@charimg aria smile assets/characters/aria/smile.png
@charimg aria blush assets/characters/aria/blush.png

# Kai images
@charimg kai neutral assets/characters/kai/neutral.png
@charimg kai smirk assets/characters/kai/smirk.png
@charimg kai serious assets/characters/kai/serious.png
```

Then every script starts with:

```vns
@scenario chapter1
@include common/characters.vns
@include common/backgrounds.vns
@include common/variables.vns
```

### Background Header Files

```vns
# common/backgrounds.vns
@background park_day assets/backgrounds/nature/park_day.png
@background park_night assets/backgrounds/nature/park_night.png
@background cafe assets/backgrounds/city/cafe.png
@background school_entrance assets/backgrounds/school/entrance.png
@background school_hallway assets/backgrounds/school/hallway.png
@background school_rooftop assets/backgrounds/school/rooftop.png
```

### Variable Header Files

```vns
# common/variables.vns — global game variables
@var trust_aria = 0
@var trust_kai = 0
@var chapter = 1
@var route = ""
@var has_key = false
@var gold = 100
@var endings_seen = 0
```

### Macro Header Files

```vns
# common/macros.vns — compile-time constants
@define VERSION 1.0
@define MAX_TRUST 100
@define STARTING_GOLD 100
@define BGM_PATH assets/audio/bgm/
@define SFX_PATH assets/audio/sfx/
```

Usage:

```vns
@include common/macros.vns
[bgm ${BGM_PATH}calm.ogg]
[sfx ${SFX_PATH}click.ogg]
```

---

## Layered Character Organization

For projects using `@charlayer` and `@charpreset`, keep a dedicated header per character:

```vns
# characters/hero_layers.vns
@charlayer hero base assets/characters/hero/layers/base.png
@charlayer hero eyes_neutral assets/characters/hero/layers/eyes_neutral.png
@charlayer hero eyes_happy assets/characters/hero/layers/eyes_happy.png
@charlayer hero eyes_sad assets/characters/hero/layers/eyes_sad.png
@charlayer hero mouth_smile assets/characters/hero/layers/mouth_smile.png
@charlayer hero mouth_frown assets/characters/hero/layers/mouth_frown.png
@charlayer hero mouth_neutral assets/characters/hero/layers/mouth_neutral.png
@charlayer hero accessory_glasses assets/characters/hero/layers/glasses.png

@charpreset hero neutral $base | $eyes_neutral | $mouth_neutral
@charpreset hero happy $base | $eyes_happy | $mouth_smile
@charpreset hero sad $base | $eyes_sad | $mouth_frown
@charpreset hero happy_glasses $base | $eyes_happy | $mouth_smile | $accessory_glasses
```

Then include it from the main character header:

```vns
# common/characters.vns
@character hero "Yuki"
@include characters/hero_layers.vns
@character aria "Aria"
@include characters/aria_layers.vns
```

---

## Multi-Route Management

### Branching Point Pattern

A central branching script determines which route to enter:

```vns
# common_route/ch3_branch.vns
@scenario ch3_branch
@include common/characters.vns
@include common/variables.vns

@label branch_point
narrator: A critical moment arrives.

[if trust_aria >= 50 && trust_kai < 50 goto aria_route]
[if trust_kai >= 50 && trust_aria < 50 goto kai_route]
[if trust_aria >= 50 && trust_kai >= 50 goto choice_route]
[jump default_route]

@label aria_route
[set route "aria"]
[goto route_aria/aria_ch1:start]

@label kai_route
[set route "kai"]
[goto route_kai/kai_ch1:start]

@label choice_route
narrator: Both paths are open.
> Follow Aria -> aria_route
> Follow Kai -> kai_route

@label default_route
[set route "common"]
narrator: The story continues on the common path.
[end]
```

### Route-Specific Variables

If a route needs variables that don't apply globally, declare them within that route's first script:

```vns
# route_aria/aria_ch1.vns
@scenario aria_ch1
@include common/characters.vns
@include common/variables.vns

# Route-specific variables
@var aria_confession_seen = false
@var aria_date_count = 0
@var aria_gift_given = ""

@label start
[bg cafe]
narrator: Aria's route begins.
```

### Ending Registry Pattern

Track which endings the player has seen for gallery/extras unlocking:

```vns
# At the end of each route
@label ending_aria_good
narrator: Aria smiles warmly.
narrator: THE END — Aria: Good Ending
[flag ending_aria_good]
[inc endings_seen 1]
[save auto]
[end]

# In the extras menu
@label extras_gallery
[if ending_aria_good]
  narrator: Aria Good Ending — unlocked.
[else]
  narrator: Aria Good Ending — ???
[endif]
```

---

## Cross-Script Navigation

### `[goto]` for permanent transitions

Use `[goto Script:label]` when moving to a different script permanently (no return):

```vns
# At the end of chapter 1
[goto chapter2:start]
```

### `[gosub]` for shared subroutines within a file

```vns
[gosub flashback_scene]
# ... continues after flashback
@label flashback_scene
narrator: A memory from long ago...
[return]
```

### `[jes push]` / `[jes replace]` for JES scenes

When transitioning to a JES scene (minigame, cutscene):

```vns
# Push = VN stays on stack, returns after JES
[jes push game/minigames/shooter.jes label after_game with difficulty=hard]

# Replace = VN is removed, JES takes over
[jes replace game/cutscenes/finale.jes]
```

---

## Asset Naming Conventions

### Backgrounds

```text
<location>_<variant>.png

park_day.png
park_night.png
park_rain.png
cafe_interior.png
cafe_exterior.png
school_hallway_empty.png
school_hallway_crowd.png
```

### Character Sprites

```text
<character>_<expression>.png      # single-image sprites
<character>/layers/<part>.png     # layered parts

hero_neutral.png
hero_happy.png
aria/layers/base.png
aria/layers/eyes_happy.png
```

### Audio

```text
bgm/<mood_or_scene>.ogg
sfx/<action>.ogg
voices/<character>/<line_id>.ogg

bgm/calm.ogg
bgm/battle_intense.ogg
sfx/door_open.ogg
sfx/sword_clash.ogg
voices/hero/line_001.ogg
voices/aria/confession.ogg
```

---

## Team Workflows

### Writer / Programmer Separation

- **Writers** edit `.vns` files — dialogue, choices, labels, flow
- **Programmers** handle Java interop, `RuntimeVnInterop`, JES scenes
- **Artists** provide assets in the agreed directory structure

Shared definitions (`common/characters.vns`, etc.) act as the contract between roles. Writers reference character IDs and background IDs without needing to know file paths.

### Version Control

- Commit `.vns` files as text — they diff cleanly in Git
- Put binary assets (images, audio) under Git LFS or a separate asset pipeline
- Use CI to run `VnScriptParser` on all `.vns` files and catch errors before merge

### Review Checklist

Before merging a script change:

1. Does it parse without errors? (`./gradlew :core:test`)
2. Do all labels referenced exist?
3. Are all choice branches reachable?
4. Do routes end with `[end]` or navigate to another script?
5. Are variable names consistent with `common/variables.vns`?
6. Are new backgrounds/characters declared in the shared headers?

---

## Scaling Tips

- **Split files at 300–500 lines** — larger scripts become hard to navigate and debug.
- **Use `@define` for paths** — if you move asset directories, update one macro instead of every command.
- **Namespace labels** — prefix with chapter or route to prevent collisions across includes.
- **Keep `common/` read-only during content authoring** — changes to shared headers affect every script.
- **Use the Label Flow Map** to visualize navigation and catch dead-end paths.
- **Test each script independently** — a script should parse without errors even when loaded alone (assuming includes resolve).
- **Document route entry conditions** — add a comment at the top of each route file explaining how the player reaches it.

---

## Example: Full Project Include Graph

```text
prologue.vns
  └── @include common/characters.vns
  └── @include common/backgrounds.vns
  └── @include common/variables.vns
  └── [goto chapter1:start]

chapter1.vns
  └── @include common/characters.vns
  └── @include common/backgrounds.vns
  └── @include common/variables.vns
  └── [goto ch3_branch:branch_point]

ch3_branch.vns
  └── @include common/characters.vns
  └── @include common/variables.vns
  ├── [goto route_aria/aria_ch1:start]
  └── [goto route_kai/kai_ch1:start]

route_aria/aria_ch1.vns
  └── @include common/characters.vns
  └── @include common/variables.vns
  └── @include characters/aria_layers.vns
  └── [goto route_aria/aria_ch2:start]
```

Each script is self-contained with its includes, so it can be parsed and validated independently.

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [Directives & Declarations](../language/vns-directives.md) — `@include`, `@define`, `@character`, etc.
- [Flow Control](../flow/vns-flow-control.md) — jumps, cross-script navigation
- [Best Practices](vns-best-practices.md) — naming conventions, code review checklist
- [Layered Character Presets](../presentation/vns-layered-charpresets.md) — `@charlayer` + `@charpreset`
- [Localization](../runtime/vns-localization.md) — multi-language project structure

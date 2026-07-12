# VNS By Example — Script Structure

Organize large visual novels with subroutines, includes, multi-file projects, and arc-based navigation.

**Difficulty:** Advanced
**Time:** 20 minutes
**Concepts:** `[gosub]`/`[return]`, `[call]`/`[return]`, `@include`, `[goto Arc:label]`, `[load]`, multi-file project layout

---

## The Script

```vns
@scenario chapter1
@include common/characters.vns

@label start
[bg school]
[show hero center neutral]
narrator: A new day begins.

hero: I should check in with everyone.

> Talk to Sakura
  [gosub sakura_chat]
  hero: That was nice.
  [jump continue]
> Visit the library
  [gosub library_visit]
  hero: Time well spent.
  [jump continue]

@label continue
narrator: The day moved on.
[goto Chapter2:start]

# --- Subroutines ---

@label sakura_chat
[show friend left happy]
friend: Hey Yuki!
hero: Hey Sakura!
friend: Want to grab lunch later?
hero: Sure!
[hide friend]
[return]

@label library_visit
[bg library]
narrator: The library was quiet as always.
hero: Let me find that book.
[bg school]
[return]
```

---

## Subroutines (`[gosub]` / `[return]`)

Subroutines let you write reusable script sections that can be "called" and "returned from" — like functions.

```vns
[gosub shared_cutscene]
narrator: Back from the cutscene.

@label shared_cutscene
narrator: This is a reusable cutscene.
[return]
```

### How It Works

1. `[gosub label]` pushes the current position onto the **call stack** and jumps to the label
2. The subroutine runs normally
3. `[return]` pops the call stack and resumes at the line after the `[gosub]`

### `[gosub]` vs `[jump]`

| Command | Saves Position | Returns | Use Case |
|---------|---------------|---------|----------|
| `[jump]` | No | No — one way | Permanent navigation |
| `[gosub]` | Yes | Yes — via `[return]` | Reusable subroutines |

### Nesting

Subroutines can call other subroutines:

```vns
[gosub routine_a]

@label routine_a
narrator: In routine A.
[gosub routine_b]
narrator: Back in routine A.
[return]

@label routine_b
narrator: In routine B.
[return]
```

The call stack handles nesting automatically.

### `[call label]` (Legacy Form)

`[call label]` is the legacy alias for `[gosub label]`. Both push onto the call stack and return via `[return]`. Prefer `[gosub]` for clarity — `[call provider payload]` is a separate command for interop.

---

## Includes (`@include`)

Pull shared declarations from other files:

```vns
@include common/characters.vns
@include common/backgrounds.vns
```

### What Gets Included

Includes process **directives** from the target file:

- `@character` declarations
- `@charimg` / `@charlayer` / `@chargroup` / `@charpreset` declarations
- `@background` declarations
- `@position` declarations
- `@var` directives

Includes do **not** bring in labels, dialogue, or commands from the included file. They are purely for shared metadata.

### Include File Example

```vns
# common/characters.vns
@character narrator ""
@character hero "Yuki"
@character friend "Sakura"
@character rival "Takeshi"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero happy assets/characters/yuki/happy.png
@charimg hero sad assets/characters/yuki/sad.png
@charimg friend neutral assets/characters/sakura/neutral.png
@charimg friend happy assets/characters/sakura/happy.png
@charimg rival neutral assets/characters/takeshi/neutral.png
@charimg rival smug assets/characters/takeshi/smug.png

@background school assets/backgrounds/school_hallway.png
@background park assets/backgrounds/park_bench.png
@background library assets/backgrounds/library.png
```

---

## Multi-File Project Structure

### Recommended Layout

```text
scripts/
├── story/
│   ├── prologue.vns
│   ├── chapter1.vns
│   ├── chapter2.vns
│   ├── route_a/
│   │   ├── route_a_part1.vns
│   │   └── route_a_ending.vns
│   ├── route_b/
│   │   ├── route_b_part1.vns
│   │   └── route_b_ending.vns
│   └── common/
│       ├── characters.vns
│       ├── backgrounds.vns
│       └── shared_scenes.vns
└── extras/
    └── bonus_content.vns
```

### Navigation Between Files

#### `[goto Arc:label]`

Jump to a label in a different script:

```vns
[goto Chapter2:start]
[goto RouteA:branch_point]
[goto Epilogue:conclusion]
```

The arc name must match the `@scenario` name in the target file.

#### `[load path]`

Load a script directly by file path:

```vns
[load scripts/story/chapter2.vns]
```

### `@scenario` Names as Arc Identifiers

Each file's `@scenario` name is its **arc identifier**:

```vns
# File: chapter1.vns
@scenario Chapter1

# File: chapter2.vns
@scenario Chapter2
```

Then in chapter1.vns:
```vns
[goto Chapter2:start]
```

---

## Patterns

### Shared Event Library

Keep reusable events in one file and call them via subroutines:

```vns
# common/shared_scenes.vns (not directly loadable — included content only)
# These labels are in the including script's namespace after @include

@label common_save_prompt
narrator: Would you like to save?
> Yes
  [save]
  [hud Saved!]
  [return]
> No
  [return]
```

```vns
# chapter1.vns
@scenario Chapter1
@include common/characters.vns

@label midpoint
narrator: A good stopping point.
[gosub common_save_prompt]
narrator: Continuing...
```

### Chapter Transitions

```vns
# chapter1.vns
@scenario Chapter1

@label ending
[bgm_fadeout 2000]
[ui hide]
[transition FADE 1500]
[wait 1000]
[hud End of Chapter 1]
[wait 2000]
[goto Chapter2:start]
```

### Route Branching Across Files

```vns
# prologue.vns
@scenario Prologue
@var route = ""

@label route_decision
[if affinity_sakura > affinity_takeshi]
  [set route "sakura"]
[elif affinity_takeshi > affinity_sakura]
  [set route "takeshi"]
[else]
  [set route "common"]
[endif]

[if route == "sakura" goto load_sakura]
[if route == "takeshi" goto load_takeshi]
[goto CommonRoute:start]

@label load_sakura
[goto SakuraRoute:start]

@label load_takeshi
[goto TakeshiRoute:start]
```

### Hub World

A central script that the player returns to:

```vns
@scenario Hub
@include common/characters.vns

@label start
[bg hub_town]
[show hero center neutral]
narrator: Where to next?

> North Gate → Forest
  [goto ForestChapter:start]
> East Road → Mountains
  [goto MountainChapter:start]
> Tavern
  [gosub tavern_scene]
  [jump start]
> Save Game
  [save]
  [hud Saved!]
  [jump start]

@label tavern_scene
[bg tavern]
[show barkeep center neutral]
barkeep: Welcome back!
hero: Any news?
barkeep: Same old, same old.
[bg hub_town]
[hide barkeep]
[return]
```

---

## Variables Across Files

Variables set with `[set]`, `[inc]`, `[flag]`, etc. **persist** when navigating between scripts via `[goto Arc:label]` or `[load]`. The variable map is part of the runtime state.

```vns
# chapter1.vns
[set chapter1_complete true]
[inc total_score 100]
[goto Chapter2:start]

# chapter2.vns
[if chapter1_complete]
  narrator: You completed chapter 1!
  narrator: Your total score is ${total_score}.
[endif]
```

---

## Full Example: Multi-Route Game

### `common/characters.vns`

```vns
@character narrator ""
@character hero "Yuki"
@character friend "Sakura"
@character rival "Takeshi"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero happy assets/characters/yuki/happy.png
@charimg friend neutral assets/characters/sakura/neutral.png
@charimg friend happy assets/characters/sakura/happy.png
@charimg rival neutral assets/characters/takeshi/neutral.png

@background school assets/backgrounds/school.png
@background rooftop assets/backgrounds/rooftop.png
```

### `prologue.vns`

```vns
@scenario Prologue
@include common/characters.vns

@var affinity_s = 0
@var affinity_t = 0

@label start
[bg school]
[bgm assets/audio/bgm/school.ogg]
[show hero center neutral]
narrator: The first day of the semester.

@label lunch
hero: Who should I eat lunch with?

> Sakura
  [inc affinity_s]
  [gosub lunch_sakura]
  [jump afternoon]
> Takeshi
  [inc affinity_t]
  [gosub lunch_takeshi]
  [jump afternoon]

@label afternoon
hero: After school, who should I walk with?

> Sakura
  [inc affinity_s]
  [jump route_split]
> Takeshi
  [inc affinity_t]
  [jump route_split]

@label route_split
[if affinity_s > affinity_t goto goto_sakura]
[if affinity_t > affinity_s goto goto_takeshi]
[goto Common:start]

@label goto_sakura
[goto SakuraRoute:start]

@label goto_takeshi
[goto TakeshiRoute:start]

# Subroutines
@label lunch_sakura
[show friend left happy]
friend: Yuki! Sit here!
hero: Thanks, Sakura.
[hide friend]
[return]

@label lunch_takeshi
[show rival left neutral]
rival: Hey. You can sit here if you want.
hero: Sure, thanks.
[hide rival]
[return]
```

### `route_sakura.vns`

```vns
@scenario SakuraRoute
@include common/characters.vns

@label start
[bg rooftop]
[bgm assets/audio/bgm/sakura_theme.ogg]
[show hero center neutral]
[show friend right happy]
friend: I'm glad we ended up here together.
hero: Me too.
narrator: Sakura's route begins.
[end]
```

---

## Key Takeaways

1. `[gosub label]` + `[return]` creates reusable subroutines with a call stack
2. `@include path` pulls shared declarations (characters, backgrounds, positions)
3. `[goto Arc:label]` navigates between script files by arc name
4. `[load path]` loads a script by file path
5. Variables persist across file navigation
6. Organize large projects with `common/` for shared declarations and per-route files
7. Use subroutines for repeating scenes (save prompts, shared cutscenes)
8. Each `@scenario name` is the arc identifier used in `[goto]`

---

## Next

- [Advanced Variables](09-advanced-variables.md) — arithmetic, persistent data, mul/div/toggle
- [Back to Index](../vns-by-example.md)

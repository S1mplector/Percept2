# VNS Best Practices & Common Pitfalls

Practical advice for writing clean, maintainable, and performant VNS scripts. Covers naming conventions, structural patterns, common mistakes, and optimization tips.

---

## Script Organization

### Use `@scenario` consistently

Every `.vns` file should start with `@scenario`. The ID should match the filename (without extension) for easy navigation:

```vns
# File: scripts/story/chapter1.vns
@scenario chapter1
```

### Group declarations at the top

Place all directives before any content nodes. The recommended ordering:

```vns
@scenario my_story

# 1. Includes (shared assets, character defs)
@include characters/aria.vns
@include characters/kai.vns

# 2. Local characters (if not included)
@character narrator "Narrator"

# 3. Backgrounds
@background park assets/backgrounds/park.png
@background cafe assets/backgrounds/cafe.png

# 4. Custom positions
@position balcony 0.3 0.6

# 5. Variables
@var score = 0
@var chapter = 1

# 6. Defines (parse-time macros)
@define VERSION 1.2

# 7. Content starts here
@label start
[bg park]
narrator: The story begins.
```

### One concern per file

Split large stories into script files by chapter or route:

```text
game/scripts/
├── common/
│   ├── characters.vns        # shared @character + @charimg
│   └── variables.vns         # shared @var declarations
├── prologue.vns
├── chapter1.vns
├── chapter2.vns
├── route_a/
│   ├── route_a_ch1.vns
│   └── route_a_ending.vns
└── route_b/
    ├── route_b_ch1.vns
    └── route_b_ending.vns
```

Use `@include` for shared definitions and `[goto Arc:label]` for cross-script navigation.

---

## Naming Conventions

### Labels

Use `snake_case` with descriptive names. Prefix with context to avoid collisions across includes:

```vns
# Good
@label ch1_park_scene
@label ch1_choice_trust
@label ch1_ending_good

# Bad — too short, collision-prone
@label start
@label end
@label choice1
```

**Exception:** `start` is acceptable as the entry point label in a standalone script.

### Variables

Use `snake_case`. Prefix related variables with a common namespace:

```vns
# Good — grouped by system
@var quest_1_started = false
@var quest_1_progress = 0
@var quest_1_complete = false

@var trust_aria = 0
@var trust_kai = 0

# Bad — inconsistent, ambiguous
@var q1 = false
@var TrustAria = 0
@var SCORE = 0
```

### Characters

Use short, lowercase IDs. Reserve display names for what the player sees:

```vns
# Good
@character aria "Aria Voss"
@character kai "Kai Chen"
@character narrator "Narrator"
@character ??? "???"

# Bad — long IDs make commands verbose
@character aria_voss "Aria Voss"
@character the_narrator "Narrator"
```

### Backgrounds

Use descriptive IDs that convey location + time of day:

```vns
@background park_day assets/backgrounds/park_day.png
@background park_night assets/backgrounds/park_night.png
@background cafe_interior assets/backgrounds/cafe.png
```

---

## Dialogue Guidelines

### Keep lines short

Visual novel textboxes have limited width. Aim for 1–3 sentences per dialogue line:

```vns
# Good — fits in a textbox
aria: I've been thinking about what you said.
aria: Maybe you're right. We should try again.

# Bad — wall of text overflows
aria: I've been thinking about what you said, and while I wasn't sure at first, I've come to the conclusion that maybe you were right all along, and we should really try again because it's important to us both and we can't just give up now.
```

### Use narrator for scene descriptions

Don't overload character dialogue with stage directions:

```vns
# Good
narrator: The rain began to fall.
aria: Let's find shelter.

# Acceptable alternative — no speaker for narration
narrator: She looked out the window, lost in thought.
```

### Use text effects sparingly

Effects like `{shake}` and `{rainbow}` lose impact when overused:

```vns
# Good — emphasis on key moments
aria: I can't believe it... {b}you actually did it!{/b}
narrator: The ground {shake}trembled{/shake} beneath their feet.

# Bad — every line has effects
aria: {wave}Hello!{/wave} {rainbow}How are you?{/rainbow}
aria: {bounce}I'm{/bounce} {shake}so{/shake} {b}excited!{/b}
```

---

## Flow Control Best Practices

### Always provide a fallback path

When using conditional jumps, ensure there's a default path:

```vns
# Good — fallback if no condition matches
[if score >= 100 goto perfect_ending]
[if score >= 50 goto good_ending]
[jump normal_ending]

# Bad — if score < 50, execution falls through to whatever is below
[if score >= 100 goto perfect_ending]
[if score >= 50 goto good_ending]
```

### Use `[gosub]` for reusable sequences

Don't duplicate dialogue — extract it into a subroutine:

```vns
# Good — shared cutscene
@label ch1_dream
narrator: Something triggered a memory.
[gosub flashback_forest]
narrator: The memory faded.
[jump ch1_continue]

@label ch3_dream
narrator: The memory returned, clearer this time.
[gosub flashback_forest]
narrator: Now it all made sense.
[jump ch3_continue]

@label flashback_forest
[transition FADE 800 forest_old]
narrator: Years ago, in this very place...
narrator: A promise was made.
[transition FADE 800]
[return]
```

### Merge branches after choices

Avoid duplicating content after diverging paths:

```vns
# Good — merge back to shared content
> Agree -> agree
> Disagree -> disagree

@label agree
[inc trust 2]
kai: Thanks for understanding.
[jump after_choice]

@label disagree
[dec trust 1]
kai: I see. That's your decision.
[jump after_choice]

@label after_choice
narrator: The conversation moved on.
```

### Use `[end]` explicitly

Every route should terminate with `[end]`. Don't let execution fall off the end of a file:

```vns
@label ending_good
narrator: And they lived happily ever after.
[end]

@label ending_bad
narrator: Perhaps next time will be different.
[end]
```

---

## Choice Design

### Keep choice text concise

Choices should be scannable at a glance:

```vns
# Good
> Trust her -> trust
> Walk away -> leave
> Ask for proof -> demand_proof

# Bad — too verbose
> Tell her that you trust her completely and will follow her plan -> trust
> Turn around and walk away from the conversation entirely -> leave
```

### Use conditional choices to gate content

Show choices only when they make narrative sense:

```vns
> Use the key [if has_key] -> use_key
> Force the lock [if strength >= 5] -> force_lock
> Look for another way -> search
```

### Don't hide all choices behind conditions

Always provide at least one unconditional choice so the player isn't stuck:

```vns
# Good — "Leave" is always available
> Cast fireball [if mana >= 20] -> cast_fire
> Use the artifact [if has_artifact] -> use_artifact
> Retreat -> retreat

# Bad — all choices might be hidden
> Cast fireball [if mana >= 20] -> cast_fire
> Use the artifact [if has_artifact] -> use_artifact
```

---

## Audio Best Practices

### Crossfade between BGM tracks

Avoid jarring BGM switches — use crossfade for smooth transitions:

```vns
# Good
[bgm_crossfade assets/audio/bgm/battle.ogg 1500]

# Acceptable for dramatic cuts
[bgm_stop]
[wait 500]
[bgm assets/audio/bgm/silence_broken.ogg]
```

### Lower BGM during voice clips

```vns
[volume bgm 0.3]
[voice assets/audio/voices/hero/confession.ogg]
hero: I have something important to tell you.
[voice_stop]
[volume bgm 0.7]
```

### Use `[wait]` between stacked SFX

```vns
[sfx assets/audio/sfx/step.ogg]
[wait 400]
[sfx assets/audio/sfx/step.ogg]
[wait 400]
[sfx assets/audio/sfx/door_open.ogg]
```

---

## Common Pitfalls

### Pitfall 1: Infinite loop from jumps

A label that jumps to itself without any player interaction creates an infinite loop:

```vns
# BAD — infinite loop, hits MAX_INSTANT_CHAIN
@label loop
[set x 1]
[jump loop]

# GOOD — include a dialogue or wait to break the chain
@label loop
narrator: Processing...
[set x 1]
[jump loop]
```

The engine limits instant chains to 1000 nodes. If you hit this, you'll see a warning in the console.

### Pitfall 2: Forgetting `[endif]`

Every `[if]` block needs a matching `[endif]`. The parser catches this, but it's a common typo:

```vns
# BAD — parse error
[if score >= 100]
  narrator: High score!

# GOOD
[if score >= 100]
  narrator: High score!
[endif]
```

### Pitfall 3: Duplicate labels across includes

If two included files declare the same label, it's a parse error. Use prefixes:

```vns
# characters/aria.vns
@label aria_intro         # prefixed with character name

# characters/kai.vns
@label kai_intro          # no collision
```

### Pitfall 4: Using `[call]` when you mean `[gosub]`

`[call <provider> <payload>]` is for **interop** (JES, Java, timelines). `[gosub <label>]` is for **subroutine calls**:

```vns
# WRONG — this calls the "shared_cutscene" interop provider, not a label
[call shared_cutscene]

# RIGHT — subroutine call to a label
[gosub shared_cutscene]
```

### Pitfall 5: Variable type confusion

Variables set via `[set]` use type inference. Be explicit:

```vns
# "10" is stored as integer 10
[set gold 10]

# "true" is stored as boolean true
[set has_key true]

# Quoted strings stay as strings
[set name "Alice"]

# Watch out: "10" as a string (rarely needed)
[set code "10"]
```

### Pitfall 6: Missing `@background` declaration

Using `[bg parkId]` without declaring `@background parkId <path>` won't show an image. Always declare backgrounds:

```vns
@background park assets/backgrounds/park.png

@label start
[bg park]    # works — "park" is declared
```

### Pitfall 7: Forgetting to enable global position mode

`[move]` without global mode does an entrance animation, not a slide:

```vns
# Unexpected: hero fades in at right instead of sliding
[show hero left neutral]
[move hero right]

# Expected: smooth slide from left to right
[char hero global on]
[show hero left neutral]
[move hero right]
```

### Pitfall 8: Stacking effects without `[wait]`

Screen effects that fire on the same frame blend together instead of sequencing:

```vns
# Effects overlap (may look fine, but not sequential)
[screen shake 8 400]
[screen flash 0.8 200]

# Sequential effects
[screen shake 8 400]
[wait 100]
[screen flash 0.8 200]
```

---

## Performance Tips

- **Minimize inline timelines** in tight loops — each creates a `TimelineRunner` object.
- **Use `@include`** to share character declarations instead of duplicating them across scripts.
- **Keep the rollback stack reasonable** — the default 100 entries is fine for most projects. Increasing it uses more memory.
- **Normalize audio file volumes** in your DAW — heavy runtime gain adjustments are less efficient than pre-normalized files.
- **Avoid rapid-fire `[screen shake]`** in long loops — each resets the effect timer and can cause visual fatigue.
- **Use `@define`** for compile-time constants (paths, version strings) instead of runtime variables when the value never changes.

---

## Code Review Checklist

Before shipping a VNS script, verify:

- [ ] Every `[if]` has a matching `[endif]`
- [ ] Every `[gosub]` target has a matching `[return]`
- [ ] Every choice block has at least one unconditional option
- [ ] Every route ends with `[end]` or `[jump]`
- [ ] All `@background` IDs are declared before use
- [ ] All `@character` IDs are declared before use
- [ ] No duplicate labels (especially across `@include` files)
- [ ] Variable names are consistent and descriptive
- [ ] Audio paths exist in the project
- [ ] Text fits within the dialogue textbox width

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [Commands Reference](../language/vns-commands.md) — full command catalog
- [Variables & Conditions](../language/vns-variables.md) — variable system
- [Flow Control](../flow/vns-flow-control.md) — labels, jumps, subroutines
- [Choices & Branching](../language/vns-choices.md) — choice patterns
- [Project Organization](vns-project-organization.md) — scaling up large projects
- [Debugging & Troubleshooting](vns-debugging.md) — diagnosing problems

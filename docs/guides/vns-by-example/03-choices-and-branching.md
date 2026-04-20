# VNS By Example — Choices and Branching

Give the player meaningful decisions that shape the story. Branch the narrative using choices, jumps, and conditional routing.

**Difficulty:** Beginner
**Time:** 15 minutes
**Concepts:** Choice blocks, `[jump]`, `[goto]`, `[if ... goto]`, branching patterns, route design

---

## The Script

```vns
@scenario choices_demo
@character narrator ""
@character hero "Yuki"
@character friend "Sakura"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero happy assets/characters/yuki/happy.png
@charimg friend neutral assets/characters/sakura/neutral.png
@charimg friend happy assets/characters/sakura/happy.png

@background school assets/backgrounds/school.png
@background park assets/backgrounds/park.png
@background library assets/backgrounds/library.png

@label start
[bg school]
[show friend center happy]
friend: Hey Yuki! What should we do after school?

> Go to the park
  [jump park_route]
> Study in the library
  [jump library_route]

@label park_route
[bg park]
[show hero center happy]
[show friend right happy]
hero: What a beautiful day!
friend: Let's come here more often!
[jump ending]

@label library_route
[bg library]
[show hero center neutral]
[show friend right neutral]
hero: Let's find some good books.
friend: I heard the new mystery novel is amazing.
[jump ending]

@label ending
narrator: And so the afternoon passed peacefully.
[end]
```

---

## Choice Blocks

Choices are lines starting with `>`:

```vns
> Choice text
  [command after choosing this]
> Another choice
  [command after choosing this]
```

### Rules

- Each `>` line becomes a button the player can select
- Indented lines after `>` execute when that choice is picked
- Typically each choice contains a `[jump label]` to route the story
- You can have 2–6+ choices (UI adapts)
- Choices pause execution until the player picks one

### Simple Pattern

```vns
hero: What should I do?

> Talk to Sakura
  [jump talk_sakura]
> Go home
  [jump go_home]
> Explore the school
  [jump explore]
```

### Inline Dialogue After Choice

You can include dialogue lines before the jump:

```vns
friend: Want to come with me?

> Sure, let's go!
  hero: Sounds great!
  [jump together]
> Sorry, I'm busy.
  hero: Maybe next time.
  [jump alone]
```

---

## Flow Control Commands

### `[jump label]`

Unconditionally jumps to a label within the same script:

```vns
[jump chapter2]
```

### `[goto label]` / `[goto Arc:label]`

Jumps to a label, optionally in another script arc:

```vns
[goto ending_a]                    # same script
[goto Chapter2:beginning]          # different script arc
```

When using `Arc:label` form, the runtime loads the target script and jumps to that label.

### `[if condition goto label]`

Conditional jump — only jumps if the condition is true:

```vns
[if has_key goto unlock_door]
[if score >= 100 goto good_ending]
[if friendship > 5 goto close_friend]
```

If the condition is false, execution continues to the next line.

---

## Branching Patterns

### Two-Way Branch

The simplest branch — two choices, two paths:

```vns
@label decision
hero: Should I go left or right?

> Go left
  [jump left_path]
> Go right
  [jump right_path]

@label left_path
narrator: The left path led to a garden.
[jump rejoin]

@label right_path
narrator: The right path led to a fountain.
[jump rejoin]

@label rejoin
narrator: Eventually, Yuki found the way forward.
```

### Three-Way Branch

```vns
friend: What's your favorite subject?

> Literature
  [set favorite "literature"]
  hero: I love stories.
  [jump continue]
> Science
  [set favorite "science"]
  hero: I love experiments.
  [jump continue]
> Art
  [set favorite "art"]
  hero: I love creating things.
  [jump continue]

@label continue
friend: That suits you!
```

### Nested Choices

Choices can lead to more choices:

```vns
@label first_choice
hero: How should I spend the weekend?

> Go out
  [jump go_out]
> Stay home
  [jump stay_home]

@label go_out
hero: Where should I go?

> The mall
  narrator: Yuki spent the day shopping.
  [jump monday]
> The park
  narrator: Yuki enjoyed nature.
  [jump monday]

@label stay_home
hero: What should I do at home?

> Read a book
  narrator: Yuki finished a novel.
  [jump monday]
> Play games
  narrator: Yuki had a gaming marathon.
  [jump monday]

@label monday
narrator: Monday came too quickly.
```

### Route Selection (Classic VN Pattern)

A common pattern where early choices determine which route the player follows:

```vns
@label prologue
narrator: The first week of school.

# Day 1 choice
friend: Want to walk home together?

> Sure! (Sakura +1)
  [inc affinity_sakura]
  [jump day2]
> I'll pass. (No change)
  [jump day2]

@label day2
# Day 2 choice
rival: Hey, want to train together?

> Let's do it! (Takeshi +1)
  [inc affinity_takeshi]
  [jump route_check]
> Not today. (No change)
  [jump route_check]

@label route_check
[if affinity_sakura > affinity_takeshi goto sakura_route]
[if affinity_takeshi > affinity_sakura goto takeshi_route]
[jump common_route]

@label sakura_route
narrator: Yuki naturally gravitated toward Sakura.
[end]

@label takeshi_route
narrator: Yuki found a rival — and maybe a friend — in Takeshi.
[end]

@label common_route
narrator: Yuki took the path less traveled.
[end]
```

---

## Conditional Choices

Choices can be shown or hidden based on conditions using `[if]` inside choice blocks:

```vns
hero: What should I do?

> Use the key
  [if has_key goto unlock_door]
  hero: I don't have a key...
> Force the door
  [jump force_door]
> Walk away
  [jump walk_away]
```

Or gate entire choices behind variables:

```vns
# Only show "Secret path" if the player found the map
[if found_map]
  > Secret path
    [jump secret]
[endif]
> Main road
  [jump main_road]
> Go back
  [jump go_back]
```

---

## Dead Ends and Safety

Always ensure every path leads somewhere. Common mistakes:

```vns
# BAD: falling through to another label
@label path_a
narrator: You went left.
# MISSING [jump] — falls through to path_b!

@label path_b
narrator: You went right.
```

```vns
# GOOD: every path explicitly jumps or ends
@label path_a
narrator: You went left.
[jump continue]

@label path_b
narrator: You went right.
[jump continue]
```

---

## Full Example: Mystery Investigation

```vns
@scenario mystery
@character narrator ""
@character detective "Detective"
@character witness "Witness"
@character suspect "Suspect"

@charimg detective neutral assets/characters/detective/neutral.png
@charimg detective thinking assets/characters/detective/thinking.png
@charimg witness nervous assets/characters/witness/nervous.png
@charimg suspect calm assets/characters/suspect/calm.png
@charimg suspect angry assets/characters/suspect/angry.png

@background office assets/backgrounds/detective_office.png
@background crime_scene assets/backgrounds/alley.png
@background interrogation assets/backgrounds/interrogation.png

@var clues = 0

@label start
[bg office]
[show detective center neutral]
detective: Another case on my desk.
detective: Time to investigate.

[bg crime_scene]
[show detective center thinking]
detective: The scene of the crime. Where to look first?

> Examine the ground
  detective: Footprints... interesting.
  [inc clues]
  [jump examine_wall]
> Check the walls
  [jump examine_wall]

@label examine_wall
detective: What about the walls?

> Look for marks
  detective: Scratch marks on the brick.
  [inc clues]
  [jump interview]
> Nothing useful here
  [jump interview]

@label interview
[bg interrogation]
detective: Time to talk to people.

> Interview the witness
  [jump witness_talk]
> Confront the suspect
  [jump suspect_talk]

@label witness_talk
[show witness center nervous]
witness: I... I saw someone running.
witness: They dropped something shiny.
[inc clues]
[hide witness]
[jump conclusion]

@label suspect_talk
[show suspect center calm]
suspect: I was home all evening.
detective: Is that so?

[if clues >= 2]
  [show detective center thinking]
  detective: The evidence says otherwise.
  [show suspect center angry]
  suspect: Fine! I was there, but I didn't do it!
  [inc clues]
[else]
  detective: I don't have enough to press further.
[endif]

[hide suspect]
[jump conclusion]

@label conclusion
[show detective center neutral]
[if clues >= 3 goto solved]
[if clues >= 1 goto partial]
detective: I need more evidence. Case remains open.
[end]

@label partial
detective: I have some leads, but not enough for an arrest.
detective: I'll keep digging.
[end]

@label solved
detective: The pieces fit. Time to close this case.
[end]
```

---

## Key Takeaways

1. `>` lines create player choices as clickable buttons
2. Indented lines after `>` execute when that choice is picked
3. `[jump label]` navigates unconditionally within the script
4. `[goto Arc:label]` can jump to a different script file
5. `[if condition goto label]` is a conditional one-line jump
6. Every branch must explicitly `[jump]` or `[end]` — don't fall through
7. Variables set during choices persist and affect later branches

---

## Next

- [Variables and Conditions](04-variables-and-conditions.md) — tracking state and conditional logic
- [Back to Index](../vns-by-example.md)

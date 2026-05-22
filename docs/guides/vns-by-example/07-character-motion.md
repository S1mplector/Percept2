# VNS By Example — Character Motion

Go beyond static `[show]`/`[hide]` with smooth character slides, global position mode, easing curves, custom positions, and advanced choreography.

**Difficulty:** Intermediate
**Time:** 20 minutes
**Concepts:** `[move]`, `[char global]`, easing curves, `@position`, inline `at x,y`, layer order, `[char]` subcommands

---

## The Script

```vns
@scenario choreography
@character narrator ""
@character hero "Yuki"
@character rival "Takeshi"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero determined assets/characters/yuki/determined.png
@charimg hero happy assets/characters/yuki/happy.png
@charimg rival neutral assets/characters/takeshi/neutral.png
@charimg rival smug assets/characters/takeshi/smug.png

@background arena assets/backgrounds/training_arena.png

@position ring_left 0.25 0.6
@position ring_right 0.75 0.6
@position ring_center 0.5 0.5

@label start
[bg arena]

# Enable smooth sliding for both characters
[char hero global on]
[char rival global on]

# Enter from edges
[show hero far_left neutral]
[show rival far_right smug]
[wait 300]

# Slide to starting positions
[move hero ring_left determined ease_out_cubic 600]
[move rival ring_center smug ease_out_cubic 600]
[wait 700]

rival: Think you can beat me?

# Hero charges forward
[move hero ring_center determined ease_in_quad 300]
[screen shake 8 300]
[sfx assets/audio/sfx/impact.ogg]

# Rival dodges
[move rival ring_right neutral ease_out_back 400]
rival: Too slow!

# Hero recovers
[move hero ring_left neutral ease_out_quad 400]
hero: One more time!

[move hero ring_center determined ease_in_expo 200]
[move rival ring_right smug ease_out_elastic 500]

[show hero center happy]
hero: Got you!
[end]
```

---

## Global Position Mode

By default, characters use **slot-based** positioning:

```vns
[show hero left neutral]
[move hero right]           # hero disappears from left, fades in from right
```

This is a **slot transition** — not a slide. The character exits one position and enters another.

### Enabling Global Mode

```vns
[char hero global on]
```

With global mode ON, characters **slide smoothly** between positions:

```vns
[char hero global on]
[show hero left neutral]
[move hero right]           # hero slides from left to right
[move hero center happy]    # hero slides to center
```

### When to Use

| Mode | Best For |
|------|----------|
| **Slot (default)** | Traditional VN conversation scenes, dialogue focus |
| **Global** | Action scenes, choreography, custom position work |

---

## The `[move]` Command

```vns
[move charId position]
[move charId position expression]
[move charId position expression easing]
[move charId position expression easing durationMs]
```

### Parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `charId` | yes | Character to move |
| `position` | yes | Target position |
| `expression` | no | Expression to switch to after move |
| `easing` | no | Animation curve |
| `durationMs` | no | Duration in ms (default ~320ms) |

### Examples

```vns
[move hero right]
[move hero center happy]
[move hero left neutral ease_out_cubic]
[move hero far_right determined ease_out_back 500]
[move hero at 0.3,0.5 smile ease_in_out_quad 600]
```

---

## Easing Curves

Easing controls the speed profile of the slide animation.

### Quick Reference

| Easing | Effect | Best For |
|--------|--------|----------|
| `linear` | Constant speed | Mechanical movement |
| `ease_out_cubic` | Fast start, smooth stop | Standard character moves |
| `ease_in_quad` | Slow start, fast end | Charges, approaches |
| `ease_out_back` | Overshoot then settle | Dodges, bouncy arrivals |
| `ease_out_elastic` | Spring oscillation | Impact reactions |
| `ease_in_out_quad` | Smooth start and stop | Calm, deliberate movement |
| `ease_out_bounce` | Bouncy landing | Playful characters |
| `ease_in_expo` | Very slow start, sudden burst | Surprise attacks |

### Full Easing List

| Family | In | Out | In-Out |
|--------|------|------|--------|
| Quad | `ease_in_quad` | `ease_out_quad` | `ease_in_out_quad` |
| Cubic | `ease_in_cubic` | `ease_out_cubic` | `ease_in_out_cubic` |
| Quart | `ease_in_quart` | `ease_out_quart` | `ease_in_out_quart` |
| Expo | `ease_in_expo` | `ease_out_expo` | `ease_in_out_expo` |
| Sine | `ease_in_sine` | `ease_out_sine` | `ease_in_out_sine` |
| Elastic | `ease_in_elastic` | `ease_out_elastic` | `ease_in_out_elastic` |
| Back | `ease_in_back` | `ease_out_back` | `ease_in_out_back` |
| Bounce | `ease_in_bounce` | `ease_out_bounce` | `ease_in_out_bounce` |
| — | `linear` | — | — |

---

## Custom Positions

### Named Positions (`@position`)

Declare reusable positions:

```vns
@position balcony 0.3 0.6
@position doorway 0.8 0.55
@position ring_center 0.5 0.5
```

| Part | Description |
|------|-------------|
| Name | Unique identifier |
| X | Horizontal fraction (0.0 = left, 1.0 = right) |
| Y | Vertical fraction (0.0 = top, 1.0 = bottom) |

Use in `[show]` and `[move]`:

```vns
[show hero balcony neutral]
[move hero doorway happy ease_out_cubic 500]
```

### Inline Positions (`at x,y`)

Place characters at arbitrary coordinates without declaring:

```vns
[show hero at 0.3,0.5]
[show hero at 0.3,0.5 happy]
[show hero at 0.3,0.5,10 happy]     # with layer order
[move hero at 0.8,0.4 smile ease_out_back 500]
```

### When to Use Each

| Approach | Best For |
|----------|----------|
| Predefined (`center`, `left`, etc.) | Standard dialogue scenes |
| Named `@position` | Recurring custom positions (battlefields, stages) |
| Inline `at x,y` | One-off precise placement |

---

## Layer Order

Control which character renders in front:

```vns
[show villain center neutral 0]      # behind
[show hero center determined 10]     # in front
```

Default layer order by position:

| Position | Layer |
|----------|-------|
| `far_left` | -20 |
| `left` | -10 |
| `center` | 0 |
| `right` | 10 |
| `far_right` | 20 |

Higher values = rendered in front.

---

## `[char]` Subcommands

The `[char]` command provides advanced character control:

### `[char charId global on/off]`

Enable/disable global position mode:

```vns
[char hero global on]
[char hero global off]
```

### `[char charId at position]`

Set the anchor position without animation:

```vns
[char hero at center]
[char hero at at 0.5,0.3]     # inline position
```

### `[char charId move position ...]`

Animated slide (same as `[move]` but provider-command form):

```vns
[char hero move right smile]
[char hero move right smile ease_out_quad 500]
[char hero move at 0.3,0.5 smile]
```

### `[char charId show position expression]`

Show at position with expression:

```vns
[char hero show center happy]
[char hero show at 0.8,0.4 neutral]
```

### `[char charId expression name]`

Change expression without moving:

```vns
[char hero expression angry]
[char hero expr surprised]       # shorthand
[char hero expression worried dur=180]
[char hero expression neutral 0] # instant switch, no crossfade
```

Expression changes preserve the character's current slot, layer order, timeline
offset, mirror state, and detached position. By default the renderer crossfades
the old and new expression over `120ms`; use `dur=0` for an instant sprite swap.

### `[char charId hide]`

Animated exit:

```vns
[char hero hide]
```

---

## Patterns

### Conversation with Subtle Motion

```vns
[char hero global on]
[char friend global on]
[show hero left neutral]
[show friend right neutral]

hero: Did you hear what happened?
[move hero at 0.3,0.5 neutral ease_out_quad 300]

friend: No, what?
[move friend at 0.65,0.5 surprised ease_out_quad 300]

hero: The school festival is cancelled!
[char friend expression shocked]
```

### Dramatic Entrance

```vns
[char villain global on]
[screen shake 6 400]
[show villain at -0.1,0.5 angry]
[move villain center angry ease_out_expo 400]
villain: You thought you could hide?
```

### Fight Choreography

```vns
@position start_left 0.2 0.6
@position start_right 0.8 0.6
@position clash_center 0.5 0.55

[char hero global on]
[char rival global on]

# Setup
[show hero start_left determined]
[show rival start_right smug]
[wait 500]

# Charge
[move hero clash_center determined ease_in_quad 250]
[move rival clash_center smug ease_in_quad 250]
[wait 260]

# Impact
[screen shake 12 400]
[screen flash 0.6 100]
[sfx assets/audio/sfx/clash.ogg]

# Recoil
[move hero start_left neutral ease_out_back 400]
[move rival start_right neutral ease_out_back 400]
```

### Stage Blocking (3+ Characters)

```vns
@position stage_l 0.15 0.6
@position stage_cl 0.35 0.6
@position stage_cr 0.65 0.6
@position stage_r 0.85 0.6

[char hero global on]
[char friend global on]
[char rival global on]
[char teacher global on]

[show hero stage_l neutral]
[show friend stage_cl happy]
[show rival stage_cr neutral]
[show teacher stage_r neutral]

teacher: Everyone, take your positions.
[move hero stage_cl determined ease_out_quad 400]
[move friend stage_l happy ease_out_quad 400]
# hero and friend swap positions
```

---

## Full Example: Dance Scene

```vns
@scenario dance
@character narrator ""
@character hero "Yuki"
@character partner "Sakura"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero happy assets/characters/yuki/happy.png
@charimg hero nervous assets/characters/yuki/nervous.png
@charimg partner neutral assets/characters/sakura/neutral.png
@charimg partner happy assets/characters/sakura/happy.png
@charimg partner wink assets/characters/sakura/wink.png

@background ballroom assets/backgrounds/ballroom.png

@position dance_left 0.35 0.55
@position dance_right 0.65 0.55
@position dance_close 0.5 0.5
@position dance_apart_l 0.2 0.6
@position dance_apart_r 0.8 0.6

@label start
[bg ballroom]
[bgm assets/audio/bgm/waltz.ogg vol=0.6]

[char hero global on]
[char partner global on]

# Enter from opposite sides
[show hero dance_apart_l nervous]
[show partner dance_apart_r neutral]
[wait 500]

partner: Shall we dance?
hero: I... sure.

# Approach each other
[move hero dance_left neutral ease_in_out_quad 800]
[move partner dance_right happy ease_in_out_quad 800]
[wait 900]

narrator: The music swelled.

# First step — close together
[move hero dance_close neutral ease_in_out_sine 600]
[move partner dance_close happy ease_in_out_sine 600]
[wait 700]

# Spin apart
[move hero dance_apart_l happy ease_out_back 500]
[move partner dance_apart_r wink ease_out_back 500]
[wait 600]

partner: Not bad!
[show hero center happy]
hero: You think so?

# Come back together
[move hero dance_left happy ease_in_out_quad 600]
[move partner dance_right happy ease_in_out_quad 600]
[wait 700]

# Final close
[move hero dance_close happy ease_in_out_sine 800]
[move partner dance_close wink ease_in_out_sine 800]

narrator: In that moment, everything else faded away.
[bgm_fadeout 3000]
[end]
```

---

## Key Takeaways

1. `[char hero global on]` enables smooth sliding (required for choreography)
2. `[move charId position expression easing durationMs]` slides characters
3. 25 easing curves from `linear` to `ease_in_out_bounce`
4. `@position name x y` declares reusable custom positions
5. `at x,y` provides inline arbitrary positioning
6. Layer order (integer after expression) controls front/back rendering
7. `[char]` subcommands: `global`, `at`, `move`, `show`, `expression`, `hide`
8. Combine moves with `[wait]`, `[screen shake]`, and `[sfx]` for cinematic choreography

---

## Next

- [Script Structure](08-script-structure.md) — subroutines, includes, multi-file projects
- [Back to Index](../vns-by-example.md)

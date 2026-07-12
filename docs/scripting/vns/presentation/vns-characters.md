# VNS Characters & Sprites

Complete reference for the VNS character system: declarations, layered sprites, expression presets, positioning, animation, and global position mode.

---

## Character Declarations

### Basic character registration

```vns
@character <id> "Display Name"
```

The `id` is used in commands and dialogue. The display name is shown to the player.

```vns
@character hero "Aria"
@character mentor "Professor Vale"
@character villain "Shadow King"
```

### Expression sprites

Each character can have multiple named expressions:

```vns
@charimg hero neutral assets/characters/aria/neutral.png
@charimg hero happy assets/characters/aria/happy.png
@charimg hero angry assets/characters/aria/angry.png
@charimg hero surprised assets/characters/aria/surprised.png
@charimg hero sad assets/characters/aria/sad.png
@charimg hero thinking assets/characters/aria/thinking.png
@charimg hero cinematic assets/characters/aria/cinematic.mp4
```

Supported formats include `.png`, `.jpg`, `.gif` (animated), and even `.mp4`/`.mov` videos. For advanced characters built from separate parts (e.g. body + eyes + mouth), you can use the layer system to mix and match formats dynamically.

If no expression is specified in a `[show]` command, `neutral` is used as default.

---

## Layered Sprite System

For characters composed of separate body parts (body, eyes, mouth, accessories), use the layer system.

### Step 1: Define layers

```vns
@charlayer <characterId> <layerId> <path>
```

```vns
@charlayer aria base assets/characters/aria/layers/body.png
@charlayer aria eyes_neutral assets/characters/aria/layers/eyes_neutral.png
@charlayer aria eyes_happy assets/characters/aria/layers/eyes_happy.png
@charlayer aria eyes_angry assets/characters/aria/layers/eyes_angry.png
@charlayer aria eyes_surprised assets/characters/aria/layers/eyes_wide.png
@charlayer aria mouth_neutral assets/characters/aria/layers/mouth_neutral.png
@charlayer aria mouth_smile assets/characters/aria/layers/mouth_smile.png
@charlayer aria mouth_frown assets/characters/aria/layers/mouth_frown.png
@charlayer aria mouth_open assets/characters/aria/layers/mouth_open.png
@charlayer aria accessory_glasses assets/characters/aria/layers/glasses.png
@charlayer aria accessory_hat assets/characters/aria/layers/hat.png
```

### Step 2: Define movable groups

```vns
@chargroup <characterId> <groupId> [parent=<parentGroupId>] [pivot=<x>,<y>] <layerSpec>
```

Groups collect layers that should be authored or animated together. Use `$groupId` inside presets just like a layer reference, then move the generated group target in Puppeteer.

```vns
@chargroup aria face $eyes_neutral | $mouth_neutral
@chargroup aria head pivot=0.5,0.28 $face | $accessory_glasses
```

Puppeteer exposes targets such as `aria_head` and `aria_face`; nested groups inherit parent movement. Expression-specific aliases such as `aria_neutral_head` are also available. If you animate both a group and an individual layer, the group transform is applied first, then the individual layer transform.

Declare groups after the layers they use, and declare nested groups before presets that use them:

```vns
@chargroup aria face parent=head $eyes_neutral | $mouth_neutral
@chargroup aria head pivot=0.5,0.28 $face | $accessory_glasses
@charpreset aria neutral $base | $head
```

### Step 3: Build expression presets

```vns
@charpreset <characterId> <expressionId> <layerSpec>
```

Layer and group references use `$layerId` or `$groupId` syntax. Separate entries with `|`.

```vns
@charpreset aria neutral $base | $head
@charpreset aria happy $base | $eyes_happy | $mouth_smile
@charpreset aria angry $base | $eyes_angry | $mouth_frown
@charpreset aria surprised $base | $eyes_surprised | $mouth_open
@charpreset aria thinking $base | $eyes_neutral | $mouth_neutral | $accessory_glasses
@charpreset aria formal $base | $eyes_neutral | $mouth_smile | $accessory_glasses | $accessory_hat
```

### Cross-character layer references

You can reference layers from other characters:

```vns
@charlayer shared_accessories bow assets/characters/shared/bow.png

# Reference from another character using $charId.layerId or $charId:layerId
@charpreset aria festive $base | $eyes_happy | $mouth_smile | $shared_accessories.bow
@charpreset aria festive2 $base | $eyes_happy | $mouth_smile | $shared_accessories:bow
```

### Multi-layer `@charimg` (shortcut)

If you don't need the full layer/preset system, you can specify layered images directly:

```vns
@charimg aria battle assets/characters/aria/body.png | assets/characters/aria/battle_eyes.png | assets/characters/aria/battle_mouth.png
```

Layers are drawn bottom-to-top (left-to-right in the declaration).

---

## Showing and Hiding Characters

### Basic show

```vns
[show hero center]           # default expression (neutral)
[show hero center happy]     # specific expression
[show hero left angry]       # different position
[show hero center @happy]    # explicit preset reference
[show hero center @happy+$glasses]
[show hero center $base+$eyes_happy+$mouth_smile]
```

### Inline preset and composite syntax

For layered characters, `show`, `move`, and `char ... expression/show/move` accept two shorthand forms:

```vns
[show aria center @happy]
[move aria right @thinking+$accessory_glasses ease_out_back 500]
[char aria expression @formal+$shared_accessories.bow]
```

- `@presetName` explicitly selects an existing preset/expression.
- `$layerId` pulls in a declared `@charlayer`.
- `$groupId` expands a declared `@chargroup`.
- `+` combines presets and layers into an inline composite.
- Cross-character refs still work inside composites: `$shared.bow` and `$shared:bow`.

The parser resolves inline composites into ordinary expression entries at parse time, so runtime rendering behaves exactly like a normal `@charpreset` or `@charimg`.

### Positions

| Full Name | Shortcut | Typical Screen X (center) |
|-----------|----------|--------------------------|
| `FAR_LEFT` | `FL` | ~10% |
| `LEFT` | `L` | ~25% |
| `CENTER` | `C` | ~50% |
| `RIGHT` | `R` | ~75% |
| `FAR_RIGHT` | `FR` | ~90% |

### Layer ordering

The optional fourth argument controls z-depth (higher = drawn in front):

```vns
# villain behind hero
[show villain center neutral 0]
[show hero center determined 10]

# crowd member far back
[show crowd_npc far_left neutral -30]
```

Default layer orders by position:
- `FAR_LEFT` → -20
- `LEFT` → -10
- `CENTER` → 0
- `RIGHT` → 10
- `FAR_RIGHT` → 20

If a character already has a layer order and you show them again without specifying one, the existing layer order is preserved.

### Display slots for same-position sprites

By default, a visible character is keyed by its position. Showing another character into the same position replaces the previous occupant:

```vns
[show body center neutral]
[show head center neutral]     # replaces body
```

Use `slot=` when multiple sprites should share the same visual position:

```vns
[show body center neutral slot=body z=0]
[show head center neutral slot=head z=10]
```

`position` controls where the sprite appears, `slot` controls which visible instance is being addressed, and `z` controls draw order. Slot aliases include `as=`, `instance=`, `display=`, `display_slot=`, and `display-slot=`.

Slots can be moved, hidden, and expression-swapped independently:

```vns
[move slot=head at 0.5,0.72 ease_out_quad 240]
[char head expression blink slot=head]
[hide slot=head]
```

See [Character Display Slots](vns-display-slots.md) for the focused guide.

### Hiding characters

```vns
[hide hero]
[hide villain]
```

The hide command triggers an exit animation (slide + fade out).

### Changing expressions

Simply show the character again at the same position with the new expression:

```vns
[show hero center neutral]
hero: I'm not sure about this...

[show hero center angry]
hero: Wait, what did you just say?!

[show hero center surprised]
hero: Oh... I didn't expect that.
```

---

## Custom Positions

Beyond the five predefined positions, you can place characters at arbitrary screen coordinates using **named custom positions** or **inline coordinates**.

### Named custom positions (`@position`)

Declare reusable positions in your script header:

```vns
@position balcony 0.3 0.6
@position doorway 0.1
@position podium 0.5 0.75
```

- `x` is a horizontal screen fraction (`0.0` = left edge, `1.0` = right edge).
- `y` is optional (`0.0` = top, `1.0` = bottom). Defaults to `0.85` (standard baseline).

Then use them anywhere a position is accepted:

```vns
[show hero balcony neutral]
[move hero doorway]
[char hero at podium]
```

### Inline positions (`at x,y[,z]`)

For one-off placements, skip the declaration and specify coordinates inline:

```vns
[show hero at 0.3,0.5]                # x=0.3, y=0.5
[show hero at 0.3,0.5 happy]          # with expression
[show hero at 0.3,0.5,10]             # with layer order (z=10)
[show hero at 0.3,0.5,10 happy]       # with both
```

Inline positions also work with `[move]`:

```vns
[move hero at 0.8,0.4]
[move hero at 0.15,0.85 smile ease_out_bounce]
```

And with `[char]` subcommands:

```vns
[char hero move at 0.3,0.5]
[char hero move at 0.3,0.5 smile]
[char hero show at 0.8,0.4 neutral]
```

### Coordinate reference

| Value | Meaning |
|-------|---------|
| `x = 0.0` | Left edge of screen |
| `x = 0.5` | Center horizontally |
| `x = 1.0` | Right edge of screen |
| `y = 0.0` | Top of screen |
| `y = 0.85` | Default character baseline |
| `y = 1.0` | Bottom of screen |
| `z` (optional) | Layer order integer — higher = drawn in front |

### Rendering behavior

- Custom positions use `x` as a fraction of screen width to compute the character's horizontal center.
- Custom positions use `y` as a fraction of screen height for vertical placement.
- For movement animations, the engine computes a smooth slide delta between the old and new position.
- Entrance/exit animations default to sliding from the nearest screen edge.
- Default layer order for custom positions is `0` unless `z` is specified.

### Full example

```vns
@scenario balcony_scene
@character hero "Aria"
@character companion "Kai"
@charimg hero neutral assets/characters/aria/neutral.png
@charimg hero happy assets/characters/aria/happy.png
@charimg companion neutral assets/characters/kai/neutral.png
@charimg companion pointing assets/characters/kai/pointing.png

@position balcony_left 0.25 0.6
@position balcony_right 0.75 0.6
@position ground_center 0.5 0.9

@label start
[bg castle_night]

# Characters appear on the balcony
[show hero balcony_left neutral]
[show companion balcony_right neutral]

companion: Look at the stars tonight!
[show companion balcony_right pointing]

hero: They're beautiful.
[show hero balcony_left happy]

# Hero jumps down to the ground using inline position
[move hero at 0.5,0.9 ease_out_bounce]
[wait 400]

hero: Come on, let's go!

[end]
```

### When to use which

- **Predefined positions** (`center`, `left`, etc.) — standard dialogue scenes, simple staging.
- **Named `@position`** — recurring custom spots (balcony, throne, podium) reused across multiple commands.
- **Inline `at x,y`** — one-off precise placements, Puppeteer-style choreography, quick prototyping.

---

## Character Motion System

JVN provides two ways to move characters: the **`[move]` top-level command** and the **`[char]` provider commands**. Both can be used with or without global position mode, but the animation behavior changes significantly depending on whether global mode is enabled.

---

### Global Position Mode

By default, characters are in **slot mode**: each `[show]` or `[move]` creates an independent display slot at the target position. The character has no memory of where it was before — it simply appears at the new position with an entrance animation (fade-in from screen edge).

**Global position mode** gives a character persistent position memory. When enabled, the engine tracks where the character currently is, and `[move]` produces a **smooth slide tween** from the current position to the target instead of an entrance animation.

#### Enabling / disabling

```vns
[char hero global on]              # enable global position mode
[char hero global_position on]     # alias
[char hero global off]             # disable (revert to slot mode)
```

#### Behavior comparison

| Action | Slot mode (default) | Global mode |
|--------|-------------------|-------------|
| `[show hero left]` | Creates slot at LEFT, entrance fade-in | Creates slot at LEFT, entrance fade-in, remembers LEFT |
| `[move hero right]` | Removes old slot, creates new slot at RIGHT with entrance fade-in from edge | **Slides** character from LEFT → RIGHT with tween animation |
| `[show hero right]` while visible at LEFT | Removes LEFT slot, entrance at RIGHT | **Slides** from LEFT → RIGHT (same as `[move]`) |
| `[hide hero]` | Exit animation | Exit animation |
| Position after hide + re-show | No memory — always entrance animation | Remembers last position for anchor fallback |

#### When to use global mode

- **Use global mode** when characters need to physically move around the scene — stepping forward, switching sides, pacing, confrontation choreography.
- **Use slot mode** (default) when characters simply appear and disappear at fixed positions — standard dialogue scenes where characters don't need to slide between positions.

#### Typical pattern

```vns
# Set up characters with global mode for a choreography scene
[char hero global on]
[char villain global on]

[show hero left neutral]
[show villain right smug]

hero: So we meet at last.

# Hero steps forward — smooth slide from left to center
[move hero center determined]
[wait 300]

villain: You dare approach me?

# Villain also steps forward — face-off
[move villain center smug]

# Hero retreats
[move hero far_left worried ease_out_back 500]
```

Without global mode, each `[move]` above would produce an entrance animation instead of a slide, which looks jarring for choreography.

#### Scope and persistence

Global mode is **per-character** and persists for the duration of the scene (or until explicitly disabled). It survives label jumps within the same scenario. It is saved/restored with save files.

Source: `modules/core/src/main/java/com/jvn/core/vn/VnState.java` — `isCharacterGlobalPositionEnabled()`, `showCharacterAnimated()`

---

### Top-Level `[move]` Command

The simplest way to reposition a visible character:

```vns
[move hero right]
[move hero left happy]
[move hero center smile ease_out_bounce]
[move hero far_left neutral ease_out_quad 500]
[move hero center @happy+$accessory_glasses]
```

**Animation depends on global mode** (see above):
- **Global mode ON** → smooth slide tween (~320ms default).
- **Global mode OFF** → entrance fade-in from screen edge (~200ms default).

**Syntax:** `[move <charId> <position> [expression] [easing] [durationMs]]`

| Parameter | Required | Description |
|-----------|----------|-------------|
| `charId` | Yes | Character ID to move |
| `position` | Yes | Target position — predefined (`left`, `center`, etc.), named `@position`, or `at x,y` |
| `expression` | No | Expression to switch to after the move |
| `easing` | No | Easing curve for the animation (see table below) |
| `durationMs` | No | Duration in ms (default: ~320ms slide / ~200ms entrance) |

**With custom positions:**

```vns
@position balcony 0.3 0.6

[move hero balcony]
[move hero at 0.8,0.4 smile]
[move hero at 0.15,0.85 neutral ease_out_back 600]
```

### Easing Reference

All 25 easing types (case-insensitive in VNS):

| Family | In | Out | In-Out |
|--------|----|----|--------|
| **Quadratic** | `ease_in_quad` | `ease_out_quad` | `ease_in_out_quad` |
| **Cubic** | `ease_in_cubic` | `ease_out_cubic` | `ease_in_out_cubic` |
| **Quartic** | `ease_in_quart` | `ease_out_quart` | `ease_in_out_quart` |
| **Exponential** | `ease_in_expo` | `ease_out_expo` | `ease_in_out_expo` |
| **Sine** | `ease_in_sine` | `ease_out_sine` | `ease_in_out_sine` |
| **Elastic** | `ease_in_elastic` | `ease_out_elastic` | `ease_in_out_elastic` |
| **Back** | `ease_in_back` | `ease_out_back` | `ease_in_out_back` |
| **Bounce** | `ease_in_bounce` | `ease_out_bounce` | `ease_in_out_bounce` |

Plus `linear` (constant speed). Source: `modules/core/src/main/java/com/jvn/core/animation/Easing.java`

**In** = accelerate from rest. **Out** = decelerate to rest. **In-Out** = both.

Best picks for character movement:
- **`ease_out_quad`** — natural deceleration (most common)
- **`ease_out_back`** — slight overshoot, lively feel
- **`ease_out_bounce`** — bouncy landing, comedic scenes
- **`ease_in_out_cubic`** — smooth start and stop, cinematic pans

---

### `[char]` Provider Commands (Advanced)

For advanced choreography beyond basic show/hide, use the `[char]` (or `[character]`) provider commands.

**Subcommand reference:**

| Subcommand | Aliases | Description |
|------------|---------|-------------|
| `global` | `global_position` | Enable/disable persistent position mode |
| `at` | `position`, `pos` | Set the character's anchor position (updates memory, shows if global mode) |
| `move` | — | Animated slide to a new position (with optional expression, easing, duration) |
| `show` | — | Show character at a position with an expression |
| `expression` | `expr` | Change expression without moving |
| `hide` | — | Animated exit |

### Setting anchor position

```vns
[char hero at center]
[char hero pos left]               # alias
[char hero position right]         # alias
```

When global mode is enabled, setting the anchor position also updates the character's visual position on screen. Without global mode, it only stores the position for later reference.

### Showing via char command

```vns
[char hero show center happy]
[char hero show center @happy+$glasses]
[char hero show at 0.3,0.5 neutral]   # with inline position
```

Shows the character at a position with an expression. Useful in global mode to place the character without a separate `[show]` command.

### Animated movement

```vns
[char hero move right]                          # move to right, keep current expression
[char hero move right smile]                    # move to right, switch to smile
[char hero move far_left neutral]               # move to far left
[char hero move center happy ease_out_quad 500] # with easing and duration
[char hero move center @formal+$shared.bow ease_out_back 500]
```

Movement is animated with a slide tween. If an expression is specified, it fades in after the move completes. Optional easing and duration parameters work the same as the top-level `[move]` command (see [Easing Reference](#easing-reference) above).

### Expression-only change

```vns
[char hero expression angry]
[char hero expr surprised]          # shorthand alias
```

Changes expression without moving position.

### Hiding via char command

```vns
[char hero hide]
```

### Full choreography example

```vns
@scenario choreography_demo
@character hero "Aria"
@character villain "Shadow"
@charimg hero neutral assets/characters/aria/neutral.png
@charimg hero angry assets/characters/aria/angry.png
@charimg hero determined assets/characters/aria/determined.png
@charimg villain neutral assets/characters/villain/neutral.png
@charimg villain smug assets/characters/villain/smug.png
@charimg villain defeated assets/characters/villain/defeated.png

@label start
[bg throne_room]

# Enter from sides
[show hero left neutral]
[show villain right smug]

# Enable global positioning for both
[char hero global on]
[char villain global on]

hero: So we meet at last.

# Hero steps forward
[char hero move center determined]
[wait 300]

villain: You're braver than I expected.

# Villain steps forward too — face-off at center
[char villain move center smug]
[wait 200]

[screen shake 6 300]
[sfx assets/audio/sfx/clash.ogg]

# Both pushed back
[char hero move left angry]
[char villain move right neutral]

hero: I won't back down!

# Final charge
[char hero move center determined]
[wait 500]
[screen flash 0.8 200]

[char villain expression defeated]
villain: Impossible...

[char villain hide]
[char hero move center neutral]
hero: It's over.

[end]
```

### Recommended usage pattern

1. Enable global mode once per character at the start of a scene.
2. Set initial anchor via `at`.
3. Use `move` for spatial transitions.
4. Use `expression` for facial changes without position changes.
5. This avoids duplicate sprite slots and keeps long scenes visually stable.

---

## Character Framing Overrides

At runtime, you can adjust how characters are rendered by setting special UI variables:

```vns
# Make characters taller
[set ui.characterHeightFactor 1.28]

# Adjust vertical baseline
[set ui.characterBaselineY 1.42]
```

These override the values from `dialogue.layout` while the scene runs. Useful for scenes with different camera framing (close-ups, wide shots).

---

## Character State in Save/Load

When a game is saved, the following character state is persisted:

- Visible characters and their positions
- Current expressions
- Layer orders
- Global position mode flags
- Defined positions for global-mode characters

When a save is loaded, characters are restored to their saved positions and expressions, including global position metadata.

---

## Character Animation Details

Characters have smooth entrance/exit/movement animations:

| Animation | Duration | Effect |
|-----------|----------|--------|
| Show (entrance) | 220ms | Slide in + fade in |
| Hide (exit) | 220ms | Slide out + fade out |
| Move (reposition) | 320ms | Slide between positions |
| Expression change | 180ms | Crossfade to new expression |

These durations are engine defaults and provide a polished feel without explicit timing commands.

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [Layered Character Presets Guide](vns-layered-charpresets.md) — practical guide to `@charlayer` + `@chargroup` + `@charpreset` with asset organization, cross-character refs, editor tooling
- [Character Display Slots](vns-display-slots.md) — same-position sprite instances with `slot=`, slot-only move/hide, and save/rollback behavior
- [Movable Character Layer Groups](vns-movable-layer-groups.md) — focused guide for nested movable groups, target names, pivots, and migration patterns
- [Directives & Declarations](../language/vns-directives.md) — `@character`, `@charimg`, `@charlayer`, `@chargroup`, `@charpreset`
- [Commands Reference](../language/vns-commands.md) — `[show]`, `[hide]`, `[char]`
- [Transitions & Screen Effects](vns-transitions.md) — visual effects that pair with character scenes

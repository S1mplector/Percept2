# VNS By Example — Characters and Backgrounds

Show character sprites on screen, change their expressions, set background images, and control the visual composition of your scenes.

**Difficulty:** Beginner
**Time:** 15 minutes
**Concepts:** `@charimg`, `@background`, `[show]`, `[hide]`, `[bg]`, positions, expressions, layer composites

---

## The Script

```vns
@scenario meeting
@character narrator ""
@character hero "Yuki"
@character friend "Sakura"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero happy assets/characters/yuki/happy.png
@charimg hero sad assets/characters/yuki/sad.png
@charimg friend neutral assets/characters/sakura/neutral.png
@charimg friend happy assets/characters/sakura/happy.png

@background school assets/backgrounds/school_hallway.png
@background park assets/backgrounds/park_bench.png

@label start
[bg school]
narrator: The school hallway was unusually quiet.

[show hero center neutral]
hero: Where is everyone?

[show friend left happy]
friend: Yuki! Over here!

hero: Oh, Sakura! What's going on?

[show friend left neutral]
friend: They cancelled afternoon classes.

[show hero center happy]
hero: Really? Let's go to the park!

[bg park]
[hide friend]
[show hero center happy]
narrator: They spent the rest of the day enjoying the sunshine.
[end]
```

---

## Character Images (`@charimg`)

Declare character expression images with `@charimg`:

```vns
@charimg characterId expression path/to/image.png
```

| Part | Description |
|------|-------------|
| `characterId` | Must match a declared `@character` |
| `expression` | Name for this expression (used in `[show]`) |
| `path` | Image file path relative to project root |

### Common Expression Names

You can use any names, but these are conventional:

```vns
@charimg hero neutral assets/characters/hero/neutral.png
@charimg hero happy assets/characters/hero/happy.png
@charimg hero sad assets/characters/hero/sad.png
@charimg hero angry assets/characters/hero/angry.png
@charimg hero surprised assets/characters/hero/surprised.png
@charimg hero thinking assets/characters/hero/thinking.png
```

### Layer Composites (`@charlayer` and `@charpreset`)

For characters with layered sprites (e.g., separate eyes, mouth, accessories):

```vns
@charlayer hero base assets/characters/hero/base.png
@charlayer hero eyes_neutral assets/characters/hero/eyes_neutral.png
@charlayer hero eyes_happy assets/characters/hero/eyes_happy.png
@charlayer hero mouth_smile assets/characters/hero/mouth_smile.png
@charlayer hero mouth_frown assets/characters/hero/mouth_frown.png
@charlayer hero glasses assets/characters/hero/glasses.png

@charpreset hero happy $eyes_happy $mouth_smile
@charpreset hero sad $eyes_neutral $mouth_frown
```

Use composites in `[show]`:

```vns
[show hero center @happy]                    # use preset
[show hero center @happy+$glasses]           # preset + extra layer
[show hero center $base+$eyes_happy+$mouth_smile]  # explicit layers
```

---

## Backgrounds (`@background`)

Declare background images:

```vns
@background bgId path/to/image.png
```

Switch backgrounds at runtime:

```vns
[bg school]         # shorthand
[background park]   # full form
```

The background fills the entire screen. Only one background is active at a time.

---

## Showing Characters (`[show]`)

```vns
[show charId position]
[show charId position expression]
[show charId position expression layer]
```

### Positions

| Position | Shortcut | Screen Placement |
|----------|----------|-----------------|
| `far_left` | `FL` | ~10% from left |
| `left` | `L` | ~25% from left |
| `center` | `C` | ~50% (centered) |
| `right` | `R` | ~75% from left |
| `far_right` | `FR` | ~90% from left |

```vns
[show hero center neutral]
[show friend left happy]
[show rival right smug]
[show npc far_left neutral]
[show boss far_right angry]
```

### Expressions

The expression name matches the second token in `@charimg`:

```vns
@charimg hero happy assets/characters/hero/happy.png

[show hero center happy]    # shows the happy image
```

If omitted, the last known expression is used (or `neutral` as fallback).

### Changing Expression (Without Moving)

Just `[show]` again at the same position with a new expression:

```vns
[show hero center neutral]
hero: Hmm...
[show hero center surprised]
hero: Wait, what?!
```

### Inline Custom Positions

Place characters at arbitrary coordinates:

```vns
[show hero at 0.3,0.5]              # x=30%, y=50%
[show hero at 0.3,0.5 happy]        # with expression
[show hero at 0.3,0.5,10 happy]     # with layer order
```

---

## Hiding Characters (`[hide]`)

```vns
[hide hero]
[hide friend]
```

The character plays an exit animation and is removed from the scene.

---

## Layer Order

Characters have a default layer order based on position:

| Position | Default Layer |
|----------|--------------|
| `far_left` | -20 |
| `left` | -10 |
| `center` | 0 |
| `right` | 10 |
| `far_right` | 20 |

Higher values render in front. Override with an explicit layer number:

```vns
[show villain center neutral 0]
[show hero center determined 10]     # hero appears in front
```

---

## Patterns

### Scene Transition

```vns
@label park_scene
[bg park]
[show hero center happy]
[show friend right neutral]
narrator: They arrived at the park.
hero: It's beautiful today!
friend: Let's find a bench.
```

### Emotional Beat

```vns
[show hero center neutral]
hero: I have something to tell you.
[show hero center sad]
hero: I'm moving away next month.
[show friend left surprised]
friend: What?! You can't be serious!
[show friend left sad]
friend: I... I don't know what to say.
```

### Crowded Scene

```vns
[show hero center neutral]
[show friend left happy]
[show rival right smug]
[show npc far_left neutral]
# Four characters on screen at once
```

### Quick Expression Changes

```vns
[show hero center neutral]
hero: ...
[show hero center thinking]
hero: Actually, I just remembered something.
[show hero center happy]
hero: Today's your birthday, isn't it?
```

---

## Full Example: First Day of School

```vns
@scenario first_day
@character narrator ""
@character hero "Yuki"
@character friend "Sakura"
@character teacher "Ms. Tanaka"

@charimg hero neutral assets/characters/yuki/neutral.png
@charimg hero happy assets/characters/yuki/happy.png
@charimg hero nervous assets/characters/yuki/nervous.png
@charimg friend neutral assets/characters/sakura/neutral.png
@charimg friend happy assets/characters/sakura/happy.png
@charimg friend wink assets/characters/sakura/wink.png
@charimg teacher neutral assets/characters/tanaka/neutral.png
@charimg teacher smile assets/characters/tanaka/smile.png

@background school_gate assets/backgrounds/school_gate.png
@background classroom assets/backgrounds/classroom.png
@background hallway assets/backgrounds/hallway.png

@label start
[bg school_gate]
narrator: Cherry blossoms drifted across the path.

[show hero center nervous]
hero: So this is my new school...

[show friend right happy]
friend: Hey! Are you the new transfer student?

[show hero center neutral]
hero: Y-yes, I'm Yuki. Nice to meet you.

[show friend right wink]
friend: I'm Sakura! Let me show you around!

[hide hero]
[hide friend]

[bg hallway]
narrator: Sakura led Yuki through the bustling hallways.

[show friend left happy]
friend: And this is where the clubs meet after school.

[show hero right neutral]
hero: There are so many...

[bg classroom]
[hide friend]
[show teacher center neutral]
teacher: Ah, you must be our new student.

[show hero left nervous]
hero: Yes, ma'am. I'm Yuki.

[show teacher center smile]
teacher: Welcome! Please take the seat by the window.

[show hero left happy]
hero: Thank you!

narrator: And so began Yuki's new chapter.
[end]
```

---

## Key Takeaways

1. `@charimg charId expression path` declares character expression images
2. `@background bgId path` declares background images
3. `[show charId position expression]` puts a character on screen
4. `[hide charId]` removes a character with an exit animation
5. `[bg bgId]` switches the background
6. Five predefined positions: `far_left`, `left`, `center`, `right`, `far_right`
7. `[show]` again at the same position changes the expression
8. Layer composites use `@charlayer`, `@charpreset`, and `$layer` syntax

---

## Next

- [Choices and Branching](03-choices-and-branching.md) — player decisions and story routing
- [Back to Index](../vns-by-example.md)

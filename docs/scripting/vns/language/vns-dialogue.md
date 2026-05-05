# VNS Dialogue & Text

This document covers all dialogue forms, inline text markup, and text rendering behavior in VNS.

---

## Dialogue Forms

VNS supports two dialogue syntaxes. Both resolve display names through `@character` declarations.

### Colon Form

The most common form. Speaker ID followed by a colon and text.

```text
Speaker: text goes here
```

**Examples:**

```vns
narrator: The room was dark and cold.
hero: I need to find the key.
guard: Halt! Who goes there?
```

If `hero` has a `@character hero "Aria"` declaration, the renderer shows **"Aria"** as the speaker name.

### Quoted Form

Speaker ID followed by quoted text. Supports escape sequences.

```text
speaker "quoted text with escapes"
```

**Examples:**

```vns
narrator "The door creaked open slowly."
hero "I said, \"stop right there!\""
guard "This is a line with a\nnewline."
```

Escape sequences:
- `\"` — literal quote
- `\\` — literal backslash
- `\n` — newline (behavior depends on renderer)

### When to Use Which

- **Colon form** — everyday dialogue, simpler to read and write.
- **Quoted form** — when your text contains colons or you need escape sequences.

```vns
# Colon form works fine here
hero: Let's go!

# Quoted form needed when text has special needs
narrator "The sign read: \"No Entry\""
```

---

## Text Effects (Inline Markup)

VNS supports inline markup tags inside dialogue text for animation and styling.

Parser: `modules/core/src/main/java/com/jvn/core/vn/text/TextParser.java`
Renderer: `modules/fx/src/main/java/com/jvn/fx/vn/VnRenderer.java`

### Tag Syntax

```text
{tag}affected text{/tag}
{tag=value}affected text{/tag}
```

### Animation Tags

These animate individual characters in the dialogue text.

**`{shake}`** — jittery random offset per character, per frame.

```vns
hero: {shake}What was that sound?!{/shake}
```

**`{wave}`** — sinusoidal vertical motion.

```vns
narrator: {wave}A strange melody filled the air.{/wave}
```

**`{bounce}`** — characters bounce up and down in sequence.

```vns
hero: {bounce}We did it!{/bounce}
```

**`{rainbow}`** — cycles hue across characters.

```vns
narrator: {rainbow}The crystal shimmered with every color imaginable.{/rainbow}
```

### Style Tags

**`{b}` / `{bold}`** — bold text.

```vns
narrator: The word {b}DANGER{/b} was carved into the wall.
```

**`{i}` / `{italic}`** — italic text.

```vns
narrator: She whispered, {i}follow me{/i}.
```

**`{color=#RRGGBB}`** — colored text span.

```vns
hero: The gem glowed {color=#4a9eff}bright blue{/color}.
narrator: {color=#ff4444}WARNING:{/color} Do not proceed.
guide: Status: {color=#44ff44}ONLINE{/color}
```

### Timing Tags

**`{speed=<multiplier>}`** — changes text reveal speed for the enclosed span.

```vns
narrator: He spoke {speed=0.3}very... slowly...{/speed} then suddenly {speed=3}rushed through the rest!{/speed}
```

- `speed=0.5` — half speed (slower reveal)
- `speed=2.0` — double speed (faster reveal)

**`{delay=<ms>}`** — inserts a pause before the next character.

```vns
narrator: {delay=500}The door opened.
narrator: Three... {delay=300}two... {delay=300}one...
```

### Combining Tags

Tags can be nested for compound effects:

```vns
narrator: {color=#ff0000}{shake}DANGER! SYSTEM OVERLOAD!{/shake}{/color}
narrator: {color=#4a9eff}{wave}The ocean whispered its secrets.{/wave}{/color}
narrator: {b}{color=#ffd700}ACHIEVEMENT UNLOCKED{/color}{/b}
```

### Full Scene Example with Text Effects

```vns
@scenario effects_demo
@character narrator "Narrator"
@character hero "Aria"
@character villain "Shadow King"

@label start

narrator: {delay=500}The throne room fell silent.

[screen shake 4 300]
villain: {shake}You dare challenge ME?{/shake}

hero: {b}I'm not afraid of you.{/b}

narrator: {wave}A warm light surrounded Aria.{/wave}

hero: {color=#ffd700}{bounce}For everyone who believed in me!{/bounce}{/color}

[screen flash 0.8 200 255 255 200]
narrator: {rainbow}The darkness shattered into a thousand colors.{/rainbow}

narrator: {speed=0.4}And then...{/speed} {delay=800}peace.

[end]
```

---

## Variable Interpolation in Dialogue

Runtime interpolation uses `${variableName}` syntax inside dialogue, choice text, and HUD messages. This allows authors to surface VN variables without custom Java code.

```vns
[set player_name "Alice"]
[set score 42]
[set coins 150]

narrator: Welcome back, ${player_name}!
narrator: You have ${coins} gold coins.
narrator: Current score: ${score}

> Spend ${cost} coins on potion -> buy_potion
> Keep saving -> skip_shop

[hud Score: ${score} | Lives: ${lives}]
```

### Behavior

- Missing variables resolve to **empty string** (no error).
- Interpolation is **single-pass** (no nested `${}` evaluation).
- Use `${...}` form to avoid collisions with text-effect tags like `{shake}`.
- Dialogue text is interpolated **before** being added to history, so history contains the resolved values.

### Advanced: Plurals, Gender Selection, Number Formatting

For more complex text transformations (plural agreement, gender-aware pronouns, number formatting), use ICU-style syntax:

```vns
narrator: {score, plural, one{# point} other{# points}} earned!
narrator: {gender, select, male{He} female{She} other{They}} smiled.
narrator: Distance: {miles, number} leagues away.
```

See **[Text Formatting & ICU](vns-text-formatting.md)** for complete reference and examples.

### Difference from `@define`

| Feature | `@define` | `${var}` |
|---------|-----------|----------|
| When | Parse time | Runtime |
| Source | Macro table | Variable map |
| Dynamic | No (baked) | Yes (live) |
| Use for | Build constants, asset paths | Player name, scores, flags |

---

## Text Reveal (Typewriter Effect)

The VN renderer reveals dialogue text character-by-character.

### Speed Control

Default speed is set in `VnSettings.textSpeed` (ms per character). Override in-script:

```vns
[textspeed 20]
narrator: This text appears quickly.

[textspeed 60]
narrator: This text appears slowly.
```

### Player Interaction

- **Click/Enter during reveal** — completes the current line instantly.
- **Click/Enter after reveal** — advances to the next node.
- The `clickRevealBeforeAdvance` setting (in save data) controls whether a click during reveal finishes text or also advances.

### Skip and Auto Modes

```vns
[skip on]     # enables skip mode (fast-forward through read text)
[skip off]    # disables skip mode
[auto on]     # enables auto-advance mode
[auto off]    # disables auto-advance
[autodelay 2000]  # set ms between auto-advance lines
```

---

## History / Backlog

All dialogue lines are recorded in the history for player review.

```vns
[history show]     # open backlog overlay
[history hide]     # close backlog overlay
[history toggle]   # toggle overlay
[history scroll 5] # scroll back 5 lines
[history clear]    # clear all history entries
```

Tags are stripped from history entries via `TextParser.stripTags()`.

---

## HUD Messages

Short temporary messages shown on-screen (save confirmations, tips, etc.).

```vns
[hud Saved successfully!]
[hud Chapter 2 — The Forest]
[hud Score: ${score}]
```

HUD messages support `${var}` interpolation and auto-expire after a fixed duration.

---

## Performance Notes

- Animation effects (`shake`, `wave`, `bounce`, `rainbow`) are rendered per visible character per frame.
- Long fully-animated lines cost more than plain text.
- Avoid `{shake}` or `{wave}` on very long paragraphs in performance-constrained targets.
- Plain text with occasional effect spans is the recommended pattern.

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [Text Formatting & ICU](vns-text-formatting.md) — advanced interpolation
- [Variables & Conditions](vns-variables.md) — variable system
- [Commands Reference](vns-commands.md) — `[textspeed]`, `[hud]`, `[history]`

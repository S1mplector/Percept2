# Text Effects

JVN supports inline text markup inside dialogue strings.

Parser: `modules/core/src/main/java/com/jvn/core/vn/text/TextParser.java`  
Renderer: `modules/fx/src/main/java/com/jvn/fx/vn/VnRenderer.java`

## Syntax

Markup format:

```text
{tag}text{/tag}
{tag=value}text{/tag}
```

Example:

```text
Narrator: {color=#4a9eff}{wave}Welcome{/wave}{/color}
```

## Supported Tags

### Complete Tag Reference

| Tag | Closing | Value | Description |
|-----|---------|-------|-------------|
| `{shake}` | `{/shake}` | — | Text shakes/vibrates with random offsets each frame |
| `{wave}` | `{/wave}` | — | Text moves in a sine wave pattern |
| `{bounce}` | `{/bounce}` | — | Text bounces up and down |
| `{rainbow}` | `{/rainbow}` | — | Text cycles through rainbow colors |
| `{b}` | `{/b}` | — | Bold text (rendered thicker) |
| `{bold}` | `{/bold}` | — | Alias for `{b}` |
| `{i}` | `{/i}` | — | Italic text (rendered slanted) |
| `{italic}` | `{/italic}` | — | Alias for `{i}` |
| `{color=V}` | `{/color}` | `#RRGGBB` | Colored text |
| `{speed=V}` | `{/speed}` | float | Speed multiplier (0.5 = half speed, 2.0 = double) |
| `{delay=V}` | none | int (ms) | Pause before the next text span |

### Reserved Effects (Enum Only)

The `TextEffect` enum also defines `FADE_IN` and `TYPEWRITER`, but these currently have **no parser tag mappings**. They exist for programmatic use or future extension.

## Parser Model

### TextSpan

Each parsed segment becomes a `TextSpan` with:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `text` | `String` | — | The text content |
| `effect` | `TextEffect` | `NONE` | Active animation/style effect |
| `colorHex` | `String` | `null` | Color override (e.g., `#FF0000`) |
| `speedMultiplier` | `float` | `1.0` | Text reveal speed multiplier |
| `delayMs` | `int` | `0` | Pause before this span (ms) |

Helper methods: `hasEffect()`, `hasColor()`, `hasSpeedChange()`, `hasDelay()`, `length()`.

### Reveal System

`TextParser.getRevealInfo(spans, revealIndex)` returns a `RevealInfo` with the character index, accumulated delay, current speed multiplier, and active span. The renderer uses this to control the typewriter-style text reveal, adjusting timing per-span based on speed multipliers and delays.

## Important Behavior Notes

TextParser is lightweight and **state-based** (not stack-based):

- **One active effect** at a time — opening `{shake}` replaces any current effect. Closing it resets to `NONE`.
- **Color is tracked separately** from effects — `{color}` + `{shake}` works correctly (color and animation are independent channels).
- **Speed is tracked separately** — `{speed}` doesn't interact with effect state.
- **Delay is consumed once** — `{delay=500}` attaches to the next text span, then resets to 0.
- Tags are **case-insensitive** (`{Shake}` works the same as `{shake}`).

### Nesting Rules

```text
✅ {color=#FF0000}{shake}scary text{/shake}{/color}   — color + animation
✅ {speed=0.5}{wave}slow wave{/wave}{/speed}           — speed + animation
❌ {shake}{wave}text{/wave}{/shake}                     — second animation replaces first
```

## Color Format

Supported format: `#RRGGBB` (hex)

If color parsing fails, the renderer falls back to the default dialogue text color.

## Usage in VNS

```vns
@label start
Narrator: {delay=250}The room went silent.
Hero: {shake}Did you hear that?{/shake}
Guide: {color=#4a9eff}Stay calm.{/color}
Narrator: {wave}A strange wind passed by.{/wave}
[end]
```

## Performance Characteristics

- Effects are rendered per visible character.
- `shake` includes randomized offsets each frame.
- Long fully-animated lines are more expensive than plain text.

## Working with History/Plain Text

When you need unstyled text:

```java
String plain = TextParser.stripTags(styledText);
int len = TextParser.plainLength(styledText);
```

## Extending the System

To add a new text effect:

1. Extend `TextEffect` enum.
2. Parse tag in `TextParser.parse`.
3. Add visual behavior in `VnRenderer.drawStyledText`.
4. Document the new tag here and in VNS scripting docs.

# Text Effects System

The JVN Engine supports rich text effects in dialogue through inline markup tags. These effects add visual polish and emphasis to your visual novel text.

## Markup Syntax

Text effects use curly brace tags: `{effect}text{/effect}`

Effects can be nested: `{shake}{color=#FF0000}scary text{/color}{/shake}`

## Available Effects

### Animation Effects

| Tag | Description | Example |
|-----|-------------|---------|
| `{shake}` | Text vibrates randomly | `{shake}Earthquake!{/shake}` |
| `{wave}` | Sine wave up/down motion | `{wave}Flowing water...{/wave}` |
| `{bounce}` | Bouncing animation | `{bounce}Jump!{/bounce}` |
| `{rainbow}` | Cycling hue colors | `{rainbow}Magical sparkles{/rainbow}` |

### Style Effects

| Tag | Description | Example |
|-----|-------------|---------|
| `{b}` or `{bold}` | Bold text | `{b}Important{/b}` |
| `{i}` or `{italic}` | Italic text | `{i}thoughts{/i}` |
| `{color=#HEX}` | Custom color | `{color=#FF0000}red{/color}` |

### Timing Effects

| Tag | Description | Example |
|-----|-------------|---------|
| `{speed=X}` | Speed multiplier (1.0 = normal) | `{speed=0.5}slow...{/speed}` |
| `{delay=ms}` | Pause before text (milliseconds) | `{delay=500}After pause` |

## Color Format

Colors are specified as hex values:
- `#RRGGBB` - e.g., `#FF0000` for red
- `#RGB` shorthand is NOT supported

Common colors:
- Red: `#FF0000`
- Green: `#00FF00`
- Blue: `#0000FF`
- Yellow: `#FFFF00`
- Cyan: `#00FFFF`
- Magenta: `#FF00FF`
- Orange: `#FF8000`
- Purple: `#8000FF`

## Speed Values

The `{speed=X}` tag modifies text reveal speed:
- `0.5` = Half speed (slower)
- `1.0` = Normal speed
- `2.0` = Double speed (faster)
- `0.25` = Very slow for dramatic effect

## Examples

### Dramatic Reveal
```
The door creaked open...{delay=800}{shake}BANG!{/shake}
```

### Character Emotion
```
{color=#FF6666}"I-I'm not scared!"{/color} she said, {shake}trembling{/shake}.
```

### Magical Text
```
The wizard chanted: {rainbow}{wave}Abracadabra!{/wave}{/rainbow}
```

### Slow Dramatic Speech
```
{speed=0.3}In the beginning...{/speed}{delay=500} there was nothing.
```

### Emphasis
```
This is {b}very{/b} {color=#FF0000}important{/color}!
```

### Nested Effects
```
{color=#8800FF}{wave}Purple waves{/wave}{/color} crashed against the shore.
```

## VNS Script Usage

In your `.vns` script files:

```
label dramatic_scene

narrator "{delay=300}The room fell silent..."

hero "{shake}W-what was that?!{/shake}"

narrator "{speed=0.5}A shadow moved in the corner...{/speed}"

villain "{color=#880000}{b}Hello, hero.{/b}{/color}"

narrator "{rainbow}Magic filled the air.{/rainbow}"

[choice Run->escape | Fight->battle]
```

## Performance Notes

- Effects are rendered per-character, which may impact performance with very long text
- The `{shake}` effect uses random values per frame, creating continuous motion
- The `{wave}` and `{bounce}` effects are synchronized across characters
- The `{rainbow}` effect cycles smoothly through the color spectrum

## Stripping Tags

To get plain text without markup (useful for history/backlog):

```java
String plain = TextParser.stripTags(dialogueText);
int length = TextParser.plainLength(dialogueText);
```

## Custom Effects

The effect system is extensible. To add new effects:

1. Add to `TextEffect` enum in `com.jvn.core.vn.text.TextEffect`
2. Handle parsing in `TextParser.parse()`
3. Implement rendering in `VnRenderer.drawStyledText()`

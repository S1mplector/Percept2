# Text Effects

JVN supports inline text markup inside dialogue strings.

Parser: `core/src/main/java/com/jvn/core/vn/text/TextParser.java`  
Renderer: `fx/src/main/java/com/jvn/fx/vn/VnRenderer.java`

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

### Animation tags

- `{shake}...{/shake}`
- `{wave}...{/wave}`
- `{bounce}...{/bounce}`
- `{rainbow}...{/rainbow}`

### Style tags

- `{b}...{/b}` or `{bold}...{/bold}`
- `{i}...{/i}` or `{italic}...{/italic}`
- `{color=#RRGGBB}...{/color}`

### Timing tags

- `{speed=<multiplier>}...{/speed}`
- `{delay=<milliseconds>}` (applies to next emitted span)

## Important Behavior Notes

TextParser is lightweight and state-based:

- one active animation/style effect channel at a time (`TextEffect` enum)
- color and speed are tracked separately
- tags are parsed in a single pass

Practical implication:
- nested color + effect works well
- deeply nested multiple animation tags are not a full stack-based rich text system

## Color Format

Supported currently by renderer helper:
- `#RRGGBB`

If color parsing fails, renderer falls back to default dialogue color.

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

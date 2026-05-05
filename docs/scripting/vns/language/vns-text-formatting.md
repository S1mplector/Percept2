# VNS Text Formatting & ICU

Advanced text formatting reference for variable interpolation, pluralization, gender/case selection, and number formatting.

Formatter: `modules/core/src/main/java/com/jvn/core/vn/VnTextFormatter.java`

---

## Simple Variable Interpolation

The basic `${variableName}` syntax inserts a variable's value into text at runtime.

```vns
[set player_name "Alice"]
[set score 42]
[set gold 1500]

narrator: Hello, ${player_name}!
narrator: Your score is ${score} and you have ${gold} gold.
```

### Behavior

- Missing variables resolve to **empty string** (no error).
- Interpolation is **single-pass** — `${${nested}}` does not work.
- Works in dialogue text, choice text, and HUD messages.
- Use `${...}` form to avoid collisions with text-effect tags like `{shake}`.

---

## ICU-Style Formatting

VNS includes ICU MessageFormat-inspired syntax for plurals, selection, and number formatting. These use `{variableName, type, ...}` syntax (single curly braces, not `${}`).

---

## Plural Formatting

Handle singular/plural forms automatically based on a numeric variable.

### Syntax

```text
{variableName, plural, category1{text with #} category2{text with #} ...}
```

The `#` token inside the text is replaced with the actual numeric value.

### Plural Categories

| Category | When Used (English) |
|----------|-------------------|
| `zero` | count == 0 |
| `one` | count == 1 |
| `two` | count == 2 |
| `few` | (not used in English) |
| `many` | (not used in English) |
| `other` | all other counts |

### Exact Match

Use `=N` for exact numeric matches:

```text
{count, plural, =0{text} =1{text} other{text}}
```

### Examples

**Basic singular/plural:**

```vns
[set apple_count 1]
narrator: You picked up {apple_count, plural, one{# apple} other{# apples}}.
# Output: "You picked up 1 apple."

[set apple_count 5]
narrator: You picked up {apple_count, plural, one{# apple} other{# apples}}.
# Output: "You picked up 5 apples."
```

**With zero handling:**

```vns
[set lives 0]
narrator: {lives, plural, =0{No lives remaining!} one{# life remaining.} other{# lives remaining.}}
# Output: "No lives remaining!"

[set lives 1]
narrator: {lives, plural, =0{No lives remaining!} one{# life remaining.} other{# lives remaining.}}
# Output: "1 life remaining."

[set lives 3]
narrator: {lives, plural, =0{No lives remaining!} one{# life remaining.} other{# lives remaining.}}
# Output: "3 lives remaining."
```

**In choice text:**

```vns
[set potions 2]
> Use a potion ({potions, plural, one{# left} other{# left}}) [if potions > 0] -> use_potion
> Save potions for later -> skip
```

**Inventory report:**

```vns
[set swords 1]
[set shields 3]
[set potions 0]
narrator: Inventory: {swords, plural, one{# sword} other{# swords}}, {shields, plural, one{# shield} other{# shields}}, {potions, plural, =0{no potions} one{# potion} other{# potions}}.
# Output: "Inventory: 1 sword, 3 shields, no potions."
```

---

## Select Formatting

Choose text based on a string variable's value. Useful for gender, class, or any categorical variable.

### Syntax

```text
{variableName, select, value1{text} value2{text} other{text}}
```

The `other` case is the fallback when no match is found.

### Examples

**Gender-aware pronouns:**

```vns
[set gender "female"]
narrator: {gender, select, male{He} female{She} other{They}} walked into the room.
# Output: "She walked into the room."

narrator: {gender, select, male{His} female{Her} other{Their}} eyes gleamed.
# Output: "Her eyes gleamed."
```

**Class-based dialogue:**

```vns
[set player_class "mage"]
narrator: As a {player_class, select, warrior{brave warrior} mage{powerful mage} rogue{cunning rogue} other{mysterious adventurer}}, you step forward.
# Output: "As a powerful mage, you step forward."

mentor: {player_class, select, warrior{Raise your shield!} mage{Channel your mana!} rogue{Stay in the shadows!} other{Be careful!}}
```

**Faction-based responses:**

```vns
[set faction "rebels"]
guard: {faction, select, royalists{Welcome, friend.} rebels{Halt! State your business.} merchants{The market is open.} other{Who are you?}}
```

**Time-of-day greetings:**

```vns
[set time_of_day "evening"]
narrator: {time_of_day, select, morning{The morning sun rises.} afternoon{The afternoon is warm.} evening{The evening stars appear.} night{The night is dark and still.} other{Time passes.}}
```

---

## Number Formatting

Format numeric values cleanly.

### Syntax

```text
{variableName, number}
```

### Behavior

- Whole numbers display without decimals: `42.0` → `42`
- Decimals display with 2 decimal places: `3.14159` → `3.14`
- Non-numeric values pass through as-is.

### Examples

```vns
[set distance 1234.5]
narrator: The destination is {distance, number} leagues away.
# Output: "The destination is 1234.50 leagues away."

[set coins 500.0]
narrator: You have {coins, number} gold coins.
# Output: "You have 500 gold coins."

[set ratio 0.756]
narrator: Success rate: {ratio, number}
# Output: "Success rate: 0.76"
```

---

## Combining Formats

All formatting types can be mixed in a single line, and combined with simple `${}` interpolation:

```vns
[set player_name "Alice"]
[set gender "female"]
[set gems 7]
[set gold 1250.0]

narrator: ${player_name} found {gems, plural, one{# gem} other{# gems}} worth {gold, number} gold. {gender, select, male{He} female{She} other{They}} was thrilled!
# Output: "Alice found 7 gems worth 1250 gold. She was thrilled!"
```

**Complex inventory report:**

```vns
[set player_name "Kai"]
[set gender "other"]
[set swords 1]
[set potions 0]
[set gold 42.5]

narrator: ${player_name} has {swords, plural, =0{no swords} one{# sword} other{# swords}} and {potions, plural, =0{no potions} one{# potion} other{# potions}}, plus {gold, number} gold. {gender, select, male{He's} female{She's} other{They're}} ready for adventure.
# Output: "Kai has 1 sword and no potions, plus 42.50 gold. They're ready for adventure."
```

---

## Formatting in Different Contexts

### Dialogue

```vns
hero: I've collected {gem_count, plural, one{# gem} other{# gems}} so far.
```

### Choice Text

```vns
> Buy sword ({gold_cost, number} gold) [if gold >= gold_cost] -> buy
```

### HUD Messages

```vns
[hud {kills, plural, one{# enemy} other{# enemies}} defeated!]
```

---

## Difference from `@define`

| Feature | `@define` | `${var}` / `{var, ...}` |
|---------|-----------|------------------------|
| When | Parse time | Runtime |
| Source | Macro table | Variable map |
| Dynamic | No (baked into scenario) | Yes (reads live values) |
| Use for | Build constants, paths | Player data, scores, flags |

---

## Related Docs

- [VNS Overview](../overview/vns-scripting.md)
- [Dialogue & Text](vns-dialogue.md) — text effects and dialogue forms
- [Variables & Conditions](vns-variables.md) — setting and modifying variables
- [Directives & Declarations](vns-directives.md) — `@define` macro system

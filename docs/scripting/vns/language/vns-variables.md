# VNS Variables & Conditions

Complete reference for the variable system, conditional logic, and expression evaluation in VNS.

Condition evaluator: `core/src/main/java/com/jvn/core/vn/VnConditionEvaluator.java`

---

## Setting Variables

### `[set key value]`

Assigns a value to a variable in the runtime variable map.

```vns
[set score 0]
[set player_name "Alice"]
[set has_key true]
[set difficulty "hard"]
[set chapter 1]
[set gold 500]
```

Value types are inferred:
- `true` / `false` → boolean
- Numeric tokens → integer or double
- Everything else → string

### `@var` directive (parse-time shorthand)

Equivalent to `[set]` but declared as a directive:

```vns
@var score = 0
@var player_name = Alice
@var seen_intro         # defaults to true (boolean flag)
```

---

## Modifying Variables

### `[inc key [delta]]`

Increments a numeric variable. Default delta is 1.

```vns
[inc score]         # score += 1
[inc score 10]      # score += 10
[inc gold 50]       # gold += 50
```

If the variable doesn't exist or isn't numeric, it's treated as 0 before incrementing.

### `[dec key [delta]]`

Decrements a numeric variable. Default delta is 1.

```vns
[dec lives]         # lives -= 1
[dec gold 30]       # gold -= 30
[dec energy 5]      # energy -= 5
```

### `[flag key]`

Sets a variable to `true`.

```vns
[flag seen_intro]
[flag has_sword]
[flag chapter1_complete]
```

### `[unflag key]`

Sets a variable to `false`.

```vns
[unflag door_locked]
[unflag enemy_alive]
```

### `[clear key]`

Removes a variable entirely from the map.

```vns
[clear temp_data]
[clear old_quest_flag]
```

---

## Variable Interpolation

Use `${variableName}` in dialogue, choice text, and HUD messages to display variable values at runtime.

```vns
narrator: Welcome, ${player_name}. Your score is ${score}.
narrator: You have ${gold} gold coins and ${lives} lives remaining.

> Buy potion (${potion_cost} gold) [if gold >= potion_cost] -> buy
> Leave shop -> exit

[hud Score: ${score} | Combo: ${combo}]
```

### Behavior

- Missing variables resolve to **empty string** (no error, no crash).
- Interpolation is **single-pass** — no nested `${}` evaluation.
- Use `${...}` form specifically to avoid collisions with text-effect tags like `{shake}`.

---

## Advanced Text Formatting (ICU-Style)

VNS includes ICU-inspired formatting for plurals, gender/case selection, and number formatting.

Formatter: `core/src/main/java/com/jvn/core/vn/VnTextFormatter.java`

### Plurals

```text
{variableName, plural, one{# item} other{# items}}
```

The `#` token is replaced with the actual count.

```vns
[set apple_count 1]
narrator: You picked up {apple_count, plural, one{# apple} other{# apples}}.
# Output: "You picked up 1 apple."

[set apple_count 5]
narrator: You picked up {apple_count, plural, one{# apple} other{# apples}}.
# Output: "You picked up 5 apples."
```

Supported plural categories: `zero`, `one`, `two`, `few`, `many`, `other`
Exact match: `=N` syntax:

```vns
narrator: {lives, plural, =0{No lives left!} one{# life remaining} other{# lives remaining}}
```

### Select (gender/case)

```text
{variableName, select, male{He} female{She} other{They}}
```

```vns
[set pronoun "male"]
narrator: {pronoun, select, male{He} female{She} other{They}} walked into the room.
# Output: "He walked into the room."

[set pronoun "female"]
narrator: {pronoun, select, male{He} female{She} other{They}} walked into the room.
# Output: "She walked into the room."
```

### Number formatting

```text
{variableName, number}
```

Formats a number cleanly (removes unnecessary decimals):

```vns
[set distance 1234.5]
narrator: The destination is {distance, number} leagues away.
# Output: "The destination is 1234.50 leagues away."

[set count 42.0]
narrator: You collected {count, number} items.
# Output: "You collected 42 items." (integer display for whole numbers)
```

### Combining with `${}`

ICU formatting and simple interpolation can coexist in the same line:

```vns
narrator: ${player_name} found {gem_count, plural, one{# gem} other{# gems}} worth ${gold_value} gold.
```

---

## Conditional Logic

### Block conditionals

```vns
[if <condition>]
  # lines executed when condition is true
[elif <condition>]
  # lines executed when this condition is true
[else]
  # lines executed when no condition was true
[endif]
```

`[elif]` and `[else]` are optional. Multiple `[elif]` blocks are allowed.

**Example:**

```vns
[if score >= 100]
  narrator: Outstanding performance!
  [flag achievement_master]
[elif score >= 50]
  narrator: Well done!
[elif score >= 25]
  narrator: Not bad, keep trying.
[else]
  narrator: Better luck next time.
[endif]
```

### Nested conditionals

```vns
[if has_sword]
  [if sword_level >= 3]
    narrator: Your legendary blade glows with power.
  [else]
    narrator: Your sword is reliable but ordinary.
  [endif]
[else]
  narrator: You're unarmed.
[endif]
```

### Shortcut conditional jump

For simple "if this, go there" logic:

```vns
[if <condition> goto <label>]
```

```vns
[if score >= 100 goto perfect_ending]
[if has_key goto unlock_door]
[if not enemy_alive goto victory]
[jump default_path]
```

This is equivalent to:

```vns
[if score >= 100]
  [jump perfect_ending]
[endif]
```

---

## Condition Expression Syntax

### Operators

| Category | Operators |
|----------|-----------|
| Comparison | `==`, `!=`, `>`, `<`, `>=`, `<=` |
| Logical | `&&`, `\|\|`, `!`, `and`, `or`, `not` |
| Grouping | `(`, `)` |

### Literals

| Type | Examples |
|------|---------|
| Numbers | `0`, `42`, `-5`, `3.14` |
| Booleans | `true`, `false` |
| Strings | `"mage"`, `"hello world"` |

### Identifiers

Bare identifiers are resolved from the VN variable map:

- `score` → looks up `variables["score"]`
- `flags.ready` → looks up `variables["flags.ready"]`
- `has_key` → looks up `variables["has_key"]`

A missing variable evaluates as falsy (false/0/empty).

### Examples

```text
# Simple flag check
seen_intro

# Numeric comparison
score >= 10

# Compound conditions
score >= 10 && lives > 0

# Negation
not seen_intro
!locked_door

# String comparison
playerClass == "mage"

# Complex expression with parentheses
(playerClass == "mage" and mana >= 20) or debug

# Multiple conditions
has_sword && sword_level >= 3 && !cursed

# Nested logic
(route == "a" || route == "b") && chapter >= 2
```

---

## Condition Validation

Conditions are validated at **parse time** — malformed expressions produce parse errors before the game runs. This applies to:

- `[if]` / `[elif]` block conditions
- Choice condition suffixes (`[if ...]`)
- `[if ... goto ...]` expressions

This means typos in condition expressions are caught by the editor lint and parser, not at runtime.

---

## Special Runtime Variables

### Character framing

```vns
[set ui.characterHeightFactor 1.28]
[set ui.characterBaselineY 1.42]
```

These override `characterHeightFactor` / `characterBaselineY` from `dialogue.layout` while the scene runs.

### Best practices

- Use descriptive variable names: `chapter1_complete` not `c1`.
- Use `flag`/`unflag` for booleans, `set`/`inc`/`dec` for numbers.
- Avoid spaces in variable names.
- Prefix related variables: `quest_1_started`, `quest_1_complete`.

---

## Practical Patterns

### Pattern: Tracking relationship points

```vns
@var trust = 0
@var friendship = 0

@label conversation
hero: What do you think of the plan?

> I agree completely -> agree
> I have concerns -> concerns
> This is a terrible idea -> disagree

@label agree
[inc trust 2]
[inc friendship 1]
mentor: Glad you're on board.
[jump continue]

@label concerns
[inc trust 1]
mentor: Fair points. Let's discuss.
[jump continue]

@label disagree
[dec trust 1]
[dec friendship 1]
mentor: I see we have different views.
[jump continue]

@label continue
[if trust >= 5 goto trusted_path]
[if trust <= -3 goto hostile_path]
narrator: The conversation moves on.
```

### Pattern: Inventory check

```vns
@var potions = 3
@var gold = 100

@label shop
narrator: The merchant greets you.

> Buy health potion (20g) [if gold >= 20] -> buy_potion
> Buy sword (80g) [if gold >= 80 && !has_sword] -> buy_sword
> Leave -> exit_shop

@label buy_potion
[dec gold 20]
[inc potions 1]
narrator: You bought a potion. (${potions} total, ${gold}g remaining)
[jump shop]

@label buy_sword
[dec gold 80]
[flag has_sword]
narrator: A fine blade! (${gold}g remaining)
[jump shop]

@label exit_shop
narrator: You leave the shop.
```

### Pattern: Day/time tracking

```vns
@var day = 1
@var time_of_day = "morning"

@label new_day
[inc day 1]
[set time_of_day "morning"]
narrator: Day ${day} begins.

# ... events ...

[set time_of_day "afternoon"]
narrator: The afternoon sun is warm.

# ... events ...

[set time_of_day "evening"]
narrator: Evening falls.

[if day >= 7 goto final_day]
[jump new_day]
```

---

## Variables in Save Data

All variables are serialized when saving and restored when loading. This includes:

- All `[set]` / `[inc]` / `[dec]` values
- All `[flag]` / `[unflag]` booleans
- String, numeric, and boolean types

Variable state is also captured by the rollback system for in-session undo/redo.

---

## Related Docs

- [VNS Overview](vns-scripting.md)
- [Text Formatting & ICU](vns-text-formatting.md) — detailed formatting reference
- [Choices & Branching](vns-choices.md) — conditional choices
- [Commands Reference](vns-commands.md) — all variable commands
- [Subroutines & Flow Control](vns-flow-control.md) — jumps and conditionals

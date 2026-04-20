# VNS By Example — Variables and Conditions

Track game state with variables and control story flow with conditional logic — the backbone of any branching narrative.

**Difficulty:** Intermediate
**Time:** 20 minutes
**Concepts:** `[set]`, `[inc]`, `[dec]`, `[flag]`, `[unflag]`, `@var`, `[if]`/`[elif]`/`[else]`/`[endif]`, `${interpolation}`, comparison operators

---

## The Script

```vns
@scenario shop
@character narrator ""
@character hero "Yuki"
@character merchant "Merchant"

@var gold = 100
@var has_sword = false
@var has_shield = false

@label start
hero: Let's see what the merchant has.
[jump shop_menu]

@label shop_menu
merchant: Welcome! You have ${gold} gold.

> Buy Sword (50g)
  [if gold >= 50 goto buy_sword]
  merchant: You don't have enough gold!
  [jump shop_menu]
> Buy Shield (30g)
  [if gold >= 30 goto buy_shield]
  merchant: You don't have enough gold!
  [jump shop_menu]
> Leave
  [jump leave_shop]

@label buy_sword
[dec gold 50]
[flag has_sword]
merchant: A fine blade! ${gold} gold remaining.
[jump shop_menu]

@label buy_shield
[dec gold 30]
[flag has_shield]
merchant: Solid defense! ${gold} gold remaining.
[jump shop_menu]

@label leave_shop
[if has_sword && has_shield goto fully_equipped]
[if has_sword || has_shield goto partially_equipped]
hero: Maybe I should have bought something...
[end]

@label partially_equipped
hero: At least I got something useful.
[end]

@label fully_equipped
hero: Sword and shield — I'm ready for anything!
[end]
```

---

## Setting Variables

### `[set key value]`

```vns
[set score 0]              # integer
[set player_name "Alice"]  # string
[set has_key true]         # boolean
[set difficulty "hard"]    # string
[set gold 500]             # integer
[set ratio 1.5]            # double
```

Value types are inferred automatically:
- `true` / `false` → boolean
- Numeric tokens → integer or double
- Everything else → string

### `@var` Directive

Parse-time shorthand for `[set]`:

```vns
@var score = 0
@var player_name = Alice
@var seen_intro            # defaults to true (boolean flag)
```

Place `@var` directives near the top of the script alongside `@character` declarations.

---

## Modifying Variables

| Command | Effect | Example |
|---------|--------|---------|
| `[inc key]` | Add 1 | `[inc score]` |
| `[inc key delta]` | Add delta | `[inc gold 50]` |
| `[dec key]` | Subtract 1 | `[dec lives]` |
| `[dec key delta]` | Subtract delta | `[dec gold 30]` |
| `[flag key]` | Set to `true` | `[flag has_sword]` |
| `[unflag key]` | Set to `false` | `[unflag door_locked]` |
| `[clear key]` | Remove entirely | `[clear temp_data]` |

If a variable doesn't exist when `[inc]` or `[dec]` is used, it starts at 0.

---

## Variable Interpolation

Use `${key}` inside dialogue text to display variable values:

```vns
hero: I have ${gold} gold and ${score} points.
merchant: Welcome, ${player_name}!
narrator: Day ${day_count} of the journey.
```

Interpolation works in:
- Dialogue text
- `[hud]` messages
- Some command arguments

---

## Conditional Logic

### Block Form (`[if]`/`[elif]`/`[else]`/`[endif]`)

```vns
[if score >= 100]
  narrator: Excellent score!
[elif score >= 50]
  narrator: Not bad.
[else]
  narrator: Try harder.
[endif]
```

- `[/if]` is accepted as an alias for `[endif]`
- `[elif]` and `[else]` are optional
- You can nest `[if]` blocks

### Shortcut Jump

```vns
[if score >= 100 goto good_ending]
[if has_key goto unlock_door]
```

If the condition is false, execution continues to the next line.

---

## Comparison Operators

| Operator | Meaning |
|----------|---------|
| `==` | Equal to |
| `!=` | Not equal to |
| `>` | Greater than |
| `>=` | Greater than or equal |
| `<` | Less than |
| `<=` | Less than or equal |

```vns
[if score == 100]
[if name != "Alice"]
[if gold > 0]
[if level >= 5]
[if hp < 20]
[if day <= 7]
```

### Boolean Operators

| Operator | Meaning |
|----------|---------|
| `&&` | AND |
| `||` | OR |
| `!` | NOT |

```vns
[if has_sword && has_shield goto fully_equipped]
[if gold >= 50 || has_coupon goto can_buy]
[if !seen_intro goto show_intro]
[if (score > 50 && has_key) || is_admin goto secret]
```

### Bare Variable Check

A variable name alone checks truthiness:

```vns
[if has_key]                  # true if has_key is truthy (true, non-zero, non-empty)
  hero: I have the key!
[endif]

[if has_key goto use_key]     # shortcut form
```

---

## Patterns

### Affinity Tracking

```vns
@var affinity_sakura = 0
@var affinity_takeshi = 0

# After choices...
[inc affinity_sakura]    # when player favors Sakura
[inc affinity_takeshi]   # when player favors Takeshi

# Route determination
[if affinity_sakura > affinity_takeshi goto sakura_route]
[if affinity_takeshi > affinity_sakura goto takeshi_route]
[jump neutral_route]
```

### Resource Management

```vns
@var gold = 200
@var hp = 100

@label battle_result
[dec hp 30]
[inc gold 50]

[if hp <= 0 goto game_over]
hero: I survived! HP: ${hp}, Gold: ${gold}
```

### Flag Gating

```vns
[if !found_secret_door]
  narrator: The wall looks ordinary.
[else]
  narrator: A hidden passage!
  > Enter the passage
    [jump secret_room]
[endif]
> Continue forward
  [jump next_area]
```

### Multi-Condition Endings

```vns
@label ending_check
[if score >= 100 && turns <= 10 goto perfect_ending]
[if score >= 100 goto great_ending]
[if score >= 50 goto good_ending]
[jump bad_ending]

@label perfect_ending
narrator: Flawless victory!
[end]

@label great_ending
narrator: Impressive work!
[end]

@label good_ending
narrator: Not bad at all.
[end]

@label bad_ending
narrator: There's always next time.
[end]
```

### Counter with Display

```vns
@var attempts = 0

@label puzzle
[inc attempts]
hero: Attempt #${attempts}...

> Try combination A
  [if attempts >= 3 goto solve_puzzle]
  hero: That wasn't right.
  [jump puzzle]
> Try combination B
  hero: Nope, not that either.
  [jump puzzle]
> Give up
  hero: I'll come back later.
  [jump leave]
```

---

## Full Example: Day Planner

```vns
@scenario day_planner
@character narrator ""
@character hero "Yuki"

@var energy = 10
@var money = 50
@var happiness = 5
@var day = 1

@label day_start
narrator: === Day ${day} ===
narrator: Energy: ${energy} | Money: ${money} | Happiness: ${happiness}

[if day > 3 goto week_end]
[if energy <= 0 goto exhausted]

hero: What should I do today?

> Work (Energy -3, Money +20)
  [if energy >= 3 goto work]
  hero: I'm too tired to work.
  [jump day_start]
> Exercise (Energy -2, Happiness +2)
  [if energy >= 2 goto exercise]
  hero: I don't have enough energy.
  [jump day_start]
> Rest (Energy +4, Happiness +1)
  [jump rest]
> Shop (Money -15, Happiness +3)
  [if money >= 15 goto shop]
  hero: I can't afford it.
  [jump day_start]

@label work
[dec energy 3]
[inc money 20]
hero: Another productive day.
[jump next_day]

@label exercise
[dec energy 2]
[inc happiness 2]
hero: That felt great!
[jump next_day]

@label rest
[inc energy 4]
[inc happiness]
hero: Nice and relaxed.
[jump next_day]

@label shop
[dec money 15]
[inc happiness 3]
hero: Retail therapy works!
[jump next_day]

@label next_day
[inc day]
[jump day_start]

@label exhausted
hero: I'm completely drained...
narrator: Yuki collapsed from exhaustion.
[jump results]

@label week_end
narrator: The week is over.
[jump results]

@label results
narrator: === Final Stats ===
narrator: Energy: ${energy} | Money: ${money} | Happiness: ${happiness}

[if happiness >= 10 && money >= 50]
  narrator: A balanced, fulfilling week!
[elif happiness >= 8]
  narrator: A happy week, even if the wallet is lighter.
[elif money >= 80]
  narrator: Wealthy, but was it worth it?
[else]
  narrator: Room for improvement.
[endif]
[end]
```

---

## Key Takeaways

1. `[set key value]` creates variables; `@var key = value` is the directive form
2. `[inc]`/`[dec]` modify numbers; `[flag]`/`[unflag]` toggle booleans
3. `${key}` interpolates variable values in dialogue text
4. `[if]`/`[elif]`/`[else]`/`[endif]` for block conditionals
5. `[if condition goto label]` for one-line conditional jumps
6. Operators: `==`, `!=`, `>`, `>=`, `<`, `<=`, `&&`, `||`, `!`
7. Bare variable names are truthy checks
8. Variables persist across labels and choices within the same playthrough

---

## Next

- [Audio and Transitions](05-audio-and-transitions.md) — music, sound effects, and visual transitions
- [Back to Index](../vns-by-example.md)

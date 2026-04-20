# VNS By Example — Advanced Variables

Go beyond basic set/inc/flag with arithmetic operators, multiplication, division, toggle, persistent cross-save data, and complex expressions.

**Difficulty:** Advanced
**Time:** 20 minutes
**Concepts:** `[mul]`, `[div]`, `[toggle]`, `[persistent]`, arithmetic expressions, operator precedence, save data, runtime variables

---

## The Script

```vns
@scenario shop_advanced
@character narrator ""
@character hero "Yuki"
@character merchant "Merchant"

@var gold = 1000
@var discount_active = false
@var items_bought = 0
@var total_spent = 0

@label start
[persistent load total_purchases]
[if total_purchases >= 10]
  [flag discount_active]
  merchant: Welcome back, valued customer! 10% discount applied.
[else]
  merchant: Welcome!
[endif]

hero: Let me browse.
[jump shop]

@label shop
merchant: You have ${gold} gold. Discount: ${discount_active}

> Buy Health Potion (100g)
  [if gold >= 100 goto buy_potion]
  merchant: Not enough gold!
  [jump shop]
> Buy Enchanted Armor (500g)
  [if gold >= 500 goto buy_armor]
  merchant: Not enough gold!
  [jump shop]
> Apply Loyalty Coupon (2x gold)
  [mul gold 2]
  [hud Gold doubled! Now: ${gold}]
  [jump shop]
> Split Gold with Friend
  [div gold 2]
  [hud Shared gold. Now: ${gold}]
  [jump shop]
> Toggle Discount
  [toggle discount_active]
  [jump shop]
> Leave
  [jump leave]

@label buy_potion
[set price 100]
[if discount_active]
  [mul price 0.9]
[endif]
[dec gold price]
[inc items_bought]
[inc total_spent price]
merchant: Health potion! That'll be ${price} gold.
[jump shop]

@label buy_armor
[set price 500]
[if discount_active]
  [mul price 0.9]
[endif]
[dec gold price]
[inc items_bought]
[inc total_spent price]
merchant: Fine choice! ${price} gold.
[jump shop]

@label leave
narrator: Items: ${items_bought} | Spent: ${total_spent} | Remaining: ${gold}
[persistent inc total_purchases items_bought]
[persistent store total_purchases]
[end]
```

---

## Arithmetic Variable Commands

### `[mul key factor]`

Multiplies a numeric variable:

```vns
[mul gold 2]        # gold *= 2
[mul score 1.5]     # score *= 1.5
[mul price 0.9]     # 10% discount
[mul damage 0]      # zero out
```

If the variable doesn't exist, it starts at 0 (so `0 × factor = 0`).

### `[div key divisor]`

Divides a numeric variable:

```vns
[div gold 2]        # gold /= 2
[div price 3]       # split three ways
[div health 1.5]    # reduce by a third
```

**Division by zero is silently ignored** — the variable keeps its current value.

### `[toggle key]`

Flips a boolean variable between `true` and `false`:

```vns
[toggle door_locked]
[toggle debug_mode]
[toggle discount_active]
```

If the variable is `true`, it becomes `false`, and vice versa. Non-boolean values are coerced.

---

## Arithmetic in Conditions

The condition evaluator supports full arithmetic expressions with correct precedence:

### Operators

| Operator | Meaning | Precedence |
|----------|---------|------------|
| `!` / `-` (unary) | NOT / negate | Highest |
| `*` `/` `%` | Multiply, divide, modulo | High |
| `+` `-` | Add, subtract | Medium |
| `==` `!=` `>` `>=` `<` `<=` | Comparison | Low |
| `&&` `||` | Logical AND, OR | Lowest |

### Examples

```vns
[if gold * 2 > 500]
  narrator: You could afford two of those.
[endif]

[if (atk + weapon_bonus) * crit_multiplier > enemy_def]
  narrator: Critical hit!
[endif]

[if score % 10 == 0]
  narrator: Milestone reached!
[endif]

[if -debt + income > 0]
  narrator: In the black!
[endif]
```

### String Concatenation

The `+` operator concatenates strings:

```vns
[set greeting "Hello, "]
[set name "Yuki"]
# In conditions: greeting + name == "Hello, Yuki"
[if greeting + name == "Hello, Yuki"]
  narrator: String concatenation works!
[endif]
```

### Combined Arithmetic + Logic

```vns
[if (gold >= 100 && has_coupon) || vip_status]
  narrator: You qualify for a discount.
[endif]

[if hp + heal_amount > max_hp]
  [set hp max_hp]
[else]
  [inc hp heal_amount]
[endif]
```

---

## Persistent Variables

Persistent variables survive across saves and playthroughs. They're stored in a separate persistent data file.

### Writing Persistent Data

```vns
[persistent set endings_seen 1]
[persistent inc endings_seen]
[persistent dec endings_seen]
[persistent flag true_route_unlocked]
[persistent unflag true_route_unlocked]
[persistent toggle new_game_plus]
[persistent clear endings_seen]
```

All the same operations as regular variables, prefixed with `[persistent]`.

### Reading Persistent Data

Persistent values are automatically mirrored into session variables as `persistent.<key>`:

```vns
[if persistent.endings_seen >= 3 goto true_route]
[if persistent.true_route_unlocked goto unlock_content]
```

### Copying Between Session and Persistent

```vns
# Copy persistent → session variable
[persistent load endings_seen]              # → endings_seen
[persistent load endings_seen my_count]     # → my_count

# Copy session variable → persistent
[persistent store endings_seen]             # endings_seen → persistent
[persistent store endings_seen my_count]    # my_count → persistent.endings_seen
```

### Persistent Store Management

```vns
[persistent reload]    # reload from disk
[persistent reset]     # clear all persistent data
```

---

## Patterns

### Dynamic Pricing

```vns
@var base_price = 100
@var multiplier = 1.0

[if day >= 7]
  [set multiplier 1.5]    # weekend markup
[endif]
[if has_coupon]
  [set multiplier 0.8]    # coupon discount
[endif]

[set final_price base_price]
[mul final_price multiplier]
merchant: That'll be ${final_price} gold.
```

### Experience Scaling

```vns
@var exp = 0
@var level = 1
@var exp_to_next = 100

@label gain_exp
[inc exp 30]
[if exp >= exp_to_next goto level_up]
narrator: EXP: ${exp} / ${exp_to_next}
[return]

@label level_up
[dec exp exp_to_next]
[inc level]
[mul exp_to_next 1.5]    # each level requires 50% more
[hud Level Up! Now level ${level}]
narrator: Next level at ${exp_to_next} EXP.
[return]
```

### New Game Plus

```vns
# First playthrough ending
@label ending
[persistent inc endings_seen]
[persistent flag saw_ending_a]
[persistent store gold]
[end]

# Start of any playthrough
@label start
[persistent load endings_seen]
[if persistent.endings_seen >= 1]
  [hud New Game+ Mode]
  [persistent load gold]     # carry over gold
  [mul gold 0.5]             # but only half
[endif]

[if persistent.saw_ending_a && persistent.saw_ending_b]
  narrator: A new option appears...
  > True Route
    [goto TrueRoute:start]
[endif]
```

### Score Leaderboard

```vns
@label game_over
narrator: Final score: ${score}

[persistent load high_score]
[if score > persistent.high_score]
  [persistent set high_score score]
  [persistent store high_score]
  [hud New High Score!]
[else]
  narrator: High score: ${persistent.high_score}
[endif]
```

### Stat Calculations

```vns
@var base_atk = 10
@var weapon_bonus = 5
@var buff_multiplier = 1.0

# Apply buff
[set buff_multiplier 1.5]

# Calculate damage (in conditions)
[if (base_atk + weapon_bonus) * buff_multiplier > enemy_def]
  narrator: You deal damage!
  [set damage base_atk]
  [inc damage weapon_bonus]
  [mul damage buff_multiplier]
  [dec damage enemy_def]
  narrator: ${damage} damage dealt!
[else]
  narrator: Your attack was blocked!
[endif]
```

---

## Runtime Variables

Special runtime variables control rendering behavior:

```vns
[set ui.characterHeightFactor 1.28]
[set ui.characterBaselineY 1.42]
```

These affect how characters are scaled and positioned on screen.

---

## Full Example: Card Game

```vns
@scenario card_game
@character narrator ""
@character dealer "Dealer"

@var hand_value = 0
@var dealer_value = 0
@var bet = 0
@var gold = 500
@var wins = 0

@label start
[persistent load total_wins]
narrator: Welcome to the card table!
narrator: Total wins: ${persistent.total_wins} | Gold: ${gold}

@label new_round
[if gold <= 0 goto broke]

narrator: Gold: ${gold}
> Bet 50
  [if gold >= 50]
    [set bet 50]
  [else]
    narrator: Not enough gold.
    [jump new_round]
  [endif]
  [jump deal]
> Bet 100
  [if gold >= 100]
    [set bet 100]
  [else]
    narrator: Not enough gold.
    [jump new_round]
  [endif]
  [jump deal]
> Cash out
  [jump cash_out]

@label deal
[dec gold bet]
# Simulate random-ish hand values
[set hand_value 15]
[inc hand_value wins]          # better hands with more wins
[set dealer_value 14]

narrator: Your hand: ${hand_value} | Dealer: ???
narrator: Bet: ${bet} gold

> Hit (risky — add value)
  [inc hand_value 5]
  [if hand_value > 21 goto bust]
  narrator: New hand value: ${hand_value}
  [jump reveal]
> Stand
  [jump reveal]
> Double Down
  [if gold >= bet]
    [dec gold bet]
    [mul bet 2]
    [inc hand_value 3]
    [if hand_value > 21 goto bust]
    [jump reveal]
  [else]
    narrator: Can't afford to double!
    [jump reveal]
  [endif]

@label reveal
narrator: Dealer shows: ${dealer_value}

[if hand_value > dealer_value goto win]
[if hand_value == dealer_value goto push]
[jump lose]

@label win
[mul bet 2]
[inc gold bet]
[inc wins]
[hud You win ${bet} gold!]
[jump new_round]

@label lose
narrator: Dealer wins.
[jump new_round]

@label push
[inc gold bet]
narrator: Push — bet returned.
[jump new_round]

@label bust
narrator: Bust! Hand value ${hand_value} exceeds 21.
[jump new_round]

@label broke
narrator: You're out of gold. Game over.
[jump cash_out]

@label cash_out
narrator: Final gold: ${gold} | Wins: ${wins}
[persistent inc total_wins wins]
[persistent store total_wins]
[if gold > 500]
  narrator: You came out ahead!
[elif gold == 500]
  narrator: You broke even.
[else]
  narrator: Better luck next time.
[endif]
[end]
```

---

## Key Takeaways

1. `[mul key factor]` multiplies variables; `[div key divisor]` divides (zero-safe)
2. `[toggle key]` flips booleans between true/false
3. Conditions support `+`, `-`, `*`, `/`, `%` with correct precedence
4. `+` on strings does concatenation; other operators coerce to numbers
5. `[persistent op key ...]` operates on cross-save persistent data
6. Persistent values are readable as `persistent.<key>` in conditions
7. `[persistent load/store]` copies between session and persistent maps
8. Combine arithmetic commands to build dynamic pricing, scaling, and stat systems

---

## Next

- [JES and Java Integration](10-jes-and-java-integration.md) — launching JES, inline timelines, Java interop
- [Back to Index](../vns-by-example.md)

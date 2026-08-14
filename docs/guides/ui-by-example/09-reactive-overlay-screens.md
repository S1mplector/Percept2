# UI By Example — Reactive Overlay Screens

Build a compact shop prompt whose text and buttons reevaluate against live VN state.

**Difficulty:** Intermediate
**Time:** 20 minutes
**Concepts:** `.screen`, interpolation, `visibleIf`, `enabledIf`, `show`, `call`, return values, timers

---

## Create the Screen

```properties
# config/screens/shop.screen
id=shop
title=Platform Kiosk
text=Coins: ${coins}\nPotion price: 10
x=0.22
y=0.18
width=0.56
height=0.42
modal=true
dim=true
dismiss=false
call=true
returnKey=screen.return.shop

buttons=buy,leave

button.buy.label=Buy potion
button.buy.action=return
button.buy.target=buy
button.buy.enabledIf=coins >= 10

button.leave.label=Leave
button.leave.action=return
button.leave.target=leave
```

Reactive screens are intentionally small: one title, one body, and standard overlay buttons. Use a Facet when the content needs several independently positioned regions.

---

## Call It from VNS

```vns
@var coins = 12
@var potions = 0

@label kiosk
[screen call shop]

[if screen.return.shop == "buy"]
  [dec coins 10]
  [inc potions]
  narrator: You bought a potion. ${coins} coins remain.
[else]
  narrator: You leave the kiosk.
[endif]
```

`[screen call shop]` pauses the script. A `return` button closes the screen and writes its target to `screen.return.shop`.

---

## Show Without Waiting

Use `show` for passive information:

```properties
# config/screens/objective.screen
title=Objective
text=${objective_text}
x=0.68
y=0.06
width=0.28
height=0.18
modal=false
dim=false
dismiss=true
buttons=
```

```vns
[set objective_text "Reach the east clock"]
[screen show objective]
narrator: The platform lights flicker.
[screen hide objective]
```

`show` continues immediately. `hide` removes the named overlay explicitly.

---

## Conditions and Persistent Unlocks

```properties
visibleIf=ui.shopUnlocked == true
button.secret.visibleIf=persistent.trueEnding == true
button.buy.enabledIf=coins >= 10
```

Screen and button conditions use the VNS condition evaluator. Persistent values are mirrored under `persistent.<key>`.

---

## Timed Notification

```properties
# config/screens/saved.screen
title=Saved
text=Progress stored successfully.
x=0.36
y=0.08
width=0.28
height=0.14
modal=false
dim=false
dismiss=false
timer=1800
timerAction=hide
buttons=
```

For a timed call, use `timerAction=return` and set `timerTarget` so the waiting script resumes with a known value.

---

## Discovery Order Matters

`[screen show id]` and `[screen call id]` search Facets before ordinary screen files. Keep IDs unique unless a Facet intentionally replaces a `.screen` implementation.

Recommended paths:

```text
config/screens/notice.screen
config/screens/shop.screen
config/facets/status.facet
```

---

## Key Takeaways

1. Use `.screen` for a title/body/button overlay driven by VN state.
2. `show` is non-blocking; `call` waits for a returned value.
3. Text interpolation and conditions reevaluate against live variables.
4. Use timers for notifications and time-limited calls.
5. Move to a Facet when the overlay needs nested or independently placed visual regions.

---

## Next

Build that richer composition in [Facet Fundamentals](10-facet-fundamentals.md).

[Back to UI By Example](../ui-by-example.md)

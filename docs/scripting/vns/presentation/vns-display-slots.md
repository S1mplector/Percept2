# Character Display Slots

Display slots let more than one character sprite occupy the same visual position without replacing each other.

Use this when a scene needs separate visible instances at the same coordinates, such as a body sprite and a head sprite, foreground and background variants, or temporary overlays that should move/hide independently.

---

## Why Slots Exist

Classic VNS character placement is position based:

```vns
[show body center neutral]
[show head center neutral]
```

Both commands target `center`, so the second show replaces the first. Layer order (`z`) controls which sprite draws in front, but it does not create another visible instance by itself.

A display slot gives the visible instance its own identity:

```vns
[show body center neutral slot=body z=0]
[show head center neutral slot=head z=10]
```

Both sprites still render at `center`, but `body` and `head` are independent display slots.

---

## Mental Model

- **Position** answers: where does the sprite appear?
- **Slot** answers: which visible instance is this?
- **Layer order / z** answers: which sprite draws in front?

If `slot=` is omitted, JVN uses the legacy behavior: one visible character per position/character flow. Existing scripts keep working the same way.

If `slot=` is present, JVN creates a distinct display instance at the same base position. Showing a new character into the same slot replaces that slot only.

---

## Syntax

### Show Into A Slot

```vns
[show <charId> <position> [expression] slot=<slotId> [z=<layerOrder>]]
[show <charId> at <x>,<y>[,<z>] [expression] slot=<slotId>]
```

Aliases for `slot=`:

- `as=`
- `instance=`
- `display=`
- `display_slot=`
- `display-slot=`

Examples:

```vns
[show body center neutral slot=body z=0]
[show head center neutral as=head z=10]
[show eyes at 0.5,0.42 neutral slot=eyes z=20]
```

### Move A Slot

Move the character that currently occupies a slot:

```vns
[move slot=head at 0.5,0.72]
[move slot=head at 0.5,0.68 ease_out_quad 240]
```

Or move a named character instance:

```vns
[move head center neutral slot=head]
[move head at 0.5,0.68 blink ease_out_quad 240 slot=head]
```

Movement follows the same rules as normal `[move]`: if global position mode is off, the instance enters at the new position; if global mode is on for the character, it slides from the old position.

### Hide A Slot

```vns
[hide slot=head]
[hide head slot=head]
```

The first form hides whichever character currently occupies `head`. The second form is useful when you want the script to say both the character and the slot.

### Change A Slot Expression With `[char]`

```vns
[char head expression blink slot=head]
[char head move at 0.5,0.68 blink ease_out_quad 240 slot=head]
[char head show center neutral slot=head]
[char head hide slot=head]
```

Expression changes are scoped to the slot, so another slot using the same character id can keep its own expression.

---

## Same Position, Different Depth

Use `z` or a numeric layer argument with slots when sprites overlap:

```vns
[show body center neutral slot=body z=0]
[show head center neutral slot=head z=10]
[show hair_front center neutral slot=hair_front z=20]
```

Higher `z` values draw in front. Without explicit `z`, a slot inherits the base position's default layer order.

---

## Replacing One Slot

Showing a different character into an existing slot replaces only that slot:

```vns
[show head_a center neutral slot=head z=10]
[show head_b center smile slot=head z=10]   # replaces head only
```

Other slots at `center` remain visible:

```vns
[show body center neutral slot=body z=0]    # still visible
```

---

## Slots And Custom Positions

Slots work with predefined positions, named `@position` entries, and inline coordinates:

```vns
@position portrait_center 0.5 0.82

[show body portrait_center neutral slot=body z=0]
[show head portrait_center neutral slot=head z=10]
[show sparkle at 0.5,0.35 twinkle slot=sparkle z=30]
```

Save/load and rollback preserve both the display slot id and the base visual position.

---

## Slots vs Movable Layer Groups

Use **movable layer groups** when one character is already a layered rig and you want one grouped target, such as `head`, `face`, or `arm`, to move together in Puppeteer:

```vns
@chargroup aria head pivot=0.5,0.28 $head_base | $eyes_neutral | $mouth_smile
@charpreset aria neutral $body | $head

[show aria center neutral]
```

Use **display slots** when you intentionally want separate visible character instances:

```vns
[show aria_body center neutral slot=body z=0]
[show aria_head center neutral slot=head z=10]
```

The two features can coexist. A slotted sprite can still be a layered character with `@chargroup` targets inside it.

---

## Migration Pattern

Before:

```vns
[show x center body]
[show x center head]       # replaces body, because both use center
```

After:

```vns
[show x_body center neutral slot=body z=0]
[show x_head center neutral slot=head z=10]
```

Or, if both are expressions on the same character id:

```vns
[show x center body slot=body z=0]
[show x center head slot=head z=10]
```

Now `body` and `head` can be moved, hidden, and expression-swapped independently.

---

## Quick Reference

```vns
[show body center neutral slot=body z=0]
[show head center neutral slot=head z=10]

[move slot=head at 0.5,0.72 ease_out_quad 240]
[char head expression blink slot=head]
[hide slot=head]
```

Related docs:

- [Characters & Sprites](vns-characters.md)
- [Movable Character Layer Groups](vns-movable-layer-groups.md)
- [Commands Reference](../language/vns-commands.md)

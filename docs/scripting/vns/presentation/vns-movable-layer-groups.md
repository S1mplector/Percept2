# Movable Character Layer Groups

Movable layer groups let a layered character expose named rig parts such as `head`, `face`, `left_arm`, or `hair_front`. A group can be reused in `@charpreset` declarations and animated as one Puppeteer target.

Use this when a character is built from many sprite parts and one part needs to move separately from the full body. The common case is a head made from several layers:

```vns
@chargroup aria head pivot=0.5,0.28 $head_base | $eyes_neutral | $mouth_smile | $brow_neutral
@charpreset aria neutral $body | $head
```

When launched into Puppeteer, the active expression exposes `aria_head`. Moving that target moves the whole head group.

---

## Syntax

```vns
@chargroup <characterId> <groupId> [parent=<parentGroupId>] [pivot=<x>,<y>] <layerSpec>
```

| Part | Meaning |
|------|---------|
| `characterId` | The character that owns the group. |
| `groupId` | The reusable group name, such as `head` or `face`. |
| `parent=<group>` | Optional parent group for nested movement. |
| `pivot=<x>,<y>` | Optional normalized pivot used for rotation and scale. |
| `layerSpec` | Pipe-separated `$layerId` and `$groupId` references. |

Declaration order matters for references:

- Declare `@charlayer` entries before groups that use them.
- Declare an earlier group before another group or preset uses `$groupId`.
- If a layer and a group share the same name, `$name` resolves to the layer first.

---

## Quick Start

```vns
@character aria "Aria"

@charlayer aria body assets/characters/aria/body.png
@charlayer aria head_base assets/characters/aria/head/base.png
@charlayer aria eyes_neutral assets/characters/aria/eyes/neutral.png
@charlayer aria mouth_smile assets/characters/aria/mouth/smile.png
@charlayer aria brow_neutral assets/characters/aria/brow/neutral.png

@chargroup aria head pivot=0.5,0.28 $head_base | $eyes_neutral | $mouth_smile | $brow_neutral

@charpreset aria neutral $body | $head

@label start
[show aria center neutral]
aria: There. One head target instead of four repeated layers.
```

In Puppeteer, animate `aria_head` for broad head motion. The individual targets still exist, so you can animate `aria_eyes_neutral` or `aria_mouth_smile` for tiny adjustments.

---

## Migrating From Repeated Layer Lists

Before movable groups, authors often split one character into body and head show calls:

```vns
[show aria center $body+$arm_left+$arm_right]
[show aria center $head_base+$eyes_neutral+$mouth_smile+$brow_neutral]
```

That makes head motion possible, but every expression has to repeat every head layer.

With `@chargroup`, write the layer list once:

```vns
@chargroup aria body $body | $arm_left | $arm_right
@chargroup aria head pivot=0.5,0.28 $head_base | $eyes_neutral | $mouth_smile | $brow_neutral

@charpreset aria neutral $body | $head
@charpreset aria happy $body | $head_base | $eyes_happy | $mouth_smile | $brow_raised
```

You can choose how much to group. If every expression uses the same head base but different eyes and mouths, keep only stable layers in `head_base_group`, or define expression-specific groups such as `head_neutral`, `head_happy`, and `head_angry`.

For characters with many one-off variants, use layer globs instead of listing every sprite:

```vns
@chargroup john body_orientation pivot=0.5,1 \
  $body_* | $neck_* | \
  $arm_front_* | $arm_behind_*
```

The glob expands against previously declared `@charlayer` IDs. Expansion is deterministic, and a pattern that matches nothing is a script error rather than a broken runtime target. Animate the resulting group through the stable `john_body_orientation` target across expression changes.

---

## Nested Groups

Nested groups let one part inherit another part's movement while keeping local controls.

```vns
@chargroup aria face parent=head $eyes_neutral | $mouth_smile | $brow_neutral
@chargroup aria head pivot=0.5,0.28 $head_base | $face
@charpreset aria neutral $body | $head
```

In Puppeteer:

- `aria_head` moves the head base and face together.
- `aria_face` moves only the face layers, while still following the head.
- `aria_eyes_neutral` can still move independently for a small eye nudge.

Transforms stack in this order:

1. Parent group transforms.
2. Child group transforms.
3. Individual layer transforms.

If `aria_head` moves right by 8px and `aria_face` moves left by 2px, the face layers render with both transforms applied.

---

## Puppeteer Target Names

For a character `aria`, expression `neutral`, group `head`, and layer `head_base`, Puppeteer and runtime playback can use:

| Target | Scope |
|--------|-------|
| `aria_head` | Stable group target across expressions. |
| `aria_neutral_head` | Expression-specific group target. |
| `aria_head_base` | Stable layer target across expressions. |
| `aria_neutral_head_base` | Expression-specific layer target. |

Prefer stable group targets for animation that should survive expression swaps:

```jes
timeline {
  move "aria_head" {
    x: 8
    y: -4
    dur: 180
    ease: ease_out_sine
  }
}
```

Use expression-specific targets when the animation should only affect one expression variant.

---

## Pivot Rules

The optional `pivot=<x>,<y>` value is normalized to the sprite bounds.

| Pivot | Useful For |
|-------|------------|
| `0.5,0.5` | Center rotation. |
| `0.5,0.28` | Head tilt from the upper face/head area. |
| `0.5,1.0` | Full-body sway or scale from the feet. |

The group pivot is a default. If Puppeteer explicitly authors a pivot/origin on the group target, the authored timeline pivot wins.

```vns
@chargroup aria head pivot=0.5,0.28 $head_base | $eyes_neutral | $mouth_smile
```

```jes
timeline {
  pivot "aria_head" {
    x: 0.48
    y: 0.22
  }
  rotate "aria_head" {
    deg: -4
    dur: 220
  }
}
```

---

## Inline Composites

Groups can also be used in inline composites anywhere layered expressions are accepted:

```vns
[show aria center @neutral+$glasses]
[show aria center $body+$head+$hat]
[char aria expression @neutral+$blush]
```

Inline composites expand the group into normal layer paths at parse time, so runtime drawing behaves like a regular `@charpreset`.

---

## Editor Support

The editor recognizes `@chargroup` in the same places it already understands layered character presets:

- VNS code highlighting and hover help.
- Puppeteer Launcher scene snapshots.
- Puppeteer imported/opened timelines.
- Layered Character Project Catalog.
- Asset Picker layered preset metadata.
- Project dependency validation for literal paths inside group specs.

When Puppeteer launches from a VNS scene, it creates a normal character group for the visible character and nested rig groups for any active `@chargroup` declarations.

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| `$head` is reported as unknown | The group is declared after the preset or inline expression. | Move `@chargroup aria head ...` above the preset that uses `$head`. |
| A group is visible but `aria_head` does not move anything | The active expression does not include the group's layers. | Make sure the current `@charpreset` includes `$head` or the layers contained by `head`. |
| Layers draw in the wrong order | The group appears at the wrong position in the preset. | Move `$head` earlier or later in the `@charpreset` list. |
| Rotation uses the wrong center | The group has no useful pivot or Puppeteer authored a different pivot. | Add `pivot=0.5,0.28` or set the pivot on the Puppeteer target. |
| Nested group motion feels doubled | Both parent and child groups animate the same kind of movement. | Keep broad motion on the parent and local offsets on the child. |

---

## Related Docs

- [Layered Character Presets Guide](vns-layered-charpresets.md)
- [Characters & Sprites](vns-characters.md)
- [Directives & Declarations](../language/vns-directives.md)
- [Puppeteer Editor Guide](../../../editor/puppeteer/puppeteer-editor-guide.md)

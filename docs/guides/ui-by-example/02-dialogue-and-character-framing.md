# UI By Example — Dialogue, Name Boxes, and Character Framing

Build a complete ADV-style dialogue surface with a readable textbox, adaptive speaker name, and consistent character staging.

**Difficulty:** Beginner
**Time:** 20 minutes
**Concepts:** `dialogue.layout`, normalized textbox geometry, name-box auto width, typography, character framing

---

## The Complete Layout

Create `config/ui/dialogue.layout`:

```properties
# Textbox geometry: viewport fractions
textBoxX=0.04
textBoxY=0.73
textBoxWidth=0.92
textBoxHeight=0.23
textBoxPadding=22

# Textbox presentation
textBoxColor=#0B1220E8
textBoxOpacity=0.94

# Name box: pixel offsets relative to the textbox
nameBoxXOffset=24
nameBoxYOffset=-38
nameBoxWidth=150
nameBoxHeight=42
nameBoxAutoWidth=true
nameTextXOffset=14
nameTextBaselineOffset=28
nameBoxColor=#1E3A5FEE
nameTextColor=#FCD34D
nameTextFontFamily=SansSerif
nameTextFontSize=20
nameTextFontWeight=BOLD

# Dialogue text
dialogueTextHorizontalPadding=24
dialogueTextTopPadding=42
dialogueTextRightPadding=170
dialogueTextBottomPadding=14
dialogueTextColor=#F8FAFC
dialogueTextFontFamily=SansSerif
dialogueTextFontSize=20
dialogueTextFontWeight=NORMAL
dialogueTextXAlign=0.0

# Character staging
characterHeightFactor=0.84
characterBaselineY=0.94
```

Point to a non-default file through `jvn.project` only when necessary:

```properties
dialogueLayout=config/ui/dialogue.layout
```

Otherwise the runtime discovers `config/ui/dialogue.layout` automatically.

---

## Preview It with VNS

```vns
@scenario ui_dialogue_demo

@character iris "Iris Holloway"
@background atrium "assets/backgrounds/atrium.png"
@charimg iris calm "assets/characters/iris/calm.png"

@label start
[bg atrium]
[show iris center calm]
iris: This line tests the textbox, the speaker name, and the character baseline together.
[end]
```

Test a short name and the longest translated speaker name in the project. With `nameBoxAutoWidth=true`, the configured width is a minimum and the renderer expands the box to fit longer names.

---

## Geometry Mental Model

```text
viewport
└── textbox (fractional x/y/width/height)
    ├── name box (pixel offset and size)
    ├── dialogue text (pixel padding)
    └── textbox action buttons (fractions of textbox)
```

The textbox is clamped inside the viewport. Name and text measurements are pixel values because they relate directly to fonts and readable padding.

---

## Character Framing

`characterHeightFactor` scales sprites relative to viewport height. `characterBaselineY` determines where their feet or lower edge settle.

| Look | Height | Baseline |
|---|---:|---:|
| Standard VN | `0.82` | `0.95` |
| Waist-up portraits | `0.72` | `1.03` |
| Distant staging | `0.58` | `0.90` |
| Tall dramatic framing | `0.94` | `1.00` |

Treat these as starting points. Asset trimming changes the visible result, so test the tallest and shortest character sprites together.

---

## Add Skinned Panels Later

Once color-only geometry works, add assets:

```properties
textBoxAsset=assets/ui/dialogue/textbox.png
nameBoxAsset=assets/ui/dialogue/namebox.png
```

Colors remain useful fallbacks. Keep frame images transparent and avoid large empty margins that make the visible box disagree with its logical bounds.

---

## Testing Checklist

- Verify one-line and multi-line dialogue.
- Verify the longest speaker name.
- Check sprites at left, center, and right positions.
- Test the minimum and maximum supported viewport.
- Confirm the textbox does not cover important character expressions.
- Check contrast both over dark and bright backgrounds.

---

## Key Takeaways

1. The textbox uses viewport fractions; its internal spacing uses pixels.
2. Enable name-box auto width for long and localized speaker names.
3. Tune character height and baseline alongside the textbox, not independently.
4. Establish geometry with colors before adding frame assets.
5. Use the running renderer as the final visual authority.

---

## Next

Add choices and alternate dialogue presentations in [Choices, Dialogue Modes, and Quick Controls](03-choices-modes-and-controls.md).

[Back to UI By Example](../ui-by-example.md)

# UI By Example — Choices, Dialogue Modes, and Quick Controls

Extend the dialogue surface with stateful choices, NVL pages, character bubbles, and textbox action buttons.

**Difficulty:** Beginner
**Time:** 25 minutes
**Concepts:** choice states, disabled options, NVL mode, bubble mode, Auto, Skip, Log, save, hide UI

---

## Style Choice Buttons

Add this to `config/ui/dialogue.layout`:

```properties
choiceXCenter=0.50
choiceYStart=-1
choiceWidthFactor=0.58
choiceHeight=56
choiceGap=12
choiceTextXPadding=20

choiceBackgroundColor=#172554EE
choiceHoverColor=#1E40AFEE
choiceSelectedColor=#2563EBEE
choiceDisabledColor=#11182799

choiceTextColor=#E0E7FF
choiceHoverTextColor=#FFFFFF
choiceSelectedTextColor=#FFFFFF
choiceDisabledTextColor=#64748B

choiceBorderColor=#3B82F6
choiceHoverBorderColor=#93C5FD
choiceSelectedBorderColor=#DBEAFE
choiceDisabledBorderColor=#334155
choiceBorderWidth=2
choiceCornerRadius=10

choiceFontFamily=SansSerif
choiceFontSize=19
choiceFontWeight=NORMAL
choiceTextXAlign=0.0
```

The four states communicate availability and focus independently. Do not rely on color alone: selected and disabled states should also differ in brightness, border, prefix, or artwork.

---

## Exercise Enabled and Disabled Choices

```vns
@var clues = 1

@label question
narrator: Which route should we take?

> Enter the station
  [jump station]
> Follow the maintenance tunnel [if clues >= 2]
  [jump tunnel]
> Wait for Iris
  [jump wait]
```

Test both sides of every condition. A disabled choice must remain readable enough to explain that an option exists while looking clearly unavailable.

---

## Add Textbox Quick Controls

Textbox controls use coordinates relative to the textbox, not the viewport:

```properties
textBoxButton.ids=auto,skip,log,save,hide

textBoxButton.auto.label=Auto
textBoxButton.auto.action=mode
textBoxButton.auto.target=auto
textBoxButton.auto.x=0.58
textBoxButton.auto.y=0.04
textBoxButton.auto.width=0.075
textBoxButton.auto.height=0.14

textBoxButton.skip.label=Skip
textBoxButton.skip.action=mode
textBoxButton.skip.target=skip
textBoxButton.skip.x=0.665
textBoxButton.skip.y=0.04
textBoxButton.skip.width=0.075
textBoxButton.skip.height=0.14

textBoxButton.log.label=Log
textBoxButton.log.action=history
textBoxButton.log.target=toggle
textBoxButton.log.x=0.75
textBoxButton.log.y=0.04
textBoxButton.log.width=0.065
textBoxButton.log.height=0.14

textBoxButton.save.label=Save
textBoxButton.save.action=save
textBoxButton.save.target=quick
textBoxButton.save.x=0.825
textBoxButton.save.y=0.04
textBoxButton.save.width=0.075
textBoxButton.save.height=0.14

textBoxButton.hide.label=Hide
textBoxButton.hide.action=ui
textBoxButton.hide.target=hide
textBoxButton.hide.x=0.91
textBoxButton.hide.y=0.04
textBoxButton.hide.width=0.075
textBoxButton.hide.height=0.14
```

Declare every ID in `textBoxButton.ids`; properties for an undeclared button do not create it.

---

## Switch to NVL Mode

NVL mode keeps several conversation entries visible:

```properties
nvlX=0.08
nvlY=0.10
nvlWidth=0.84
nvlHeight=0.72
nvlPadding=24
nvlSpeakerWidth=160
nvlEntryGap=18
nvlMaxEntries=6
nvlPanelColor=#08111ACC
nvlPanelOpacity=0.88
nvlSpeakerTextColor=#F7D89A
nvlTextColor=#E8EDF6
```

```vns
[mode dialogue nvl]
iris: First we heard the signal.
mc: Then the east clock stopped.
iris: And now the platform is empty.
[mode dialogue standard]
```

Use NVL when accumulated conversation is part of the composition. Test the configured maximum number of entries and long translated lines.

---

## Switch to Bubble Mode

```properties
bubbleWidthFactor=0.28
bubbleMinHeight=92
bubbleTextPadding=18
bubbleYOffset=26
bubbleTailSize=18
bubbleColor=#152238EE
bubbleOpacity=0.96
bubbleBorderColor=#A9BCD9
bubbleSpeakerTextColor=#FFD78A
bubbleTextColor=#F1F5FF
bubbleCornerRadius=20
bubbleBorderWidth=2
```

```vns
[mode bubble on]
[char iris bubble right]
[char iris bubble_offset 10 -8]
iris: This line stays visually attached to me.
[char iris bubble clear]
[mode bubble off]
```

Bubble placement belongs to the character's script state. The shared bubble shape and typography remain in `dialogue.layout`.

---

## Key Takeaways

1. Style normal, hover, selected, and disabled choice states deliberately.
2. Textbox buttons use fractions of the textbox rectangle.
3. NVL is a stacked dialogue presentation; bubble mode anchors speech to characters.
4. Script commands switch presentation modes while layout properties control their appearance.
5. Test conditional choices, maximum NVL history, and multi-character bubble placement.

---

## Next

Unify the visual language in [Themes, Typography, and Assets](04-themes-typography-and-assets.md).

[Back to UI By Example](../ui-by-example.md)

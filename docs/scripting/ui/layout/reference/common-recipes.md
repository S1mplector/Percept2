# Common Layout Recipes

These recipes are small source patterns, not complete themes. Copy only the properties relevant to
the owning file and validate the result in runtime.

## Right-side main menu

```properties
# layouts/default.layout
listXCenter=0.80
listYStart=0.38
listWidthFactor=0.30
lineHeight=54
textAlign=left
titleX=0.08
titleAlign=left
```

This composition keeps the left side available for character or logo artwork.

## Centered submenu

```properties
extends=default
listXCenter=0.50
listYStart=0.32
listWidthFactor=0.48
textAlign=center
titleX=0.50
titleAlign=center
```

## Background per screen

```properties
# menus/chapters.menu
backgroundAsset=assets/backgrounds/chapter_select.png
```

The screen background overrides the style-level background. Keep shared backgrounds in a style and
use screen backgrounds only for intentional exceptions.

## Image-backed button states

```properties
# styles/default.style
buttonAsset=assets/ui/menu/button.png
buttonHoverAsset=assets/ui/menu/button_hover.png
buttonSelectedAsset=assets/ui/menu/button_selected.png
buttonDisabledAsset=assets/ui/menu/button_disabled.png
buttonTextPaddingX=24
buttonTextPaddingY=0
```

Use images with identical dimensions so state changes do not move surrounding content.

## Explicit item bounds

```properties
# menus/main.menu
item.start.boundsX=0.66
item.start.boundsY=0.40
item.start.boundsWidth=0.26
item.start.boundsHeight=0.07
```

Always declare all four values. Prefer the list layout for conventional vertical menus; explicit
bounds are appropriate when artwork dictates non-list placement.

## Open another menu

```properties
item.extras.label=Extras
item.extras.action=open_menu
item.extras.target=extras
```

Register `extras` and create `menus/extras.menu`. Use `back` inside the destination when it should
return to the calling screen.

## Run a story script

```properties
item.prologue.label=Prologue
item.prologue.action=run_script
item.prologue.target=scripts/story/prologue_sample.vns
```

Use exact, project-relative paths. Test the transition from a packaged build.

## Minimal monochrome style

```properties
itemColor=#333333
itemHoverColor=#111111
itemSelectedColor=#000000
itemDisabledColor=#999999
itemSelectedPrefix=—
itemFontFamily=SansSerif
itemFontSize=24
titleColor=#111111
titleFontFamily=SansSerif
titleFontWeight=BOLD
titleFontSize=44
backgroundColor=#FAFAF7
backgroundOpacity=1.0
```

## Choices beside a left-side character

```properties
# config/ui/dialogue.layout
choiceXCenter=0.72
choiceWidthFactor=0.46
choiceHeight=48
choiceGap=10
characterHeightFactor=0.85
characterBaselineY=1.0
```

Test the largest expected number of choices and the longest localized choice text.

## Compact dialogue panel

```properties
textBoxX=0.04
textBoxY=0.78
textBoxWidth=0.92
textBoxHeight=0.18
dialogueTextHorizontalPadding=24
dialogueTextTopPadding=32
dialogueTextBottomPadding=12
```

Do not reduce the panel until long dialogue and speaker names have been tested.

## Textbox quick actions

```properties
textBoxButton.ids=history,menu

textBoxButton.history.label=History
textBoxButton.history.action=history
textBoxButton.history.enabled=true

textBoxButton.menu.label=Menu
textBoxButton.menu.action=settings_menu
textBoxButton.menu.enabled=true
```

Add geometry and assets according to the [textbox action button reference](../components/textbox-action-buttons.md).

## Related pages

- [DSL cookbook](layout-dsl-cookbook.md)
- [Menu actions](../structure/menu-actions.md)
- [Dialogue layout](../components/dialogue-layout.md)
- [Production review checklist](production-review-checklist.md)

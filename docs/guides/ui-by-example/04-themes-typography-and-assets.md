# UI By Example — Themes, Typography, and Assets

Create a shared visual language that survives dialogue, menus, hover states, localization, and asset failures.

**Difficulty:** Beginner
**Time:** 20 minutes
**Concepts:** palette roles, alpha, font fallbacks, state styling, project-relative assets

---

## Start with Semantic Roles

Define a small palette on paper before distributing values across files:

| Role | Example | Used for |
|---|---|---|
| Surface | `#0B1220E8` | Textboxes and panels |
| Raised surface | `#172554F2` | Buttons and cards |
| Primary text | `#F8FAFC` | Dialogue and labels |
| Muted text | `#94A3B8` | Hints and disabled labels |
| Accent | `#FCD34D` | Names and selected items |
| Focus | `#60A5FA` | Hover borders and focus indication |
| Danger | `#F87171` | Destructive actions |

JVN accepts hex colors with optional alpha. `#0B1220E8` is translucent; `#0B1220` is opaque.

---

## Apply the Theme to Dialogue

```properties
# config/ui/dialogue.layout
textBoxColor=#0B1220E8
nameBoxColor=#172554F2
nameTextColor=#FCD34D
dialogueTextColor=#F8FAFC

choiceBackgroundColor=#172554F2
choiceHoverColor=#1E3A8AF2
choiceSelectedColor=#1D4ED8F2
choiceDisabledColor=#0F172AB3
choiceTextColor=#F8FAFC
choiceHoverTextColor=#FFFFFF
choiceSelectedTextColor=#FFFFFF
choiceDisabledTextColor=#94A3B8
choiceBorderColor=#334155
choiceHoverBorderColor=#60A5FA
choiceSelectedBorderColor=#BFDBFE

dialogueTextFontFamily=SansSerif
dialogueTextFontSize=20
nameTextFontFamily=SansSerif
nameTextFontSize=20
nameTextFontWeight=BOLD
choiceFontFamily=SansSerif
choiceFontSize=19
```

---

## Apply the Theme to Menus

```properties
# config/menu/styles/default.style
itemColor=#F8FAFC
itemSelectedColor=#FCD34D
itemDisabledColor=#64748B
itemSelectedPrefix=▶
itemFontFamily=SansSerif
itemFontSize=25

titleColor=#F8FAFC
titleFontFamily=SansSerif
titleFontSize=54

hintsColor=#94A3B8
hintsFontFamily=SansSerif
hintsFontSize=15

backgroundColor=#020617
backgroundAsset=assets/ui/menu/title-background.png
backgroundOpacity=1.0
```

One conceptual theme spans both files, but dialogue and menus remain separate runtime formats.

---

## Add Button Skins Safely

Color-only rendering is the fallback. Once it works, add assets for richer states:

```properties
buttonAsset=assets/ui/menu/button-normal.png
buttonHoverAsset=assets/ui/menu/button-hover.png
buttonSelectedAsset=assets/ui/menu/button-selected.png
buttonDisabledAsset=assets/ui/menu/button-disabled.png
```

For dialogue choices:

```properties
choiceButtonAsset=assets/ui/dialogue/choice-normal.png
choiceButtonHoverAsset=assets/ui/dialogue/choice-hover.png
choiceButtonSelectedAsset=assets/ui/dialogue/choice-selected.png
choiceButtonDisabledAsset=assets/ui/dialogue/choice-disabled.png
```

Use matching dimensions and transparent padding across state images. Otherwise a button appears to jump when its state changes.

---

## Font Strategy

Java logical fonts such as `SansSerif`, `Serif`, and `Monospaced` are reliable fallbacks. If the art direction depends on a particular typeface:

1. Confirm the runtime can load it on every target platform.
2. Verify glyph coverage for all locales.
3. Test labels at the longest translated length.
4. Increase bounds or reduce density before shrinking text below a readable size.

Do not communicate hierarchy using size alone. Weight, spacing, color, and position should reinforce it.

---

## Asset Rules

- Use paths like `assets/ui/menu/button.png`, never absolute machine paths.
- Preserve filename case exactly for packaged and Linux builds.
- Prefer transparent PNG/WebP assets for frames and buttons.
- Keep essential art away from background crop edges.
- Validate both with and without optional skin assets.
- Use polygons only when the interactive hit area genuinely differs from its rectangle.

---

## Key Takeaways

1. Design semantic palette roles before copying colors into source files.
2. Keep readable color fallbacks even when using image skins.
3. Make hover, selection, focus, and disabled states distinguishable beyond hue alone.
4. Choose fonts for platform availability and glyph coverage.
5. Use consistent, project-relative assets with matching state dimensions.

---

## Next

Turn the theme into a navigable application shell in [A Complete Menu Profile](05-complete-menu-profile.md).

[Back to UI By Example](../ui-by-example.md)

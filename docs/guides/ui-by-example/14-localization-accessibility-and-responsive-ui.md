# UI By Example — Localization, Accessibility, and Responsive Layout

Prepare a UI for translated text, different input methods, large type, and multiple viewport sizes.

**Difficulty:** Advanced
**Time:** 25 minutes
**Concepts:** locale catalogs, `i18n:` keys, source extraction, focus states, contrast, fractional geometry, viewport testing

---

## Add Named UI Strings

```properties
# config/locales/en.properties
menu.start=Begin Journey
menu.settings=Settings
menu.quit=Quit
shop.title=Platform Kiosk
shop.buy=Buy Potion
shop.leave=Leave
common.back=Back
```

```properties
# config/locales/ja.properties
menu.start=旅を始める
menu.settings=設定
menu.quit=終了
shop.title=駅の売店
shop.buy=ポーションを買う
shop.leave=立ち去る
common.back=戻る
```

Use explicit keys where the format supports them:

```properties
title=i18n:shop.title
button.buy.label=i18n:shop.buy
button.leave.label=i18n:shop.leave
```

Literal dialogue, choices, menu labels, and overlay text also participate in generated `source.<hash>` catalogs.

---

## Extract and Refresh Catalogs

From the engine workspace:

```bash
./gradlew extractJvnTranslations \
  -PjvnGameProject=/path/to/game \
  -PjvnLocale=ja \
  -PjvnEmptyMissing=true
```

Refresh an existing catalog without discarding translated values:

```bash
./gradlew updateJvnTranslations \
  -PjvnGameProject=/path/to/game \
  -PjvnLocale=ja \
  -PjvnEmptyMissing=true
```

The scanner covers VNS, menus, layouts, styles, reactive screens, and related properties sources.

---

## Design for Text Expansion

- Test the longest locale, not only the default language.
- Prefer automatic menu rows for dynamic text.
- Enable `nameBoxAutoWidth` for speaker names.
- Give help paragraphs enough `rowSpan`.
- Reserve padding before reducing font size.
- Verify glyph coverage in the selected font.
- Keep variables such as `${coins}` unchanged across translations.

Fixed item bounds are especially vulnerable to expansion. If a title screen must use them, test every supported locale and provide enough width for focus indicators.

---

## Make Interaction Perceivable

Every interactive state should be distinguishable through more than hue:

- normal versus hover: border, brightness, or asset change;
- selected/focused: prefix, outline, shape, or position cue;
- disabled: lower emphasis while preserving legibility;
- destructive: explicit label plus danger styling;
- toggled: visible state text such as `ON`/`OFF`.

Labels should remain understandable without their icons. Pointer hit areas must match visible controls, and keyboard focus order should follow the visual reading order.

---

## Test Responsive Behavior

Use at least three viewports:

| Viewport | Find |
|---|---|
| Minimum supported | Clipping, cramped targets, overlapping text |
| Reference/design | Intended composition and rhythm |
| Maximum supported | Excessive gaps and undersized pixel elements |

Fractions scale naturally, while font size, line height, and padding remain pixel values. A layout can therefore preserve its broad composition but still need size adjustments for extreme resolutions.

Also test:

- keyboard-only navigation;
- pointer edges and polygon hit tests;
- controller navigation if advertised;
- high-contrast and bright background scenes;
- Auto, Skip, history, hide UI, and save controls;
- long save names and settings values.

---

## Key Takeaways

1. Use locale catalogs for named interface vocabulary and extracted source strings.
2. Design bounds for translated text expansion and missing-glyph risk.
3. Communicate state using multiple visual cues.
4. Keep focus order aligned with visual order.
5. Test fractional and pixel geometry at minimum, reference, and maximum viewports.
6. Accessibility is a behavior test, not a palette checklist.

---

## Next

Turn these checks into a repeatable workflow in [Tooling, Diagnostics, and Shipping](15-tooling-validation-and-shipping.md).

[Back to UI By Example](../ui-by-example.md)

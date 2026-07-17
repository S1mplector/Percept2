# Production UI Review Checklist

Use this checklist before merging a layout/menu change or publishing a build. Not every project
uses every feature; mark non-applicable checks explicitly rather than silently skipping them.

## Source contract

- [ ] Layout Studio reports no syntax, duplicate-key, unknown-key, range, or deprecation warnings.
- [ ] Layout Editors reports no missing registry members, layout/style references, or targets.
- [ ] IDs match filenames, including case.
- [ ] Asset paths are project-relative and use forward slashes.
- [ ] Compatibility aliases have been replaced with current keys.
- [ ] Inherited files contain only intentional overrides.
- [ ] Custom/plugin actions identify their owner and required plugin version.

## Menu behavior

- [ ] The configured default screen opens.
- [ ] Every visible item has the intended enabled state and action.
- [ ] Every `open_menu` action reaches its target.
- [ ] Every `back` action returns to the intended previous screen.
- [ ] Every `run_script` target starts the intended script and label.
- [ ] Save, load, settings, history, and quit flows work where exposed.
- [ ] Empty, unavailable, and failure states remain navigable.
- [ ] Selection wrapping matches the screen configuration.

## Input and accessibility

- [ ] Pointer hit areas match visible controls.
- [ ] Keyboard focus order follows visual order.
- [ ] Controller navigation works if the project advertises controller support.
- [ ] Normal, hover, selected, focused, and disabled states are distinguishable.
- [ ] Text contrast is sufficient over every background used by the screen.
- [ ] Information is not communicated by color alone.
- [ ] Labels remain understandable without relying on icons.
- [ ] Animation does not prevent selection or obscure focus.

## Typography and localization

- [ ] Required fonts are bundled or intentionally use reliable platform fallbacks.
- [ ] Missing glyphs were checked for every supported locale.
- [ ] Long translations do not clip, overlap, or push essential controls off-screen.
- [ ] Right-to-left or non-Latin behavior was tested when supported by the project.
- [ ] Title, item, subtitle, hint, dialogue, and choice sizes remain readable.
- [ ] Dynamic save names, character names, and settings values fit their regions.

## Responsive rendering

Test at a minimum of three viewports:

| Viewport | Purpose | Result |
|---|---|---|
| Minimum supported | Detect clipping and cramped hit targets | |
| Reference/design | Confirm intended composition | |
| Maximum supported | Detect excessive gaps and undersized fixed-pixel elements | |

- [ ] Fractional anchors scale as intended.
- [ ] Pixel-based line heights and fonts remain proportionate.
- [ ] Background cropping/fitting preserves essential artwork.
- [ ] Explicit bounds remain aligned with their visual controls.
- [ ] Safe areas and window chrome do not obscure controls on target platforms.

## Dialogue UI

- [ ] Speaker name, dialogue, and textbox actions fit the standard textbox.
- [ ] Choice count extremes remain usable.
- [ ] Disabled and selected choice states are readable.
- [ ] NVL mode handles the maximum configured entry count.
- [ ] Bubble mode remains associated with the intended character.
- [ ] Hide UI, history, auto, skip, quick save, and quick load interactions still work where enabled.
- [ ] Character framing does not conflict with dialogue or choice placement.

## Assets and delivery

- [ ] Every referenced asset exists with exact filename case.
- [ ] No absolute developer-machine paths remain.
- [ ] Source images have appropriate dimensions and file sizes.
- [ ] Transparent assets render correctly over every intended background.
- [ ] A clean checkout/build contains all required files.
- [ ] Packaged builds were tested rather than only running from the repository.

## Review evidence

Attach or record:

- the source diff;
- Layout Studio/sidebar diagnostic status;
- runtime screenshots at tested viewports;
- the navigation paths exercised;
- platform and input devices tested;
- known limitations or intentionally deferred warnings.

## Related pages

- [Complete menu tutorial](../workflow/complete-menu-tutorial.md)
- [Text-first workflow](../workflow/text-first-layout-workflow.md)
- [Validation and diagnostics](../tooling/validation-diagnostics.md)
- [Assets and backgrounds](../styling/assets-backgrounds.md)

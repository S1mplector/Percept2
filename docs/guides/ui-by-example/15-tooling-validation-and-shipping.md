# UI By Example — Tooling, Diagnostics, and Shipping

Use JVN's text-first tools and runtime checks to take an interface from first source file to a reviewable release artifact.

**Difficulty:** Advanced
**Time:** 20 minutes
**Concepts:** Layout Editors, Layout Studio, diagnostics, source diffs, runtime authority, packaged-build review

---

## The Authoring Loop

1. Open **Layout Editors** and resolve missing registry members or references.
2. Open the source in **Layout Studio**.
3. Apply a commented template when starting a new file.
4. Fix line diagnostics before saving.
5. Use **Save and Run Runtime** (`Ctrl/Cmd+Enter`).
6. Exercise navigation, state changes, assets, and input in the real renderer.
7. Review the source diff.
8. Repeat at alternate viewports and locales.

The properties files are the source of truth. Editor tools assist authoring; they do not maintain a parallel visual model.

---

## Which Tool Answers Which Question?

| Question | Best surface |
|---|---|
| Is this key accepted? | Layout Studio key reference and line diagnostics |
| Is this number in range? | Runtime loader diagnostics |
| Does the referenced layout/style exist? | Layout Editors sidebar |
| Is every menu target reachable? | Sidebar graph checks plus runtime navigation |
| Does the asset resolve? | Asset utilities, then packaged runtime |
| Does the UI look correct? | Running renderer |
| What changed? | Source diff/version control |

Fix syntax first, cross-file references second, behavior third, and presentation last. One malformed property can create several misleading downstream symptoms.

---

## Common Diagnostic Repairs

| Diagnostic | Repair |
|---|---|
| Unknown key | Check spelling and the active file type |
| Duplicate key | Remove one declaration; properties loading otherwise keeps the later value |
| Invalid or clamped number | Use the documented range and rerun |
| Missing layout/style | Create it, register it, or fix the ID |
| Missing menu target | Correct `open_menu` target or register the screen |
| Circular inheritance | Break one `extends` edge |
| Partial bounds | Set X, Y, width, and height together |
| Missing asset | Use a project-relative, case-correct path |

Runtime fallback is resilience, not success. A warning that says a default was used means the authored value did not take effect.

---

## Test by UI Layer

### Dialogue

- Long names and dialogue
- Maximum choices and disabled choices
- NVL entry limit and bubble placement
- Auto, Skip, Log, Save, and Hide controls
- Character framing at supported viewports

### Menus

- Default screen and every navigation edge
- Back-stack behavior
- Normal, focused, selected, and disabled states
- Settings adjustment and dynamic labels
- Empty and populated save/load slots

### Reactive screens and Facets

- Empty, normal, long, and missing variable values
- Every `visibleIf` and `enabledIf` branch
- `show`, `call`, `hide`, timer, and returned-value paths
- Keyboard/controller access to overlay buttons

### JES and specialized surfaces

- Widget hit areas and continuous slider callbacks
- HUD behavior under camera movement
- Phone navigation, save/load, and rollback
- Locked and unlocked gallery/music states

---

## Review the Packaged Build

A repository run can hide delivery problems. Before release:

- build from a clean checkout;
- confirm all config and asset files are included;
- test on a case-sensitive filesystem;
- verify fonts and non-Latin glyphs;
- run every menu and overlay entry point;
- inspect minimum and maximum supported viewport sizes;
- confirm custom action handlers and required plugins are present;
- retain screenshots and the navigation paths tested as review evidence.

---

## Completion Checklist

- [ ] No syntax, range, duplicate-key, or deprecation diagnostics
- [ ] No missing registry, layout, style, navigation, or asset references
- [ ] Every visible action performs the intended behavior
- [ ] Every screen has a reachable escape path
- [ ] All supported input methods expose focus clearly
- [ ] Long translations and dynamic values fit
- [ ] Minimum, reference, and maximum viewports pass
- [ ] Reactive conditions and timer outcomes are tested
- [ ] Packaged builds contain exact-case assets and fonts
- [ ] Source diff and runtime evidence are ready for review

---

## Key Takeaways

1. Author UI as reviewable source and validate it in the real runtime.
2. Use each diagnostic layer for the problems it can actually prove.
3. Treat fallbacks as warnings that require repair.
4. Test lifecycle and input behavior separately from visual styling.
5. Validate the packaged artifact, not only the development checkout.

---

## Where to Go Next

You have completed UI By Example. Use the [UI authoring reference](../../scripting/ui/layout/README.md) for exhaustive property lookup, [JVN Facets](../../scripting/ui/facets.md) for the current Facet contract, and the [Production UI Review Checklist](../../scripting/ui/layout/reference/production-review-checklist.md) during release review.

[Back to UI By Example](../ui-by-example.md)

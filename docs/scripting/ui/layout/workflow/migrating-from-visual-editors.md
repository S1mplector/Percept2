# Migrating from the Former Visual Layout Editors

Older JVN releases included form-based menu/dialogue editors, approximate previews, a combined menu
workspace, and a bounds drawing canvas. Projects created with those tools already use the same text
formats, so migration does not require a file conversion.

## What was removed

- dialogue, menu-screen, menu-layout, and menu-style form editors;
- the combined screen/layout/style preview workspace;
- theme and layout preview renderers;
- the bounds drawing canvas and preview-only controls.

The runtime formats, loaders, menu registry, Layout Editors sidebar, normal source editing, asset
paths, and project files remain supported.

## First migration pass

1. Commit or back up the project before editing.
2. Open **Layout Editors** and refresh the project scan.
3. Open every customized `.menu`, `.layout`, `.style`, and `dialogue.layout` source.
4. Resolve syntax, unknown-key, and deprecation diagnostics.
5. Inspect the source diff; remove duplicate keys left by older round trips.
6. Run every affected screen at the project's supported resolutions.

Do not replace customized files with templates simply because they came from an older editor.
Templates replace the complete buffer and are intended for new baselines.

## Operation mapping

| Former operation | Current operation |
|---|---|
| Drag textbox | Edit `textBoxX`, `textBoxY`, `textBoxWidth`, `textBoxHeight` |
| Drag menu list | Edit `listXCenter`, `listYStart`, `listWidthFactor` |
| Change row spacing slider | Edit `lineHeight` |
| Pick a state color | Enter an explicit hex value in `.style` |
| Assign an image | Use Asset Utilities to write a project-relative path |
| Draw bounds | Measure and write the complete X/Y/width/height group |
| Combined preview | Save all participating files and run runtime |
| Apply preset | Copy or replace with a commented source template |
| Inspect supported fields | Use Key Reference and the DSL cookbook |

## Recovering exact values

The old editors wrote changes back into properties files. The exact last-saved values are therefore
already visible in source control. Use Git history to compare a known-good runtime state rather than
trying to reproduce a former slider position.

```bash
git diff stable -- config/menu config/ui/dialogue.layout
git log -p -- config/menu/layouts/default.layout
```

## Bounds migration

Bounds must be complete groups. For menu items:

```properties
item.gallery.boundsX=0.58
item.gallery.boundsY=0.32
item.gallery.boundsWidth=0.30
item.gallery.boundsHeight=0.08
```

For each group:

1. establish the intended design viewport;
2. convert pixel measurements to normalized values when scaling is required;
3. run at the design viewport;
4. run at the smallest and largest supported viewports;
5. confirm the hit region and rendered asset agree.

Partial groups are discarded by the runtime with a diagnostic because an incomplete rectangle has
no reliable interpretation.

## Color migration

Store explicit `#RRGGBB` or `#RRGGBBAA` values. If a color was selected visually but never saved,
recover it from a screenshot with an external color sampler, then verify contrast in runtime. Keep
normal, hover, selected, and disabled states distinguishable without relying solely on hue.

## Preview migration

The former preview could not validate actions, real input routing, installed or bundled fonts,
platform rendering, or the final viewport. Replace preview checks with a short runtime matrix:

| Pass | Check |
|---|---|
| Smoke | Screen opens with no loader errors |
| Visual | Geometry, typography, assets, and states |
| Behavior | Every action and target |
| Input | Pointer plus supported keyboard/controller paths |
| Responsive | Minimum, reference, and maximum viewport |

## Team migration

- update contribution guides and review templates to require runtime evidence;
- remove screenshots that teach deleted controls;
- link directly to source files in issues and reviews;
- keep deprecation warnings at zero;
- add parser fixtures for any project-specific extensions.

## Troubleshooting migration regressions

If a file rendered before migration but not afterward, check whether the old editor had unsaved
state, whether a deprecated alias is present, whether inheritance changed the effective value, and
whether all related sources were saved before launch. Use the runtime diagnostic text as the primary
evidence; do not compensate for one invalid property by adjusting unrelated geometry.

## Related pages

- [Layout authoring tools](../tooling/layout-editor-tools.md)
- [Validation and diagnostics](../tooling/validation-diagnostics.md)
- [Production review checklist](../reference/production-review-checklist.md)

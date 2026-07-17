# Layout Authoring Model

This page explains the boundaries of JVN's UI system: which file owns which decision, how the
runtime resolves those files, and what the editor can and cannot prove before launch.

## One source model

JVN does not compile a visual-editor document into a separate runtime format. The `.menu`,
`.layout`, `.style`, `.registry`, and dialogue layout files in the project are the runtime inputs.
Layout Studio is a source editor with contract-aware assistance around those inputs.

This gives the workflow four useful properties:

- a Git diff describes the actual runtime change;
- headless tests can load the same source as the game;
- editor upgrades cannot silently rewrite an opaque document model;
- third-party tools can produce valid sources without depending on editor internals.

## Separation of responsibilities

| Concern | Owner | Examples |
|---|---|---|
| Discovery | `menu.registry` | default screen and registered IDs |
| Screen content | `.menu` | titles, item order, actions, targets |
| Geometry | `.layout` | anchors, widths, spacing, alignment |
| Presentation | `.style` | typography, colors, state assets |
| Dialogue UI | `dialogue.layout` | textbox, name, choices, NVL, bubbles, quick actions |
| Rendering and behavior | Runtime | font resolution, input, navigation, animation |

When a change seems to require the same property in several files, first ask whether the concern is
in the right layer. Moving a list belongs in its layout; changing a selected color belongs in its
style; adding a destination belongs in its screen.

## Resolution sequence

At launch, the menu loader:

1. locates the registry and discovers registered or conventional source files;
2. resolves layout, style, and screen inheritance;
3. parses values using runtime defaults and compatibility aliases;
4. validates types, ranges, actions, and bounds groups;
5. builds a `MenuProfile` and validates cross-profile references;
6. exposes diagnostics before the renderer consumes the profile.

Layout Studio calls the same single-source validation entry points for menu sources. Dialogue
sources are passed through `VnUiLayoutLoader`. The sidebar then adds checks requiring project-wide
knowledge, such as whether a target file exists.

## Defaults, omission, and inheritance

Omitting a property is different from writing an arbitrary zero or empty value. Omission preserves
the runtime or inherited value; an explicit property requests an override.

Use inheritance for a meaningful family of screens:

```properties
# layouts/submenu.layout
extends=default
listXCenter=0.5
titleAlign=center
```

Keep inherited files short. Repeating the complete parent makes later theme changes harder and
obscures the few values that make the child distinct.

## Coordinates and measurement

JVN combines normalized and pixel measurements:

- normalized values such as `listXCenter=0.75` follow viewport dimensions;
- pixel values such as `lineHeight=52` preserve readable rhythm and type scale;
- optional explicit bounds must define X, Y, width, and height together.

Design against a declared viewport range. A layout that works at one screenshot size is not yet a
responsive layout.

## Authority by question

| Question | Authority |
|---|---|
| Is the syntax parseable? | Layout Studio/runtime loader diagnostics |
| Is this key supported? | Runtime loader key catalog |
| Does this target exist? | Layout Editors cross-file validation |
| Which value wins? | Inheritance and loader resolution rules |
| Does it render correctly? | Running project |
| Does input/navigation work? | Running project |
| Is the change reviewable? | Source-control diff |

## Compatibility policy

Compatibility aliases are accepted only as migration aids. The runtime emits a deprecation warning
before their removal in an incompatible language/specification version. Current examples:

| Deprecated | Replacement |
|---|---|
| `listWidth` | `listWidthFactor` |
| `layoutId` | `layout` |

Do not introduce deprecated keys into new sources. Treat warnings as scheduled work, not harmless
noise.

## Extension safety

Plugins may introduce actions or additional data. Keep extension keys namespaced where the
extension contract permits it, document the owning plugin and version in a nearby comment, and add
a fixture that loads the source with that plugin enabled. Core diagnostics should never be disabled
globally merely to accommodate one extension.

## Related pages

- [Text-first workflow](text-first-layout-workflow.md)
- [Complete menu tutorial](complete-menu-tutorial.md)
- [Validation and diagnostics](../tooling/validation-diagnostics.md)
- [Menu inheritance](../structure/menu-inheritance.md)
- [DSL cookbook](../reference/layout-dsl-cookbook.md)

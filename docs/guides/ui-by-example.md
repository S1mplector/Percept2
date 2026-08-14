# UI By Example

A progressive, source-backed tutorial series for building interfaces across Java Vector Nexus. It covers every public UI authoring layer: VN dialogue presentation, menu profiles, standard screens, reactive overlays, Facets, JES widgets, phone and gallery surfaces, localization, accessibility, and production validation.

Each chapter builds one practical interface and explains which file owns content, geometry, appearance, state, and behavior.

Source references:

- Dialogue loader: `modules/core/src/main/java/com/jvn/core/vn/ui/VnUiLayoutLoader.java`
- Menu loader: `modules/core/src/main/java/com/jvn/core/menu/config/MenuProfileLoader.java`
- Menu renderer: `modules/fx/src/main/java/com/jvn/fx/menu/MenuRenderer.java`
- Reactive overlays and Facets: `modules/core/src/main/java/com/jvn/core/vn/ui/`
- VN renderer: `modules/fx/src/main/java/com/jvn/fx/vn/VnRenderer.java`
- JES runtime: `modules/scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`

---

## Prerequisites

- A working JVN project ([Getting Started](getting-started.md))
- Comfort editing UTF-8 properties files
- Basic VNS knowledge for story-bound overlays ([VNS By Example](vns-by-example.md))
- Java knowledge only for chapters 6 and 12, where custom callbacks are introduced

Read chapters 1–5 in order if this is your first JVN interface. Later chapters can be followed by system, but chapters 9–11 assume you understand VNS variables and screen calls.

---

## Chapters

### Foundations

| # | Chapter | What You Build |
|---|---|---|
| 1 | [Choose the Right UI System](ui-by-example/01-choose-the-right-ui-system.md) | A project UI map and a minimal interface in each authoring layer |
| 2 | [Dialogue, Name Boxes, and Character Framing](ui-by-example/02-dialogue-and-character-framing.md) | A complete ADV dialogue presentation |
| 3 | [Choices, Dialogue Modes, and Quick Controls](ui-by-example/03-choices-modes-and-controls.md) | Choice styling, NVL, bubbles, Auto, Skip, Log, and quick save |
| 4 | [Themes, Typography, and Assets](ui-by-example/04-themes-typography-and-assets.md) | A shared visual language for dialogue and menus |

### Menus and Standard Screens

| # | Chapter | What You Build |
|---|---|---|
| 5 | [A Complete Menu Profile](ui-by-example/05-complete-menu-profile.md) | Registry, main screen, layout, and style files |
| 6 | [Navigation, Scripts, and Custom Actions](ui-by-example/06-navigation-and-actions.md) | Nested menus, back-stack navigation, script launches, and a Java callback |
| 7 | [Layouts, Inheritance, and Bespoke Buttons](ui-by-example/07-layouts-inheritance-and-buttons.md) | Reusable layout/style variants and explicitly placed buttons |
| 8 | [Settings, Save/Load, and Help](ui-by-example/08-settings-save-load-and-help.md) | Production versions of JVN's standard system screens |

### Reactive and Composable UI

| # | Chapter | What You Build |
|---|---|---|
| 9 | [Reactive Overlay Screens](ui-by-example/09-reactive-overlay-screens.md) | A variable-driven shop prompt with conditional actions |
| 10 | [Facet Fundamentals](ui-by-example/10-facet-fundamentals.md) | A nested companion status card using text, image, group, and bar nodes |
| 11 | [Advanced Facets and Reusable Presentation](ui-by-example/11-advanced-facets.md) | Conditional panels, timers, localization, returned values, and motif staging |

### Runtime and Production UI

| # | Chapter | What You Build |
|---|---|---|
| 12 | [JES HUDs and Interactive Widgets](ui-by-example/12-jes-huds-and-widgets.md) | A gameplay HUD with buttons, a slider, and Java handlers |
| 13 | [Phone, Gallery, and Music Room Surfaces](ui-by-example/13-specialized-surfaces.md) | A skinned story phone plus unlockable extras |
| 14 | [Localization, Accessibility, and Responsive Layout](ui-by-example/14-localization-accessibility-and-responsive-ui.md) | A translatable interface tested across input methods and viewports |
| 15 | [Tooling, Diagnostics, and Shipping](ui-by-example/15-tooling-validation-and-shipping.md) | A repeatable source-to-runtime review and release workflow |

---

## The UI Systems at a Glance

| Need | JVN system | Primary source |
|---|---|---|
| VN textbox, name box, choices, NVL, bubbles | Dialogue layout | `config/ui/dialogue.layout` |
| Main, pause, settings, save/load, help, extras | Menu profile | `config/menu/**` |
| Small story-state prompt or notification | Reactive screen | `config/screens/<id>.screen` |
| Nested freeform story overlay | Facet | `config/facets/<id>.facet` |
| HUD or controls inside a JES scene | JES components | `game/**/*.jes` |
| Story phone data and skin | Phone properties | `config/phone/phone.properties` |
| Unlockable CGs and tracks | Gallery registries | `config/gallery/*.properties` |

These systems intentionally share state and lifecycle where appropriate, but they are not interchangeable. Chapter 1 gives the decision rule before any large example is introduced.

---

## Reference Documentation

- [In-Game UI and Menu Authoring](../scripting/ui/layout/README.md)
- [Menu Profiles](../scripting/ui/menus/menu-profiles.md)
- [Dialogue Layout](../scripting/ui/layout/components/dialogue-layout.md)
- [Reactive Overlay Screens](../scripting/ui/menus/reactive-screens.md)
- [JVN Facets](../scripting/ui/facets.md)
- [JES UI Widgets](../scripting/jes/gameplay/jes-ui-widgets.md)
- [VNS Localization](../scripting/vns/runtime/vns-localization.md)
- [Production UI Review Checklist](../scripting/ui/layout/reference/production-review-checklist.md)

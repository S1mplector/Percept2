# UI By Example — Advanced Facets and Reusable Presentation

Turn the companion card into a reusable UI contract with conditions, localization, timed behavior, and coordinated scene animation.

**Difficulty:** Advanced
**Time:** 30 minutes
**Concepts:** node conditions, overlay visibility, localization, timed returns, variable contracts, Facets with Puppeteer motifs

---

## Add Conditional Nodes

Extend the card's node list:

```properties
nodes=content,portrait,details,name,relationship_label,relationship,warning,ready

node.warning.type=text
node.warning.parent=details
node.warning.text=i18n:companion.relationship.low
node.warning.visibleIf=relationship_ratio < 0.25
node.warning.x=0.00
node.warning.y=0.73
node.warning.width=1.00
node.warning.height=0.20

node.ready.type=text
node.ready.parent=details
node.ready.text=i18n:companion.relationship.ready
node.ready.visibleIf=relationship_ratio >= 0.75
node.ready.x=0.00
node.ready.y=0.73
node.ready.width=1.00
node.ready.height=0.20
```

Only nodes whose `visibleIf` expression is true render. The entire Facet can also use `visibleIf` when its existence depends on story state.

---

## Localize the Contract

```properties
# config/locales/en.properties
companion.card.title=Companion
companion.relationship=Relationship
companion.relationship.low=Spend more time together first.
companion.relationship.ready=Ready for a new route.
companion.invite=Invite
common.close=Close
```

Reference named keys from the Facet:

```properties
title=i18n:companion.card.title
node.relationship_label.text=i18n:companion.relationship
button.invite.label=i18n:companion.invite
button.close.label=i18n:common.close
```

Literal strings also participate in source-text extraction, but explicit keys are useful for shared interface vocabulary.

---

## Add a Timed Decision

```properties
timer=7000
timerAction=return
timerTarget=close
```

When the timer expires, the modal call resumes with `close`. Always select a safe default that leaves the story in a valid state.

For a passive toast, use `timerAction=hide` instead.

---

## Define the Variable Contract

Treat reusable Facet variables like a small API:

| Variable | Type | Meaning |
|---|---|---|
| `companion_name` | string | Display name |
| `companion_portrait` | asset path | Portrait image |
| `relationship_points` | number | Player-facing score |
| `relationship_ratio` | number | Normalized bar value |

Initialize every required value before the first call. Use a consistent prefix to avoid accidental collisions with unrelated story state.

---

## Stage the Scene with a Motif

A Facet cannot animate its individual nodes. Animate the surrounding scene separately with a Puppeteer motif:

```jes
motif inspect_focus(target, x=640, zoom=1.12, duration=320) {
  move "${target}" {
    x: ${x}
    dur: ${duration}
    easing: ease_out_cubic
  }
  cameraZoom {
    zoom: ${zoom}
    dur: ${duration}
    easing: ease_out_cubic
  }
  wait ${duration}
}

timeline {
  use inspect_focus(target="iris")
}
```

Call the registered timeline, then the Facet:

```vns
[call jes_timeline companion_inspect_focus]
[screen call companion_card]
```

The motif is expanded into ordinary timeline actions before playback. The Facet remains live and reactive at runtime. They coordinate at the call site but do not import one another.

---

## Current Facet Boundaries

Facets currently provide four visual node types and standard overlay buttons. They do not currently provide:

- node-level click or hover actions;
- automatic row, column, grid, or wrap layout;
- loops over collections;
- reusable subcomponents or Facet imports;
- per-node typography, borders, or backgrounds;
- scrolling, text input, sliders, or drag-and-drop;
- animation attached directly to a node.

Use menu profiles for navigable lists, JES for bespoke gameplay interaction, and surrounding motifs for animation.

---

## Migration Pattern

A `.screen` with one interpolated body can become a Facet without changing its VNS call site:

```vns
[screen show status]
```

Create `config/facets/status.facet`, retain the overlay and button fields, and replace the body with nodes. Facet discovery precedes ordinary screen files, so remove or deliberately retain the old file according to the intended precedence.

---

## Key Takeaways

1. Conditions can control individual nodes, buttons, or the entire overlay.
2. Use explicit localization keys for shared UI vocabulary.
3. Timed calls need a safe `timerTarget` return value.
4. Document each reusable Facet's required variable contract.
5. Coordinate animations outside the Facet; node animation is not part of the current contract.
6. Choose another UI layer when the interaction exceeds Facet boundaries.

---

## Next

Build controls owned by a gameplay scene in [JES HUDs and Interactive Widgets](12-jes-huds-and-widgets.md).

[Back to UI By Example](../ui-by-example.md)

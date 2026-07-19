# Reusable Authoring With Facets And Motifs

Facets and Puppeteer Motifs solve the same broad authoring problem in different domains: they let a project name and reuse intent instead of repeating low-level declarations.

- A **Facet** names a reactive UI composition: groups, text, images, bars, and overlay buttons.
- A **Motif** names a parameterized animation composition: moves, fades, camera actions, waits, audio cues, events, and other timeline actions.

They are complementary, but they are not interchangeable. A Facet becomes a live overlay at runtime. A motif disappears during source expansion and becomes ordinary timeline actions before playback.

## Mental Model

```text
story variables -----> .facet file -----> reactive overlay rendering
                              |
                              +---- standard screen lifecycle and buttons

motif arguments -----> motif expansion --> ordinary timeline actions
                                              |
                                              +---- TimelineData and playback
```

This distinction explains their different behavior:

| Question | Facet | Motif |
|---|---|---|
| What does it reuse? | A nested reactive UI tree | A parameterized action sequence |
| When is it resolved? | Loaded as an overlay definition; values are reevaluated while rendered | Expanded before timeline parsing |
| Does the abstraction survive at runtime? | Yes, as a Facet specification | No, only the expanded actions remain |
| How is it invoked? | `[screen show id]` or `[screen call id]` | `use name(argument=value)` inside timeline source |
| Where does state come from? | Live VNS/VN variables and localization | Invocation arguments, defaults, and surrounding timeline state |
| What owns interaction? | Standard overlay buttons | Timeline events or actions |

## Choose The Primitive By The Intent

Use a Facet when the reusable idea is a **piece of interface**:

- a relationship card;
- a quest or inventory summary;
- an inspect panel;
- a custom confirmation prompt;
- a story-variable-driven HUD panel.

Use a motif when the reusable idea is **motion or staging**:

- a character entrance;
- an emphasis pulse;
- a camera focus beat;
- a fade-and-hide transition;
- a synchronized expression and sound cue.

Use both when a scene needs a consistent presentation beat around a custom interface. Keep UI composition in the Facet and scene motion in motifs.

## Combined Example: Character Inspection

The following pattern opens a reactive character card while the scene uses a standard focus motion.

### 1. Define the Facet

Create `config/facets/character_inspect.facet`:

```properties
title=Character
width=0.56
height=0.48
modal=true
dim=true

nodes=portrait,details,name,affinity_label,affinity

node.portrait.type=image
node.portrait.value=${inspect_portrait}
node.portrait.x=0.06
node.portrait.y=0.18
node.portrait.width=0.28
node.portrait.height=0.62

node.details.type=group
node.details.x=0.39
node.details.y=0.18
node.details.width=0.55
node.details.height=0.62

node.name.type=text
node.name.parent=details
node.name.text=${inspect_name}
node.name.x=0
node.name.y=0
node.name.width=1
node.name.height=0.20

node.affinity_label.type=text
node.affinity_label.parent=details
node.affinity_label.text=Affinity
node.affinity_label.x=0
node.affinity_label.y=0.38
node.affinity_label.width=1
node.affinity_label.height=0.15

node.affinity.type=bar
node.affinity.parent=details
node.affinity.value=${inspect_affinity}
node.affinity.x=0
node.affinity.y=0.58
node.affinity.width=1
node.affinity.height=0.12

buttons=close
button.close.label=Close
button.close.action=hide
```

### 2. Define the staging motif

Keep the motif in the timeline source that uses it:

```jes
motif inspect_focus(target, x=640, zoom=1.12, duration=320) {
  move "${target}" {
    x: ${x}
    dur: ${duration}
    easing: camera_glide
  }
  cameraZoom {
    zoom: ${zoom}
    dur: ${duration}
    easing: camera_glide
  }
  wait ${duration}
}

timeline {
  use inspect_focus(target="hero")
}
```

Save or register this source as the named Puppeteer timeline `character_inspect_focus`.

### 3. Supply live data and show the UI

```vns
[set inspect_name "Lavender"]
[set inspect_portrait "assets/images/characters/lavender.png"]
[set inspect_affinity 0.72]
[call jes_timeline character_inspect_focus]
[screen call character_inspect]
```

The motif controls scene staging; the Facet controls the overlay. Updating `inspect_affinity` while the overlay is visible changes the bar on a later render without re-expanding or replaying the motif.

## Composition Boundaries

Keeping the boundary explicit makes reusable project APIs easier to understand.

### Facets do not import motifs

A `.facet` file cannot contain `motif` or `use` declarations. Facet nodes do not currently have animation hooks, timeline IDs, or node-level actions. To coordinate motion with a Facet today, run the animation from the surrounding VNS/JES flow and show or call the screen separately.

### Motifs do not define UI

A motif expands only into source accepted by the timeline parser. It cannot declare Facet nodes or create a new overlay type. It may emit a supported event for surrounding code to handle, but that event does not turn the motif into a UI component.

### Their reuse scopes differ

Facet files are discovered by screen ID from project paths. Motifs currently have source-local definitions: there is no global motif registry or cross-file motif import. A Facet can therefore be reused from many VNS call sites, while a motif must be present in each complete timeline source that invokes it.

## Design A Project Vocabulary

Treat Facet IDs and motif names as a small project-facing API.

Prefer intent-based names:

```text
Facets: relationship_card, quest_summary, item_inspect
Motifs: hero_arrival, inspect_focus, warning_shake
```

Avoid names that expose incidental implementation:

```text
Facets: panel_04, three_texts_and_bar
Motifs: move_x_then_alpha, animation_7
```

For stable authoring primitives:

1. Keep the public parameter or variable set small.
2. Give motif parameters useful defaults.
3. Use consistent variable prefixes for each Facet, such as `inspect_*`.
4. Document whether a motif advances the timeline cursor.
5. Document whether a Facet is modal and whether callers should use `show` or `call`.
6. Preserve motif-authored source when the abstraction matters; visual round trips retain expanded keyframes, not motif boundaries.

## Test The Contract

For a Facet, verify:

- discovery from the intended `config/facets/` path;
- empty, normal, and long text values;
- missing and out-of-range numeric variables;
- each conditional visibility branch;
- keyboard/controller behavior of overlay buttons;
- both the project viewport and any supported alternate aspect ratio.

For a motif, verify:

- defaults and every named override;
- starting, midpoint, and final visual state;
- cursor movement when composed before another motif;
- nested motif expansion;
- import into Puppeteer and runtime playback;
- emitted audio or script events, if any.

For a combined interaction, verify the animation and screen lifecycle independently first. Then test timing at the call site so a modal `[screen call]` does not unexpectedly block scene work that was intended to happen afterward.

## Current Extension Direction

Facets and motifs demonstrate two useful extension patterns for JVN:

- **persistent declarative composition**, where a named object remains live and reactive;
- **authoring-time composition**, where a named abstraction compiles into existing runtime primitives.

Future extension APIs can follow either model without adding parallel runtimes. Potential additions such as reusable Facet components, motif libraries, custom Facet node renderers, or animation hooks should preserve the existing screen and timeline lifecycles.

These are design directions, not current syntax. The current contracts are documented in the references below.

## Read The Full References

- [JVN Facets](../scripting/ui/facets.md) — file discovery, fields, nodes, geometry, reactivity, interaction, diagnostics, and limitations
- [Puppeteer Motifs](../editor/puppeteer/puppeteer-motifs.md) — grammar, parameters, substitution, timing, nesting, editor behavior, recipes, and limitations
- [Reactive Overlay Screens](../scripting/ui/menus/reactive-screens.md) — the lifecycle inherited by Facets
- [Puppeteer JES Timeline DSL](../editor/puppeteer/puppeteer-jes-dsl.md) — the action language produced by motif expansion

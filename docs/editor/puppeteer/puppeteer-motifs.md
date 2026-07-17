# Puppeteer Motifs

Puppeteer Motifs are named, parameterized source fragments for JVN animation timelines. A motif packages one or more existing Puppeteer/JES timeline actions behind a readable `use` invocation.

Use motifs for motion that should stay consistent across characters and scenes:

- standard character entrances and exits;
- hover, emphasis, shake, bounce, or settle beats;
- coordinated move-and-fade transitions;
- reusable camera pans and zooms;
- expression, audio, or script-event sequences;
- project-specific animation vocabulary such as `arrive`, `react_surprised`, or `focus_on`.

Motifs expand the existing Puppeteer pipeline. They are not a second animation runtime and do not introduce another keyframe format.

```text
motif definitions + use invocations
                ↓ source expansion
ordinary timeline actions
                ↓ TimelineDataParser
TimelineData tracks, keyframes, audio, and events
                ↓ TimelineRunner
existing scene/VN playback
```

Implementation:

- expansion: `modules/core/src/main/java/com/jvn/core/animation/PuppeteerMotifExpander.java`
- timeline parsing: `modules/core/src/main/java/com/jvn/core/animation/TimelineDataParser.java`
- editor export/import: `modules/editor/src/main/java/com/jvn/editor/ui/actioneditor/CodeExporter.java` and `CodeImporter.java`
- playback: `modules/core/src/main/java/com/jvn/core/animation/TimelineRunner.java`

---

## Motifs Versus Clips, Presets, And Timelines

| Feature | Best use | Stored form |
|---|---|---|
| Puppeteer preset | Quickly generate a known keyframe pattern in the editor | Editor operation/preset |
| `.clip` | Copy or share a selected keyframe range | Puppeteer clip data |
| Motif | Reuse parameterized source-level timeline actions | `motif` definition and `use` call |
| Named timeline | Register and play a complete animation sequence | `.jes` timeline/registry entry |

A motif is most useful after a visual animation has been tuned in Puppeteer and the stable, reusable portion has been extracted from its generated actions.

---

## Minimal Example

```jes
motif fade_in(target, duration=300) {
  fade "${target}" {
    alpha: 1
    dur: ${duration}
    easing: ease_out
  }
}

timeline {
  use fade_in(target="hero", duration=450)
}
```

Before timeline parsing, the source becomes equivalent to:

```jes
timeline {
  fade "hero" {
    alpha: 1
    dur: 450
    easing: ease_out
  }
}
```

The resulting fade is therefore indistinguishable from an ordinary Puppeteer action during parsing and playback.

---

## Complete Syntax

### Definition

```text
motif <name>(<parameter>, <parameter>=<default>, ...) {
  <timeline actions or motif uses>
}
```

Example:

```jes
motif arrive(target, destination=640, duration=400, easing=ease_out) {
  move "${target}" {
    x: ${destination}
    dur: ${duration}
    easing: ${easing}
  }
}
```

### Invocation

```text
use <name>(<argument>=<value>, ...)
```

Example:

```jes
use arrive(target="hero", destination=720, duration=300)
```

The complete `use` invocation must occupy its own logical line. Leading indentation is allowed and is preserved in the expanded body.

### Identifier rules

Motif names use this shape:

```text
[A-Za-z_][A-Za-z0-9_]*
```

Valid:

```text
arrive
hero_arrival
cameraFocus2
_internal_bounce
```

Invalid:

```text
2nd_arrival
hero-arrival
hero arrival
```

Parameter names should follow the same identifier convention, although expansion itself performs literal placeholder replacement.

---

## Parameters And Defaults

Parameters are declared between parentheses.

```jes
motif slide(target, x=640, y=400, duration=350) {
  move "${target}" {
    x: ${x}
    y: ${y}
    dur: ${duration}
  }
}
```

### Required-by-convention parameters

A parameter without `=` has an empty default:

```jes
motif fade_in(target, duration=300) {
```

Callers should always supply it:

```jes
use fade_in(target="hero")
```

The current expander does not raise a dedicated missing-argument diagnostic. If `target` is omitted, `${target}` is replaced with an empty string. Treat parameters without defaults as required by authoring convention.

### Default parameters

Omitted named arguments retain their declared defaults:

```jes
use slide(target="hero")
```

This uses `x=640`, `y=400`, and `duration=350` from the definition.

### Named overrides

Invocation values override defaults by matching parameter name:

```jes
use slide(target="villain", x=980, duration=600)
```

### Named arguments are the supported authoring style

Always use named arguments. The source expander internally recognizes bare comma-separated values, but they map to numeric placeholders such as `${0}` rather than parameter declaration order. Named arguments are the stable and readable contract:

```jes
# Recommended
use arrive(target="hero", duration=300)

# Do not use as normal project syntax
use arrive("hero", 300)
```

### Quoted arguments

A single matching pair of outer double quotes is removed during substitution:

```jes
use arrive(target="hero")
```

Given this body:

```jes
move "${target}" {
```

the result is:

```jes
move "hero" {
```

Numeric and keyword arguments normally remain unquoted:

```jes
use arrive(target="hero", duration=300, easing=ease_out)
```

### Commas in arguments

Argument splitting respects double-quoted strings, so this stays one argument:

```jes
use cue(message="Ready, set, go")
```

Nested expression parsing is intentionally small. Avoid unquoted values that themselves require comma-aware parsing.

---

## Placeholder Substitution

Use `${parameter}` anywhere in the motif body:

```jes
motif rotate_to(target, angle=0, duration=250, curve=ease_in_out) {
  rotate "${target}" {
    deg: ${angle}
    dur: ${duration}
    easing: ${curve}
  }
}
```

Substitution is literal text replacement performed before timeline parsing.

This means parameters may represent:

- entity names;
- numeric destinations;
- durations;
- easing names or easing expressions;
- interpolation modes;
- asset paths;
- event types and payload values;
- custom property keys and values.

Example with audio:

```jes
motif sound_beat(path, volume=1.0) {
  playAudio "${path}" {
    volume: ${volume}
    channel: sound
  }
}
```

Example with a custom property:

```jes
motif tint_to(target, channel=color.m04, value=1, duration=300) {
  property "${target}" {
    key: "${channel}"
    value: ${value}
    dur: ${duration}
  }
}
```

There is no arithmetic, type system, expression evaluation, or local variable scope in motif expansion. Compute values before passing them or expose the exact text the target timeline action expects.

---

## Supported Body Content

A motif body may contain any source accepted by the timeline parser after expansion, including:

| Category | Actions |
|---|---|
| Entity transform | `move`, `depth`, `pivot`, `rotate`, `scale`, `mirror`, `fade`, `visible` |
| Advanced numeric channels | `brightness` / `exposure`, `property` |
| Camera | `cameraMove`, `cameraZoom` |
| VN/event shortcuts | `expression`, `show`, `hide`, `replace`, `scene`, `event` |
| Audio | `playAudio` |
| Timing | `wait`, `parallel` |
| Composition | `use` another motif |

Use the exact property names documented in [Puppeteer JES Timeline DSL Reference](puppeteer-jes-dsl.md).

---

## Timeline Timing Semantics

Motifs do not have a separate clock. Their expanded actions participate in the containing timeline's normal cursor model.

### Actions at the current cursor

Timeline actions begin at the current cursor. Multiple actions without an intervening `wait` can animate together.

```jes
motif arrive(target, x=640, duration=300) {
  move "${target}" {
    x: ${x}
    dur: ${duration}
  }
  fade "${target}" {
    alpha: 1
    dur: ${duration}
  }
}
```

The move and fade share the current start time.

### Advancing time

`wait` advances the timeline cursor:

```jes
motif bounce(target, amount=1.08, duration=120) {
  scale "${target}" {
    sx: ${amount}
    sy: ${amount}
    dur: ${duration}
  }
  wait ${duration}
  scale "${target}" {
    sx: 1
    sy: 1
    dur: ${duration}
  }
}
```

The second scale begins after the first segment because the motif explicitly waits.

### Duration of a motif invocation

A motif has no declared duration of its own. The effective duration is determined by the expanded actions and cursor movement. This is important when composing motifs: a motif containing only actions but no `wait` does not automatically advance the cursor for the next invocation.

To serialize calls, put the required `wait` in the motif or between `use` lines:

```jes
timeline {
  use arrive(target="hero", duration=300)
  wait 300
  use settle(target="hero", duration=160)
}
```

---

## Composition And Nested Motifs

Motifs can invoke other motifs:

```jes
motif fade_in(target, duration=300) {
  fade "${target}" {
    alpha: 1
    dur: ${duration}
  }
}

motif move_in(target, x=640, duration=300) {
  move "${target}" {
    x: ${x}
    dur: ${duration}
    easing: ease_out
  }
}

motif hero_arrival(target, x=640, duration=300) {
  use move_in(target="${target}", x=${x}, duration=${duration})
  use fade_in(target="${target}", duration=${duration})
}

timeline {
  use hero_arrival(target="hero", x=720, duration=420)
}
```

Expansion repeats for at most 16 passes. This supports nested composition while bounding accidental recursion.

Do not create direct or indirect cycles:

```jes
# Invalid design: recursive expansion
motif pulse(target) {
  use pulse(target="${target}")
}
```

After the expansion limit, unresolved `use` text remains. The current timeline parser does not provide a motif-specific recursion diagnostic, so recursive definitions may degrade into missing animation rather than a clear runtime failure.

---

## Definition Scope And Resolution

Motif definitions are collected from the complete source passed to `TimelineDataParser`, removed, and then used to expand invocations in the remaining source.

Practical rules:

- Define motifs before the `timeline` block for readability.
- A motif can be invoked before or after its textual definition because collection happens first.
- Definitions are local to the source string being parsed; they are not automatically global across files.
- If the same motif name is defined more than once in one source, the later collected definition replaces the earlier map entry.
- An invocation of an unknown motif remains unexpanded; the current timeline parser has no dedicated unknown-motif error.

Recommended structure:

```jes
// Reusable definitions first.
motif arrive(...) {
  ...
}

motif leave(...) {
  ...
}

// One clear playback body.
timeline {
  use arrive(...)
  ...
}
```

Cross-file motif libraries or include/import directives are not part of the current contract. Keep the definitions with the timeline source that uses them, or duplicate a stable project template intentionally.

---

## Puppeteer Editor Workflow

Motifs are a source-level layer around the existing visual editor.

### Recommended workflow

1. Build and preview the animation in Puppeteer.
2. Export or register the named timeline.
3. Identify the stable action sequence that should be reused.
4. Copy that sequence into a `motif` definition.
5. Replace concrete entity names and tunable values with `${parameters}`.
6. Replace the original sequence with a `use` invocation.
7. Parse/reopen the timeline and compare playback with the original.
8. Test at least one invocation using defaults and one using overrides.

### Import behavior

The editor's code importer calls the runtime timeline parser. Motifs are expanded before `TimelineData` is constructed, so the importer receives ordinary tracks and keyframes.

This gives reliable visual playback, but it has an important round-trip consequence:

- the imported visual model represents the expanded result;
- motif boundaries, parameter declarations, and `use` calls are not stored in `TimelineData`;
- exporting that visual model again may emit ordinary timeline actions rather than reconstructing the original motif source.

Keep the motif-authored source as the canonical reusable version when preserving abstraction matters.

### Clips remain useful

Use `.clip` files when you want to move selected keyframes between editor projects. Use motifs when you want readable, parameterized source calls. They complement rather than replace one another.

---

## VNS And JES Usage

Motif expansion happens anywhere the relevant source reaches `TimelineDataParser`.

### Inline timeline used by VNS

```jes
motif nod(target, duration=100) {
  rotate "${target}" { deg: -4 dur: ${duration} easing: ease_out }
  wait ${duration}
  rotate "${target}" { deg: 0 dur: ${duration} easing: ease_in }
}

timeline {
  use nod(target="hero", duration=120)
}
```

### Named Puppeteer timeline

Store the motif definitions and invocation in the named `.jes` source registered by the Puppeteer workflow. VNS continues to call the resulting timeline by its normal name; motif names are compile-time/source-expansion names, not `TimelineRegistry` IDs.

```vns
[call jes_timeline hero_reaction]
```

---

## Reusable Recipes

### Move and fade in

```jes
motif enter(target, x=640, duration=360, curve=ease_out_cubic) {
  move "${target}" {
    x: ${x}
    dur: ${duration}
    easing: ${curve}
  }
  fade "${target}" {
    alpha: 1
    dur: ${duration}
    easing: ${curve}
  }
}
```

### Fade and hide

```jes
motif fade_hide(target, duration=240) {
  fade "${target}" {
    alpha: 0
    dur: ${duration}
    easing: ease_in
  }
  wait ${duration}
  visible "${target}" {
    value: 0
  }
}
```

### Emphasis pulse

```jes
motif pulse(target, scale=1.06, duration=120) {
  scale "${target}" {
    sx: ${scale}
    sy: ${scale}
    dur: ${duration}
    easing: ease_out
  }
  wait ${duration}
  scale "${target}" {
    sx: 1
    sy: 1
    dur: ${duration}
    easing: ease_in_out
  }
}
```

### Camera focus

```jes
motif focus_camera(x, y, zoom=1.20, duration=500) {
  cameraMove {
    x: ${x}
    y: ${y}
    dur: ${duration}
    easing: ease_in_out_cubic
  }
  cameraZoom {
    zoom: ${zoom}
    dur: ${duration}
    easing: ease_in_out_cubic
  }
}
```

### Expression and sound beat

```jes
motif reaction(target, expression=surprised, sound=assets/audio/sfx/gasp.ogg) {
  expression "${target}" {
    value: "${expression}"
  }
  playAudio "${sound}" {
    channel: sound
    volume: 1
  }
}
```

### Custom brightness channel

```jes
motif flash_subject(target, peak=1.35, duration=90) {
  brightness "${target}" {
    value: ${peak}
    dur: ${duration}
    easing: ease_out
  }
  wait ${duration}
  brightness "${target}" {
    value: 1
    dur: ${duration}
    easing: ease_in
  }
}
```

---

## Naming And API Design Guidelines

Treat a motif like a small project API.

Good names describe intent:

```text
arrive
leave
focus_camera
reaction_surprised
choice_emphasis
```

Less useful names expose accidental implementation:

```text
move_x_then_alpha
animation_7
thing
```

Parameter guidance:

- Put the required target first.
- Use milliseconds consistently for duration parameters.
- Prefer `duration`, `x`, `y`, `scale`, `angle`, and `curve` across the project.
- Give safe visual defaults.
- Avoid exposing every underlying property if the motif is meant to enforce a house style.
- Keep event-producing behavior clear from the motif name.

---

## Expansion Details And Limitations

The current expander is intentionally deterministic and small.

Guaranteed behavior:

- definitions and invocations are recognized at the start of a logical line after optional whitespace;
- braces inside double-quoted strings do not affect definition brace matching;
- commas inside double-quoted argument values do not split arguments;
- named invocation values override declared defaults;
- one outer pair of double quotes is removed from substituted values;
- expanded lines inherit the invocation's leading indentation;
- nested expansion is bounded to 16 passes.

Current limitations:

- no global motif registry or cross-file import;
- no overloads, namespaces, inheritance, or visibility modifiers;
- no typed parameters or compile-time type checking;
- no arithmetic, conditionals, loops, or return values;
- no variadic arguments;
- no dedicated error for missing required values, unknown arguments, unknown motifs, or recursion;
- `use` must occupy a complete line;
- motif abstractions are not preserved in `TimelineData` or reconstructed by visual export;
- argument parsing is quote-aware but is not a general nested-expression parser;
- a malformed definition with unmatched braces cannot be expanded reliably.

These boundaries should guide source authoring and diagnostics work; they should not be mistaken for supported syntax.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `use` appears to do nothing | Unknown motif or invocation not on its own line | Check the name and line shape |
| Entity track has a blank name | Required `target` argument was omitted | Pass `target="entity_id"` |
| Placeholder text reaches parsing | Argument name does not match placeholder | Compare `${name}` with the definition/call |
| Two calls animate simultaneously | Neither invocation advanced the cursor | Add `wait` inside or between motifs |
| Nested motif stops expanding | Recursion or nesting beyond 16 passes | Remove cycles and flatten excessive nesting |
| Comma breaks an argument | Comma-bearing value was not double quoted | Quote the complete value |
| Visual import works but export loses `use` | `TimelineData` stores expanded keyframes | Preserve motif source as canonical |
| Duration differs from expectation | Motif duration is derived from actions/waits | Review the timeline cursor sequence |
| Later definition unexpectedly wins | Duplicate motif name in one source | Keep one definition per name |
| Inline action formatting fails | Expanded body is not valid timeline DSL | Compare with the JES DSL reference |

---

## Testing A Motif Library

For each reusable motif, test:

1. an invocation using only defaults;
2. an invocation overriding every public parameter;
3. at least two distinct entity targets;
4. timing relative to actions before and after the invocation;
5. nested composition, if used;
6. code import into Puppeteer;
7. named runtime playback from VNS;
8. event/audio side effects, where applicable.

For a regression fixture, assert the resulting `TimelineData` track, end values, easing, and duration rather than testing only the expanded source text.

---

## Related Documentation

- [Puppeteer Overview And Architecture](puppeteer.md)
- [Puppeteer Editor Guide](puppeteer-editor-guide.md)
- [Puppeteer JES Timeline DSL Reference](puppeteer-jes-dsl.md)
- [Hand-Coding Puppeteer Timelines](../../scripting/timeline/animation/timeline-hand-coding.md)
- [Puppeteer Animation Timelines](../../scripting/timeline/animation/timeline-animation.md)
- [Timeline Scripting Overview](../../scripting/timeline/overview/timeline-scripting.md)

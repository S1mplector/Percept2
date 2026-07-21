# Animation Easing Extensions

Plugin API 1.1 introduces named, metadata-rich easing curves as JVN's first end-to-end animation extension family. A plugin can define a curve once, then use its stable ID in timeline source, runtime playback, Puppeteer imports, easing previews, and the editor easing catalog.

This is the first vertical slice of a broader animation extension model. The current public contract covers **easing curves**. Transitions, timeline actions, constraints, and procedural-motion providers are design directions, not APIs available in 1.1.

## Why Easing Is The First Extension

An easing curve has a deliberately narrow contract:

```text
normalized progress + validated parameters -> interpolated progress
```

That small boundary is enough to exercise the complete extension path:

```text
plugin manifest capability
        ↓
context.contribute().animations().easing(...)
        ↓
owned host registry and lifecycle cleanup
        ↓
JES/Puppeteer easing parser
        ↓
TimelineData keyframes
        ↓
runtime and editor preview evaluation
        ↓
Puppeteer easing discovery and search
```

No plugin receives a `TimelineRunner`, editor window, renderer, scene, or other engine-internal object. The extension operates on an immutable frame supplied by the host.

## Complete Example

### Manifest

Declare the `animation.easing` capability and require Plugin API 1.1 or newer:

```json
{
  "id": "com.example.motion",
  "name": "Example Motion",
  "version": "1.0.0",
  "jvnApi": ">=1.1.0 <2.0.0",
  "entrypoint": "com.example.motion.MotionPlugin",
  "description": "Project easing vocabulary.",
  "vendor": "Example Studio",
  "capabilities": ["animation.easing"]
}
```

### Plugin entry point

```java
package com.example.motion;

import com.jvn.plugin.api.JvnPlugin;
import com.jvn.plugin.api.PluginContext;

import static com.jvn.plugin.api.animation.AnimationEasingDefinition.easing;
import static com.jvn.plugin.api.animation.AnimationEasingDefinition.range;

public final class MotionPlugin implements JvnPlugin {
  @Override
  public void initialize(PluginContext context) {
    context.contribute().animations().easing("example.elastic-pop",
        easing("Elastic Pop")
            .description("A quick overshoot followed by a soft settle.")
            .category("Expressive")
            .documentation("https://example.com/motion/elastic-pop")
            .parameter("overshoot", 1.2, range(0.0, 3.0))
            .parameter("settle", 0.7, range(0.1, 2.0))
            .evaluate(frame -> {
              double t = frame.progress();
              double overshoot = frame.parameter("overshoot");
              double settle = frame.parameter("settle");
              return 1.0 - Math.exp(-6.0 * settle * t)
                  * Math.cos((Math.PI * 2.0 + overshoot) * t);
            }));
  }
}
```

### Timeline usage

Use the registered ID as an easing value. Named arguments accept `:` or `=`; `:` is the canonical exported form.

```jes
move "hero" {
  x: 640
  dur: 420
  easing: "example.elastic-pop(overshoot: 1.4, settle: 0.8)"
}
```

Omit the argument list to use every declared default:

```jes
easing: "example.elastic-pop"
```

The quoted form is recommended because the value includes punctuation. The timeline parser also accepts an unquoted extension ID where the surrounding grammar preserves the complete token.

## Public Authoring API

The author-facing entry point is:

```java
context.contribute().animations()
```

It intentionally separates contribution authoring from host inspection. The host still exposes a read-only-style registry view to engine integrations, while plugins should use `contribute()` for new extension families.

### `AnimationEasingDefinition.easing(label)`

Creates a fluent easing definition builder. The label is required and is displayed in authoring tools.

```java
easing("Elastic Pop")
```

### Metadata

Metadata travels with the evaluator:

| Builder method | Meaning | Current consumer |
|---|---|---|
| `description(text)` | Short explanation of the motion | Editor search and future detail surfaces |
| `category(name)` | Author-facing grouping | Puppeteer easing catalog |
| `documentation(url)` | Stable external guide | Public contract; reserved for richer editor help |
| `parameter(...)` | Named numeric control and range | Parser validation, default resolution, future controls |
| `evaluate(function)` | Completes the definition | Runtime and preview evaluation |

Metadata must describe behavior, not implementation. Prefer “Quick overshoot with a soft settle” over “Calls cosine after exponentiation.”

### Parameters

The compact overload declares a name, default, and allowed range:

```java
.parameter("overshoot", 1.2, range(0.0, 3.0))
```

The full overload adds a display label and description:

```java
.parameter(
    "overshoot",
    "Overshoot",
    "Strength of the first pass beyond the destination.",
    1.2,
    range(0.0, 3.0))
```

Parameter names are normalized to lowercase snake case. Hyphens are normalized to underscores. Defaults and range endpoints must be finite, the minimum cannot exceed the maximum, and the default must fall inside the range.

All parameters have defaults. This guarantees that a bare extension ID remains a complete easing specification and gives editor discovery a usable preview curve.

### Evaluation frame

`AnimationEasingFrame` exposes:

| Member | Contract |
|---|---|
| `progress()` | Input clamped to `[0, 1]` |
| `parameters()` | Immutable map containing every declared parameter after defaults and overrides are resolved |
| `parameter(name)` | Convenient lookup that throws for an undeclared name |

The evaluator may return values outside `[0, 1]`. Overshoot and anticipation curves depend on that behavior. It must return a finite number; a non-finite result falls back to linear progress for that evaluation.

Keep evaluation deterministic, fast, non-blocking, and free of observable side effects. It runs frequently on render/update paths and may run many times while the editor draws a preview curve.

Do not perform file access, network access, logging per frame, random-number generation, scene mutation, or allocation-heavy work inside the evaluator.

## IDs And Namespacing

Extension IDs are case-normalized and must be stable. Use at least two dot-separated segments:

```text
studio.elastic-pop
studio.motion.elastic-pop
com.example.motion.elastic-pop
```

The parser recognizes a dot-qualified ID as an extension reference. Built-in easing tokens remain unqualified, such as `linear`, `spring(...)`, and `ease_out_cubic`.

Good IDs describe a durable motion concept:

```text
acme.hero-arrival
acme.ui-soft-settle
acme.camera-drift
```

Avoid IDs tied to an implementation revision or one scene:

```text
curve-v2
scene-14-fix
test
```

Changing an ID breaks timeline source that refers to it. Treat IDs and parameter names as a project API.

## DSL Grammar

The extension form is:

```text
<qualified-id>
<qualified-id>(<name>: <number>, ...)
```

Equivalent argument separators:

```jes
easing: "studio.elastic-pop(overshoot: 1.4)"
easing: "studio.elastic-pop(overshoot=1.4)"
```

Canonical formatting uses a colon and preserves the resolved parameter set:

```text
studio.elastic-pop(overshoot: 1.4, settle: 0.7)
```

Values are numeric literals. Expressions, strings, booleans, positional arguments, nested calls, and arithmetic are not part of the 1.1 contract.

### Default resolution

Given:

```java
.parameter("overshoot", 1.2, range(0.0, 3.0))
.parameter("settle", 0.7, range(0.1, 2.0))
```

This source:

```jes
easing: "studio.elastic-pop(overshoot: 1.4)"
```

evaluates with:

```text
overshoot = 1.4
settle    = 0.7
```

Defaults are resolved while the easing specification is parsed. The resulting `EasingSpec` retains the extension ID and resolved named values through `TimelineData`, editor import, preview, and playback.

## Where Extensions Work

### Puppeteer-authored timelines

Plugin easings appear in the Puppeteer easing catalog after the editor loads the project plugin host. The catalog uses the contributed label and category and indexes the ID, description, and DSL form for search.

Selecting an extension applies its default specification. Parameterized values can be entered through the easing specification text field. Dedicated generated parameter controls are not part of the initial 1.1 editor slice.

### Hand-authored JES timelines

Every timeline action that already accepts an easing specification can use a contributed ID. This includes entity transforms, generic property channels, and camera actions handled by the shared timeline parser.

### JES scene actions

The JES scene runtime uses the same `EasingSpec` parser and evaluator. Contributed easings therefore work in action paths that already resolve their easing through that shared contract.

### VNS

VNS can use contributed easing curves through named Puppeteer/JES timelines:

```vns
[call jes_timeline hero_arrival]
```

If `hero_arrival` contains `easing: "studio.elastic-pop(...)"`, runtime playback resolves the installed plugin evaluator. The specialized easing tokens on individual legacy VNS movement commands still use their existing built-in enum contract in 1.1; they do not directly accept plugin IDs yet.

### Java engine code

Engine modules parse a script-facing specification with `EasingSpec.tryParse(...)` and evaluate it with `Easing.apply(...)`. Plugin projects should not depend on `core` merely to call their own evaluator; test the dependency-light `AnimationEasing` directly instead.

## Validation And Diagnostics

Validation happens at several layers.

### Plugin loading

Initialization fails with a plugin diagnostic when:

- the manifest omits `animation.easing` but the plugin accesses animation contributions;
- another extension already owns the same normalized ID;
- the definition or label is null;
- parameter metadata contains null or duplicate names;
- builder validation rejects a malformed range or default;
- the plugin requires an incompatible Plugin API version.

Because registrations are owned, any earlier registrations made by that plugin are removed when initialization fails.

### Script parsing

An easing specification is rejected when:

- the qualified ID is not installed;
- an argument is unknown or repeated;
- an argument is missing `:` or `=`;
- a value is not numeric or finite;
- a value falls outside its declared range.

Timeline diagnostics report the easing value as invalid through the existing easing diagnostic path. The parser does not silently bind an unknown qualified ID to a built-in curve.

### Evaluation

The evaluator is isolated at the narrow function boundary. If it throws or returns a non-finite value, JVN uses the input progress for that sample. This protects playback and previews from a single bad frame calculation.

This fallback is not a substitute for plugin tests. Repeated evaluator failures are not currently promoted to a per-frame plugin diagnostic because doing so could flood logs and damage frame pacing.

## Lifecycle And Ownership

The animation easing registry follows the normal plugin ownership rules:

1. The plugin declares `animation.easing`.
2. `initialize` registers the definition.
3. The editor or runtime exposes the live host registry to animation parsing and evaluation.
4. The extension remains available while its plugin is active.
5. Failure or shutdown removes every easing owned by that plugin.

An already-parsed `EasingSpec` retains its ID and parameters, not a strong reference to plugin code. If the plugin disappears before evaluation, the curve falls back to linear progress. Newly parsed source rejects the now-unknown ID.

This distinction makes project reload and plugin shutdown deterministic while avoiding stale evaluator instances.

## Editor Reload Behavior

The editor rebuilds its plugin host when a project opens. Project-local plugins are discovered from `<project>/plugins/`; user plugins are discovered from the normal user plugin location.

After replacing a plugin JAR:

1. reopen the project or restart the editor;
2. reopen Puppeteer so its catalog is rebuilt from the current registry;
3. verify the contributed category and label appear;
4. import or preview a timeline that uses the qualified ID.

This release does not watch plugin JARs continuously or unload arbitrary classes while an editor window is using them.

## Testing An Easing Plugin

Test the evaluator as a pure function first:

```java
AnimationEasing easing = createElasticPop();
double value = easing.evaluate(new AnimationEasingFrame(
    0.5,
    Map.of("overshoot", 1.2, "settle", 0.7)));
```

Then test these contract points:

- `progress = 0` and `progress = 1`;
- at least three intermediate samples;
- every parameter at its minimum, default, and maximum;
- intentional overshoot or anticipation extrema;
- finite output for the complete supported range;
- deterministic output for repeated frames;
- registration with and without the manifest capability;
- duplicate IDs and duplicate parameter names;
- DSL parsing with defaults and overrides;
- unknown and out-of-range arguments;
- cleanup after plugin shutdown;
- Puppeteer catalog discovery;
- runtime playback of an exported timeline.

Sample curves before release. A small discontinuity that is hard to notice numerically can be very visible in camera motion.

## Performance Guidance

An easing evaluator sits on a hot path. Prefer basic arithmetic and `Math` functions. Capture immutable constants in the evaluator closure and precompute anything independent of `frame.progress()`.

Good:

```java
double angularFrequency = Math.PI * 2.0;

.evaluate(frame -> {
  double t = frame.progress();
  return 1.0 - Math.exp(-6.0 * t) * Math.cos(angularFrequency * t);
})
```

Avoid:

```java
.evaluate(frame -> {
  Files.readString(configPath);       // blocking I/O
  context.logger().info("{}", frame); // per-frame logging
  return new Random().nextDouble();    // nondeterministic and allocates
})
```

The host supplies an immutable parameter map for safety. Evaluators should read from it, not copy or transform it on every sample.

## Compatibility Rules

Animation easing extensions require Plugin API 1.1. Use:

```json
"jvnApi": ">=1.1.0 <2.0.0"
```

Adding optional metadata or new host consumers is compatible within API 1.x. Removing a parameter, renaming an ID, narrowing an accepted range, or changing the mathematical meaning of an existing parameter can break project content even when Java binary compatibility is preserved.

For a significant behavioral redesign, register a new ID and keep the old curve available through a deprecation window.

## Current Limitations

Plugin API 1.1 deliberately stops at a complete, narrow curve contract:

- only numeric easing parameters are supported;
- editor catalog selection starts from defaults; generated parameter controls are not yet available;
- legacy per-command VNS easing enums do not accept extension IDs directly;
- evaluator failures fall back safely but do not emit per-frame diagnostics;
- plugin JAR hot replacement still requires project reopen or restart;
- extension documentation URLs are metadata but do not yet produce an editor help button;
- transitions, timeline actions, constraints, and procedural motion are not yet public contribution families;
- there is one active editor/runtime easing registry view per application process.

These boundaries keep the first API deterministic and portable. Future animation families should reuse the same ownership, metadata, validation, discovery, and DSL principles rather than introducing parallel plugin mechanisms.

## Related Documentation

- [Plugin authoring](authoring.md)
- [Extension-point reference](extension-points.md)
- [Plugin manifest reference](manifest.md)
- [Plugin API reference](api-reference.md)
- [Compatibility and security](compatibility-security.md)
- [Puppeteer JES Timeline DSL](../editor/puppeteer/puppeteer-jes-dsl.md)
- [Puppeteer Motifs](../editor/puppeteer/puppeteer-motifs.md)

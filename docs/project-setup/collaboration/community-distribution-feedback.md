# Community Distribution Feedback Notes

These notes summarize useful external feedback on JVN's dependency model, scripting layers, Java interop, and distribution strategy.

## Current Dependency Shape

JVN is not only Swing, JavaFX, and the scripting languages, but the intended runtime surface is still relatively controlled.

- Swing is part of the JDK through `java.desktop`.
- JavaFX/OpenJFX is the main UI and rendering dependency.
- VNS and JES are custom in-repo scripting DSLs, not external language standards.
- Optional language utility modules currently include Clojure and Scala support.
- The editor uses RichTextFX.
- The audio layer pulls in audio decoding/tagging libraries such as BasicPlayer, VorbisSPI, MP3SPI, jFLAC, and jaudiotagger.
- Jackson is used by the bundled audio/player integration layer.
- SLF4J/Logback handle logging.
- JUnit is used for tests.

The important product distinction is that game authors are expected to ship scripts, assets, and configuration more often than arbitrary JVM dependency graphs.

## VNS And JES As Languages

From a programming language design perspective, VNS and JES are custom external DSLs implemented by JVN.

VNS is the high-level narrative DSL. Its core abstraction is a story graph made from labels, dialogue nodes, choices, commands, variables, and jumps. It is optimized for authoring story flow: a line such as `hero: Hello` lowers into a dialogue node in the VN runtime.

JES is the lower-level 2D scene and gameplay DSL. Its core abstraction is a scene/entity/component model with timelines and event hooks. It is partly declarative, because scene/entity/component blocks describe runtime data, and partly imperative, because timelines, input bindings, and `call` actions trigger effects over time.

In short:

- VNS owns narrative flow, dialogue pacing, branching, and VN state.
- JES owns scene composition, input, camera, timelines, physics, lightweight gameplay, and UI widgets.
- Java owns the engine implementation, advanced systems, platform integration, and stable extension hooks.

## Interop Model

The interop model is a controlled host-runtime boundary.

VNS can launch or control JES:

```vns
[jes push game/minigames/arena.jes label after_arena with difficulty=hard round=2]
[jes replace game/scenes/credits.jes]
[jes call spawnWave count=5]
[jes pop]
```

JES can return to VNS with payload data:

```jes
call "return" { label: "after_arena" score: 1200 rank: "A" }
```

The bridge pops the JES scene, copies return props into VNS variables, and jumps to the requested VNS label.

VNS can call Java through a restricted reflection hook:

```vns
[java com.jvn.game.GameHooks#beginEncounter dragon 1]
```

JES can call Java through registered handlers:

```java
scene.registerCall("spawnWave", props -> {
  // Custom Java gameplay/system logic.
});
```

```jes
call "spawnWave" { count: 5 speed: 120 }
```

This keeps the author-facing languages small while still allowing deeper Java systems where needed.

## Existing Distribution Paths

The larger Gradle tasks are mostly distribution and release orchestration.

`dist-runtime-all` builds self-contained desktop zips for each supported target, for games made in the JVN engine. Those zips include:

- bundled game files
- JVN engine/runtime jars
- third-party jars
- target-specific JavaFX native jars
- launcher scripts
- metadata/readme files
- a bundled Eclipse Temurin runtime

This means players do not need Java installed.

`release-native` is the current-host native packaging path. It uses `jlink` plus `jpackage` to create a native app image or installer for the host OS, then can run release-profile hooks such as signing, notarization, or publish commands.

Supported native package families are host-bound:

- macOS: `app-image`, `dmg`, `pkg`
- Windows: `app-image`, `exe`, `msi`
- Linux: `app-image`, `deb`, `rpm`

## Feedback Theme: Fixed Runtime Surface

One useful observation is that a visual novel engine usually does not expect game authors to add random JVM libraries.

That makes JVN different from a general Java application template. Most game projects should be scripts, assets, fonts, audio, images, and configuration on top of a relatively fixed engine runtime. Java interop exists, but it should feel like controlled hooks and extensions rather than every game shipping an arbitrary Maven graph.

This suggests JVN can optimize distribution around:

- a fixed or semi-fixed engine runtime
- visible game project assets and scripts
- stable Java hook points
- predictable packaging
- fewer requirements for authors to understand Gradle dependency management

## Feedback Theme: Self-Contained Engine Distribution

The current exported-game flow already bundles a runtime for players. A related idea is to do the same for the authoring side: distribute the whole JVN engine/editor with a bundled JDK, so authors do not need to install Java 21 before launching the hub or editor.

That would make an SDK-style distribution possible:

```text
JVN/
|-- bin/
|-- engine/
|-- editor/
|-- runtime/
|-- bundled-jdk/
`-- bundled-tooling/
```

The launcher would point all internal Java and Gradle calls at the bundled runtime. This would reduce first-run setup friction.

## Feedback Theme: jlink Friction

The hard part is that the full authoring/development environment is not the same as the fixed game runtime.

Exported games are comparatively simple:

- known runtime entry point
- known runtime dependencies
- known JavaFX native target
- no expectation that players compile Clojure, Scala, or Java code

The full engine/editor/dev side is messier:

- Gradle itself is dynamic and classpath-heavy.
- Gradle plugins use their own classloader behavior.
- Clojure and Scala support introduce language runtimes and tooling.
- Compiler/toolchain behavior does not fit as cleanly into a small `jlink` image.

This suggests a split model:

- use `jlink`/`jpackage` for the launcher, editor, runtime, and exported games
- treat Gradle plus language toolchains as bundled external tooling or an optional dev pack
- avoid making every dev tool part of one linked runtime image

## Feedback Theme: jmod As A Packaging Primitive

`jlink` operates on modules. The JDK's own modules are commonly distributed as `jmod` files, which can carry more than normal jars.

Unlike jars, jmods can contain:

- classes
- resources
- native libraries
- launcher scripts
- legal files
- man pages

This matters because a module-first ecosystem could make runtime assembly cleaner. Instead of libraries hiding native binaries inside jars and extracting them at runtime, native code could be represented directly in the packaged module artifact.

For JVN, this is worth watching because JavaFX and other desktop/game libraries often have platform-native pieces.

## Feedback Theme: Native Library Packaging

Current Java ecosystem practice for native dependencies is often awkward. Libraries may put `.dll`, `.so`, or `.dylib` files inside jars and extract them at runtime before loading them.

This works, but it is not ideal:

- each library tends to solve extraction/loading differently
- temporary native extraction can be brittle
- platform classifiers and native jars complicate packaging
- distribution tooling has to know which native jars belong to which target

JVN already handles this in its desktop bundle path by separating target-specific JavaFX native jars under `lib/javafx/`. A stronger module/jmod ecosystem could make that less ad hoc over time.

## Feedback Theme: Future Single-Executable jlink Output

There is also background JDK work worth tracking around producing a single self-contained executable from a linked runtime.

The interesting distinction is:

- GraalVM native-image can produce a single binary, but it imposes closed-world constraints and restricts some dynamic JVM behavior.
- A future `jlink`-style single executable could preserve normal JVM semantics while giving users a simpler "run this one file" distribution story.

That would be especially relevant to JVN because the engine benefits from JVM dynamism: reflection hooks, scripting, resource loading, and runtime extension points.

## Possible Next Steps

- Keep the exported game runtime path focused on fixed, predictable dependencies.
- Consider an SDK-style engine/editor distribution with a bundled JDK for authoring.
- Keep Gradle/toolchains separate from the minimal player runtime.
- Investigate whether any JVN modules should become explicit JPMS modules over time.
- Investigate where `jmod` packaging could help native/runtime distribution.
- Watch JDK work around single-executable linked runtimes.
- Keep Java interop framed as controlled extension hooks, not open-ended dependency management for every game.


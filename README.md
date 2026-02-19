# Java Vector Nexus (JVN)

<div align="left">
  <img src="docs/images/jvn_logo.png" width="512" alt="JVN logo">
</div>

JVN is a modular Java game engine focused on visual novels and 2D gameplay, with:
- VN runtime (`.vns` scripts, choices, history, save/load, interop hooks)
- JES runtime integration (for minigames/overlays)
- JavaFX and Swing rendering backends
- JavaFX editor for iterative content authoring

## Requirements

- JDK 21
- No global Gradle install required (wrapper is included)

Toolchains are configured in Gradle and can auto-download matching JDKs, but having local JDK 21 is still recommended.

## Build

```bash
./gradlew build
```

### Simp3 Backend (Optional)

Default builds work without Simp3.  
If you want to compile and run with the Simp3 backend enabled, first install Simp3 to your local Maven repo, then build with the Gradle property:

```bash
mvn -f Simp3/pom.xml install
./gradlew -PuseSimp3=true build
```

Then launch runtime with Simp3 audio explicitly:

```bash
./gradlew :runtime:run --args='--audio simp3'
```

## Run The Engine (Runtime)

Default runtime launch:

```bash
./gradlew :runtime:run
```

Run with explicit script and UI backend:

```bash
./gradlew :runtime:run --args='--script demo.vns --ui fx'
```

Run with Swing backend:

```bash
./gradlew :runtime:run --args='--script demo.vns --ui swing'
```

Run JES directly:

```bash
./gradlew :runtime:run --args='--jes <path-to-scene.jes>'
```

Use external assets from disk (overlaid on classpath assets):

```bash
./gradlew :runtime:run --args='--assets /absolute/path/to/assets --script chapter1.vns'
```

Runtime CLI flags (from `com.jvn.runtime.JvnApp`):
- `--script <name>`: default VNS script (default: `demo.vns`)
- `--ui <fx|swing>`: renderer backend (default: `fx`)
- `--jes <path[,path2...]>`: launch JES script(s) directly
- `--assets <dir>`: external asset root
- `--locale <code>`: localization code (default: `en`)
- `--audio <fx|simp3|auto>`: audio backend selection
- `--title <text>`, `--width <px>`, `--height <px>`

If a script cannot be loaded, runtime falls back to built-in demo content.

## Run The Editor

```bash
./gradlew :editor:run
```

## Useful Development Commands

Compile key app modules:

```bash
./gradlew :fx:compileJava :runtime:compileJava :editor:compileJava
```

Run unit tests:

```bash
./gradlew :core:test :scripting:test :swing:test
```

Create runtime distribution zip:

```bash
./gradlew :runtime:distZip
```

## Module Overview

- `core`: engine loop, scene system, VN model/runtime state, 2D primitives, physics
- `scripting`: JES parser/runtime
- `fx`: JavaFX renderer + launcher
- `swing`: Swing renderer + launcher
- `runtime`: app entrypoint (`JvnApp`) and runtime interop
- `editor`: JavaFX-based editor tooling
- `audio-integration`: optional Simp3-backed audio adapter

## Documentation

- `docs/Overview.md`
- `docs/Architecture/Architecture.md`
- `docs/VNS Scripting/VNS Scripting.md`
- `docs/JES Scripting/JES Scripting.md`
- `docs/Interop.md`

## License

TBD.

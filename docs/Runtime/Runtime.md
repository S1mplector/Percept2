# Runtime

The runtime app boots the engine, loads your script content, and launches the selected renderer backend.

Entrypoint:
- `runtime/src/main/java/com/jvn/runtime/JvnApp.java`

## Basic Launch

```bash
./gradlew :runtime:run
```

## Common Launch Patterns

Run specific VNS script:

```bash
./gradlew :runtime:run --args='--script demo.vns'
```

Run VNS with explicit backend:

```bash
./gradlew :runtime:run --args='--script story/prologue.vns --ui fx'
./gradlew :runtime:run --args='--script story/prologue.vns --ui swing'
```

Run JES directly:

```bash
./gradlew :runtime:run --args='--jes game/minigames/arcade.jes'
```

Run JES by merging multiple scripts:

```bash
./gradlew :runtime:run --args='--jes game/minigames/base.jes,game/minigames/overlay.jes'
```

Use project assets from disk:

```bash
./gradlew :runtime:run --args='--assets /absolute/path/to/project --script story/prologue.vns'
```

## CLI Options

- `--title <text>`: window title override
- `--width <px>`: initial width
- `--height <px>`: initial height
- `--script <name>`: startup VNS script, default `demo.vns`
- `--locale <code>`: localization key set, default `en`
- `--billiards`: attempts billiards entry flow (currently may log fallback warning)
- `--ui <fx|swing>`: rendering backend, default `fx`
- `--jes <path[,path2...]>`: load JES scene(s) directly instead of menu/VNS entry
- `--audio <fx|simp3|auto>`: audio backend preference
- `--assets <dir>`: filesystem asset root overlaid with classpath fallback

## Audio Backend Behavior

- Default mode is `auto`.
- `--audio simp3` forces Simp3 backend.
- `--audio fx` forces JavaFX backend.
- `--audio auto` tries Simp3 first, falls back to FX automatically.

## Scene Entry Behavior

When `--jes` is omitted:

1. Runtime loads user settings.
2. Builds main menu scene.
3. Main menu starts selected script or opens load/save/settings scenes.

When `--jes` is provided:

1. Runtime loads JES scene(s).
2. Attaches JES/VN bridge handlers.
3. Pushes JES scene directly.

## Asset Lookup Behavior

Runtime uses `AssetCatalog` and selected manager chain:

- classpath only by default
- filesystem overlay + classpath fallback when `--assets` is provided

This is why project-run from editor passes `--assets <projectRoot>` and a normalized script path.

## Runtime Interop Notes

Runtime extends default interop with providers:

- `jes`: push/replace/pop/call JES scenes
- `menu`: open menu scenes (main/settings/save/load/custom)
- `vns`: push/replace/goto VNS scenes/labels

See `docs/Interop.md` for provider details.

## Troubleshooting

### Gradle journal lock errors on Linux

If build startup fails with lock ping errors under `~/.gradle/caches/journal-1`:

```bash
./gradlew --stop
rm -f ~/.gradle/caches/journal-1/*.lock
./gradlew build --no-daemon --no-watch-fs
```

Also ensure `org.gradle.vfs.watch=false` remains enabled in `gradle.properties`.

### Script not loading

If a script path is wrong or not found, runtime falls back to demo scenario content.

Typical fixes:
- confirm script is reachable from configured asset root
- verify path relative to script asset namespace used by runtime
- test with `--assets <projectRoot>` for local project runs

### Simp3 backend unavailable

If `--audio simp3` is explicitly selected and Simp3 cannot initialize, switch to `--audio auto` or `--audio fx`.
In `auto`, runtime falls back to FX automatically.

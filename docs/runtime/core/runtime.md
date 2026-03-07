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
./gradlew :runtime:run --args='--script scripts/story/prologue.vns'
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

Run exactly like editor project-run (typical VN project):

```bash
./gradlew :runtime:run --args='--assets /absolute/path/to/project --script scripts/story/prologue.vns --ui fx --audio auto'
```

## CLI Options

- `--title <text>`: window title override
- `--width <px>`: initial width
- `--height <px>`: initial height
- `--script <name>`: optional startup VNS script override
- `--locale <code>`: localization key set, default `en`
- `--billiards`: attempts billiards entry flow (currently may log fallback warning)
- `--ui <fx|swing>`: rendering backend, default `fx`
- `--jes <path[,path2...]>`: load JES scene(s) directly instead of menu/VNS entry
- `--audio <fx|simp3|auto>`: audio backend preference
- `--assets <dir>`: filesystem asset root overlaid with classpath fallback

## Configure Project Entry Script

Set startup script in project root `jvn.project`:

```properties
entryVns=scripts/story/prologue.vns
```

`--script` always overrides `entryVns` for that launch.

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

If `--script` is omitted, runtime resolves the startup script in this order:

1. `jvn.project` -> `entryVns`
2. system property `jvn.entryVns`
3. discovered `.vns` under `scripts/` (prefers `prologue`, then `start`, then `main`)

Recommended path conventions for reliable loading:
- scripts: `scripts/story/prologue.vns`
- backgrounds: `assets/backgrounds/...` or `assets/demo/backgrounds/...`
- character sprites: `assets/characters/...` or `assets/demo/characters/...`
- audio: `assets/audio/{bgm,sfx,voices}/...`

## Runtime Interop Notes

Runtime extends default interop with providers:

- `jes`: push/replace/pop/call JES scenes
- `menu`: open menu scenes (main/settings/save/load/custom)
- `vns`: push/replace/goto VNS scenes/labels

See `docs/runtime/core/interop.md` for provider details.

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

If a script path is wrong or not found, runtime opens a generated "missing script" fallback scene so startup still succeeds.

Typical fixes:
- confirm script is reachable from configured asset root
- verify path relative to script asset namespace used by runtime
- test with `--assets <projectRoot>` for local project runs

### Assets load count shows zero

If runtime logs something like:

```text
Assets -> images=0, audio=0, scripts=...
```

check:
1. `--assets` points to the project root (the directory that contains `assets/` and `scripts/`).
2. Script path is project-relative (`scripts/story/prologue.vns`), not classpath-only shortcut.
3. Asset paths in VNS/menu files use project-relative paths that actually exist on disk.

### Audio continues after closing previews in editor

Runtime itself stops with process exit, but in-editor previews can outlive tab focus if not stopped explicitly.
If this is observed, close the preview and verify the editor teardown path calls scene/audio stop before disposing the preview stage.

### Simp3 backend unavailable

If `--audio simp3` is explicitly selected and Simp3 cannot initialize, switch to `--audio auto` or `--audio fx`.
In `auto`, runtime falls back to FX automatically.

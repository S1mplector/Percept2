# Build System

JVN's first packaging layer is for games made with JVN. It produces portable runtime archives that contain:

- the JVN runtime jars
- JavaFX native jars for the selected target
- the selected game project's `jvn.project`, `scripts/`, `assets/`, `config/`, and other project files
- a launcher script that starts `com.jvn.runtime.JvnApp` with `--assets` pointed at the bundled game folder

These archives are not native installers yet. They require Java 21 or newer on the player machine. Native app bundles, installers, signing, notarization, and bundled JRE images are the next packaging layer.

## Editor Popup

Open a game project in the editor, then use one of:

```text
Build -> Build & Publish...
File -> Project -> Build & Publish...
Run -> Build & Publish...
Project Explorer -> Build
```

The popup reads the current project's `jvn.project`, lets you set the release name/version, chooses a target, and launches the matching Gradle task in the run console. It also reveals the output folder and copies CLI/publish notes for release prep.

## CLI Commands

All CLI builds require `-PjvnGameProject=<dir>`.

| Command | Output |
|---------|--------|
| `./jvnw dist -PjvnGameProject=<dir>` | Game archive for the current host OS/arch |
| `./jvnw dist-all -PjvnGameProject=<dir>` | Game archives for every supported target |
| `./gradlew assembleJvnGamePortableCurrent -PjvnGameProject=<dir>` | Same as `./jvnw dist` |
| `./gradlew assembleJvnGamePortable -PjvnGameProject=<dir>` | Same as `./jvnw dist-all` |
| `./gradlew validateJvnGameProject -PjvnGameProject=<dir>` | Validate and print selected game metadata |
| `./gradlew printJvnGamePortableTargets` | List supported platform targets |

Optional properties:

| Property | Purpose |
|----------|---------|
| `-PjvnGameName=<name>` | Override the archive/launcher display name |
| `-PjvnGameVersion=<version>` | Override release version used in archive names |

Archives are written to:

```text
build/distributions/games/
```

## Targets

| Target | JavaFX classifier | Per-target Gradle task |
|--------|-------------------|------------------------|
| `windows-x64` | `win` | `assembleJvnGamePortableWindowsX64` |
| `linux-x64` | `linux` | `assembleJvnGamePortableLinuxX64` |
| `macos-x64` | `mac` | `assembleJvnGamePortableMacosX64` |
| `macos-aarch64` | `mac-aarch64` | `assembleJvnGamePortableMacosAarch64` |

Linux aarch64 is not part of the first supported set because OpenJFX 21.0.3 does not publish `linux-aarch64` classifier jars in Maven Central. Add it after upgrading the JavaFX runtime or supplying native JavaFX artifacts for that platform.

## Archive Layout

Each archive expands into a self-contained game folder:

```text
<game>-<version>-<target>/
|-- bin/
|   `-- <game-launcher>
|-- game/
|   |-- jvn.project
|   |-- scripts/
|   |-- assets/
|   `-- config/
|-- lib/
|   |-- *.jar
|   `-- javafx/
|       `-- javafx native jars for the target
`-- README.txt
```

Windows archives use `.bat` launchers. Linux and macOS archives use executable shell scripts.

The launchers keep JavaFX jars on the module path and the rest of JVN on the classpath. Runtime configuration such as `entryVns`, `width`, `height`, `runtime.ui`, `runtime.audio`, and `runtime.locale` is read from the bundled `game/jvn.project`.

## Release Workflow

1. Open the game in the editor and run it once from the editor.
2. Open `Build & Publish...`.
3. Set the release version.
4. Build the current target for smoke testing.
5. Build all targets.
6. Upload the generated zips from `build/distributions/games/`.

CLI equivalent:

```bash
./jvnw dist-all \
  -PjvnGameProject=/absolute/path/to/my-game \
  -PjvnGameVersion=1.0.0
```

## Native Packaging Roadmap

Portable game archives are the cross-host baseline. The native packaging pass should add:

- OS-matrix CI runners for Windows, Linux, and macOS.
- `jlink` runtime images for each target.
- `jpackage` outputs for `.dmg`, `.msi`, `.deb`, and `.rpm`.
- Signing and notarization hooks for release builds.
- Store/channel publish profiles.
- Linux aarch64 support after the JavaFX runtime dependency can resolve that target.

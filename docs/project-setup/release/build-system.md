# Build System

JVN's game packaging has three output layers:

- **Portable zip**: cross-target zip with runtime jars and JavaFX jars. Players still need Java 21 installed.
- **Bundled-runtime zip**: self-contained zip with a trimmed runtime image built by `jlink`. Locally this is current-host only. Players do not need Java installed.
- **Native package**: app image or installer built by `jpackage`. Locally this is current-host only. Players do not need Java installed.

Portable packaging supports `type=vn` and `type=jes` game manifests. It rejects `type=gradle` manifests and the engine workspace itself because those describe development run commands, not a distributable game.

## Editor Popup

Open a game project in the editor, then use one of:

```text
Build -> Build & Publish...
File -> Project -> Build & Publish...
Run -> Build & Publish...
Project Explorer -> Build
```

The popup reads the current project's `jvn.project`, lets you set the release name/version, target, format, native package type, and release profile, then launches the matching Gradle task in the run console. It also reveals the output folder, copies CLI/publish notes, and can run the selected release profile.

Before enabling build actions, the popup validates:

- the selected project is not the JVN engine workspace
- `jvn.project` is readable and uses `type=vn` or `type=jes`
- VN `entryVns` or JES `entry` resolves to an existing script
- the selected target is supported
- the output folder is writable

Warnings are shown for easy-to-miss cases such as a project folder with leading/trailing whitespace, a script-only package without an `assets/` folder, or missing release-profile config for native packaging.

## CLI Commands

All CLI builds require `-PjvnGameProject=<dir>`. The path is used exactly as provided, so quote paths with spaces or intentional trailing whitespace.

| Command | Output |
|---------|--------|
| `./jvnw dist -PjvnGameProject=<dir>` | Game archive for the current host OS/arch |
| `./jvnw dist-all -PjvnGameProject=<dir>` | Game archives for every supported target |
| `./jvnw dist-runtime -PjvnGameProject=<dir>` | Self-contained current-host zip with bundled runtime |
| `./jvnw native -PjvnGameProject=<dir>` | Current-host native package using the host default type |
| `./jvnw release-native -PjvnGameProject=<dir>` | Current-host native package plus release-profile hooks |
| `./gradlew assembleJvnGamePortableCurrent -PjvnGameProject=<dir>` | Same as `./jvnw dist` |
| `./gradlew assembleJvnGamePortable -PjvnGameProject=<dir>` | Same as `./jvnw dist-all` |
| `./gradlew assembleJvnGameBundledRuntimeCurrent -PjvnGameProject=<dir>` | Self-contained current-host zip |
| `./gradlew packageJvnGameNativeCurrent -PjvnGameProject=<dir>` | Current-host native package |
| `./gradlew releaseJvnGameNativeCurrent -PjvnGameProject=<dir>` | Native package + signing/notarization/publish hooks |
| `./gradlew validateJvnGameProject -PjvnGameProject=<dir>` | Validate and print selected game metadata |
| `./gradlew printJvnGamePortableTargets` | List supported platform targets |
| `./gradlew printJvnGameNativePackageTypes` | List supported native package types for this host |
| `./gradlew printJvnGameReleaseProfiles` | List available release profiles from the selected game project |

Optional properties:

| Property | Purpose |
|----------|---------|
| `-PjvnGameName=<name>` | Override the archive/launcher display name |
| `-PjvnGameVersion=<version>` | Override release version used in archive names |
| `-PjvnNativeVersion=<version>` | Override version used for native packages when installer rules require numeric versions |
| `-PjvnNativePackageType=<type>` | Override current-host native package type (`app-image`, `dmg`, `pkg`, `exe`, `msi`, `deb`, `rpm`) |
| `-PjvnReleaseProfile=<name>` | Select release profile from `jvn-release.properties` |
| `-PjvnAllowEngineWorkspacePackage=true` | Advanced escape hatch for intentionally packaging the engine workspace |

Archives are written to:

```text
build/distributions/games/
```

## Portable Targets

| Target | JavaFX classifier | Per-target Gradle task |
|--------|-------------------|------------------------|
| `windows-x64` | `win` | `assembleJvnGamePortableWindowsX64` |
| `linux-x64` | `linux` | `assembleJvnGamePortableLinuxX64` |
| `macos-x64` | `mac` | `assembleJvnGamePortableMacosX64` |
| `macos-aarch64` | `mac-aarch64` | `assembleJvnGamePortableMacosAarch64` |

Linux aarch64 is not part of the current supported set because OpenJFX 21.0.3 does not publish `linux-aarch64` classifier jars in Maven Central. Add it after upgrading the JavaFX runtime or supplying native JavaFX artifacts for that platform.

## Local Host Packaging

Local `jlink` and `jpackage` runs are still **host-bound**:

- build mac packages on macOS
- build Windows packages on Windows
- build Linux packages on Linux

Supported native package types by host:

- macOS: `app-image`, `dmg`, `pkg`
- Windows: `app-image`, `exe`, `msi`
- Linux: `app-image`, `deb`, `rpm`

## Cross-Host Native Builds

True cross-host native packaging is handled through the reusable CI matrix workflow at [native-builds.yml](../../../.github/workflows/native-builds.yml).

That workflow fans out to matching GitHub-hosted runners for:

- `linux-x64` on `ubuntu-24.04`
- `windows-x64` on `windows-2022`
- `macos-x64` on `macos-15-intel`
- `macos-aarch64` on `macos-14`

It checks out the engine, checks out the selected game repository, validates the chosen `jvn.project`, then builds:

- bundled-runtime zips
- native `app-image` bundles
- native installer packages for the target host (`deb`/`rpm`, `exe`/`msi`, `dmg`/`pkg`)

Example caller workflow inside a game repository:

```yaml
name: Release Native Builds

on:
  workflow_dispatch:
  push:
    tags:
      - "v*"

jobs:
  native:
    uses: S1mplector/Java-Vector-Nexus/.github/workflows/native-builds.yml@main
    with:
      game_project_path: .
      release_profile: release
      run_release_profile_hooks: true
    secrets: inherit
```

For monorepos, set `game_project_path` to the game folder. For manual dispatches in the engine repository, set `game_repository` to the external game repo you want to package. Use `game_version` when you need to override the version coming from `jvn.project`.

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
|-- BUILD-METADATA.txt
`-- README.txt
```

Windows archives use `.bat` launchers. Linux and macOS archives use executable shell scripts.

The launchers keep JavaFX jars on the module path and the rest of JVN on the classpath. They fail early with a clear message when Java is missing, Java is older than 21, or the bundled `game/jvn.project` file is missing.

Runtime configuration such as `entryVns`, `width`, `height`, `runtime.ui`, `runtime.audio`, and `runtime.locale` is read from the bundled `game/jvn.project`. VN builds also pass the resolved entry script to `--script`; JES builds pass the configured `entry` file to `--jes`.

## Bundled Runtime Layout

Bundled-runtime zips expand into this shape:

```text
<game>-<version>-<target>-runtime/
|-- bin/
|-- game/
|-- lib/
|-- runtime/
|-- BUILD-METADATA.txt
`-- README.txt
```

The `runtime/` directory is created with `jlink`, and the launcher runs the packaged game through `com.jvn.runtime.GamePackageLauncher`, which resolves the bundled `game/` directory automatically.

## Native Packages

Native packaging uses `jpackage` with:

- input jars from the JVN runtime modules
- the bundled `game/` directory added as app content
- a prebuilt runtime image from `createJvnGameRuntimeImageCurrent`
- `com.jvn.runtime.GamePackageLauncher` as the package entrypoint

Current-host native app versions must satisfy installer rules, so JVN derives a numeric native version automatically when the game version is a development label such as `0.1-SNAPSHOT`. Override it with `-PjvnNativeVersion=...` when needed.

## Release Profiles

Release profiles live in one of these locations inside the game project:

```text
config/release/jvn-release.properties
config/release/release.properties
release/jvn-release.properties
jvn-release.properties
```

Example:

```properties
defaultProfile=release

profile.release.vendor=Studio Name
profile.release.description=Was I Write?
profile.release.aboutUrl=https://example.com/was-i-write
profile.release.icon=packaging/icon.icns
profile.release.mac.packageIdentifier=com.example.wasiwrite
profile.release.mac.sign=true
profile.release.mac.signingIdentity=Developer ID Application: Studio Name
profile.release.mac.notarize=true
profile.release.mac.notarytoolProfile=AC_PASSWORD
profile.release.publish.command.1=butler push "{artifact}" user/game:mac
```

Supported first-pass profile categories:

- package metadata: `vendor`, `description`, `aboutUrl`, `copyright`, `licenseFile`, `icon`
- runtime tuning: `runtime.modules`
- mac packaging: `mac.packageIdentifier`, `mac.packageName`, `mac.sign`, `mac.signingIdentity`, `mac.keychain`, `mac.notarize`, `mac.notarytoolProfile`, `mac.entitlements`, `mac.appStore`
- Windows packaging/signing: `win.dirChooser`, `win.menu`, `win.shortcut`, `win.perUserInstall`, `win.console`, `win.sign`, `win.signtool`, `win.certificateFile`, `win.certificatePasswordEnv`, `win.subjectName`, `win.timestampUrl`
- Linux packaging: `linux.shortcut`, `linux.packageName`, `linux.appCategory`, `linux.debMaintainer`, `linux.rpmLicenseType`
- publish hooks: `publish.command.<n>`

## Release Workflow

1. Open the game in the editor and run it once from the editor.
2. Open `Build & Publish...`.
3. Set the release version.
4. Build the current target for smoke testing.
5. Smoke-test the current host locally with either a bundled-runtime zip or a native package.
6. Run the cross-host native workflow when you need Windows, Linux, macOS Intel, and macOS Apple Silicon artifacts from one control point.
7. Run the release profile when signing, notarization, or publish commands are configured.
8. Upload the generated artifacts from `build/distributions/games/` or from the CI artifact set.

CLI equivalent:

```bash
./jvnw dist-runtime \
  -PjvnGameProject=/absolute/path/to/my-game \
  -PjvnGameVersion=1.0.0
```

## Remaining Gaps

What is still not solved:

- local `jpackage` remains host-bound; cross-host native packaging depends on CI or self-hosted matching runners
- Linux aarch64 remains unsupported
- platform-store specific publishing still needs profile command templates and external tooling
- mac signing/notarization and Windows signing require real local credentials/tools; JVN now provides hooks, not credentials

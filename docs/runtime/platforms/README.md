# Platform Runtime Status

This page describes what the repository can build today. A Java module that
compiles on the desktop is not necessarily a deployable platform runtime.

## Support Matrix

| Target | Module | Current status | Shipping output |
|---|---|---|---|
| JavaFX desktop | `fx`, `runtime` | Supported primary runtime | Portable zip, bundled-runtime zip, native `jpackage` output |
| Swing desktop | `swing`, `runtime` | Supported secondary renderer with a smaller scene surface | Same desktop launch process; select with `--ui swing` |
| Android | `android-runtime` | Scaffold only | None; no APK or AAB task |
| iOS | `ios-runtime` | Scaffold only | None; no Xcode project, framework, app, or IPA task |
| Browser | `web-runtime` | Executable Canvas 2D bootstrap; not game-ready | Static HTML + TeaVM JavaScript via `webDist` |

The project toolchain is Java 21. The desktop release system is the only
production packaging path currently present in this repository.

## Supported Desktop Workflow

Run a project through the JavaFX runtime:

```bash
./jvnw runtime -- --assets /path/to/game
```

Use Swing instead:

```bash
./jvnw runtime -- --assets /path/to/game --ui swing
```

Build desktop packages with the tasks documented in
[Build System](../../project-setup/release/build-system.md). The repository
supports portable packages that require Java on the player machine,
self-contained bundled-runtime zips, and host-native `jpackage` output.

Swing is an alternate AWT renderer, not a Java 8 compatibility mode. It uses
the same Java 21 engine build and currently renders `Scene2D`; it does not have
the JavaFX runtime's full menu/VN renderer registry.

## Scaffold And Preview Modules

The Android and iOS modules remain architectural experiments with unresolved
platform stubs and JVM-only Gradle validation.

Consequently, these commands validate JVM-compilable scaffold code only:

```bash
./gradlew :android-runtime:test
./gradlew :ios-runtime:test
```

They do not create or run a mobile application.

The web module has moved beyond that state. It applies TeaVM 0.15, uses typed
browser bindings, builds a static bootstrap, and smoke-tests its generated
JavaScript:

```bash
./gradlew :web-runtime:test
./gradlew :web-runtime:webDist
./gradlew :web-runtime:webSmoke
```

The output boots an engine loop and Canvas 2D frame, but does not yet render or
package a normal JVN game.

## Gates Before Advertising Platform Support

Android needs an Android Gradle Plugin application/library module, real Android
SDK types and lifecycle integration, implemented Canvas/input/audio/storage
bridges, device tests, and signed APK/AAB packaging.

iOS needs a selected Java/AOT toolchain, implemented UIKit/CoreGraphics
bindings, an Xcode project and lifecycle bridge, input/audio/storage support,
device tests, signing, and app/IPA packaging.

Web still needs a render adapter for the VN/menu scene family, project
asset/script packaging, input/audio/storage support, and real cross-browser
tests. The TeaVM compiler, compatible logging path, typed browser bindings,
animation loop, and reproducible static bootstrap are now present.

See the platform pages for the exact state of each scaffold:

- [Android scaffold](android-runtime.md)
- [iOS scaffold](ios-runtime.md)
- [Web runtime bootstrap](web-runtime.md)
- [Swing desktop runtime](swing-runtime.md)

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
| Browser | `web-runtime` | Scaffold only | None; no JavaScript, WebAssembly, or static-site task |

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

## Scaffold Modules

The Android, iOS, and web modules are architectural experiments. They contain
`RenderSurface`, `Blitter2D`, factory, cache, launcher, and loop-shaped classes,
but their platform calls are unresolved native stubs. Their Gradle builds apply
only `java-library`.

Consequently, these commands validate JVM-compilable scaffold code only:

```bash
./gradlew :android-runtime:test
./gradlew :ios-runtime:test
./gradlew :web-runtime:test
```

They do not create or run a mobile/browser application.

## Gates Before Advertising Platform Support

Android needs an Android Gradle Plugin application/library module, real Android
SDK types and lifecycle integration, implemented Canvas/input/audio/storage
bridges, device tests, and signed APK/AAB packaging.

iOS needs a selected Java/AOT toolchain, implemented UIKit/CoreGraphics
bindings, an Xcode project and lifecycle bridge, input/audio/storage support,
device tests, signing, and app/IPA packaging.

Web needs a TeaVM or equivalent plugin, compatible replacements for unsupported
JVM APIs, implemented browser bindings, a render loop that actually renders
engine scenes, input/audio/storage support, browser tests, and a reproducible
static distribution task.

See the platform pages for the exact state of each scaffold:

- [Android scaffold](android-runtime.md)
- [iOS scaffold](ios-runtime.md)
- [Web scaffold](web-runtime.md)
- [Swing desktop runtime](swing-runtime.md)

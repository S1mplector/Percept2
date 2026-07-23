# iOS Runtime Scaffold

**Module:** `modules/ios-runtime/`  
**Status:** Architectural scaffold; not deployable

## What Exists

The module contains JVM-compilable shapes for a CoreGraphics-oriented backend:

- `IosRenderSurface`
- `IosRenderer` and `IosRendererFactory`
- `IosImageCache`
- `IosGameLoop`
- `IosLauncher`
- a `ServiceLoader` registration and small JVM-side tests

The implementation describes a possible boundary between JVN and UIKit /
CoreGraphics. It is not a Metal backend.

## What Does Not Exist

This repository does not currently contain:

- Multi-OS Engine or another supported Java/AOT iOS toolchain;
- an Xcode project, Swift/Objective-C bridge, UIKit SDK bindings, or Info.plist;
- implemented CoreGraphics, image, or `CADisplayLink` calls;
- touch, audio-session, or iOS storage integration;
- simulator/device tests, signing, framework, app, archive, or IPA tasks.

The platform methods are unresolved `native` stubs. Compiling this module with
the desktop Java compiler does not make those calls executable on iOS or on the
JVM.

## What You Can Run Today

```bash
./gradlew :ios-runtime:test
./gradlew :ios-runtime:compileJava
```

These commands validate scaffold code only. Commands such as
`:ios-runtime:buildIosFramework` or `:ios-runtime:compileIosJava` do not exist.

## Definition of Deployable

Before this target can be marked supported, it needs a maintained iOS
compilation strategy, real UIKit/CoreGraphics bindings, lifecycle and input,
audio and per-game storage, an Xcode/signing workflow, device tests, and
reproducible app/IPA packaging.

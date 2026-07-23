# Android Runtime Scaffold

**Module:** `modules/android-runtime/`  
**Status:** Architectural scaffold; not deployable

## What Exists

The module contains JVM-compilable shapes for:

- `AndroidRenderSurface`
- `AndroidRenderer` and `AndroidRendererFactory`
- `AndroidImageCache`
- `AndroidGameLoop`
- `AndroidLauncher`
- a `ServiceLoader` registration and small JVM-side tests

These classes help exercise the render abstraction and document a possible
Android backend boundary.

## What Does Not Exist

This repository does not currently contain:

- the Android Gradle Plugin or an Android application module;
- Android SDK dependencies;
- an `Activity`, `AndroidManifest.xml`, resources, or a real `SurfaceView`;
- implemented Canvas/Bitmap native calls;
- touch, keyboard, gamepad, MediaPlayer, or Android storage integration;
- APK/AAB assembly, signing, installation, emulator, or device-test tasks.

Several methods are declared `native` with comments describing future Android
calls. On a normal JVM—and without a real bridge implementation—calling them
results in `UnsatisfiedLinkError`. `AndroidLauncher` is a plain Java object, not
an Android `Activity`.

## What You Can Run Today

```bash
./gradlew :android-runtime:test
./gradlew :android-runtime:compileJava
```

These commands validate the scaffold as Java code. They do not build an APK,
prove Android API compatibility, or run JVN on a device.

## Definition of Deployable

Before this target can be marked supported, it needs a real Android build,
implemented platform bridges, end-to-end scene rendering and input, Android
audio and per-game storage, lifecycle handling, device tests, and a signed
APK/AAB release pipeline.

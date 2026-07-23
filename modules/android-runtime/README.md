# Android Runtime Scaffold

This module is a JVM-compilable design scaffold, not an Android application or
library artifact.

It contains renderer/surface/cache/loop/launcher shapes and service registration
for the shared render API. The Android Gradle Plugin, SDK types, Activity,
manifest, implemented native calls, input/audio/storage bridges, device tests,
and APK/AAB tasks are not present.

`./gradlew :android-runtime:test` tests the Java-side scaffold only.

See [the platform status page](../../docs/runtime/platforms/android-runtime.md).

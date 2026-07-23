# iOS Runtime Scaffold

This module is a JVM-compilable design scaffold, not an iOS framework or
application.

It contains CoreGraphics-oriented renderer/surface/cache/loop/launcher shapes.
No iOS compilation toolchain, Xcode project, implemented native bindings,
Swift/Objective-C bridge, signing, device tests, or app/IPA tasks are present.

`./gradlew :ios-runtime:test` tests the Java-side scaffold only.

See [the platform status page](../../docs/runtime/platforms/ios-runtime.md).

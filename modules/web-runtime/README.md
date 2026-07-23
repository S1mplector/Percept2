# Web Runtime Scaffold

This module is a JVM-compilable design scaffold, not a browser distribution.

It contains Canvas 2D-oriented renderer/surface/cache/loop/launcher shapes. No
TeaVM/GWT plugin, implemented browser bridge, JavaScript/WASM compilation,
browser integration tests, or static distribution task is present.

`./gradlew :web-runtime:test` tests the Java-side scaffold only.

See [the platform status page](../../docs/runtime/platforms/web-runtime.md).

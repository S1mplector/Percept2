# Platform Runtimes

Deploy JVN applications to different platforms: desktop (FX), mobile (Android/iOS), and web (browser).

---

## Platform Options

| Platform | Module | Renderer | Status |
|----------|--------|----------|--------|
| **Desktop (JavaFX)** | `modules/fx/` | JavaFX Canvas | Stable |
| **Android** | `modules/android-runtime/` | Android Canvas | Documented |
| **iOS** | `modules/ios-runtime/` | Metal/OpenGL | Documented |
| **Web** | `modules/web-runtime/` | WebGL/Canvas2D | Documented |

---

## Quick Start by Platform

### Desktop (JavaFX)
For local development and desktop distribution.
- **Min Requirements:** Java 11+
- **Setup:** Default; no extra config needed
- **Deploy:** Package as JAR or native binary (GraalVM)
- **Docs:** Covered in [Architecture Overview](../../architecture/core/overview.md)

### Android
For mobile phones/tablets running Android 5.0+ (API 21+).
- **Min Requirements:** Android SDK, Android device or emulator
- **Setup:** Gradle + AndroidManifest.xml
- **Deploy:** APK (direct install) or AAB (Play Store)
- **Docs:** [Android Runtime](android-runtime.md)

### iOS
For iPhones and iPads running iOS 12+.
- **Min Requirements:** Xcode, Mac, iOS device or simulator
- **Setup:** Gradle + Xcode project + Swift bridge
- **Deploy:** TestFlight (beta) or App Store
- **Docs:** [iOS Runtime](ios-runtime.md)

### Web
For browser-based play; no installation.
- **Min Requirements:** Modern browser (Chrome 60+, Firefox 55+, Safari 11+)
- **Setup:** Gradle + GWT transpiler
- **Deploy:** Static hosting (GitHub Pages, Netlify) or server
- **Docs:** [Web Runtime](web-runtime.md)

---

## Choosing a Platform

**For widest reach:** Web (browser, no install)  
**For mobile focus:** Android + iOS (native performance)  
**For desktop:** JavaFX (one-time download, full features)  
**For all platforms:** Implement all four (more effort, maximum reach)

---

## Common Tasks

### Building & Testing

```bash
# Desktop (JavaFX)
./gradlew :runtime:run --args='--scene path/to/game.jes'

# Android
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

# iOS
# In Xcode: Product → Run (or ⌘R)
# Requires Mac + Xcode

# Web
./gradlew gwtCompile
open war/jvn-game/index.html
```

### Packaging for Distribution

```bash
# Desktop JAR
./gradlew :app:jar

# Android APK for Play Store
./gradlew :app:bundleRelease

# iOS TestFlight
# In Xcode: Product → Archive

# Web
./gradlew gwtCompile
# Deploy war/jvn-game/ to static hosting
```

---

## Render-API Abstraction

All platforms use the same core engine; the only difference is the **renderer backend**. See [Render-API](../../architecture/core/render-api.md) for how to add a new platform.

Key classes:
- `RendererRegistry` — auto-discovers available backends
- `RendererFactory` — creates platform-specific renderers
- `RenderSurface` — window/framebuffer abstraction
- `Blitter2D` — 2D drawing interface (same for all platforms)

---

## Testing Across Platforms

1. **Desktop (JavaFX):** Fastest iteration; use for development
2. **Web:** Quick validation; copy code from desktop
3. **Android:** Test on actual device via adb
4. **iOS:** Test on simulator (Xcode) or real device

---

## Asset & Audio Format Recommendations

| Type | Desktop | Android | iOS | Web |
|------|---------|---------|-----|-----|
| **Images** | PNG | PNG | PNG | PNG/WebP |
| **Audio** | .ogg, .wav | .ogg, .mp3 | .m4a, .mp3 | .ogg, .mp3 |
| **Scripts** | .vns, .jes | .vns, .jes | .vns, .jes | .vns, .jes |

---

## Troubleshooting

### "No renderer found"
- Check platform module is on classpath
- Verify ServiceLoader config exists (see Render-API docs)

### Assets not loading
- Check asset paths are relative to app root
- For web: verify CORS headers if assets on different domain

### Audio not playing
- Check format is supported on target platform
- Verify audio file is not compressed in package (for Android/iOS)

### Performance issues
- Desktop: profile with JProfiler or YourKit
- Android: use Android Studio profiler
- iOS: use Xcode Instruments
- Web: use Chrome DevTools Performance tab

---

## Related Documentation

- [Render-API Abstraction](../../architecture/core/render-api.md)
- [System Architecture](../../architecture/core/system-architecture.md)
- [Asset Management](../systems/asset-management.md)
- [Runtime Core](../core/runtime.md)

---

**Last Updated:** May 2026

# Android Runtime Module

Native Android game runtime for Java Vector Nexus.

## Overview

Enables JVN games to run on Android devices as native applications (APK/AAB).

## Components

### AndroidRenderSurface
Wraps an Android `SurfaceView` for use as a `RenderSurface`.

```java
AndroidRenderSurface surface = new AndroidRenderSurface(context);
```

**Features:**
- Direct access to `SurfaceHolder` for frame buffering
- Hardware-accelerated rendering support
- Pixel density awareness (dips to pixels conversion)

### AndroidRenderer
Implements `Blitter2D` using Android `Canvas` API.

```java
Blitter2D renderer = new AndroidRenderer(surface, assetManager);

// Drawing operations
renderer.setFill(1.0, 0.0, 0.0, 1.0);
renderer.fillRect(10, 10, 100, 50);
renderer.drawImage("game/images/hero.png", 50, 50, 32, 32);
renderer.drawText("Score: 100", 10, 10, 14, false);
```

**Supported Features:**
- Rectangles, circles, lines, paths
- Text rendering with custom fonts
- Image/bitmap drawing (full and region-based)
- Transformations (translate, rotate, scale)
- Clipping rectangles
- State stack (push/pop)
- Alpha transparency

**Implementation Notes:**
- Uses `Paint` objects for fill/stroke styling
- Bitmap scaling handled by `Canvas.drawBitmap()` overload
- Text metrics via `Paint.measureText()`

### AndroidImageCache
Bitmap decoding and caching from asset streams.

```java
AndroidImageCache cache = new AndroidImageCache(assetManager);

// Synchronous loading (blocks until bitmap is decoded)
Object bitmap = cache.getOrLoad("game/images/hero.png");

if (bitmap != null) {
  renderer.drawImage("game/images/hero.png", 50, 50, 32, 32);
}

// Cleanup when done
cache.clear();  // Calls Bitmap.recycle() on all cached bitmaps
```

**Features:**
- Synchronous decoding via `BitmapFactory.decodeByteArray()`
- Memory-efficient caching with LRU eviction
- Automatic bitmap recycling on cache clear
- Support for common formats: PNG, JPEG, WebP, GIF

### AndroidGameLoop
Background thread-based game loop with lifecycle integration.

```java
AndroidGameLoop gameLoop = new AndroidGameLoop(engine, renderer, surface);
gameLoop.start();

// Handle app lifecycle
public void onPause() {
  gameLoop.pause();
}

public void onResume() {
  gameLoop.resume();
}

public void onDestroy() {
  gameLoop.stop();
}
```

**Features:**
- Separate rendering thread (60 FPS)
- Pause/resume support for activity lifecycle
- Exception handling and crash prevention
- FPS limiting and frame skipping

### AndroidLauncher
Entry point for Android application initialization.

```java
public class GameActivity extends AppCompatActivity {
  private AndroidLauncher launcher;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    launcher = new AndroidLauncher();
    ApplicationConfig config = new ApplicationConfig("MyGame", 800, 600);
    launcher.initialize(this, config);
  }

  @Override
  protected void onPause() {
    super.onPause();
    launcher.pause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    launcher.resume();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    launcher.destroy();
  }
}
```

**Initialization:**
1. Creates `AndroidRenderSurface` from context
2. Sets up asset manager (packed assets + classpath fallback)
3. Initializes game engine
4. Starts game loop

### AndroidRendererFactory
Service provider for renderer discovery.

```java
RendererRegistry registry = new RendererRegistry();
RendererFactory factory = registry.get("Android Canvas");
Blitter2D renderer = factory.createBlitter2D(surface);
```

## Asset Loading Strategy

Assets are loaded with the following fallback chain:

1. **Primary:** Packed assets (`.pack` file in APK)
2. **Fallback:** Classpath resources (JAR embedded in APK)

Benefits:
- Reduced APK size via compression
- Faster loading from packed format
- Transparent fallback for unpackaged assets

## Activity Integration

The launcher is designed to be used as a component in a standard Android Activity:

```java
public class MainActivity extends AppCompatActivity {
  private AndroidLauncher launcher;
  private LinearLayout gameContainer;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    gameContainer = findViewById(R.id.game_container);
    
    launcher = new AndroidLauncher();
    ApplicationConfig config = new ApplicationConfig("MyGame", getScreenWidth(), getScreenHeight());
    launcher.initialize(this, config);
  }
}
```

## Manifest Configuration

Required in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<application>
  <activity android:name=".GameActivity"
    android:screenOrientation="landscape"
    android:exported="true">
    <intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
  </activity>
</application>
```

## Build Configuration

Requires Android Gradle Plugin (pending):

```gradle
plugins {
  id 'com.android.application'
}

android {
  compileSdkVersion 34
  defaultConfig {
    targetSdkVersion 34
    minSdkVersion 24
  }
}

dependencies {
  implementation project(':android-runtime')
  implementation 'androidx.appcompat:appcompat:1.6.1'
}
```

## Performance Optimization

**Memory Management:**
- Bitmap caching with LRU eviction
- Explicit recycling on game exit
- Canvas reuse (no allocation per frame)

**Rendering:**
- Hardware acceleration via SurfaceView
- Frame rate capped at 60 FPS
- Efficient transformations via Canvas matrix

**Threading:**
- Rendering on separate thread
- Safe pause/resume during lifecycle events
- No blocking on main thread

## Input Handling

Input events (future implementation):

```java
// Keyboard
public boolean onKeyDown(int keyCode, KeyEvent event) {
  surface.onKeyDown(keyCode, event);
  return true;
}

// Touch
@Override
public boolean onTouchEvent(MotionEvent event) {
  surface.onTouchEvent(event);
  return true;
}
```

## Limitations & Future Work

**Current (Scaffolding):**
- Native method stubs (reflection-based, not compiled)
- No actual APK/AAB generation
- Input handling not yet implemented
- No camera/accelerometer support

**Next Phase:**
- [ ] Full Android Gradle Plugin integration
- [ ] APK and AAB signing and optimization
- [ ] Touch and gesture input handling
- [ ] Hardware acceleration verification
- [ ] Device orientation and screen size handling
- [ ] Notification support for background notifications
- [ ] In-app purchases and analytics

## Testing

Run tests via Gradle:

```bash
./gradlew :android-runtime:test
```

For device testing (future):
- Emulator testing via Android Studio
- Device farm testing (cloud providers)
- Performance profiling via Android Profiler

## Android API Level Support

- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)

Supported architectures:
- arm64-v8a (primary)
- armeabi-v7a (legacy)
- x86_64 (emulator)

## App Lifecycle Handling

Proper pause/resume ensures:
- Game loop stops when activity is not visible
- Rendering resources are preserved
- Assets remain cached across resume
- Battery usage minimized when paused

## Troubleshooting

**Canvas rendering issues:**
- Check hardware acceleration: `android:hardwareAccelerated="true"`
- Verify SurfaceView is properly attached to layout
- Monitor logcat for rendering exceptions

**Memory issues:**
- Monitor bitmap cache size
- Use `cache.clear()` in `onDestroy()`
- Profile with Android Profiler

**Performance issues:**
- Reduce draw call count
- Optimize bitmap sizes
- Consider software rendering as fallback

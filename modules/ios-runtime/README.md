# iOS Runtime Module

Native iOS game runtime for Java Vector Nexus using Multi-OS Engine.

## Overview

Enables JVN games to run on iOS devices as native applications (IPA) via Java-to-Objective-C compilation using Multi-OS Engine (MOE).

## Components

### IosRenderSurface
Wraps an iOS `UIView` for use as a `RenderSurface`.

```java
IosRenderSurface surface = new IosRenderSurface(window);
surface.setPixelScale(2.0);  // Retina support
```

**Features:**
- Direct access to UIView for drawing
- Device pixel ratio support (1x, 2x, 3x)
- CADisplayLink integration for frame synchronization
- Safe handling of view lifecycle events

### IosRenderer
Implements `Blitter2D` using iOS CoreGraphics API.

```java
Blitter2D renderer = new IosRenderer(surface, assetManager);

// Drawing operations
renderer.setFill(1.0, 0.0, 0.0, 1.0);
renderer.fillRect(10, 10, 100, 50);
renderer.drawImage("game/images/hero.png", 50, 50, 32, 32);
renderer.drawText("Score: 100", 10, 10, 14, false);
```

**Supported Features:**
- Rectangles, circles, arcs, paths
- Text rendering with native fonts (UIFont)
- Image/UIImage drawing (full and region-based)
- Transformations (affine matrices for translate, rotate, scale)
- Clipping to rectangles or paths
- State stack (graphics state save/restore)
- Blend modes (kCGBlendMode*)
- Gradients and patterns (future)

**Implementation Notes:**
- Uses `CGContext` for Core Graphics operations
- Coordinate system: origin at bottom-left (standard CoreGraphics)
- Colors in RGB float range [0.0, 1.0]
- Matrix transformations via `CGAffineTransform` struct

### IosImageCache
UIImage creation and caching from asset data.

```java
IosImageCache cache = new IosImageCache(assetManager);

// Synchronous decoding (blocks until UIImage is created)
Object image = cache.getOrLoad("game/images/hero.png");

if (image != null) {
  renderer.drawImage("game/images/hero.png", 50, 50, 32, 32);
}

// Cleanup
cache.clear();  // Releases all UIImage references
```

**Features:**
- Synchronous decoding via `UIImage.imageWithData()`
- Memory-efficient caching with explicit management
- Support for PNG, JPEG, WebP, GIF formats
- Automatic memory management via ARC (Automatic Reference Counting)

### IosGameLoop
CADisplayLink-based game loop synchronized to display refresh.

```java
IosGameLoop gameLoop = new IosGameLoop(engine, renderer, surface);
gameLoop.start();

// Handle app lifecycle
public void pause() {
  gameLoop.pause();
}

public void resume() {
  gameLoop.resume();
}

public void stop() {
  gameLoop.stop();
}
```

**Features:**
- CADisplayLink for 60/120 FPS synchronization (device-dependent)
- Automatic pause/resume during app lifecycle
- Efficient frame timing calculation
- Exception handling prevents app crashes

### IosLauncher
Entry point for iOS application initialization.

```java
public class GameViewController extends UIViewController {
  private IosLauncher launcher;

  public void viewDidLoad() {
    super.viewDidLoad();
    
    launcher = new IosLauncher();
    ApplicationConfig config = new ApplicationConfig("MyGame", 800, 600);
    launcher.initialize(this.view.window, config);
  }

  public void viewWillDisappear(boolean animated) {
    super.viewWillDisappear(animated);
    launcher.pause();
  }

  public void viewDidAppear(boolean animated) {
    super.viewDidAppear(animated);
    launcher.resume();
  }

  public void dealloc() {
    launcher.terminate();
  }
}
```

**Initialization:**
1. Creates `IosRenderSurface` from UIView
2. Sets up asset manager (packed assets + classpath fallback)
3. Initializes game engine
4. Starts game loop with CADisplayLink

### IosRendererFactory
Service provider for renderer discovery.

```java
RendererRegistry registry = new RendererRegistry();
RendererFactory factory = registry.get("iOS CoreGraphics");
Blitter2D renderer = factory.createBlitter2D(surface);
```

## Asset Loading Strategy

Assets are loaded with the following fallback chain:

1. **Primary:** Packed assets (`.pack` file in app bundle)
2. **Fallback:** Classpath resources (JAR in app bundle)

Benefits:
- Reduced app size via compression
- Efficient loading from packed format
- Automatic fallback for unpackaged assets

## UIView Integration

The launcher integrates with standard iOS view controllers:

```swift
import UIKit

class GameViewController: UIViewController {
    var launcher: IosLauncher!

    override func viewDidLoad() {
        super.viewDidLoad()
        
        launcher = IosLauncher()
        let config = ApplicationConfig(title: "MyGame", width: 800, height: 600)
        launcher.initialize(view.window, config: config)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        launcher.pause()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        launcher.resume()
    }

    deinit {
        launcher.terminate()
    }
}
```

## Project Configuration

Requires Multi-OS Engine plugin (pending):

```gradle
plugins {
  id 'org.moe' version '1.10.13'
}

moe {
  xcode {
    project = 'ios/MyGame.xcodeproj'
    scheme = 'MyGame'
  }
}
```

## Xcode Integration

Project structure after MOE setup:

```
MyGame/
├── ios/
│   ├── MyGame.xcodeproj/
│   └── MyGame/
│       ├── Info.plist
│       ├── LaunchScreen.storyboard
│       └── Assets.xcassets/
├── build/
│   └── moe/
│       ├── ios/
│       │   └── MyGame.app/
│       └── xcode/
│           └── build/
```

## CoreGraphics Coordinate System

Important: iOS CoreGraphics uses **bottom-left origin**, unlike typical UI frameworks:

```
(0, height) ────── (width, height)
    │                      │
    │                      │
    │                      │
(0, 0) ────────────── (width, 0)
```

To work with top-left origin (standard for games):
- Translate to (0, height)
- Scale y by -1
- Apply transformations

The renderer handles this internally.

## Device Considerations

**Screen Dimensions:**
- iPhone: 375x667, 390x844, 428x926 (various models)
- iPad: 768x1024, 834x1112, 1024x1366 (various models)

**Pixel Density:**
- iPhone: 2x (retina) or 3x (plus models)
- iPad: 2x (most models)

Set via `surface.setPixelScale(scale)` for proper rendering.

## Input Handling

Touch and gesture handling (future implementation):

```java
// Multi-touch events
public void onTouchBegin(float x, float y) {
  inputSource.onTouchEvent(x, y, true);
}

public void onTouchMove(float x, float y) {
  inputSource.onTouchEvent(x, y, true);
}

public void onTouchEnd(float x, float y) {
  inputSource.onTouchEvent(x, y, false);
}

// Accelerometer and gyro (future)
public void onMotionChanged(float accelX, float accelY, float accelZ) {
  // Handle device motion
}
```

## Performance Optimization

**Rendering:**
- CADisplayLink synchronization (no frame skipping)
- Efficient Core Graphics batching
- Vector drawing optimized for screen resolution

**Memory:**
- Image caching with explicit eviction
- View hierarchy minimization
- Background task scheduling for cleanup

**Threading:**
- Main thread for UI updates
- Game logic on separate thread
- Thread-safe renderer updates

## Limitations & Future Work

**Current (Scaffolding):**
- Native method stubs (MOE reflection-based)
- No actual IPA/archive generation
- Touch input not yet implemented
- No motion sensor support

**Next Phase:**
- [ ] Full Multi-OS Engine integration
- [ ] Xcode project generation and management
- [ ] IPA signing and distribution
- [ ] Touch and gesture recognition
- [ ] Accelerometer and gyroscope input
- [ ] Notification support
- [ ] Game Center integration (achievements, leaderboards)
- [ ] In-app purchases

## Testing

Run tests via Gradle:

```bash
./gradlew :ios-runtime:test
```

For device testing (future):
- iOS Simulator testing in Xcode
- Physical device testing via Xcode
- Cloud device farm testing (e.g., BrowserStack, TestCloud)

## iOS Version Support

- **Min Deployment Target:** iOS 13.0
- **Target SDK:** iOS 17.0

Supported architectures:
- arm64 (production)
- arm64e (A12 Bionic and later)
- x86_64 (simulator)

## App Lifecycle Management

Proper pause/resume ensures:
- Game loop pauses when app is backgrounded
- Memory is managed during app suspension
- Game state is preserved across resume
- Battery usage is optimized

## SafeArea Handling

For modern iPhones with notches/dynamic islands:

```java
// Adjust rendering viewport to avoid unsafe areas
double safeAreaInsetTop = surface.getSafeAreaInsets().top;
double safeAreaInsetBottom = surface.getSafeAreaInsets().bottom;

// Render content within safe area
renderer.fillRect(0, safeAreaInsetTop, width, height - safeAreaInsetTop - safeAreaInsetBottom);
```

## Troubleshooting

**Rendering issues:**
- Verify CADisplayLink is attached to main run loop
- Check CoreGraphics context is valid in drawRect
- Monitor Metal/OpenGL resource allocation

**Memory leaks:**
- Profile with Xcode Instruments (Allocations tool)
- Ensure UIImage references are released
- Call `cache.clear()` in dealloc

**Performance issues:**
- Profile with Xcode Instruments (Core Animation tool)
- Reduce path complexity for drawing
- Consider cached rendering for static content
- Use Metal instead of Core Graphics for GPU acceleration (future)

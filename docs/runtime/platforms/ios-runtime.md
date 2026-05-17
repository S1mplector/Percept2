# iOS Runtime

**Module:** `modules/ios-runtime/`  
**Package:** `com.jvn.ios`  
**Purpose:** Deploy JVN applications to iOS devices via Swift/Objective-C bridge

---

## Overview

The iOS runtime module enables JVN games and visual novels to run on iOS 12+. It provides:
- iOS Metal 2D renderer backend (or OpenGL ES fallback)
- Swift↔Java interop for UIKit integration
- Asset loading from app bundle and Documents folder
- AVAudioSession audio integration
- Touch and sensor input handling
- Game state persistence to device storage

---

## Architecture

### IosRendererFactory

**Location:** `modules/ios-runtime/src/main/java/com/jvn/ios/IosRendererFactory.java`

Implements `RendererFactory` to create iOS Metal/OpenGL renderer instances.

```java
public class IosRendererFactory implements RendererFactory {
  @Override public String getRendererName() { return "iOS"; }
  @Override public Renderer createRenderer(RenderConfig config) { ... }
}
```

**Called by:**
- JvnViewController (Swift UIViewController) during viewDidLoad()
- Engine initialization via RendererRegistry lookup

---

### Swift Interop

JVN core is Java; iOS UI is Swift. The bridge:

```swift
// JvnViewController.swift
import UIKit

class JvnViewController: UIViewController {
  var jvnEngine: JvnEngineWrapper?
  
  override func viewDidLoad() {
    super.viewDidLoad()
    
    // Initialize JVM on iOS
    let registry = JvnRendererRegistry()
    let factory = registry.get("iOS")
    let renderer = factory?.createRenderer(config: renderConfig)
    
    // Create engine with iOS renderer
    jvnEngine = JvnEngineWrapper(renderer: renderer!)
    
    // Attach to view hierarchy
    let gameView = renderer?.surfaceView
    self.view.addSubview(gameView!)
  }
  
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    // Forward touch to JVN input system
    jvnEngine?.onTouchDown(touch: touches.first)
  }
}
```

---

## Building for iOS

### Xcode Project Setup

1. **Create Xcode project:**
   ```bash
   mkdir -p ios-app
   cd ios-app
   swift package init --type app
   ```

2. **Add JVN dependencies:**
   Edit `Package.swift` to include JVN frameworks compiled for iOS.

3. **Gradle build for iOS:**
   ```bash
   # Compile Java to bytecode + JNI stubs
   ./gradlew :ios-runtime:compileIosJava
   
   # Generate Xcode frameworks
   ./gradlew :ios-runtime:buildIosFramework
   ```

### AndroidManifest Equivalent: Info.plist

iOS uses `Info.plist` instead of AndroidManifest. Key settings:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" 
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <!-- App Identity -->
  <key>CFBundleName</key>
  <string>My JVN Game</string>
  <key>CFBundleIdentifier</key>
  <string>com.example.mygame</string>
  <key>CFBundleVersion</key>
  <string>1</string>
  <key>CFBundleShortVersionString</key>
  <string>1.0</string>
  
  <!-- Minimum iOS Version -->
  <key>MinimumOSVersion</key>
  <string>12.0</string>
  
  <!-- Supported Device Types -->
  <key>UISupportedInterfaceOrientations</key>
  <array>
    <string>UIInterfaceOrientationLandscapeLeft</string>
    <string>UIInterfaceOrientationLandscapeRight</string>
  </array>
  
  <!-- Permissions -->
  <key>NSMicrophoneUsageDescription</key>
  <string>Recording audio for dialogue</string>
  <key>NSCameraUsageDescription</key>
  <string>Camera access if using photo features</string>
  <key>NSPhotoLibraryUsageDescription</key>
  <string>Access photo library for user-generated content</string>
</dict>
</plist>
```

**Key Settings:**
- `MinimumOSVersion = "12.0"` — minimum iOS 12 (iPhone 5s+)
- `UISupportedInterfaceOrientations` — landscape only (most games)
- Permission descriptions required for app store review

---

## Assets & Resources

### App Bundle Assets

JVN assets embedded in Xcode project:

```
ios-app.xcodeproj/
├── Assets.xcassets/
│   └── (image assets for app icon, launch screen)
└── Resources/
    ├── game/
    │   ├── scenes/
    │   │   └── main.jes
    │   └── story/
    │       └── intro.vns
    └── audio/
        └── bgm/
            └── theme.m4a
```

Added to Xcode target's "Copy Bundle Resources" phase.

### Runtime Asset Loading

```swift
// Asset paths relative to app bundle
let assetPath = Bundle.main.path(forResource: "game/scenes/main", ofType: "jes")
```

Or use JVN asset manager:
```java
AssetManager assetManager = new IosAssetManager();
InputStream stream = assetManager.load("audio/bgm/theme.m4a");
```

### iCloud/Documents Folder

For large games or user-generated content:

```swift
let documentsURL = FileManager.default.urls(
  for: .documentDirectory, 
  in: .userDomainMask
)[0]
let gameDataURL = documentsURL.appendingPathComponent("JVN")
```

---

## Audio Playback

iOS uses AVAudioSession + AVAudioPlayer or AVFoundation.

```swift
import AVFoundation

// Configure audio session for gameplay
let audioSession = AVAudioSession.sharedInstance()
try? audioSession.setCategory(
  .playback,
  mode: .default,
  options: [.duckOthers]
)
try? audioSession.setActive(true)

// JVN audio requests play via JVM
let audioManager: JvnAudioManager = ...
audioManager.play("audio/bgm/theme.m4a", volume: 0.8, loop: true)
```

**Configuration:**
- Category: `.playback` (continue even if muted)
- Options: `.duckOthers` (lower other audio when playing)
- Format support: `.m4a`, `.mp3`, `.wav`
- Sample rate: 44.1kHz or 48kHz

---

## Input Handling

### Touch Events

Multi-touch gesture recognition:

```swift
override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
  for touch in touches {
    let location = touch.location(in: self.view)
    jvnEngine?.onTouchDown(x: location.x, y: location.y)
  }
}

override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
  for touch in touches {
    let location = touch.location(in: self.view)
    jvnEngine?.onTouchMove(x: location.x, y: location.y)
  }
}
```

**Gestures:**
- Single finger drag = mouse/touch movement
- Tap = click
- Two-finger pinch = camera zoom (customizable)
- Swipe = UI navigation

### Motion/Accelerometer

For tilt controls:

```swift
import CoreMotion

let motionManager = CMMotionManager()
motionManager.startAccelerometerUpdates(to: .main) { data, error in
  let x = data?.acceleration.x ?? 0
  let y = data?.acceleration.y ?? 0
  jvnEngine?.onAccelerometerChange(x: x, y: y)
}
```

### Game Controller

For MFi game controller support:

```swift
import GameController

GCController.shouldMonitorBackgroundEvents = true
NotificationCenter.default.addObserver(
  forName: NSNotification.Name.GCControllerDidConnect,
  object: nil, queue: .main) { _ in
  if let gamepad = GCController.controllers().first?.gamepad {
    jvnEngine?.onGamepadConnected(gamepad)
  }
}
```

---

## Save & Load

Game data persists to Documents folder:

```swift
let documentsURL = FileManager.default.urls(
  for: .documentDirectory, in: .userDomainMask)[0]
let savesURL = documentsURL.appendingPathComponent("Saves")

// Create saves directory if needed
try? FileManager.default.createDirectory(
  at: savesURL, withIntermediateDirectories: true)

// Save file paths
let slot1 = savesURL.appendingPathComponent("slot1.jvnsave")
```

JVN save system abstracts to platform:
```java
VnSaveManager saveManager = ...
saveManager.save(scenario, slot: 1); // → /Documents/Saves/slot1.jvnsave
```

---

## Deployment

### TestFlight (Testing)

1. Archive app in Xcode:
   ```
   Product → Scheme → Edit Scheme → Run → Release
   Product → Archive
   ```

2. Upload to App Store Connect:
   ```
   Xcode → Window → Organizer → Archives → Distribute App
   ```

3. Select "TestFlight" → add testers → send invite

### App Store (Production)

1. **Prepare for submission:**
   - Fill app metadata (description, keywords, screenshots, privacy policy)
   - Configure pricing and availability
   - Add app categories and content ratings

2. **Build versioning:**
   ```plist
   <!-- Info.plist -->
   <key>CFBundleVersion</key>
   <string>2</string>        <!-- Build number -->
   <key>CFBundleShortVersionString</key>
   <string>1.1</string>      <!-- User-facing version -->
   ```

3. **Final submission:**
   - Run App Store Connect validation
   - Await Apple review (typically 24-48 hours)
   - App appears on App Store after approval

### Code Review Notes

Apple reviewers check:
- No private APIs used (only public Apple frameworks)
- No jailbreak or anti-jailbreak detection
- Age-appropriate content rating
- Privacy policy valid and up-to-date
- No ads or payments without disclosure

---

## Debugging

### Xcode Console

In Xcode, set breakpoints and step through Objective-C/Swift code:
```
Xcode → Debug → Breakpoints → Edit Scheme → Diagnostics
```

Enable address sanitizer and memory debugging.

### JVM Logs

Capture JVM output:
```swift
// In JvnViewController
let logPath = NSTemporaryDirectory() + "jvn.log"
freopen(logPath.cString(using: .utf8), "a+", stderr)
print("JVN Engine initialized", to: &StdErr)
```

---

## Optimization Tips

1. **Memory:** Minimize Java object allocation in frame loop
2. **Rendering:** Use Metal over OpenGL on supported devices
3. **Audio:** Pre-compress audio to .m4a (better than .wav on iOS)
4. **Scripting:** Pre-parse large .vns files; lazy-load scenes
5. **Power:** Reduce frame rate or add dynamic resolution scaling on low-end devices

---

## Related Documentation

- **Render-API:** [docs/architecture/core/render-api.md](../architecture/core/render-api.md) — backend abstraction
- **Runtime Core:** [docs/runtime/core/runtime.md](../core/runtime.md) — JvnApp integration
- **Asset Management:** [docs/runtime/systems/asset-management.md](../systems/asset-management.md) — asset resolution
- **Save System:** [docs/runtime/systems/save-system.md](../systems/save-system.md) — save/load persistence

---

**Last Updated:** May 2026  
**Minimum iOS Version:** 12.0 (iPhone 5s+)

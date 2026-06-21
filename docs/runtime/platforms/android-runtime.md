# Android Runtime

**Module:** `modules/android-runtime/`  
**Package:** `com.jvn.android`  
**Purpose:** Deploy JVN applications to Android devices and emulators

---

## Overview

The Android runtime module enables JVN games and visual novels to run on Android devices (API level 21+). It provides:
- Android Canvas 2D renderer backend
- Activity lifecycle integration
- Asset loading from APK and external storage
- Audio playback via Android MediaPlayer
- Input handling (touch, keys, gamepad)
- Save/load game data to device storage

---

## Architecture

### AndroidRendererFactory

**Location:** `modules/android-runtime/src/main/java/com/jvn/android/AndroidRendererFactory.java`

Implements `RendererFactory` to create Android Canvas renderer instances.

```java
public class AndroidRendererFactory implements RendererFactory {
  @Override public String getRendererName() { return "Android"; }
  @Override public Renderer createRenderer(RenderConfig config) { ... }
}
```

**Called by:**
- JvnActivity (Android main activity) during onCreate()
- Engine initialization via RendererRegistry lookup

---

### Activity Integration

The Android app structure:

```
com.jvn.android.JvnActivity (extends AppCompatActivity)
  ├── onCreate()
  │   ├── Create RendererRegistry
  │   ├── Look up AndroidRendererFactory
  │   ├── Create Android Canvas Renderer
  │   └── Initialize Engine with scene
  ├── onResume()
  │   └── Unpause engine/audio
  └── onPause()
      └── Pause engine/audio
```

---

## Building for Android

### Gradle Configuration

In `build.gradle.kts` (app module):

```kotlin
android {
  compileSdk = 33
  
  defaultConfig {
    applicationId = "com.example.mygame"
    minSdk = 21           // Android 5.0 Lollipop
    targetSdk = 33
    versionCode = 1
    versionName = "1.0"
  }
  
  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }
}

dependencies {
  implementation(project(":core"))
  implementation(project(":scripting"))
  implementation(project(":runtime"))
  implementation(project(":android-runtime"))
}
```

### AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.mygame">

    <!-- Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/AppTheme">
        
        <activity
            android:name=".JvnActivity"
            android:exported="true"
            android:screenOrientation="sensorLandscape"
            android:configChanges="orientation|screenSize|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**Key Permissions:**
- `READ_EXTERNAL_STORAGE` — load assets from sdcard (if not in APK)
- `WRITE_EXTERNAL_STORAGE` — save game data
- `RECORD_AUDIO` — if recording audio in-game

**Key Attributes:**
- `minSdk = 21` — minimum Android version (5.0 Lollipop)
- `screenOrientation="sensorLandscape"` — lock landscape mode, respond to sensor
- `configChanges` — prevent activity restart on orientation change

---

## Assets & Resources

### APK Asset Bundling

JVN assets (images, audio, scripts) go in:
```
src/main/assets/
├── game/
│   ├── scenes/
│   │   └── main.jes
│   └── story/
│       └── intro.vns
└── audio/
    └── bgm/
        └── theme.ogg
```

These are bundled into the APK and accessible at runtime via `assets://` prefix.

### Runtime Asset Loading

```java
// AssetManager resolves assets from:
// 1. assets/ (APK bundled)
// 2. /sdcard/JVN/ (external storage)
// 3. Context.getFilesDir() (app private storage)

AssetManager assetManager = new ClasspathAssetManager();
InputStream stream = assetManager.load("audio/bgm/theme.ogg");
```

### External Storage Fallback

For large games, assets can be downloaded post-install:
```
/sdcard/Android/data/com.example.mygame/files/
├── assets/
│   ├── images/
│   └── audio/
```

AssetManager checks external storage if asset not found in APK.

---

## Audio Playback

JVN audio on Android uses Android MediaPlayer or ExoPlayer.

```java
// Core audio system abstracts platform-specific player
AudioManager audioManager = ... // from runtime
audioManager.play("audio/bgm/theme.ogg", volume: 0.8, loop: true);
```

**Configuration:**
- Audio focus: request focus when game active
- Output: speaker, headphones, Bluetooth (automatic)
- Sample rate: 44.1kHz or 48kHz supported
- Formats: .ogg, .mp3, .wav

---

## Input Handling

### Touch Events

Android touch → JVN Input:
- Single finger drag = mouse move
- Tap = mouse click
- Two-finger gesture = camera zoom (customizable)

```
canvas.onTouchEvent(MotionEvent event)
  ├─ ACTION_DOWN → Input.onMouseDown(x, y)
  ├─ ACTION_MOVE → Input.onMouseMove(x, y)
  └─ ACTION_UP → Input.onMouseUp(x, y)
```

### Keyboard

Physical keyboard and soft keyboard:
- Back key → VNS choice dismissal or navigation
- Volume keys → volume control (customizable)
- Gamepad D-pad/buttons → JES input bindings

---

## Save & Load

Game data persists to device storage:

```
/sdcard/Android/data/com.example.mygame/files/
├── save/
│   ├── slot1.jvnsave
│   ├── slot2.jvnsave
│   └── slot3.jvnsave
└── settings.properties
```

Or app private directory (no user access needed):
```
Context.getFilesDir() / "saves" / "slot1.jvnsave"
```

### Permissions

Saving to external storage requires:
```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

Apps targeting Android 11+ should use `getExternalFilesDir()` for easier access.

---

## Deployment

### Signing the APK

Debug build (for testing):
```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Release build (for Play Store):
```bash
# Create keystore (one-time)
keytool -genkey -v -keystore release.keystore -keyalg RSA -keysize 2048 -validity 10000 -alias release

# Build signed APK
./gradlew :app:assembleRelease

# Or generate AAB (Android App Bundle) for Play Store
./gradlew :app:bundleRelease
```

Sign in `build.gradle.kts`:
```kotlin
android {
  signingConfigs {
    release {
      storeFile = file("release.keystore")
      storePassword = "..."
      keyAlias = "release"
      keyPassword = "..."
    }
  }
  buildTypes.release.signingConfig = signingConfigs.release
}
```

### Play Store Requirements

- **Minification:** Enable ProGuard/R8 (see build config above)
- **Testing:** Test on API 21+ devices and emulator
- **Permissions:** Justify each permission in store listing
- **Content Rating:** Fill out IARC rating questionnaire
- **Privacy Policy:** Required for apps collecting any data

---

## Troubleshooting

### Common Issues

**Issue: "No renderer found"**
- Check that `AndroidRendererFactory` is on classpath
- Verify ServiceLoader config exists in `META-INF/services/`

**Issue: Assets not found at runtime**
- Verify asset path relative to `src/main/assets/`
- Check APK contents: `unzip -l app.apk | grep assets/`
- Ensure external storage permissions granted if using sdcard

**Issue: Audio plays too quietly**
- Check device volume settings
- Verify audio file is not compressed in APK (set media compression off in build config)
- Use ExoPlayer instead of MediaPlayer for better codec support

**Issue: Game crashes on rotation**
- Set `android:configChanges="orientation|screenSize"` in AndroidManifest
- Or freeze orientation: `android:screenOrientation="sensorLandscape"`

---

## API Level Compatibility

| Feature | Min API | Notes |
|---------|---------|-------|
| Canvas 2D | 21 (5.0) | Full support |
| Audio | 21 (5.0) | MediaPlayer available |
| Gesture detection | 21 (5.0) | GestureDetector |
| File access | 21 (5.0) | External storage requires permission |
| Runtime permissions | 23 (6.0) | Check & request at runtime |
| Scoped storage | 30 (11) | Recommended for file I/O |

---

## Related Documentation

- **Render-API:** [docs/architecture/core/render-api.md](../../architecture/core/render-api.md) — backend abstraction
- **Runtime Core:** [docs/runtime/core/runtime.md](../core/runtime.md) — JvnApp integration
- **Asset Management:** [docs/runtime/systems/asset-management.md](../systems/asset-management.md) — asset resolution
- **Save System:** [docs/runtime/systems/save-system.md](../systems/save-system.md) — save/load persistence

---

**Last Updated:** May 2026  
**Minimum SDK:** API 21 (Android 5.0 Lollipop)

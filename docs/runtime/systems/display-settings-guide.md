# Display & Resolution Settings Guide

Quick reference for implementing display settings in your JVN game.

---

## Overview

Players can now adjust game resolution in-game via the Settings menu without requiring developers to create project settings. Three options are available:

- **Screen Width** — 320–7680 pixels (default: 1920)
- **Screen Height** — 180–4320 pixels (default: 1080)
- **Auto-Fit Resolution** — Toggle to automatically fit the player's screen

---

## For Players

Players access these settings through the in-game Settings menu:

1. Open Settings (usually via menu or Escape key)
2. Find "Screen Width", "Screen Height", and "Auto-Fit Resolution"
3. Adjust width/height with arrow keys or sliders
4. Toggle Auto-Fit to enable/disable auto-scaling
5. Exit settings — changes are saved automatically

---

## For Developers

### Zero-Config Usage

Display settings are **included automatically** in the settings menu:

```java
// Players see display options without any code
SettingsScene settings = new SettingsScene(engine, saveManager, script, vnSettings, audio);
engine.scenes().push(settings);
```

### Reading Resolution Values

Access the player's chosen resolution:

```java
VnSettings settings = vnScene.getState().getSettings();

int width = settings.getDisplayWidth();        // e.g., 1920
int height = settings.getDisplayHeight();      // e.g., 1080
boolean autoFit = settings.isAutoFitResolution(); // e.g., false
```

### Applying Resolution to Window

To actually resize the window when settings change:

#### JavaFX

```java
private void applyResolutionSettings(VnSettings settings) {
  if (settings.isAutoFitResolution()) {
    Screen screen = Screen.getPrimary();
    primaryStage.setWidth(screen.getVisualBounds().getWidth());
    primaryStage.setHeight(screen.getVisualBounds().getHeight());
  } else {
    primaryStage.setWidth(settings.getDisplayWidth());
    primaryStage.setHeight(settings.getDisplayHeight());
  }
}
```

#### Swing

```java
private void applyResolutionSettings(VnSettings settings, JFrame frame) {
  if (settings.isAutoFitResolution()) {
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    frame.setSize((int) screenSize.getWidth(), (int) screenSize.getHeight());
  } else {
    frame.setSize(settings.getDisplayWidth(), settings.getDisplayHeight());
  }
}
```

### Updating Viewport Scaling

When the resolution changes, recalculate the viewport transform:

```java
// Called after window resize
int logicalWidth = 1920;   // Your game's logical resolution
int logicalHeight = 1080;

ViewportScaler2D.Transform transform = ViewportScaler2D.fit(
  logicalWidth, logicalHeight,
  windowWidth, windowHeight  // New physical window dimensions
);

// Apply transform to renderer
myRenderer.applyTransform(transform);
```

### Detecting Resolution Changes

Listen for changes when the Settings scene exits:

```java
// In your main game loop or scene management
if (previousSettings.getDisplayWidth() != currentSettings.getDisplayWidth() ||
    previousSettings.getDisplayHeight() != currentSettings.getDisplayHeight() ||
    previousSettings.isAutoFitResolution() != currentSettings.isAutoFitResolution()) {
  
  applyResolutionSettings(currentSettings);
}
```

### Saving Resolution with Saves

Resolution settings are **automatically** included in save data:

```java
// When creating a save
VnSaveData saveData = new VnSaveData();
saveData.setSettings(currentSettings.copy());  // Includes displayWidth, displayHeight, autoFitResolution

// When loading a save
VnSettings savedSettings = loadedSaveData.getSettings();
applyResolutionSettings(savedSettings);
```

---

## Common Patterns

### Full Resolution Implementation

```java
// At game startup
VnSettings settings = settingsStore.load();
applyResolutionSettings(settings);

// When SettingsScene closes
settings = engine.scenes().peek().getState().getSettings();
if (resolutionChanged(previousSettings, settings)) {
  applyResolutionSettings(settings);
  settingsStore.save(settings);
}

// Helper
private boolean resolutionChanged(VnSettings a, VnSettings b) {
  return a.getDisplayWidth() != b.getDisplayWidth() ||
         a.getDisplayHeight() != b.getDisplayHeight() ||
         a.isAutoFitResolution() != b.isAutoFitResolution();
}
```

### Auto-Fit Implementation

```java
private void initializeAutoFitResolution(VnSettings settings) {
  if (settings.isAutoFitResolution()) {
    Screen screen = Screen.getPrimary();
    Bounds bounds = screen.getVisualBounds();
    settings.setDisplayWidth((int) bounds.getWidth());
    settings.setDisplayHeight((int) bounds.getHeight());
  }
}
```

### Menu Profile Customization

To customize how resolution options appear in the menu, define them in your menu profile:

```yaml
screens:
  settings:
    items:
      - id: resolution_header
        label: "Display Options"
        enabled: false
      - id: width_option
        label: "Screen Width: {value}"
        action: { target: "display_width" }
      - id: height_option
        label: "Screen Height: {value}"
        action: { target: "display_height" }
      - id: auto_fit_option
        label: "Auto-Fit: {value}"
        action: { target: "auto_fit_resolution" }
```

---

## Edge Cases

### Minimum/Maximum Constraints

Resolution values are automatically clamped:

```java
settings.setDisplayWidth(100);   // Clamped to 320 (minimum)
settings.setDisplayWidth(9000);  // Clamped to 7680 (maximum)
```

### Multi-Monitor Systems

For auto-fit on multi-monitor setups, query the specific screen:

```java
ObservableList<Screen> screens = Screen.getScreens();
Screen targetScreen = screens.get(0);  // Primary screen
Bounds bounds = targetScreen.getVisualBounds();

settings.setDisplayWidth((int) bounds.getWidth());
settings.setDisplayHeight((int) bounds.getHeight());
```

### Fullscreen Handling

If your game supports fullscreen, you may want to disable width/height controls when fullscreen is active:

```java
if (stage.isFullScreen()) {
  // Skip applying width/height, use native resolution
  return;
}
```

---

## VNS Script Integration

Players and creators can change resolution from VNS scripts:

```vns
; Set fixed resolution
[settings display_width 1280]
[settings display_height 720]

; Enable auto-fit
[settings auto_fit_resolution true]
```

---

## Reference

- **Settings Values**: See [VN Settings Reference](vn-settings.md)
- **Viewport Scaling**: See [ViewportScaler2D](../../architecture/core/2d-engine.md)
- **Save System**: See [Save System](save-system.md)

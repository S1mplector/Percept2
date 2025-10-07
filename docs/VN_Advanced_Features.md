# Advanced Visual Novel Features

## New Features Summary

### 1. Audio System Integration
- **BGM (Background Music)** - Play, stop, fade out
- **SFX (Sound Effects)** - Play one-shot sounds
- **Voice Acting** - Support for character voice lines
- **Volume Controls** - Separate volume for BGM, SFX, and voices

#### Usage:
```java
VnAudioCommand bgm = VnAudioCommand.builder(AudioCommandType.PLAY_BGM)
    .trackId("music/theme.mp3")
    .loop(true)
    .volume(0.7f)
    .build();

VnNode.builder(VnNodeType.DIALOGUE)
    .audioCommand(bgm)
    .dialogue(...)
    .build();
```

### 2. Visual Transitions
- **Fade** - Smooth fade between scenes
- **Dissolve** - Crossfade effect
- **Slide** - Slide left/right transitions
- **Wipe** - Directional wipe
- **Customizable Duration** - Control transition speed

#### Usage:
```java
VnTransition fade = VnTransition.builder(TransitionType.FADE)
    .durationMs(1000)
    .targetBackgroundId("newBg")
    .build();

VnNode.builder(VnNodeType.BACKGROUND)
    .transition(fade)
    .backgroundId("newBg")
    .build();
```

### 3. Character Show/Hide Commands
- **Show Character** - Display character at specific position
- **Hide Character** - Remove character from scene
- **Position Control** - LEFT, CENTER, RIGHT, FAR_LEFT, FAR_RIGHT

#### Usage:
```java
VnNode.builder(VnNodeType.DIALOGUE)
    .characterToShow("alice")
    .showPosition(CharacterPosition.LEFT)
    .build();

VnNode.builder(VnNodeType.DIALOGUE)
    .characterToHide("bob")
    .build();
```

### 4. History/Backlog System
- **Dialogue History** - Stores up to 200 dialogue entries
- **Speaker & Text** - Records who said what
- **Timestamps** - Tracks when dialogue was shown
- **Accessible API** - Easy to build UI for backlog view

#### Usage:
```java
VnHistory history = vnScene.getState().getHistory();
for (VnHistory.HistoryEntry entry : history.getEntries()) {
    System.out.println(entry.getSpeaker() + ": " + entry.getText());
}
```

### 5. Skip Mode
- **Fast Forward** - Instantly reveal text and auto-advance
- **Skip Unread** - Option to skip only previously read dialogue
- **Stop at Choices** - Option to disable skip after choices
- **Keyboard Toggle** - Ctrl/Cmd to toggle skip mode

#### Usage:
```java
vnScene.toggleSkipMode();

// Or configure skip behavior
vnScene.getState().getSettings().setSkipUnreadText(false);
vnScene.getState().getSettings().setSkipAfterChoices(false);
```

### 6. Auto-Play Mode
- **Automatic Advancement** - Auto-advance after text is fully revealed
- **Configurable Delay** - Control how long to wait (default: 2000ms)
- **Stops at Choices** - Automatically disabled when choices appear
- **Keyboard Toggle** - Press 'A' to toggle auto-play

#### Usage:
```java
vnScene.toggleAutoPlayMode();

// Configure auto-play delay
vnScene.getState().getSettings().setAutoPlayDelay(3000); // 3 seconds
```

### 7. Adjustable Text Speed
- **Configurable Speed** - 1-200ms per character (default: 30ms)
- **Instant Text** - Set to 1ms for near-instant reveal
- **Slow Reveal** - Set to 100ms+ for dramatic effect

#### Usage:
```java
VnSettings settings = vnScene.getState().getSettings();
settings.setTextSpeed(50); // 50ms per character
```

### 8. Read Tracking
- **Node History** - Tracks which nodes have been read
- **Skip Integration** - Skip mode can respect read/unread status
- **Save Compatible** - Can be persisted in save data

#### Usage:
```java
boolean hasRead = vnScene.getState().isNodeRead(nodeIndex);
```

### 9. Settings System
All VN playback settings in one place:
- Text speed (1-200ms)
- BGM volume (0.0-1.0)
- SFX volume (0.0-1.0)
- Voice volume (0.0-1.0)
- Auto-play delay (500ms+)
- Skip unread text (boolean)
- Skip after choices (boolean)

#### Usage:
```java
VnSettings settings = vnScene.getState().getSettings();
settings.setBgmVolume(0.8f);
settings.setSfxVolume(0.9f);
settings.setTextSpeed(40);
```

## Keyboard Controls

| Key | Action |
|-----|--------|
| **Space/Enter** | Advance dialogue or skip text animation |
| **Ctrl/Cmd** | Toggle skip mode |
| **A** | Toggle auto-play mode |
| **Mouse Click** | Advance dialogue or select choice |
| **H** | Hide UI (planned) |
| **F5** | Quick save (planned) |
| **F9** | Quick load (planned) |

## Example: Full-Featured Scene

```java
VnScenario scenario = new VnScenarioBuilder("advanced_demo")
    // Setup
    .addCharacter("alice", "Alice")
    .addBackground("room", "game/images/room.png")
    
    // Scene with music and transition
    .background("room")
    .dialogue("Alice", "Welcome!", "alice", "neutral", CharacterPosition.CENTER)
    
    // Add BGM
    .label("with_music")
    .dialogue("Alice", "Let me play some music...")
    
    // Play background music
    .addNode(VnNode.builder(VnNodeType.DIALOGUE)
        .dialogue(DialogueLine.builder()
            .speakerName("Alice")
            .text("This sets the mood!")
            .characterId("alice")
            .expression("happy")
            .position(CharacterPosition.CENTER)
            .build())
        .audioCommand(VnAudioCommand.builder(AudioCommandType.PLAY_BGM)
            .trackId("music/happy_theme.mp3")
            .loop(true)
            .volume(0.7f)
            .build())
        .build())
    
    // Transition to new background
    .addNode(VnNode.builder(VnNodeType.BACKGROUND)
        .backgroundId("garden")
        .transition(VnTransition.builder(TransitionType.FADE)
            .durationMs(1500)
            .targetBackgroundId("garden")
            .build())
        .build())
    
    .dialogue("Alice", "Look at this beautiful garden!")
    
    // Hide Alice
    .addNode(VnNode.builder(VnNodeType.DIALOGUE)
        .dialogue(DialogueLine.builder()
            .speakerName("Alice")
            .text("I'll step aside for a moment...")
            .build())
        .characterToHide("alice")
        .build())
    
    .end()
    .build();
```

## API Reference

### VnScene Methods
- `void setAudioFacade(AudioFacade audio)` - Connect audio system
- `void toggleSkipMode()` - Toggle skip mode
- `void toggleAutoPlayMode()` - Toggle auto-play mode
- `VnState getState()` - Get current state
- `void advance()` - Manually advance to next node
- `void selectChoice(int index)` - Select a choice option

### VnState Methods
- `VnHistory getHistory()` - Get dialogue history
- `VnSettings getSettings()` - Get settings
- `boolean isSkipMode()` / `setSkipMode(boolean)` - Skip mode control
- `boolean isAutoPlayMode()` / `setAutoPlayMode(boolean)` - Auto-play control
- `boolean isNodeRead(int index)` / `markNodeAsRead(int)` - Read tracking
- `VnTransition getActiveTransition()` - Get current transition
- `float getTransitionProgress()` - Get transition progress (0.0-1.0)

### VnSettings Methods
- `void setTextSpeed(int ms)` - Set text reveal speed
- `void setBgmVolume(float volume)` - Set BGM volume (0.0-1.0)
- `void setSfxVolume(float volume)` - Set SFX volume (0.0-1.0)
- `void setVoiceVolume(float volume)` - Set voice volume (0.0-1.0)
- `void setAutoPlayDelay(long ms)` - Set auto-play delay
- `void setSkipUnreadText(boolean)` - Allow skipping unread text
- `void setSkipAfterChoices(boolean)` - Continue skip after choices

## Planned Enhancements

### Phase 2.5 (Next)
- [ ] **Renderer Support for Transitions** - Visual fade/dissolve effects
- [ ] **History UI** - Scrollable backlog interface
- [ ] **Settings Menu** - In-game settings screen
- [ ] **Quick Save/Load** - F5/F9 implementation
- [ ] **Text Box Hide** - H key to hide UI temporarily
- [ ] **Skip/Auto Indicators** - On-screen indicators when active

### Phase 3 (Future)
- [ ] **Character Animations** - Bounce, slide-in effects
- [ ] **Screen Effects** - Shake, flash, vignette
- [ ] **Text Effects** - Wave, shake, gradient text
- [ ] **Multiple Save Slots** - Full save/load menu
- [ ] **Gallery Mode** - View unlocked CGs
- [ ] **Music Room** - Replay unlocked music
- [ ] **Achievements** - Track player progress

## 💾 Save System Integration

The enhanced state can be saved:

```java
VnSaveManager saveManager = new VnSaveManager();
VnState state = vnScene.getState();

// Save includes:
// - Current node index
// - Background ID
// - Variables/flags
// - Settings (if modified)

saveManager.save(state, "save_slot_1");
```

## 🐛 Known Limitations

1. **Transitions** - Visual rendering not yet implemented (structure ready)
2. **Audio Facade** - Requires AudioFacade instance to be set
3. **Voice Lines** - Currently plays as SFX (no dedicated channel)
4. **History UI** - Data structure ready, UI not implemented
5. **Quick Save** - Keyboard shortcut recognized, implementation pending

## 🎯 Best Practices

1. **Audio** - Always set `AudioFacade` before using audio commands
2. **Skip Mode** - Disable `skipUnreadText` for better player experience
3. **Text Speed** - 30-50ms is comfortable for most players
4. **Auto-Play Delay** - 2-3 seconds gives players time to read
5. **Transitions** - Use sparingly for dramatic moments
6. **History** - Clear history when starting new chapter

## 📚 See Also

- [Visual Novel System Guide](VisualNovelSystem.md) - Core VN documentation
- [Script Format Reference](VisualNovelSystem.md#script-syntax-reference)  - Script commands
- [README](../README.md) - Project overview

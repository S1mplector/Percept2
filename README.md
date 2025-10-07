# Percept4 Engine - Java Visual Novel & Game Engine

A modular JavaFX-based game engine with complete visual novel support, designed to scale to full 3D capabilities.

## ✨ Features

### Visual Novel System (Complete)
- **Dialogue System**: Character sprites, name boxes, animated text reveal
- **Branching Narratives**: Choice-based story paths with labels and jumps
- **Character Management**: Multiple expressions per character, dynamic positioning
- **Background System**: Full-screen images with scene transitions
- **Save/Load**: Persistent game state with multiple save slots
- **Script Parser**: Text-based `.vns` script format for easy story authoring
- **Builder API**: Programmatic scenario creation with fluent API
- **Input Handling**: Keyboard and mouse support with hover effects

### Engine Core
- **Scene Management**: Stack-based scene system with lifecycle hooks
- **Asset Pipeline**: Type-aware asset loading from classpath/JAR
- **Configuration**: Builder pattern for engine setup
- **Delta Time**: Frame-independent update loop

## 📦 Modules

- **core**: Engine-agnostic domain/services, VN system, asset management
- **fx**: JavaFX renderer, input handling, VN visual components
- **runtime**: Desktop launcher application
- **scripting**: VN script parsing (`.vns` format)
- **audio-integration**: Audio backend bridge (Simp3 - planned)
- **editor**: GUI authoring tool (planned)
- **demo-game**: Example VN content and scripts
- **testkit**: Shared test utilities

## 🚀 Quick Start

### Requirements
- JDK 22 (or JDK 21 with minor changes)
- Gradle 8+ (wrapper included)

### Running the Demo
```bash
./gradlew :runtime:run
```

This launches a minimal VN demo with dialogue and choices.

### Creating Your First VN

#### Option 1: Script File
Create `src/main/resources/game/scripts/story.vns`:
```
@scenario my_story
@character alice "Alice"
@background room game/images/bg_room.png

[background room]
Alice: Welcome to Percept4!
> Continue -> next
@label next
Alice: Let's begin our adventure!
[end]
```

#### Option 2: Java API
```java
VnScenario scenario = new VnScenarioBuilder("story")
    .addCharacter("alice", "Alice")
    .addBackground("room", "game/images/bg_room.png")
    .background("room")
    .dialogue("Alice", "Welcome to Percept4!")
    .end()
    .build();

VnScene vnScene = new VnScene(scenario);
engine.scenes().push(vnScene);
```

## 📖 Documentation

- [Visual Novel System Guide](docs/VisualNovelSystem.md) - Complete VN documentation
- [Script Format Reference](docs/VisualNovelSystem.md#script-syntax-reference)
- [API Examples](demo-game/) - See demo-game module

## 🎮 Controls

- **Space/Enter**: Advance dialogue or skip text animation
- **Mouse Click**: Advance dialogue or select choices
- **Mouse Hover**: Highlight choice options

## 🏗️ Architecture

```
Engine
  ├── SceneManager (stack-based scenes)
  ├── AssetCatalog (images, audio, scripts, fonts)
  └── Config (title, resolution, etc.)

VnScene (implements Scene)
  ├── VnState (current playthrough state)
  └── VnScenario (story data)

FxLauncher
  ├── Canvas rendering
  ├── VnRenderer (visual novel UI)
  └── Input handling
```

## 🔨 Building

```bash
# Build all modules
./gradlew build

# Build specific modules
./gradlew :core:build :fx:build :runtime:build

# Run tests
./gradlew test

# Create distribution
./gradlew :runtime:distZip
```

## 📂 Project Structure

```
Percept4 Engine/
├── core/              # Engine core + VN system
│   └── src/main/java/com/jvn/core/
│       ├── engine/    # Engine core
│       ├── scene/     # Scene management
│       ├── assets/    # Asset loading
│       └── vn/        # Visual novel components
├── fx/                # JavaFX rendering
│   └── src/main/java/com/jvn/fx/
│       ├── FxLauncher.java
│       └── vn/        # VN renderer
├── runtime/           # Application launcher
├── demo-game/         # Example VN content
│   └── src/main/resources/game/
│       ├── images/
│       ├── scripts/
│       └── audio/
└── docs/              # Documentation
```

## 🎨 Asset Organization

Place assets in `src/main/resources/game/`:
- `images/` - Backgrounds, character sprites
- `audio/` - Music, sound effects
- `scripts/` - VN scenario files (.vns)
- `fonts/` - Custom fonts

## 🔮 Roadmap

### Phase 1: Visual Novel (✅ Complete)
- [x] Dialogue system with text animation
- [x] Character sprites and positioning
- [x] Background images
- [x] Choice-based branching
- [x] Script parser (.vns format)
- [x] Save/load system
- [x] Mouse and keyboard input

### Phase 2: Enhanced VN Features (Planned)
- [ ] Audio/music integration
- [ ] Character animations and transitions
- [ ] Screen effects (fade, shake, etc.)
- [ ] History/backlog system
- [ ] Voice acting support
- [ ] Custom fonts and text styling

### Phase 3: 3D Engine (Future)
- [ ] 3D rendering pipeline (OpenGL/Vulkan)
- [ ] Physics engine integration
- [ ] 3D model loading
- [ ] Lighting system
- [ ] Particle effects
- [ ] Collision detection

## 🤝 Contributing

This is a personal project in active development. The VN system is complete and functional.

## 📄 License

See LICENSE file for details.

## 🙏 Acknowledgments

- JavaFX for the UI framework
- JUnit 5 for testing
- SLF4J for logging

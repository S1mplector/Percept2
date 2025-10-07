# Visual Novel System Documentation

## Overview

Percept4 Engine includes a complete visual novel (VN) system with support for:
- **Dialogue** with character sprites and text animation
- **Branching choices** with jump labels
- **Background images** and scene management
- **Character positioning** (left, center, right, far left, far right)
- **Save/load system**
- **Script parser** for text-based scenario files
- **Programmatic scenario building**

## Core Components

### VnScene
The main scene implementation that manages VN gameplay. It handles:
- Text reveal animation (30ms per character)
- Input processing (space/enter to advance, mouse clicks for choices)
- Node execution and branching logic

### VnState
Manages the current playthrough state:
- Current node index
- Visible characters and their expressions
- Background state
- Variables for flags/conditions
- Text reveal progress

### VnScenario
Container for a complete VN story:
- Nodes (dialogue, choices, jumps, backgrounds)
- Characters with multiple expressions
- Backgrounds
- Label system for branching

## Creating Scenarios

### Method 1: Programmatic Builder

```java
VnScenarioBuilder builder = new VnScenarioBuilder("my_story");

// Define characters
builder.addCharacter("alice", "Alice");
builder.addCharacter("bob", "Bob");

// Define backgrounds
builder.addBackground("room", "game/images/bg_room.png");

// Build story
VnScenario scenario = builder
    .background("room")
    .dialogue("Alice", "Hello there!", "alice", "neutral", CharacterPosition.LEFT)
    .dialogue("Bob", "Nice to meet you!", "bob", "neutral", CharacterPosition.RIGHT)
    .choiceWithTargets(new String[][] {
        {"Be friendly", "friendly_path"},
        {"Be cautious", "cautious_path"}
    })
    .label("friendly_path")
    .dialogue("Alice", "I'm glad you're friendly!")
    .jump("ending")
    .label("cautious_path")
    .dialogue("Bob", "That's understandable.")
    .label("ending")
    .dialogue("Alice", "See you later!")
    .end()
    .build();
```

### Method 2: Script Files (.vns)

Create a text file with the `.vns` extension:

```
# My Visual Novel Script
@scenario my_story

# Define characters
@character alice "Alice"
@character bob "Bob"

# Define backgrounds
@background room game/images/bg_room.png
@background forest game/images/bg_forest.png

# Story begins
[background room]

Alice: Hello there!
Bob: Nice to meet you!

> Be friendly
> Be cautious

Alice: That's great!

[background forest]
Narrator: The scene changes...

[end]
```

Parse the script:
```java
VnScriptParser parser = new VnScriptParser();
VnScenario scenario = parser.parse(inputStream);
```

## Script Syntax Reference

### Commands
- `@scenario <id>` - Define scenario ID
- `@character <id> "Display Name"` - Define a character
- `@background <id> <path>` - Define a background
- `@label <name>` - Create a jump label
- `[background <id>]` or `[bg <id>]` - Change background
- `[jump <label>]` - Jump to a label
- `[end]` - End the scenario

### Dialogue
```
Speaker: Text goes here
```

### Choices
```
> Choice 1 text
> Choice 2 text -> label_name
```
If no label is specified, the scenario continues linearly.

### Comments
```
# This is a comment
```

## Running a VN Scenario

```java
// Create and configure engine
ApplicationConfig config = ApplicationConfig.builder()
    .title("My Visual Novel")
    .width(960)
    .height(540)
    .build();
Engine engine = new Engine(config);
engine.start();

// Load scenario
VnScenario scenario = DemoScenario.createSimpleDemo();
VnScene vnScene = new VnScene(scenario);
engine.scenes().push(vnScene);

// Launch JavaFX
FxLauncher.launch(engine);
```

## Save/Load System

```java
VnSaveManager saveManager = new VnSaveManager();

// Save current state
VnState state = vnScene.getState();
saveManager.save(state, "save_slot_1");

// Load saved state
VnSaveData saveData = saveManager.load("save_slot_1");
saveManager.applyToState(saveData, state);

// List all saves
List<String> saves = saveManager.listSaves();

// Delete a save
saveManager.deleteSave("save_slot_1");
```

Save files are stored in `~/.jvn/saves/` by default.

## Input Controls

- **Space/Enter** - Advance dialogue (or skip text animation)
- **Mouse Click** - Advance dialogue or select choice
- **Mouse Hover** - Highlight choices

## Rendering Features

### Text Box
- Appears at the bottom 25% of the screen
- Semi-transparent black background
- Character name box (if speaker exists)
- Word-wrapped dialogue text
- Continue indicator (triangle) when text is fully revealed

### Character Sprites
- Positioned according to CharacterPosition enum
- Scaled to 70% of screen height
- Support for multiple expressions per character
- Automatically managed by dialogue commands

### Backgrounds
- Full-screen images
- Changed via background nodes or script commands
- Loaded from `game/images/` directory

## Asset Organization

```
src/main/resources/
└── game/
    ├── images/
    │   ├── bg_room.png
    │   ├── bg_forest.png
    │   ├── char_alice_neutral.png
    │   └── char_alice_happy.png
    ├── audio/
    │   └── (music and sound effects)
    ├── scripts/
    │   ├── demo.vns
    │   └── chapter1.vns
    └── fonts/
        └── (custom fonts)
```

## Demo Scenarios

### Simple Demo
```java
VnScenario scenario = DemoScenario.createSimpleDemo();
```
A complete demo with dialogue, choices, and multiple scenes.

### Minimal Test
```java
VnScenario scenario = DemoScenario.createMinimalTest();
```
A minimal scenario for testing the system.

## Advanced Features

### Character Expressions
```java
VnCharacter alice = VnCharacter.builder("alice")
    .displayName("Alice")
    .addExpression("neutral", "game/images/alice_neutral.png")
    .addExpression("happy", "game/images/alice_happy.png")
    .addExpression("sad", "game/images/alice_sad.png")
    .build();
```

### Variables and Flags
The `VnState` includes a variable system for tracking game state:
```java
state.setVariable("met_alice", true);
state.setVariable("friendship_points", 5);

boolean metAlice = (Boolean) state.getVariable("met_alice");
```

### Custom Text Speed
Modify `TEXT_REVEAL_SPEED_MS` in `VnScene` to adjust animation speed.

## Performance Tips

1. **Image Caching**: VnRenderer automatically caches loaded images
2. **Clear Cache**: Call `vnRenderer.clearCache()` to free memory
3. **Lazy Loading**: Only load scenarios when needed
4. **Asset Optimization**: Use compressed PNGs for backgrounds and sprites

## Future Enhancements

- Audio/music integration
- Character animations and transitions
- Screen effects (fade, shake, etc.)
- Custom text fonts and colors
- History/backlog system
- Auto-play mode
- Voice acting support
- Mobile/touch input support

## Example Projects

See `demo-game` module for complete examples and sample scripts.

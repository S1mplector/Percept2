# Beez

A chiptune synthesizer and toy DAW built with C.

## Architecture

```
beez/
├── src/
│   ├── core/           # Domain logic (no external dependencies)
│   │   ├── synthesis/  # Oscillators, envelopes, channels
│   │   ├── engine/     # Main synth engine
│   │   └── sequencer/  # Pattern & song sequencing
│   │
│   ├── ports/          # Abstract interfaces (hexagonal boundaries)
│   │   ├── audio_port.h
│   │   ├── renderer_port.h
│   │   ├── input_port.h
│   │   └── file_port.h
│   │
│   ├── adapters/       # Concrete implementations
│   │   ├── audio/      # miniaudio backend
│   │   ├── platform/   # SDL2 renderer/input
│   │   └── file/       # Standard file I/O
│   │
│   ├── app/            # Application layer
│   │   ├── editor/     # Pattern editor UI
│   │   └── application # Main app orchestration
│   │
│   └── external/       # Single-header libraries
│       └── miniaudio.h
│
├── CMakeLists.txt
└── README.md
```

## Hexagonal Architecture

The project follows hexagonal (ports & adapters) architecture:

- **Core**: Pure domain logic with no external dependencies. Contains synthesis algorithms, sequencer logic, and data structures.
- **Ports**: Abstract interfaces defining how the core interacts with the outside world.
- **Adapters**: Concrete implementations of ports for specific platforms/libraries.

This separation allows:
- Easy testing of core logic in isolation
- Swapping backends without touching core code
- Clear dependency boundaries

## Features

### Synthesis
- Classic chiptune waveforms: Square (50%, 25%, 12.5%), Triangle, Sawtooth, Noise
- ADSR envelopes per channel
- Instrument bank with presets (pulse, bass, pad, bell, organ)
- Per-instrument filters (LP/HP/BP/Notch) and LFO modulation (pitch/pan/duty/volume)
- 8 independent synthesis channels
- Stereo panning

### Sequencer
- 64-row patterns
- 256 pattern slots
- Variable tempo (BPM) and speed
- Classic tracker-style effects (planned)

### Editor
- Pattern editor with tracker-style columns (note/instrument/volume/effect/param)
- Instrument sidebar with live preset details
- Real-time audio preview
- Keyboard-based workflow

## Building

### Prerequisites
- CMake 3.16+
- C11 compiler (GCC, Clang, MSVC)
- SDL2 development libraries

### macOS
```bash
brew install sdl2
mkdir build && cd build
cmake ..
make
./beez
```

### Linux
```bash
sudo apt install libsdl2-dev  # Debian/Ubuntu
mkdir build && cd build
cmake ..
make
./beez
```

### Windows
```bash
# Using vcpkg
vcpkg install sdl2
mkdir build && cd build
cmake .. -DCMAKE_TOOLCHAIN_FILE=[vcpkg-root]/scripts/buildsystems/vcpkg.cmake
cmake --build .
./Debug/beez.exe
```

## Controls

| Key | Action |
|-----|--------|
| Z-M | Play notes (piano layout) |
| F1/F2 | Octave down/up |
| Arrows | Navigate pattern |
| TAB | Toggle edit mode |
| SPACE | Play/Pause |
| ENTER | Stop |
| F6/F7 | Previous/Next instrument |
| Q/W | Decrease/Increase step |
| Ctrl+C / Ctrl+V | Copy/Paste row |
| Ctrl+X | Cut row |
| Shift+Arrows | Fast navigation |
| Ctrl+H/J/K/L | Vim-style navigation |
| Ctrl+U / Ctrl+D | Page up/down (half screen) |
| Ctrl+G / Ctrl+Shift+G | Jump to top/bottom |
| Mouse Click | Select row/channel/column, instrument |
| ESC | Quit |

### Piano Roll Tools
| Key | Action |
|-----|--------|
| 1/2/3/4 | Select/Draw/Erase/Resize tool |
| Ctrl+Z / Ctrl+Y | Undo/Redo |
| Space | Play/Pause |
| Enter | Stop |
| Mouse Drag | Move/resize notes or box-select (Select tool) |
| F8/F9 | Previous/Next track |
| Shift+F8/F9 | Previous/Next MIDI channel filter |

### Keybinding Customization
Create a `keybindings.cfg` in the project root to override piano roll bindings:
```
pianoroll.undo=Ctrl+Z
pianoroll.redo=Ctrl+Y
pianoroll.tool_select=1
pianoroll.tool_draw=2
pianoroll.tool_erase=3
pianoroll.tool_resize=4
pianoroll.play_pause=Space
pianoroll.stop=Enter
```

## Piano Layout

```
 S D   G H J
Z X C V B N M
C C# D D# E F F# G G# A A# B
```

## Dependencies

- **SDL2** - Window/input handling (external)

## License

MIT License

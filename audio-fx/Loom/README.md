# Loom Flight & Cape Physics Demo

A C++ exploration of procedural cloth physics and wind-driven kinesthetics, inspired by the beautiful flight mechanics of **Sky: Children of the Light** by thatgamecompany.

![Demo Preview](docs/preview.gif)

## Overview

This project demonstrates:
- **Verlet Integration** for realistic cloth/cape physics
- **Perlin Noise** driven dynamic wind fields
- **Flight mechanics** with energy-based glide physics
- **Soft, ethereal aesthetic** rendering
- **Real-time performance monitoring**

Built from scratch in pure C++ with Raylib.

## Features

### Cape Physics (Verlet Integration)
- Multi-segment cloth simulation using position-based dynamics
- Distance constraints for structural integrity
- Bending constraints for natural cloth behavior
- Mass-weighted constraint solving
- Wind force integration with depth-based influence

### Wind System
- 3D Perlin noise for organic wind patterns
- Curl noise for turbulence
- Dynamic gusts with spatial falloff
- Configurable base direction and strength

### Flight Mechanics
- **Gliding**: Speed converts to lift when moving horizontally
- **Climbing**: Costs energy, reduces speed
- **Diving**: Gains energy and speed
- **Hovering**: Gentle descent when still
- Wind assist affects flight trajectory

### Performance
- Efficient Verlet solver (O(n) per iteration)
- Optimized constraint solving with configurable iterations
- Frame time and memory monitoring
- Targets 60 FPS on modest hardware

## Controls

| Key | Action |
|-----|--------|
| W / ↑ | Climb (uses energy) |
| S / ↓ | Dive (gains energy) |
| A / ← | Move left |
| D / → | Move right |
| Space | Boost |
| V | Toggle wind visualization |
| R | Reset position |
| Tab | Show debug info |

## Building

### Prerequisites
- CMake 3.16+
- C++17 compatible compiler
- Git (for fetching Raylib)

### Build Steps

```bash
# Clone the repository
git clone https://github.com/yourusername/ethereal-flight.git
cd ethereal-flight

# Create build directory
mkdir build && cd build

# Configure and build
cmake ..
cmake --build . --config Release

# Run
./EtherealFlight
```

### macOS
```bash
cmake -DCMAKE_BUILD_TYPE=Release ..
make -j$(sysctl -n hw.ncpu)
```

### Linux
```bash
sudo apt-get install libgl1-mesa-dev libx11-dev libxrandr-dev libxi-dev
cmake -DCMAKE_BUILD_TYPE=Release ..
make -j$(nproc)
```

### Windows (Visual Studio)
```powershell
cmake -G "Visual Studio 17 2022" ..
cmake --build . --config Release
```

## Project Structure

```
ethereal-flight/
├── CMakeLists.txt
├── README.md
├── src/
│   ├── main.cpp                 # Application entry point
│   ├── core/
│   │   ├── Vector2D.hpp/cpp     # 2D vector math
│   │   └── Vector3D.hpp/cpp     # 3D vector math
│   ├── physics/
│   │   ├── VerletParticle.hpp/cpp    # Verlet particle system
│   │   ├── VerletConstraint.hpp/cpp  # Distance & bending constraints
│   │   ├── Cape.hpp/cpp              # Cape cloth simulation
│   │   └── WindField.hpp/cpp         # Perlin noise wind system
│   ├── entities/
│   │   ├── Character.hpp/cpp         # Player character
│   │   └── FlightController.hpp/cpp  # Flight physics
│   ├── rendering/
│   │   └── Renderer.hpp/cpp          # Raylib rendering
│   └── utils/
│       ├── PerlinNoise.hpp/cpp       # Perlin noise implementation
│       └── PerformanceMonitor.hpp/cpp
├── tools/
│   └── wind_map_generator.py    # Python wind map pipeline tool
└── assets/
    └── windmaps/
```

## Technical Deep Dive

### Verlet Integration

Instead of storing velocity explicitly, Verlet integration derives it from position history:

```cpp
Vector2D velocity = position - previousPosition;
previousPosition = position;
position = position + velocity * damping + acceleration * dt * dt;
```

This approach is:
- Stable under high forces
- Simple constraint satisfaction
- Natural damping behavior

### Constraint Solving

Distance constraints maintain cloth structure:

```cpp
Vector2D delta = particleB->position - particleA->position;
float diff = (currentLength - restLength) / currentLength;
Vector2D correction = delta * diff * 0.5f * stiffness;
// Apply correction to both particles
```

### Wind Field

Wind is sampled using multi-octave Perlin noise:

```cpp
Vector2D getWindAt(float x, float y) {
    float nx = noise.octaveNoise(x * scale, y * scale, time, 3);
    float ny = noise.octaveNoise(x * scale + 100, y * scale + 100, time, 3);
    return baseDirection * baseStrength + Vector2D(nx, ny) * turbulence;
}
```

## Python Pipeline Tool

The `tools/wind_map_generator.py` script generates pre-baked wind map data:

```bash
cd tools
python wind_map_generator.py --output ../assets/windmaps/custom.wind \
    --width 128 --height 128 \
    --octaves 6 \
    --visualize
```

This demonstrates a cross-language pipeline workflow.

## Configuration

Key parameters can be tuned in `main.cpp`:

```cpp
// Cape behavior
CapeConfig capeConfig;
capeConfig.segments = 14;        // Cloth resolution
capeConfig.stiffness = 0.92f;    // Constraint strength
capeConfig.windInfluence = 1.4f; // Wind response

// Flight feel
FlightConfig flightConfig;
flightConfig.liftForce = 600.0f;
flightConfig.glideRatio = 2.8f;
flightConfig.windAssist = 0.9f;
```

## Why This Project?

This demo was created to showcase:

1. **Low-level physics understanding** — No Unity/Unreal physics "auto-magic"
2. **Aesthetic sensibility** — Soft rendering matching TGC's visual style
3. **Kinesthetic design** — The "feel" of movement matters
4. **Clean C++ architecture** — Modular, readable, efficient
5. **Pipeline thinking** — Python tool integration

## Inspiration

- **Sky: Children of the Light** — Flight and cape beauty
- **Journey** — Emotional movement design
- **Flower** — Wind as a character

## License

MIT License — Feel free to learn from and build upon this work.

## Author

Built with passion for beautiful interactive experiences.

---

*"The feeling of flight is more important than the mechanics of flight."*

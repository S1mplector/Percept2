#!/usr/bin/env python3
"""
Wind Map Generator for Ethereal Flight Demo
============================================
Generates wind map data files that can be loaded by the C++ application.
This demonstrates a Python -> C++ pipeline workflow.

Usage:
    python wind_map_generator.py --output ../assets/windmaps/default.wind
    python wind_map_generator.py --width 128 --height 128 --octaves 4
"""

import argparse
import struct
import math
import random
from dataclasses import dataclass
from pathlib import Path
from typing import List, Tuple


@dataclass
class WindMapConfig:
    width: int = 64
    height: int = 64
    octaves: int = 4
    persistence: float = 0.5
    lacunarity: float = 2.0
    scale: float = 0.05
    seed: int = 42
    base_strength: float = 50.0
    gust_strength: float = 80.0


class PerlinNoise:
    """Simple Perlin noise implementation for wind generation."""
    
    def __init__(self, seed: int = 42):
        self.seed = seed
        random.seed(seed)
        self.p = list(range(256))
        random.shuffle(self.p)
        self.p = self.p + self.p
    
    def _fade(self, t: float) -> float:
        return t * t * t * (t * (t * 6 - 15) + 10)
    
    def _lerp(self, t: float, a: float, b: float) -> float:
        return a + t * (b - a)
    
    def _grad(self, hash_val: int, x: float, y: float) -> float:
        h = hash_val & 7
        u = x if h < 4 else y
        v = y if h < 4 else x
        return (u if (h & 1) == 0 else -u) + (v if (h & 2) == 0 else -v)
    
    def noise2d(self, x: float, y: float) -> float:
        X = int(math.floor(x)) & 255
        Y = int(math.floor(y)) & 255
        
        x -= math.floor(x)
        y -= math.floor(y)
        
        u = self._fade(x)
        v = self._fade(y)
        
        A = self.p[X] + Y
        AA = self.p[A]
        AB = self.p[A + 1]
        B = self.p[X + 1] + Y
        BA = self.p[B]
        BB = self.p[B + 1]
        
        return self._lerp(v,
            self._lerp(u, self._grad(self.p[AA], x, y), self._grad(self.p[BA], x - 1, y)),
            self._lerp(u, self._grad(self.p[AB], x, y - 1), self._grad(self.p[BB], x - 1, y - 1))
        )
    
    def octave_noise(self, x: float, y: float, octaves: int, persistence: float = 0.5) -> float:
        total = 0.0
        frequency = 1.0
        amplitude = 1.0
        max_value = 0.0
        
        for _ in range(octaves):
            total += self.noise2d(x * frequency, y * frequency) * amplitude
            max_value += amplitude
            amplitude *= persistence
            frequency *= 2.0
        
        return total / max_value


def generate_wind_map(config: WindMapConfig) -> List[Tuple[float, float]]:
    """Generate a 2D wind map using Perlin noise."""
    noise = PerlinNoise(config.seed)
    wind_data = []
    
    for y in range(config.height):
        for x in range(config.width):
            nx = x * config.scale
            ny = y * config.scale
            
            # Sample noise for X and Y wind components
            wind_x = noise.octave_noise(nx, ny, config.octaves, config.persistence)
            wind_y = noise.octave_noise(nx + 100, ny + 100, config.octaves, config.persistence)
            
            # Add base wind direction (mostly horizontal)
            wind_x = wind_x * config.gust_strength + config.base_strength
            wind_y = wind_y * config.gust_strength * 0.3
            
            wind_data.append((wind_x, wind_y))
    
    return wind_data


def save_wind_map(wind_data: List[Tuple[float, float]], config: WindMapConfig, filepath: Path):
    """
    Save wind map to binary file format.
    
    Format:
    - Header (16 bytes):
        - Magic number: 4 bytes ("WIND")
        - Version: 4 bytes (uint32)
        - Width: 4 bytes (uint32)
        - Height: 4 bytes (uint32)
    - Data:
        - For each cell: wind_x (float32), wind_y (float32)
    """
    filepath.parent.mkdir(parents=True, exist_ok=True)
    
    with open(filepath, 'wb') as f:
        # Write header
        f.write(b'WIND')
        f.write(struct.pack('<I', 1))  # Version
        f.write(struct.pack('<I', config.width))
        f.write(struct.pack('<I', config.height))
        
        # Write wind data
        for wind_x, wind_y in wind_data:
            f.write(struct.pack('<ff', wind_x, wind_y))
    
    print(f"Wind map saved: {filepath}")
    print(f"  Size: {config.width}x{config.height}")
    print(f"  Data size: {len(wind_data) * 8} bytes")


def save_wind_map_json(wind_data: List[Tuple[float, float]], config: WindMapConfig, filepath: Path):
    """Save wind map as JSON for debugging/visualization."""
    import json
    
    json_filepath = filepath.with_suffix('.json')
    
    data = {
        "version": 1,
        "width": config.width,
        "height": config.height,
        "config": {
            "octaves": config.octaves,
            "persistence": config.persistence,
            "scale": config.scale,
            "seed": config.seed,
            "base_strength": config.base_strength,
            "gust_strength": config.gust_strength
        },
        "wind": [{"x": round(w[0], 2), "y": round(w[1], 2)} for w in wind_data]
    }
    
    with open(json_filepath, 'w') as f:
        json.dump(data, f, indent=2)
    
    print(f"JSON debug file saved: {json_filepath}")


def visualize_wind_map(wind_data: List[Tuple[float, float]], config: WindMapConfig, filepath: Path):
    """Generate a simple ASCII visualization of the wind map."""
    viz_filepath = filepath.with_suffix('.txt')
    
    arrows = ['→', '↗', '↑', '↖', '←', '↙', '↓', '↘']
    
    lines = [f"Wind Map Visualization ({config.width}x{config.height})", "=" * 40, ""]
    
    for y in range(config.height):
        line = ""
        for x in range(config.width):
            idx = y * config.width + x
            wx, wy = wind_data[idx]
            
            angle = math.atan2(wy, wx)
            arrow_idx = int((angle + math.pi) / (2 * math.pi) * 8) % 8
            line += arrows[arrow_idx] + " "
        lines.append(line)
    
    with open(viz_filepath, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))
    
    print(f"Visualization saved: {viz_filepath}")


def main():
    parser = argparse.ArgumentParser(
        description='Generate wind map data for Ethereal Flight Demo',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python wind_map_generator.py
  python wind_map_generator.py --output custom.wind --width 128 --height 128
  python wind_map_generator.py --seed 12345 --octaves 6 --json --visualize
        """
    )
    
    parser.add_argument('--output', '-o', type=str, default='../assets/windmaps/default.wind',
                       help='Output file path')
    parser.add_argument('--width', '-W', type=int, default=64, help='Map width')
    parser.add_argument('--height', '-H', type=int, default=64, help='Map height')
    parser.add_argument('--octaves', type=int, default=4, help='Noise octaves')
    parser.add_argument('--persistence', type=float, default=0.5, help='Noise persistence')
    parser.add_argument('--scale', type=float, default=0.05, help='Noise scale')
    parser.add_argument('--seed', type=int, default=42, help='Random seed')
    parser.add_argument('--base-strength', type=float, default=50.0, help='Base wind strength')
    parser.add_argument('--gust-strength', type=float, default=80.0, help='Gust strength')
    parser.add_argument('--json', action='store_true', help='Also output JSON file')
    parser.add_argument('--visualize', '-v', action='store_true', help='Generate ASCII visualization')
    
    args = parser.parse_args()
    
    config = WindMapConfig(
        width=args.width,
        height=args.height,
        octaves=args.octaves,
        persistence=args.persistence,
        scale=args.scale,
        seed=args.seed,
        base_strength=args.base_strength,
        gust_strength=args.gust_strength
    )
    
    print("Generating wind map...")
    print(f"  Config: {config.width}x{config.height}, {config.octaves} octaves, seed={config.seed}")
    
    wind_data = generate_wind_map(config)
    
    output_path = Path(args.output)
    save_wind_map(wind_data, config, output_path)
    
    if args.json:
        save_wind_map_json(wind_data, config, output_path)
    
    if args.visualize:
        visualize_wind_map(wind_data, config, output_path)
    
    print("\nDone! Wind map ready for use in Ethereal Flight Demo.")


if __name__ == '__main__':
    main()

#!/bin/bash
#
# Build script for Simjot Profiler CLI
# Usage: ./build_profiler.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║           Building Simjot Profiler CLI                       ║"
echo "╚══════════════════════════════════════════════════════════════╝"

# Create build directory
mkdir -p "${BUILD_DIR}"
cd "${BUILD_DIR}"

# Configure with CMake
echo "[1/3] Configuring..."
cmake .. -DCMAKE_BUILD_TYPE=Release

# Build
echo "[2/3] Building..."
cmake --build . --target profiler_cli -j$(sysctl -n hw.ncpu 2>/dev/null || nproc)

# Copy to native directory
echo "[3/3] Installing..."
if [ -f "simjot_profiler" ]; then
    cp simjot_profiler "${SCRIPT_DIR}/"
    echo ""
    echo "✅ Build complete!"
    echo ""
    echo "Profiler location: ${SCRIPT_DIR}/simjot_profiler"
    echo ""
    echo "Usage:"
    echo "  ./simjot_profiler -p <pid>           # Profile a process"
    echo "  ./simjot_profiler -p <pid> -c        # Continuous profiling"
    echo "  ./simjot_profiler -s                 # Self-test"
    echo "  ./simjot_profiler -h                 # Help"
else
    echo "❌ Build failed - executable not found"
    exit 1
fi

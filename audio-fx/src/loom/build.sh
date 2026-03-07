#!/bin/bash
# Build script for Ethereal Flight Demo

set -e

BUILD_TYPE="${1:-Release}"
BUILD_DIR="build"

echo "=================================="
echo "Ethereal Flight - Build Script"
echo "=================================="
echo "Build type: $BUILD_TYPE"
echo ""

# Create build directory
if [ ! -d "$BUILD_DIR" ]; then
    echo "Creating build directory..."
    mkdir -p "$BUILD_DIR"
fi

cd "$BUILD_DIR"

# Configure with CMake
echo "Configuring with CMake..."
cmake -DCMAKE_BUILD_TYPE="$BUILD_TYPE" ..

# Build
echo ""
echo "Building..."
if [[ "$OSTYPE" == "darwin"* ]]; then
    make -j$(sysctl -n hw.ncpu)
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    make -j$(nproc)
else
    cmake --build . --config "$BUILD_TYPE"
fi

echo ""
echo "=================================="
echo "Build complete!"
echo "Run with: ./build/EtherealFlight"
echo "=================================="

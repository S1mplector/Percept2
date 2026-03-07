#!/bin/bash

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$PROJECT_DIR/build"

usage() {
    echo "Beez Chiptune Synthesizer - Build & Run Script"
    echo ""
    echo "Usage: ./run.sh [command] [options]"
    echo ""
    echo "Commands:"
    echo "  build       Build the project (default)"
    echo "  run         Build and run beez"
    echo "  clean       Clean build directory"
    echo "  rebuild     Clean and rebuild"
    echo "  test        Build and run tests"
    echo "  help        Show this help"
    echo ""
    echo "Options:"
    echo "  --release   Build in Release mode"
    echo "  --debug     Build in Debug mode (default)"
    echo ""
    echo "Examples:"
    echo "  ./run.sh                  # Build only"
    echo "  ./run.sh run              # Build and run"
    echo "  ./run.sh run --release    # Build release and run"
    echo "  ./run.sh test             # Build and run tests"
    echo "  ./run.sh run song.mid     # Build and run with MIDI file"
}

BUILD_TYPE="Debug"
COMMAND="build"
MIDI_FILE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        build|run|clean|rebuild|test|help)
            COMMAND="$1"
            shift
            ;;
        --release)
            BUILD_TYPE="Release"
            shift
            ;;
        --debug)
            BUILD_TYPE="Debug"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *.mid|*.midi)
            MIDI_FILE="$1"
            shift
            ;;
        *)
            echo "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

do_clean() {
    echo "🧹 Cleaning build directory..."
    rm -rf "$BUILD_DIR"
    echo "✓ Clean complete"
}

do_configure() {
    echo "⚙️  Configuring ($BUILD_TYPE)..."
    cmake -B "$BUILD_DIR" -S "$PROJECT_DIR" -DCMAKE_BUILD_TYPE="$BUILD_TYPE"
}

do_build() {
    if [ ! -d "$BUILD_DIR" ]; then
        do_configure
    fi
    
    echo "Building beez..."
    cmake --build "$BUILD_DIR" --parallel
    echo "✓ Build complete"
}

do_run() {
    do_build
    echo ""
    echo "Running Beez..."
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    if [ -n "$MIDI_FILE" ]; then
        "$BUILD_DIR/beez" "$MIDI_FILE"
    else
        "$BUILD_DIR/beez"
    fi
}

do_test() {
    do_build
    echo ""
    echo "Running tests..."
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    "$BUILD_DIR/beez_tests"
}

case $COMMAND in
    build)
        do_build
        ;;
    run)
        do_run
        ;;
    clean)
        do_clean
        ;;
    rebuild)
        do_clean
        do_build
        ;;
    test)
        do_test
        ;;
    help)
        usage
        ;;
esac

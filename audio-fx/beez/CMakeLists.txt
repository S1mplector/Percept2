cmake_minimum_required(VERSION 3.16)
project(beez VERSION 0.1.0 LANGUAGES C OBJC)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Compiler warnings
if(CMAKE_C_COMPILER_ID MATCHES "GNU|Clang|AppleClang")
    add_compile_options(-Wall -Wextra -Wpedantic)
endif()

# Core synthesis library (pure C)
add_library(beez_core STATIC
    src/core/synthesis/oscillator.c
    src/core/synthesis/envelope.c
    src/core/synthesis/channel.c
    src/core/synthesis/effects.c
    src/core/synthesis/instrument.c
    src/core/synthesis/filter.c
    src/core/synthesis/lfo.c
    src/core/engine/synth_engine.c
    src/core/sequencer/pattern.c
    src/core/sequencer/sequencer.c
    src/core/audio/ring_buffer.c
    src/core/audio/audio_utils.c
    src/core/audio/wav_export.c
    src/core/midi/midi_file.c
    src/core/midi/midi_player.c
)
target_include_directories(beez_core PUBLIC src)
target_link_libraries(beez_core PRIVATE m)

# macOS frameworks
find_library(COCOA_LIBRARY Cocoa REQUIRED)
find_library(QUARTZCORE_LIBRARY QuartzCore REQUIRED)
find_library(AUDIOTOOLBOX_LIBRARY AudioToolbox REQUIRED)

# Adapters library (native macOS)
add_library(beez_adapters STATIC
    src/adapters/audio/coreaudio_adapter.c
    src/adapters/file/std_file_adapter.c
    src/adapters/platform/macos_platform.m
)
target_include_directories(beez_adapters PUBLIC src)
target_link_libraries(beez_adapters 
    PRIVATE 
        beez_core
        ${COCOA_LIBRARY}
        ${QUARTZCORE_LIBRARY}
        ${AUDIOTOOLBOX_LIBRARY}
)

# Application library
add_library(beez_app STATIC
    src/app/application.c
    src/app/editor/editor.c
    src/app/editor/piano_roll.c
    src/app/ui/theme.c
    src/app/ui/widgets.c
    src/app/ui/font.c
    src/app/ui/toast.c
)
target_include_directories(beez_app PUBLIC src)
target_link_libraries(beez_app PRIVATE beez_core beez_adapters)

# Main executable
add_executable(beez src/main.c)
target_link_libraries(beez PRIVATE 
    beez_app 
    beez_core 
    beez_adapters
    ${COCOA_LIBRARY}
    ${QUARTZCORE_LIBRARY}
    ${AUDIOTOOLBOX_LIBRARY}
)

# Installation
install(TARGETS beez RUNTIME DESTINATION bin)

# Test executable
enable_testing()
add_executable(beez_tests
    tests/test_main.c
    tests/test_oscillator.c
    tests/test_envelope.c
    tests/test_channel.c
    tests/test_synth_engine.c
    tests/test_ring_buffer.c
    tests/test_audio_utils.c
)
target_include_directories(beez_tests PRIVATE src tests)
target_link_libraries(beez_tests PRIVATE beez_core m)
add_test(NAME beez_tests COMMAND beez_tests)

# Output build info
message(STATUS "")
message(STATUS "=== Beez Chiptune Tracker ===")
message(STATUS "Version: ${PROJECT_VERSION}")
message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "C Compiler: ${CMAKE_C_COMPILER}")
message(STATUS "")

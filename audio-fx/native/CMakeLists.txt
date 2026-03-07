cmake_minimum_required(VERSION 3.16)
project(jvn_audiofx_native LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(CMAKE_C_COMPILER_ID MATCHES "GNU|Clang|AppleClang")
    add_compile_options(-Wall -Wextra -Wpedantic)
endif()

find_package(JNI REQUIRED)

set(BEEZ_CORE_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/../beez/src/core/synthesis/oscillator.c
    ${CMAKE_CURRENT_SOURCE_DIR}/../beez/src/core/synthesis/envelope.c
    ${CMAKE_CURRENT_SOURCE_DIR}/../beez/src/core/synthesis/effects.c
    ${CMAKE_CURRENT_SOURCE_DIR}/../beez/src/core/synthesis/filter.c
    ${CMAKE_CURRENT_SOURCE_DIR}/../beez/src/core/synthesis/lfo.c
    ${CMAKE_CURRENT_SOURCE_DIR}/../beez/src/core/synthesis/channel.c
    ${CMAKE_CURRENT_SOURCE_DIR}/../beez/src/core/engine/synth_engine.c
)

add_library(jvn_audiofx_native SHARED
    src/beez_renderer.cpp
    src/loom_ambience_renderer.cpp
    src/jvn_audiofx_bridge.cpp
    ${BEEZ_CORE_SOURCES}
)

target_include_directories(jvn_audiofx_native PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${CMAKE_CURRENT_SOURCE_DIR}/../beez/src
)

target_link_libraries(jvn_audiofx_native PRIVATE ${JNI_LIBRARIES})

if(UNIX AND NOT APPLE)
    target_link_libraries(jvn_audiofx_native PRIVATE m)
endif()

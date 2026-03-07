cmake_minimum_required(VERSION 3.16)
project(jvn_audiofx_native LANGUAGES C CXX)
enable_testing()

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(CMAKE_C_COMPILER_ID MATCHES "GNU|Clang|AppleClang")
    add_compile_options(-Wall -Wextra -Wpedantic)
endif()

set(JAVA_HOME "" CACHE PATH "Path to the JDK home used for JNI headers")

if(NOT JAVA_HOME AND DEFINED ENV{JAVA_HOME})
    set(JAVA_HOME "$ENV{JAVA_HOME}")
endif()

if(NOT JAVA_HOME)
    message(FATAL_ERROR "JAVA_HOME must point to a JDK so JNI headers can be found")
endif()

set(JAVA_INCLUDE_DIR "${JAVA_HOME}/include")
if(APPLE)
    set(JAVA_PLATFORM_INCLUDE_DIR "${JAVA_HOME}/include/darwin")
elseif(WIN32)
    set(JAVA_PLATFORM_INCLUDE_DIR "${JAVA_HOME}/include/win32")
else()
    set(JAVA_PLATFORM_INCLUDE_DIR "${JAVA_HOME}/include/linux")
endif()

if(NOT EXISTS "${JAVA_INCLUDE_DIR}/jni.h")
    message(FATAL_ERROR "jni.h was not found under ${JAVA_INCLUDE_DIR}")
endif()

set(BEEZ_CORE_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/beez/src/core/synthesis/oscillator.c
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/beez/src/core/synthesis/envelope.c
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/beez/src/core/synthesis/effects.c
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/beez/src/core/synthesis/filter.c
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/beez/src/core/synthesis/lfo.c
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/beez/src/core/synthesis/channel.c
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/beez/src/core/engine/synth_engine.c
)

add_library(jvn_audiofx_native SHARED
    src/beez_renderer.cpp
    src/loom_ambience_renderer.cpp
    src/loom_ambience_synth.cpp
    src/ambience/ambience_filters.cpp
    src/ambience/ambience_mastering.cpp
    src/ambience/ambience_modulation.cpp
    src/ambience/ambience_noise.cpp
    src/ambience/ambience_mode.cpp
    src/ambience/wind_ambience_mode.cpp
    src/ambience/rain_ambience_mode.cpp
    src/ambience/ocean_ambience_mode.cpp
    src/ambience/thunder_ambience_mode.cpp
    src/ambience/fireplace_ambience_mode.cpp
    src/ambience/night_insects_ambience_mode.cpp
    src/jvn_audiofx_bridge.cpp
    ${BEEZ_CORE_SOURCES}
)

target_include_directories(jvn_audiofx_native PRIVATE
    ${JAVA_INCLUDE_DIR}
    ${JAVA_PLATFORM_INCLUDE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/beez/src
)

if(UNIX AND NOT APPLE)
    target_link_libraries(jvn_audiofx_native PRIVATE m)
endif()

add_executable(jvn_audiofx_native_math_test
    test/ambience_math_test.cpp
    src/ambience/ambience_filters.cpp
    src/ambience/ambience_mastering.cpp
    src/ambience/ambience_modulation.cpp
    src/ambience/ambience_noise.cpp
    src/ambience/ambience_mode.cpp
    src/ambience/rain_ambience_mode.cpp
)

target_include_directories(jvn_audiofx_native_math_test PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)

if(UNIX AND NOT APPLE)
    target_link_libraries(jvn_audiofx_native_math_test PRIVATE m)
endif()

add_test(NAME jvn_audiofx_native_math_test COMMAND jvn_audiofx_native_math_test)

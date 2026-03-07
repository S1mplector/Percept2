cmake_minimum_required(VERSION 3.16)

# Fix for newer CMake with older Raylib
set(CMAKE_POLICY_VERSION_MINIMUM 3.5)

project(EtherealFlight VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Build type
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Optimization flags
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "-g -O0")

# Find or fetch Raylib
include(FetchContent)
FetchContent_Declare(
    raylib
    GIT_REPOSITORY https://github.com/raysan5/raylib.git
    GIT_TAG 5.0
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(raylib)

# Core source files (shared)
set(CORE_SOURCES
    src/core/Vector2D.cpp
    src/core/Vector3D.cpp
    src/core/Matrix4.cpp
    src/core/Quaternion.cpp
    src/utils/PerlinNoise.cpp
    src/utils/PerformanceMonitor.cpp
)

# 2D source files
set(SOURCES_2D
    src/main.cpp
    src/physics/VerletParticle.cpp
    src/physics/VerletConstraint.cpp
    src/physics/Cape.cpp
    src/physics/WindField.cpp
    src/entities/Character.cpp
    src/entities/FlightController.cpp
    src/rendering/Renderer.cpp
)

# 3D source files
set(SOURCES_3D
    src/main3d.cpp
    src/physics/VerletParticle3D.cpp
    src/physics/Cape3D.cpp
    src/physics/WindField3D.cpp
    src/entities/Character3D.cpp
    src/entities/Camera3D.cpp
    src/entities/FlightController3D.cpp
    src/environment/Terrain.cpp
    src/rendering/Renderer3D.cpp
    src/rendering/EnergyBeingRenderer.cpp
    src/rendering/EnvironmentRenderer.cpp
    src/audio/WindSoundSynthesizer.cpp
)

# 2D Executable (original)
add_executable(EtherealFlight2D ${CORE_SOURCES} ${SOURCES_2D})

# 3D Executable (new)
add_executable(${PROJECT_NAME} ${CORE_SOURCES} ${SOURCES_3D})

# Include directories for both targets
target_include_directories(EtherealFlight2D PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_include_directories(${PROJECT_NAME} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)

# Link libraries for both targets
target_link_libraries(EtherealFlight2D PRIVATE raylib)
target_link_libraries(${PROJECT_NAME} PRIVATE raylib)

# Platform-specific settings
if(APPLE)
    target_link_libraries(EtherealFlight2D PRIVATE "-framework IOKit" "-framework Cocoa" "-framework OpenGL")
    target_link_libraries(${PROJECT_NAME} PRIVATE "-framework IOKit" "-framework Cocoa" "-framework OpenGL")
elseif(UNIX)
    target_link_libraries(EtherealFlight2D PRIVATE m pthread dl)
    target_link_libraries(${PROJECT_NAME} PRIVATE m pthread dl)
endif()

# Copy assets to build directory
file(COPY ${CMAKE_CURRENT_SOURCE_DIR}/assets DESTINATION ${CMAKE_CURRENT_BINARY_DIR})

# Install targets
install(TARGETS EtherealFlight2D ${PROJECT_NAME} DESTINATION bin)
install(DIRECTORY assets DESTINATION share/${PROJECT_NAME})

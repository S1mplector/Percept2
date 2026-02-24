cmake_minimum_required(VERSION 3.20)
project(simjot_native C CXX)

option(SIMJOT_NATIVE_BUILD_TESTS "Build native test CLI" ON)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

file(GLOB_RECURSE SIMJOT_SOURCES CONFIGURE_DEPENDS
    "${CMAKE_CURRENT_SOURCE_DIR}/src/*.c"
    "${CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp"
)

if(APPLE)
    enable_language(OBJCXX)
    file(GLOB_RECURSE SIMJOT_OBJCXX_SOURCES CONFIGURE_DEPENDS
        "${CMAKE_CURRENT_SOURCE_DIR}/src/*.mm"
    )
    list(APPEND SIMJOT_SOURCES ${SIMJOT_OBJCXX_SOURCES})
endif()

add_library(simjot_native SHARED ${SIMJOT_SOURCES})
set_target_properties(simjot_native PROPERTIES OUTPUT_NAME "simjot_native")
target_include_directories(simjot_native PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/include")

# Platform-specific linking for UI scaling
if(APPLE)
    find_library(QUICKLOOKTHUMBNAILING_FRAMEWORK QuickLookThumbnailing)
    target_link_libraries(simjot_native PRIVATE
        "-framework CoreGraphics"
        "-framework ApplicationServices"
        "-framework CoreServices"
        "-framework CoreText"
        "-framework CoreFoundation"
        "-framework Cocoa"
        "-framework CoreVideo"
        "-framework Accelerate"
        "-framework IOKit"
        "-framework SystemConfiguration"
    )
    if(QUICKLOOKTHUMBNAILING_FRAMEWORK)
        target_link_libraries(simjot_native PRIVATE "${QUICKLOOKTHUMBNAILING_FRAMEWORK}")
    endif()
elseif(WIN32)
    target_link_libraries(simjot_native PRIVATE Shcore)
endif()

if (SIMJOT_NATIVE_BUILD_TESTS)
    add_executable(simjot_native_cli tests/simjot_native_cli.c)
    target_link_libraries(simjot_native_cli PRIVATE simjot_native)
    target_include_directories(simjot_native_cli PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include")

    if (APPLE)
        set_target_properties(simjot_native_cli PROPERTIES BUILD_RPATH "@loader_path")
    elseif(UNIX)
        set_target_properties(simjot_native_cli PROPERTIES BUILD_RPATH "\$ORIGIN")
    endif()

    enable_testing()
    add_test(NAME simjot_native_cli COMMAND simjot_native_cli)
endif()

# Standalone profiler CLI tool
add_executable(profiler_cli tests/profiler_cli.c)
target_include_directories(profiler_cli PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include")

if(APPLE)
    target_link_libraries(profiler_cli PRIVATE "-framework CoreFoundation")
endif()

set_target_properties(profiler_cli PROPERTIES OUTPUT_NAME "simjot_profiler")

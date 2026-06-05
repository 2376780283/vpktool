#示例 这不是给android用的
cmake_minimum_required(VERSION 3.18.1)

project("vpk")

# Add the directory containing Containment.h to include directories
# Assuming Containment.h is in app/src/main/jni/
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

add_library(vpk SHARED
    libvpk.c
    vpk_jni.c
)

target_include_directories(vpk PUBLIC .)

find_library(log-lib log)
target_link_libraries(vpk ${log-lib})
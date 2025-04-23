cmake_minimum_required(VERSION 3.31.6)
project(afinador_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/oboe/include)
add_subdirectory(oboe)

add_library(afinador_native
        SHARED
        NativeAudioEngine.cpp
)

find_library(log-lib     log)
find_library(android-lib android)

target_link_libraries(afinador_native
        PRIVATE
        ${log-lib}
        ${android-lib}
        oboe
)

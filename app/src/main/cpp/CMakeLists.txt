cmake_minimum_required(VERSION 3.22.1)

project("goldenorg")

# Oboe
find_package(oboe REQUIRED CONFIG)

# Audio Engine
add_library(goldenorg SHARED
    AudioEngine.cpp
)

# C++17
target_compile_features(goldenorg PRIVATE
    cxx_std_17
)

# Link libraries
target_link_libraries(goldenorg
    PRIVATE
    oboe::oboe
    log
)
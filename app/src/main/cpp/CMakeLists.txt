cmake_minimum_required(VERSION 3.22.1)
project(poolphysics LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_library(pool_engine STATIC pool_engine.cpp)
target_include_directories(pool_engine PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
add_library(poolphysics SHARED jni_bridge.cpp)
target_link_libraries(poolphysics PRIVATE pool_engine log)


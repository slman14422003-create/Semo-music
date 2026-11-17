cmake_minimum_required(VERSION 3.18)
project(semowhisper C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

set(W ${CMAKE_CURRENT_SOURCE_DIR}/whisper_cpp)

# CPU only, everything linked statically into one libsemowhisper.so
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
add_subdirectory(${W}/ggml ggml_build)

add_library(semowhisper SHARED
        ${W}/src/whisper.cpp
        ${W}/src/aneforge/whisper-aneforge.cpp
        jni.cpp)
target_compile_definitions(semowhisper PRIVATE GGML_USE_CPU WHISPER_VERSION="1.9.5")
target_include_directories(semowhisper PRIVATE ${W}/include ${W}/src ${W}/ggml/include)
find_library(LOG_LIB log)
target_link_libraries(semowhisper ggml ${LOG_LIB} android)
if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(semowhisper PRIVATE -O3 -ffunction-sections -fdata-sections)
    target_link_options(semowhisper PRIVATE -Wl,--gc-sections)
endif ()

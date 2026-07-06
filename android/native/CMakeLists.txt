cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

set(WHISPER_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/whisper.cpp)

if(NOT EXISTS ${WHISPER_CPP_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
        "whisper.cpp not found at ${WHISPER_CPP_DIR}. Run:\n"
        "  git submodule add https://github.com/ggml-org/whisper.cpp android/native/third_party/whisper.cpp\n"
        "then re-sync. See native/README.md.")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_CPP_DIR} whisper_cpp_build)

add_library(whisper_jni SHARED whisper_jni.cpp)

target_include_directories(whisper_jni PRIVATE
    ${WHISPER_CPP_DIR}/include
    ${WHISPER_CPP_DIR}/ggml/include
)

find_library(log-lib log)
target_link_libraries(whisper_jni PRIVATE whisper ${log-lib})

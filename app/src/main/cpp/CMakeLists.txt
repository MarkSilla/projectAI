cmake_minimum_required(VERSION 3.22)

project(aura_llama LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

if(NOT EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    find_package(Git REQUIRED)
    message(STATUS "llama.cpp source not found at ${LLAMA_CPP_DIR}; cloning upstream project automatically")

    execute_process(
        COMMAND ${GIT_EXECUTABLE} clone --depth 1 --branch master https://github.com/ggerganov/llama.cpp.git ${LLAMA_CPP_DIR}
        RESULT_VARIABLE LLAMA_CLONE_RESULT
        OUTPUT_VARIABLE LLAMA_CLONE_OUTPUT
        ERROR_VARIABLE LLAMA_CLONE_ERROR
        WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
    )

    if(NOT LLAMA_CLONE_RESULT EQUAL 0)
        message(FATAL_ERROR "Failed to clone llama.cpp into ${LLAMA_CPP_DIR}. ${LLAMA_CLONE_ERROR}")
    endif()
endif()

set(BUILD_SHARED_LIBS ON)

# Keep the Android build lean but functional.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH "ARM")
    set(GGML_OPENMP OFF)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(GGML_SYSTEM_ARCH "ARM")
    set(GGML_OPENMP OFF)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(GGML_SYSTEM_ARCH "x86")
    set(GGML_OPENMP OFF)
endif()

add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama_build)

add_library(aura_llama SHARED
    aura_llama_jni.cpp
)

target_include_directories(aura_llama PRIVATE
    ${LLAMA_CPP_DIR}
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
    ${LLAMA_CPP_DIR}/ggml/src
    ${LLAMA_CPP_DIR}/common
)

target_compile_definitions(aura_llama PRIVATE
    GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
    GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

target_link_libraries(aura_llama
    PRIVATE
        llama
        android
        log
)

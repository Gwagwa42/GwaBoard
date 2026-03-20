cmake_minimum_required(VERSION 3.22.1)
project(gwaboard_llm LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# ---------------------------------------------------------------------------
# llama.cpp vendor source (placed in llama.cpp/ subdirectory)
# ---------------------------------------------------------------------------

set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# Guard: only build if the vendor source is present
if(NOT EXISTS "${LLAMA_CPP_DIR}/llama.h")
    message(WARNING
        "llama.cpp source not vendored yet. "
        "See llama.cpp/VENDOR.md for setup instructions. "
        "Building JNI bridge stub only."
    )

    # Build a minimal stub library so Gradle/CMake doesn't fail
    add_library(gwaboard_llm SHARED
        llama_jni_bridge.cpp
    )

    target_compile_definitions(gwaboard_llm PRIVATE LLAMA_CPP_NOT_VENDORED=1)

    find_library(log-lib log)
    target_link_libraries(gwaboard_llm ${log-lib})

    return()
endif()

# ---------------------------------------------------------------------------
# ARM NEON + i8mm optimizations for Tensor G3 (Cortex-X3 / A715)
# ---------------------------------------------------------------------------

set(GGML_NATIVE OFF CACHE BOOL "Disable native arch detection for cross-compile")
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+i8mm+fp16" CACHE STRING "ARM ISA extensions")

# Enable NEON (always available on arm64-v8a)
add_compile_options(
    -march=armv8.2-a+dotprod+i8mm+fp16
    -mtune=cortex-x3
    -O3
    -ffast-math
    -DNDEBUG
)

# ---------------------------------------------------------------------------
# ggml core (tensor computation library)
# ---------------------------------------------------------------------------

set(GGML_SOURCES
    ${LLAMA_CPP_DIR}/ggml/src/ggml.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-alloc.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-backend.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-threading.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-quants.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/ggml-cpu.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/ggml-cpu-aarch64.c
)

add_library(ggml STATIC ${GGML_SOURCES})

target_include_directories(ggml PUBLIC
    ${LLAMA_CPP_DIR}/ggml/include
    ${LLAMA_CPP_DIR}/ggml/src
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu
)

# ---------------------------------------------------------------------------
# llama core (model loading, inference)
# ---------------------------------------------------------------------------

set(LLAMA_SOURCES
    ${LLAMA_CPP_DIR}/src/llama.cpp
    ${LLAMA_CPP_DIR}/src/llama-chat.cpp
    ${LLAMA_CPP_DIR}/src/llama-grammar.cpp
    ${LLAMA_CPP_DIR}/src/llama-sampling.cpp
    ${LLAMA_CPP_DIR}/src/llama-vocab.cpp
    ${LLAMA_CPP_DIR}/src/unicode.cpp
    ${LLAMA_CPP_DIR}/src/unicode-data.cpp
)

add_library(llama STATIC ${LLAMA_SOURCES})

target_include_directories(llama PUBLIC
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/src
    ${LLAMA_CPP_DIR}/ggml/include
)

target_link_libraries(llama PRIVATE ggml)

# ---------------------------------------------------------------------------
# llama common (sampling, tokenization helpers)
# ---------------------------------------------------------------------------

set(LLAMA_COMMON_SOURCES
    ${LLAMA_CPP_DIR}/common/common.cpp
    ${LLAMA_CPP_DIR}/common/sampling.cpp
    ${LLAMA_CPP_DIR}/common/json-schema-to-grammar.cpp
)

add_library(llama_common STATIC ${LLAMA_COMMON_SOURCES})

target_include_directories(llama_common PUBLIC
    ${LLAMA_CPP_DIR}/common
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
)

target_link_libraries(llama_common PRIVATE llama ggml)

# ---------------------------------------------------------------------------
# JNI bridge — exposed to Kotlin via LlamaCppBridge
# ---------------------------------------------------------------------------

add_library(gwaboard_llm SHARED
    llama_jni_bridge.cpp
)

find_library(log-lib log)

target_include_directories(gwaboard_llm PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/common
    ${LLAMA_CPP_DIR}/ggml/include
)

target_link_libraries(gwaboard_llm
    llama
    llama_common
    ggml
    ${log-lib}
)

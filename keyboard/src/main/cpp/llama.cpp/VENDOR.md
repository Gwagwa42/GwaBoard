# llama.cpp Vendor Directory

This directory should contain the vendored llama.cpp source code.

## Setup

Clone or copy the llama.cpp source into this directory:

```bash
# From the project root
git clone https://github.com/ggerganov/llama.cpp.git keyboard/src/main/cpp/llama.cpp/upstream
```

Required files from llama.cpp for the NDK build:
- `llama.h` / `llama.cpp` — Core inference API
- `ggml.h` / `ggml.c` / `ggml-alloc.*` / `ggml-backend.*` — Tensor library
- `common/` — Sampling, tokenization helpers
- `ggml-cpu/` — CPU backend (ARM NEON optimized)

## F-Droid Compliance

All C++ source is vendored (no binary blobs) to comply with F-Droid's
reproducible build requirements. The NDK version is pinned in
`gradle/libs.versions.toml` (ndk = "27.2.12479018").

## Target Architecture

- **ABI**: `arm64-v8a` only (Pixel 8A / Tensor G3 / Cortex-X3 + A715)
- **Optimizations**: ARM NEON + i8mm dot-product instructions
- **Quantization**: Q4_K_M (SmolLM2-360M-Instruct)

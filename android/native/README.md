# Native whisper.cpp JNI bridge

`whisper.cpp` itself is intentionally not vendored in this repo (it's a full separate project).
To build on-device transcription:

```bash
git submodule add https://github.com/ggml-org/whisper.cpp android/native/third_party/whisper.cpp
git submodule update --init --recursive
```

Then uncomment the `externalNativeBuild` block in `android/app/build.gradle.kts` so Gradle picks
up `native/CMakeLists.txt`.

## Model file

Download a GGML Whisper model (e.g. `ggml-base.en.bin` or `ggml-small.bin` for multilingual) from
whisper.cpp's model repo and either:

- bundle it under `app/src/main/assets/models/` (increases APK size, works offline out of the box), or
- download it on first run to app-private storage (keeps the APK small, needs one-time network access).

`WhisperBridge(modelPath)` just needs an absolute path to whichever `.bin` file you choose - it
doesn't care which of the above got it there.

## Status

This JNI shim (`whisper_jni.cpp`) was written without access to an Android NDK toolchain or a real
device to build/run against, so treat it as a solid, carefully-written starting point rather than
something already proven to compile. Things worth double-checking once you build it for real:

- `whisper_init_from_file_with_params` / `whisper_context_default_params` signatures match
  whichever whisper.cpp commit you pin (the C API has moved around between releases).
- GPU backends (Vulkan/OpenCL) are disabled by default above for a simpler, more portable first
  build; enable one once CPU-only inference is confirmed working, since a small model on CPU
  should already be reasonably fast on a modern phone.

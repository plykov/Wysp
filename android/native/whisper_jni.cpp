// JNI glue between WhisperBridge.kt and whisper.cpp's public C API (whisper.h).
//
// Not buildable as-is: whisper.cpp is not vendored in this repo (see native/README.md to add it
// as a submodule under native/third_party/whisper.cpp). Written carefully but unverified against
// a real NDK build - there is no Android SDK/NDK in the environment this was authored in, so
// treat this as a strong starting point to compile and fix up on your own machine, not as
// something already proven to build.

#include <jni.h>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#include "whisper.h"

namespace {

struct WavAudio {
    std::vector<float> samples; // mono, normalized to [-1, 1]
    int sampleRate = 0;
    bool ok = false;
};

// Minimal PCM16 mono/stereo WAV reader (44-byte canonical header + "data" chunk). Good enough for
// files this app itself writes via WavFileWriter.kt; not a general-purpose WAV parser.
WavAudio readWav(const std::string &path) {
    WavAudio result;
    std::ifstream file(path, std::ios::binary);
    if (!file) return result;

    char riff[4];
    file.read(riff, 4);
    if (std::strncmp(riff, "RIFF", 4) != 0) return result;
    file.seekg(8);
    char wave[4];
    file.read(wave, 4);
    if (std::strncmp(wave, "WAVE", 4) != 0) return result;

    uint16_t numChannels = 1;
    uint32_t sampleRate = 16000;
    uint16_t bitsPerSample = 16;
    bool haveFmt = false;

    char chunkId[4];
    uint32_t chunkSize;
    while (file.read(chunkId, 4)) {
        file.read(reinterpret_cast<char *>(&chunkSize), 4);
        if (std::strncmp(chunkId, "fmt ", 4) == 0) {
            uint16_t audioFormat;
            file.read(reinterpret_cast<char *>(&audioFormat), 2);
            file.read(reinterpret_cast<char *>(&numChannels), 2);
            file.read(reinterpret_cast<char *>(&sampleRate), 4);
            file.seekg(6, std::ios::cur); // byteRate(4) + blockAlign(2)
            file.read(reinterpret_cast<char *>(&bitsPerSample), 2);
            if (chunkSize > 16) file.seekg(chunkSize - 16, std::ios::cur);
            haveFmt = true;
        } else if (std::strncmp(chunkId, "data", 4) == 0) {
            if (!haveFmt || bitsPerSample != 16) return result;

            std::vector<int16_t> raw(chunkSize / 2);
            file.read(reinterpret_cast<char *>(raw.data()), chunkSize);

            result.samples.reserve(raw.size() / numChannels);
            for (size_t i = 0; i < raw.size(); i += numChannels) {
                // Downmix to mono by taking channel 0 if stereo; our own writer only emits mono anyway.
                result.samples.push_back(static_cast<float>(raw[i]) / 32768.0f);
            }
            result.sampleRate = static_cast<int>(sampleRate);
            result.ok = true;
            break;
        } else {
            file.seekg(chunkSize, std::ios::cur);
        }
    }
    return result;
}

std::string jsonEscape(const std::string &text) {
    std::string out;
    out.reserve(text.size());
    for (char c : text) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += c;
                }
        }
    }
    return out;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_wysp_krysp_android_transcribe_WhisperBridge_nativeInit(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wysp_krysp_android_transcribe_WhisperBridge_nativeTranscribe(
    JNIEnv *env, jobject, jlong handle, jstring wavPath) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(handle);
    if (ctx == nullptr) return env->NewStringUTF("{\"segments\":[]}");

    const char *pathChars = env->GetStringUTFChars(wavPath, nullptr);
    WavAudio audio = readWav(pathChars);
    env->ReleaseStringUTFChars(wavPath, pathChars);

    if (!audio.ok || audio.sampleRate != WHISPER_SAMPLE_RATE) {
        // WavFileWriter.kt always writes 16kHz mono, matching WHISPER_SAMPLE_RATE, so this
        // should only trip if the file is missing/corrupt or the writer's sample rate changes.
        return env->NewStringUTF("{\"segments\":[]}");
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    params.n_threads = 4;

    if (whisper_full(ctx, params, audio.samples.data(), static_cast<int>(audio.samples.size())) != 0) {
        return env->NewStringUTF("{\"segments\":[]}");
    }

    std::ostringstream json;
    json << "{\"segments\":[";
    const int numSegments = whisper_full_n_segments(ctx);
    for (int i = 0; i < numSegments; i++) {
        // whisper timestamps are in 10ms units.
        double start = whisper_full_get_segment_t0(ctx, i) * 0.01;
        double end = whisper_full_get_segment_t1(ctx, i) * 0.01;
        std::string text = whisper_full_get_segment_text(ctx, i);
        if (i > 0) json << ",";
        json << "{\"start\":" << start << ",\"end\":" << end
             << ",\"text\":\"" << jsonEscape(text) << "\"}";
    }
    json << "]}";

    return env->NewStringUTF(json.str().c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_wysp_krysp_android_transcribe_WhisperBridge_nativeRelease(JNIEnv *, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}

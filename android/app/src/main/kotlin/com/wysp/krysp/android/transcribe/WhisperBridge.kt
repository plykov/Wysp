package com.wysp.krysp.android.transcribe

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the native side hands back for one recognized segment. */
@Serializable
data class WhisperSegment(val start: Double, val end: Double, val text: String)

@Serializable
private data class WhisperSegmentsJson(val segments: List<WhisperSegment>)

/**
 * JNI bridge to a small C++ shim (native/whisper_jni.cpp) around whisper.cpp's public C API,
 * running speech recognition fully on-device. whisper.cpp itself isn't vendored in this repo to
 * keep it small - see native/README.md to add it as a submodule and enable the CMake build in
 * app/build.gradle.kts before this class will actually load/link anything.
 *
 * The native side returns one JSON string per call (rather than an array of JNI objects, or
 * multiple parallel-array round trips that would each re-run inference) - this keeps the JNI
 * surface to a single jstring in, jstring out per method, which is the simplest boundary to get
 * right and the cheapest to keep correct as the native side evolves.
 */
class WhisperBridge(modelPath: String) : AutoCloseable {
    private var nativeHandle: Long = nativeInit(modelPath)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        check(nativeHandle != 0L) { "Failed to load Whisper model from $modelPath" }
    }

    /** Transcribes a 16kHz mono PCM16 WAV file. */
    fun transcribeWav(wavPath: String): List<WhisperSegment> {
        check(nativeHandle != 0L) { "WhisperBridge already closed." }
        val raw = nativeTranscribe(nativeHandle, wavPath)
        return json.decodeFromString(WhisperSegmentsJson.serializer(), raw).segments
    }

    override fun close() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    private external fun nativeInit(modelPath: String): Long

    /** Returns a JSON string: `{"segments":[{"start":0.0,"end":1.2,"text":"..."}, ...]}`. */
    private external fun nativeTranscribe(handle: Long, wavPath: String): String
    private external fun nativeRelease(handle: Long)

    companion object {
        init {
            System.loadLibrary("whisper_jni")
        }
    }
}

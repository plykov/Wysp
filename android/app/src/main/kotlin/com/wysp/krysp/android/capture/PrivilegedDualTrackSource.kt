package com.wysp.krysp.android.capture

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Root/priv-app-only capture path: two independently-attributed tracks.
 *
 * "You" is a normal mic uplink AudioRecord. "Call" is captured via
 * [AudioPlaybackCaptureConfiguration] filtered to [AudioAttributes.USAGE_VOICE_COMMUNICATION] -
 * the exact usage tag Zoom/WeChat/Telegram apply to call audio. On a normal (non-privileged)
 * install, the OS silently excludes that usage from playback capture regardless of the
 * MediaProjection consent grant; it's only actually captured when this APK is installed as a
 * system priv-app allowlisted for CAPTURE_AUDIO_OUTPUT / CAPTURE_VOICE_COMMUNICATION_OUTPUT
 * (see /android/magisk-module). Behavior here is genuinely version/OEM-dependent - verify on
 * your actual device and see android/README.md's troubleshooting section if the call track
 * comes back silent despite the permission being granted.
 *
 * A [MediaProjection] token is still required to construct the playback-capture config even
 * though a privileged permission is what actually authorizes the voice-communication usage; this
 * means the user still sees the one-time system "Start recording?" prompt when the projection is
 * requested. That's kept deliberately rather than routed around via hidden APIs, both for API
 * stability across Android versions and because it doubles as a visible recording-consent signal.
 */
class PrivilegedDualTrackSource(private val mediaProjection: MediaProjection) : CallAudioSource {
    override val producesSeparateTracks = true

    private val sampleRate = 16000
    private var youRecord: AudioRecord? = null
    private var callRecord: AudioRecord? = null
    private var youThread: Thread? = null
    private var callThread: Thread? = null
    private val running = AtomicBoolean(false)

    @SuppressLint("MissingPermission") // CAPTURE_AUDIO_OUTPUT checked by caller via SourceAvailability
    override fun start(outputDir: File): CaptureOutput {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "AudioPlaybackCaptureConfiguration requires API 29+."
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBufferSize > 0) { "Device doesn't support ${sampleRate}Hz mono 16-bit capture." }

        val you = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
        check(you.state == AudioRecord.STATE_INITIALIZED) { "Failed to initialize mic AudioRecord." }

        val playbackCaptureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val call = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufferSize * 2)
            .setAudioPlaybackCaptureConfig(playbackCaptureConfig)
            .build()
        check(call.state == AudioRecord.STATE_INITIALIZED) {
            "Failed to initialize call-audio playback capture. " +
                "This APK likely isn't installed as a privileged system app with " +
                "CAPTURE_AUDIO_OUTPUT/CAPTURE_VOICE_COMMUNICATION_OUTPUT granted - see " +
                "android/magisk-module."
        }

        val youWav = File(outputDir, "you.wav")
        val callWav = File(outputDir, "call.wav")

        youRecord = you
        callRecord = call
        running.set(true)

        you.startRecording()
        call.startRecording()

        youThread = startCaptureThread(you, youWav, minBufferSize)
        callThread = startCaptureThread(call, callWav, minBufferSize)

        return CaptureOutput(youWav = youWav, callWav = callWav)
    }

    private fun startCaptureThread(record: AudioRecord, outFile: File, bufferSize: Int): Thread {
        val writer = WavFileWriter(outFile, sampleRate, channels = 1)
        return thread(name = "capture-${outFile.name}") {
            val buffer = ByteArray(bufferSize)
            while (running.get()) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) writer.write(buffer, read)
            }
            writer.close()
        }
    }

    override fun stop() {
        running.set(false)
        youThread?.join(2000)
        callThread?.join(2000)
        youThread = null
        callThread = null

        youRecord?.stop()
        youRecord?.release()
        youRecord = null

        callRecord?.stop()
        callRecord?.release()
        callRecord = null

        mediaProjection.stop()
    }
}

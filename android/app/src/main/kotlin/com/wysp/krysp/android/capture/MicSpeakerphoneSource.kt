package com.wysp.krysp.android.capture

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Always-available capture path: records the microphone while the call is on speakerphone,
 * so the mic picks up both your voice and (via the speaker) the other participant(s). Works with
 * any calling app (Zoom, WeChat, Telegram, regular calls) since it doesn't touch the call app at
 * all - it's just ambient audio capture. Produces a single mixed mono track; see
 * [com.wysp.krysp.core.naiveDiarize] for the best-effort turn-splitting used on this track.
 */
class MicSpeakerphoneSource(private val context: Context) : CallAudioSource {
    override val producesSeparateTracks = false

    private val sampleRate = 16000
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val running = AtomicBoolean(false)

    // Lint's MissingPermission check can't trace the permission check through
    // requireRecordAudioPermission() into this method - it's genuinely checked, just not in a
    // form lint's dataflow analysis recognizes.
    @SuppressLint("MissingPermission")
    override fun start(outputDir: File): CaptureOutput {
        requireRecordAudioPermission(context)

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBufferSize > 0) { "Device doesn't support ${sampleRate}Hz mono 16-bit capture." }

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Failed to initialize AudioRecord." }

        val outFile = File(outputDir, "mixed.wav")
        val writer = WavFileWriter(outFile, sampleRate, channels = 1)

        audioRecord = record
        running.set(true)
        record.startRecording()

        recordingThread = thread(name = "mic-speakerphone-capture") {
            val buffer = ByteArray(minBufferSize)
            while (running.get()) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) writer.write(buffer, read)
            }
            writer.close()
        }

        return CaptureOutput(youWav = outFile, callWav = null)
    }

    override fun stop() {
        running.set(false)
        recordingThread?.join(2000)
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}

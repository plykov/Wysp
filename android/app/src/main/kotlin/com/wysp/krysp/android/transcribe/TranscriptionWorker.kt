package com.wysp.krysp.android.transcribe

import com.wysp.krysp.android.storage.RecordingMeta
import com.wysp.krysp.android.storage.RecordingsStore
import com.wysp.krysp.core.RawSegment
import com.wysp.krysp.core.Track
import com.wysp.krysp.core.TranscriptSegment
import com.wysp.krysp.core.formatMinutes
import com.wysp.krysp.core.mergeSegments
import com.wysp.krysp.core.naiveDiarize
import java.io.File

/**
 * Ties WhisperBridge (on-device ASR) to the `core` module's merge/diarization/summary/action-item
 * logic, and writes the resulting Markdown minutes doc to disk. Call off the main thread - both
 * whisper inference and file I/O here are blocking.
 */
class TranscriptionWorker(private val modelPath: String) {

    fun transcribe(meta: RecordingMeta, store: RecordingsStore, onProgress: (String) -> Unit = {}): String {
        WhisperBridge(modelPath).use { whisper ->
            val timeline = if (meta.producesSeparateTracks) {
                onProgress("Transcribing your mic track...")
                val youSegments = whisper.transcribeWav(meta.youWavPath).map {
                    TranscriptSegment(Track.YOU, "You", it.start, it.end, it.text)
                }

                onProgress("Transcribing call audio track...")
                val callSegments = whisper.transcribeWav(meta.callWavPath!!).map {
                    TranscriptSegment(Track.CALL, "Call", it.start, it.end, it.text)
                }

                mergeSegments(youSegments, callSegments)
            } else {
                onProgress("Transcribing mixed audio track...")
                val raw = whisper.transcribeWav(meta.youWavPath).map { RawSegment(it.start, it.end, it.text) }
                naiveDiarize(raw)
            }

            onProgress("Building summary and action items...")
            val minutes = formatMinutes(meta.name, timeline)

            onProgress("Saving minutes...")
            File(meta.minutesPath).writeText(minutes)
            store.markTranscribed(meta)

            onProgress("Done.")
            return minutes
        }
    }
}

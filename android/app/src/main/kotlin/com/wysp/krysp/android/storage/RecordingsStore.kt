package com.wysp.krysp.android.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class RecordingMeta(
    val id: String,
    val name: String,
    val createdAt: String,
    val producesSeparateTracks: Boolean,
    val youWavPath: String,
    val callWavPath: String?,
    val minutesPath: String,
    val transcribed: Boolean = false,
)

/** Mirrors the Windows companion app's storage.py: `<baseDir>/<recordingId>/` + a meta.json sidecar. */
class RecordingsStore(private val baseDir: File) {
    companion object {
        private const val META_FILENAME = "meta.json"
        private val ID_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun createRecording(name: String? = null, producesSeparateTracks: Boolean, `when`: Date = Date()): RecordingMeta {
        var id = ID_FORMAT.format(`when`)
        var dir = File(baseDir, id)
        var suffix = 1
        val base = id
        while (dir.exists()) {
            id = "$base-$suffix"
            dir = File(baseDir, id)
            suffix++
        }
        dir.mkdirs()

        val meta = RecordingMeta(
            id = id,
            name = name ?: id,
            createdAt = `when`.toInstant().toString(),
            producesSeparateTracks = producesSeparateTracks,
            youWavPath = File(dir, if (producesSeparateTracks) "you.wav" else "mixed.wav").absolutePath,
            callWavPath = if (producesSeparateTracks) File(dir, "call.wav").absolutePath else null,
            minutesPath = File(dir, "minutes.md").absolutePath,
            transcribed = false,
        )
        saveMeta(dir, meta)
        return meta
    }

    fun listRecordings(): List<RecordingMeta> {
        if (!baseDir.exists()) return emptyList()
        return baseDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir -> readMeta(dir) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    fun getRecording(id: String): RecordingMeta? = readMeta(File(baseDir, id))

    fun markTranscribed(meta: RecordingMeta): RecordingMeta {
        val updated = meta.copy(transcribed = true)
        saveMeta(File(baseDir, meta.id), updated)
        return updated
    }

    fun deleteRecording(meta: RecordingMeta) {
        File(baseDir, meta.id).deleteRecursively()
    }

    private fun saveMeta(dir: File, meta: RecordingMeta) {
        File(dir, META_FILENAME).writeText(json.encodeToString(RecordingMeta.serializer(), meta))
    }

    private fun readMeta(dir: File): RecordingMeta? {
        val metaFile = File(dir, META_FILENAME)
        if (!metaFile.exists()) return null
        return try {
            json.decodeFromString(RecordingMeta.serializer(), metaFile.readText())
        } catch (e: Exception) {
            null
        }
    }
}

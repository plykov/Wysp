package com.wysp.krysp.android.capture

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal streaming PCM16 mono/stereo WAV writer (Android has no built-in equivalent of Python's `wave`). */
class WavFileWriter(private val file: File, private val sampleRate: Int, private val channels: Int) {
    private val stream = BufferedOutputStream(FileOutputStream(file))
    private var dataBytesWritten = 0L

    init {
        // Placeholder header; sizes get patched in on close().
        stream.write(ByteArray(44))
    }

    fun write(buffer: ByteArray, length: Int) {
        stream.write(buffer, 0, length)
        dataBytesWritten += length
    }

    fun close() {
        stream.flush()
        stream.close()
        patchHeader()
    }

    private fun patchHeader() {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        header.put("RIFF".toByteArray())
        header.putInt((36 + dataBytesWritten).toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // PCM fmt chunk size
        header.putShort(1) // audio format = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray())
        header.putInt(dataBytesWritten.toInt())

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header.array())
        }
    }
}

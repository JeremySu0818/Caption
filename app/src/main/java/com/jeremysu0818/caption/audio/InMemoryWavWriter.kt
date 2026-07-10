package com.jeremysu0818.caption.audio

import java.io.File
import java.io.FileOutputStream

/**
 * Writes PCM-16 mono audio data to a WAV file using an in-memory buffer
 * to minimise disk-I/O overhead. The whole WAV (header + body) is built
 * in RAM first, then flushed to disk in a single write.
 *
 * For typical streaming chunks (0.5–3 s at 16 kHz) the byte array is
 * only ~16–96 KB, so memory pressure is negligible.
 */
object InMemoryWavWriter {
    private const val BYTES_PER_SAMPLE = 2
    private const val WAV_HEADER_SIZE = 44
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16

    /**
     * Writes [samples] as a 16 kHz, mono, 16-bit PCM WAV to [file].
     * The file is created/overwritten atomically.
     */
    fun write(file: File, samples: ShortArray, sampleRate: Int = SystemAudioCapture.SAMPLE_RATE) {
        val dataSize = samples.size * BYTES_PER_SAMPLE
        val totalSize = WAV_HEADER_SIZE + dataSize
        val buf = ByteArray(totalSize)


        writeAscii(buf, 0, "RIFF")
        writeInt32Le(buf, 4, totalSize - 8)
        writeAscii(buf, 8, "WAVE")


        writeAscii(buf, 12, "fmt ")
        writeInt32Le(buf, 16, 16)
        writeInt16Le(buf, 20, 1)
        writeInt16Le(buf, 22, CHANNELS)
        writeInt32Le(buf, 24, sampleRate)
        writeInt32Le(buf, 28, sampleRate * CHANNELS * BYTES_PER_SAMPLE)
        writeInt16Le(buf, 32, CHANNELS * BYTES_PER_SAMPLE)
        writeInt16Le(buf, 34, BITS_PER_SAMPLE)


        writeAscii(buf, 36, "data")
        writeInt32Le(buf, 40, dataSize)


        var offset = WAV_HEADER_SIZE
        for (s in samples) {
            val v = s.toInt()
            buf[offset++] = (v and 0xFF).toByte()
            buf[offset++] = (v shr 8 and 0xFF).toByte()
        }

        file.parentFile?.mkdirs()
        FileOutputStream(file).use { it.write(buf) }
    }

    /**
     * Writes float samples (−1..1) as a 16 kHz, mono, 16-bit PCM WAV to [file].
     */
    fun writeFloat(file: File, samples: FloatArray, sampleRate: Int = SystemAudioCapture.SAMPLE_RATE) {
        val dataSize = samples.size * BYTES_PER_SAMPLE
        val totalSize = WAV_HEADER_SIZE + dataSize
        val buf = ByteArray(totalSize)

        writeAscii(buf, 0, "RIFF")
        writeInt32Le(buf, 4, totalSize - 8)
        writeAscii(buf, 8, "WAVE")
        writeAscii(buf, 12, "fmt ")
        writeInt32Le(buf, 16, 16)
        writeInt16Le(buf, 20, 1)
        writeInt16Le(buf, 22, CHANNELS)
        writeInt32Le(buf, 24, sampleRate)
        writeInt32Le(buf, 28, sampleRate * CHANNELS * BYTES_PER_SAMPLE)
        writeInt16Le(buf, 32, CHANNELS * BYTES_PER_SAMPLE)
        writeInt16Le(buf, 34, BITS_PER_SAMPLE)
        writeAscii(buf, 36, "data")
        writeInt32Le(buf, 40, dataSize)

        var offset = WAV_HEADER_SIZE
        for (s in samples) {
            val clamped = s.coerceIn(-1f, 1f)
            val v = (clamped * 32767f).toInt().toShort().toInt()
            buf[offset++] = (v and 0xFF).toByte()
            buf[offset++] = (v shr 8 and 0xFF).toByte()
        }

        file.parentFile?.mkdirs()
        FileOutputStream(file).use { it.write(buf) }
    }


    private fun writeAscii(buf: ByteArray, offset: Int, s: String) {
        for (i in s.indices) buf[offset + i] = s[i].code.toByte()
    }

    private fun writeInt32Le(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = (value shr 8 and 0xFF).toByte()
        buf[offset + 2] = (value shr 16 and 0xFF).toByte()
        buf[offset + 3] = (value shr 24 and 0xFF).toByte()
    }

    private fun writeInt16Le(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = (value shr 8 and 0xFF).toByte()
    }
}

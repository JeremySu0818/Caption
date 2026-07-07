package com.jeremysu0818.caption.audio

import java.io.File
import java.io.FileOutputStream

object WavFileWriter {
    fun writePcm16Mono(file: File, samples: ShortArray, sampleRate: Int = SystemAudioCapture.SAMPLE_RATE) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            val dataSize = samples.size * BYTES_PER_SAMPLE
            val byteRate = sampleRate * BYTES_PER_SAMPLE

            output.writeAscii("RIFF")
            output.writeIntLe(WAV_HEADER_SIZE + dataSize - 8)
            output.writeAscii("WAVE")
            output.writeAscii("fmt ")
            output.writeIntLe(16)
            output.writeShortLe(1)
            output.writeShortLe(1)
            output.writeIntLe(sampleRate)
            output.writeIntLe(byteRate)
            output.writeShortLe(BYTES_PER_SAMPLE)
            output.writeShortLe(16)
            output.writeAscii("data")
            output.writeIntLe(dataSize)

            samples.forEach { output.writeShortLe(it.toInt()) }
        }
    }

    private fun FileOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun FileOutputStream.writeIntLe(value: Int) {
        write(value and 0xff)
        write(value shr 8 and 0xff)
        write(value shr 16 and 0xff)
        write(value shr 24 and 0xff)
    }

    private fun FileOutputStream.writeShortLe(value: Int) {
        write(value and 0xff)
        write(value shr 8 and 0xff)
    }

    private const val BYTES_PER_SAMPLE = 2
    private const val WAV_HEADER_SIZE = 44
}

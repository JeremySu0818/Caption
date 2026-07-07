package com.jeremysu0818.caption.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SystemAudioCapture(private val mediaProjection: MediaProjection) {
    suspend fun captureChunks(
        output: SendChannel<ShortArray>,
        chunkDurationMs: Int = CHUNK_DURATION_MS,
    ) = withContext(Dispatchers.IO) {
        val audioRecord = createAudioRecord()
        val samplesPerChunk = SAMPLE_RATE * chunkDurationMs / 1000
        val chunk = ShortArray(samplesPerChunk)
        val buffer = ShortArray(READ_BUFFER_SAMPLES)
        var chunkOffset = 0

        try {
            audioRecord.startRecording()
            while (true) {
                currentCoroutineContext().ensureActive()
                val readLimit = min(buffer.size, samplesPerChunk - chunkOffset)
                val read = audioRecord.read(buffer, 0, readLimit, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue

                System.arraycopy(buffer, 0, chunk, chunkOffset, read)
                chunkOffset += read

                if (chunkOffset >= samplesPerChunk) {
                    output.trySend(chunk.copyOf())
                    chunkOffset = 0
                }
            }
        } finally {
            if (chunkOffset > SAMPLE_RATE) {
                output.trySend(chunk.copyOf(chunkOffset))
            }
            audioRecord.stop()
            audioRecord.release()
            output.close()
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord {
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = minBufferSize
            .coerceAtLeast(READ_BUFFER_SAMPLES * BYTES_PER_SAMPLE)
            .coerceAtLeast(SAMPLE_RATE * BYTES_PER_SAMPLE)

        return AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .build()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHUNK_DURATION_MS = 5_000
        private const val READ_BUFFER_SAMPLES = 2_048
        private const val BYTES_PER_SAMPLE = 2
    }
}

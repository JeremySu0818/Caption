package com.jeremysu0818.caption.whisper

import android.content.Context
import android.util.Log
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WhisperTranscriber(private val context: Context) {

    companion object {
        private const val TAG = "WhisperTranscriber"
    }

    private val mutex = Mutex()
    private var loadedModel: WhisperModel? = null
    private var loadedModelPath: String? = null

    /**
     * Original batch transcription from a WAV file — used by both the
     * legacy loop and the new streaming loop.
     */
    suspend fun transcribe(wavFile: File, modelFile: File, languageTag: String): String =
        mutex.withLock {
            val model = loadModel(modelFile)
            val result = Whisper.transcribe(
                model = model,
                audioPath = wavFile.absolutePath,
                config = WhisperConfig(
                    language = languageTag.ifBlank { "auto" },
                    translate = false,
                    threads = preferredThreadCount(),
                    maxSegmentLength = 0,
                    printTimestamps = false,
                ),
            )
            result.text.trim()
        }

    /**
     * Pre-loads the model so that the first streaming inference
     * doesn't incur model-load latency.
     */
    suspend fun ensureModelLoaded(modelFile: File) = mutex.withLock {
        loadModel(modelFile)
    }

    suspend fun release() = mutex.withLock {
        loadedModel?.let { Whisper.releaseModel(it) }
        loadedModel = null
        loadedModelPath = null
    }

    private suspend fun loadModel(modelFile: File): WhisperModel {
        val path = modelFile.absolutePath
        val current = loadedModel
        if (current != null && loadedModelPath == path && current.isValid) {
            return current
        }

        current?.let { Whisper.releaseModel(it) }
        Log.d(TAG, "Loading Whisper model: $path")
        val next = Whisper.loadModel(context, path)
        loadedModel = next
        loadedModelPath = path
        Log.d(TAG, "Whisper model loaded successfully")
        return next
    }

    private fun preferredThreadCount(): Int =
        (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)
}

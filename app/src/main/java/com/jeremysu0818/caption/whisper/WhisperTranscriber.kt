package com.jeremysu0818.caption.whisper

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WhisperTranscriber(private val context: Context) {
    private val mutex = Mutex()
    private var loadedModel: WhisperModel? = null
    private var loadedModelPath: String? = null

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
        val next = Whisper.loadModel(context, path)
        loadedModel = next
        loadedModelPath = path
        return next
    }

    private fun preferredThreadCount(): Int =
        (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)
}

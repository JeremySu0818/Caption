package com.jeremysu0818.caption.mlkit

import android.os.ParcelFileDescriptor
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import com.jeremysu0818.caption.audio.SystemAudioCapture
import com.jeremysu0818.caption.data.SpeechEngineOption
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class MlKitSpeechTranscriber {
    private val mutex = Mutex()
    private var recognizer: SpeechRecognizer? = null
    private var recognizerKey: String? = null

    suspend fun isAdvancedAvailable(languageTag: String): Boolean = mutex.withLock {
        isEngineAvailable(languageTag, SpeechEngineOption.MLKIT_ADVANCED)
    }

    suspend fun transcribe(
        samples: ShortArray,
        languageTag: String,
        engine: SpeechEngineOption,
        onStatus: suspend (String) -> Unit,
        onPartialText: suspend (String) -> Unit,
    ): String = mutex.withLock {
        val locale = Locale.forLanguageTag(languageTag.toMlKitLocaleTag())
        val mode = when (engine) {
            SpeechEngineOption.MLKIT_ADVANCED -> SpeechRecognizerOptions.Mode.MODE_ADVANCED
            else -> SpeechRecognizerOptions.Mode.MODE_BASIC
        }
        val client = recognizerFor(locale, mode)
        prepare(client, engine, onStatus)
        recognizeFromSamples(client, samples, onPartialText)
    }

    suspend fun stream(
        audioChunks: ReceiveChannel<ShortArray>,
        languageTag: String,
        engine: SpeechEngineOption,
        onStatus: suspend (String) -> Unit,
        onPartialText: suspend (String) -> Unit,
        onFinalText: suspend (String) -> Unit,
    ) = mutex.withLock {
        val locale = Locale.forLanguageTag(languageTag.toMlKitLocaleTag())
        val mode = when (engine) {
            SpeechEngineOption.MLKIT_ADVANCED -> SpeechRecognizerOptions.Mode.MODE_ADVANCED
            else -> SpeechRecognizerOptions.Mode.MODE_BASIC
        }
        val client = recognizerFor(locale, mode)
        prepare(client, engine, onStatus)
        streamFromChunks(client, audioChunks, onPartialText, onFinalText)
    }

    suspend fun close() = mutex.withLock {
        recognizer?.close()
        recognizer = null
        recognizerKey = null
    }

    private fun recognizerFor(locale: Locale, mode: Int): SpeechRecognizer {
        val key = "${locale.toLanguageTag()}:$mode"
        val current = recognizer
        if (current != null && recognizerKey == key) return current

        current?.close()
        return SpeechRecognition.getClient(
            speechRecognizerOptions {
                this.locale = locale
                this.preferredMode = mode
            },
        ).also {
            recognizer = it
            recognizerKey = key
        }
    }

    private suspend fun isEngineAvailable(
        languageTag: String,
        engine: SpeechEngineOption,
    ): Boolean {
        val locale = Locale.forLanguageTag(languageTag.toMlKitLocaleTag())
        val mode = when (engine) {
            SpeechEngineOption.MLKIT_ADVANCED -> SpeechRecognizerOptions.Mode.MODE_ADVANCED
            else -> SpeechRecognizerOptions.Mode.MODE_BASIC
        }
        return when (recognizerFor(locale, mode).checkStatus()) {
            FeatureStatus.AVAILABLE,
            FeatureStatus.DOWNLOADABLE,
            FeatureStatus.DOWNLOADING,
            -> true
            else -> false
        }
    }

    private suspend fun prepare(
        client: SpeechRecognizer,
        engine: SpeechEngineOption,
        onStatus: suspend (String) -> Unit,
    ) {
        when (val status = client.checkStatus()) {
            FeatureStatus.AVAILABLE -> return
            FeatureStatus.DOWNLOADABLE -> {
                onStatus("下載 ${engine.label} 語音模型")
                client.download().collect { downloadStatus ->
                    when (downloadStatus) {
                        is DownloadStatus.DownloadStarted -> onStatus("下載 ${engine.label} 語音模型")
                        is DownloadStatus.DownloadProgress -> onStatus(
                            "下載 ${engine.label} ${(downloadStatus.totalBytesDownloaded / 1024 / 1024)} MB"
                        )
                        is DownloadStatus.DownloadCompleted -> onStatus("${engine.label} 模型已就緒")
                        is DownloadStatus.DownloadFailed -> throw downloadStatus.e
                    }
                }
            }
            FeatureStatus.DOWNLOADING -> throw IllegalStateException("${engine.label} 模型仍在下載中。")
            FeatureStatus.UNAVAILABLE -> throw IllegalStateException("${engine.label} 不支援此裝置或語言。")
            else -> throw IllegalStateException("${engine.label} 狀態不可用：$status")
        }
    }

    private suspend fun recognizeFromSamples(
        client: SpeechRecognizer,
        samples: ShortArray,
        onPartialText: suspend (String) -> Unit,
    ): String = coroutineScope {
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]
        val request = speechRecognizerRequest {
            audioSource = AudioSource.fromPfd(readSide)
        }
        val recognition = async(Dispatchers.Default) {
            val builder = StringBuilder()
            var latestPartial = ""
            client.startRecognition(request).collect { response ->
                when (response) {
                    is SpeechRecognizerResponse.FinalTextResponse -> {
                        if (response.text.isNotBlank()) {
                            builder.append(response.text).append(' ')
                            onPartialText(builder.toString().trim())
                        }
                    }
                    is SpeechRecognizerResponse.PartialTextResponse -> {
                        latestPartial = response.text
                        if (latestPartial.isNotBlank()) onPartialText(latestPartial)
                    }
                    is SpeechRecognizerResponse.CompletedResponse -> return@collect
                    is SpeechRecognizerResponse.ErrorResponse -> throw response.e
                }
            }
            builder.toString().trim().ifBlank { latestPartial.trim() }
        }

        val writer = async(Dispatchers.IO) {
            FileOutputStream(writeSide.fileDescriptor).use { output ->
                var offset = 0
                while (offset < samples.size) {
                    val count = minOf(SAMPLES_PER_WRITE, samples.size - offset)
                    output.write(samples.toPcmBytes(offset, count))
                    offset += count
                }
                output.flush()
            }
        }

        try {
            writer.await()
            runCatching { writeSide.close() }
            delay(RECOGNITION_DRAIN_MS)
            runCatching { client.stopRecognition() }
            withTimeoutOrNull(RECOGNITION_TIMEOUT_MS) {
                recognition.await()
            }.orEmpty()
        } finally {
            runCatching { readSide.close() }
            runCatching { writeSide.close() }
        }
    }

    private suspend fun streamFromChunks(
        client: SpeechRecognizer,
        audioChunks: ReceiveChannel<ShortArray>,
        onPartialText: suspend (String) -> Unit,
        onFinalText: suspend (String) -> Unit,
    ) = coroutineScope {
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]
        val request = speechRecognizerRequest {
            audioSource = AudioSource.fromPfd(readSide)
        }
        val recognition = async(Dispatchers.Default) {
            client.startRecognition(request).collect { response ->
                when (response) {
                    is SpeechRecognizerResponse.FinalTextResponse -> {
                        if (response.text.isNotBlank()) onFinalText(response.text.trim())
                    }
                    is SpeechRecognizerResponse.PartialTextResponse -> {
                        if (response.text.isNotBlank()) onPartialText(response.text.trim())
                    }
                    is SpeechRecognizerResponse.CompletedResponse -> Unit
                    is SpeechRecognizerResponse.ErrorResponse -> throw response.e
                }
            }
        }
        val writer = async(Dispatchers.IO) {
            FileOutputStream(writeSide.fileDescriptor).use { output ->
                while (isActive) {
                    val samples = audioChunks.receiveCatching().getOrNull() ?: break
                    output.write(samples.toPcmBytes(0, samples.size))
                    output.flush()
                }
            }
        }

        try {
            writer.await()
            runCatching { writeSide.close() }
            withTimeoutOrNull(RECOGNITION_TIMEOUT_MS) {
                recognition.await()
            }
        } finally {
            runCatching { client.stopRecognition() }
            runCatching { readSide.close() }
            runCatching { writeSide.close() }
        }
    }

    private fun ShortArray.toPcmBytes(offset: Int, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val sample = this[offset + i].toInt()
            bytes[i * 2] = (sample and 0xff).toByte()
            bytes[i * 2 + 1] = (sample shr 8 and 0xff).toByte()
        }
        return bytes
    }

    private fun String.toMlKitLocaleTag(): String =
        when (this) {
            "en" -> "en-US"
            "zh" -> "cmn-Hant-TW"
            "ja" -> "ja-JP"
            "ko" -> "ko-KR"
            "de" -> "de-DE"
            "es" -> "es-ES"
            "fr" -> "fr-FR"
            "it" -> "it-IT"
            "pt" -> "pt-BR"
            "ru" -> "ru-RU"
            "th" -> "th-TH"
            "vi" -> "vi-VN"
            "id" -> "id-ID"
            else -> this
        }

    companion object {
        private const val RECOGNITION_DRAIN_MS = 250L
        private const val RECOGNITION_TIMEOUT_MS = 1_200L
        private const val SAMPLES_PER_WRITE = SystemAudioCapture.SAMPLE_RATE / 10
    }
}

package com.jeremysu0818.caption.translation

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CaptionTranslator {
    private val mutex = Mutex()
    private var translator: Translator? = null
    private var languagePair: Pair<String, String>? = null

    suspend fun translate(text: String, sourceLanguageTag: String, targetLanguageTag: String): String =
        mutex.withLock {
            if (sourceLanguageTag == targetLanguageTag) return@withLock text
            val sourceLanguage = TranslateLanguage.fromLanguageTag(sourceLanguageTag)
                ?: throw IllegalArgumentException("ML Kit 不支援來源語言：$sourceLanguageTag")
            val targetLanguage = TranslateLanguage.fromLanguageTag(targetLanguageTag)
                ?: throw IllegalArgumentException("ML Kit 不支援目標語言：$targetLanguageTag")
            val client = translatorFor(sourceLanguage, targetLanguage)
            client.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitTask()
            client.translate(text).awaitTask().trim()
        }

    suspend fun close() = mutex.withLock {
        translator?.close()
        translator = null
        languagePair = null
    }

    private fun translatorFor(sourceLanguage: String, targetLanguage: String): Translator {
        val requestedPair = sourceLanguage to targetLanguage
        val current = translator
        if (current != null && languagePair == requestedPair) return current

        current?.close()
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()
        return Translation.getClient(options).also {
            translator = it
            languagePair = requestedPair
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            addOnCanceledListener {
                continuation.cancel()
            }
        }
}

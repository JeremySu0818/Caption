package com.jeremysu0818.caption.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CaptionPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("caption_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())

    val settings: StateFlow<CaptionSettings> = _settings.asStateFlow()

    fun updateModel(model: WhisperModelOption) {
        update { it.copy(model = model) }
    }

    fun updateSpeechEngine(engine: SpeechEngineOption) {
        update { it.copy(speechEngine = engine) }
    }

    fun updateTranslationEnabled(enabled: Boolean) {
        update { it.copy(translationEnabled = enabled) }
    }

    fun updateSourceLanguage(tag: String) {
        update { it.copy(sourceLanguageTag = tag) }
    }

    fun updateTargetLanguage(tag: String) {
        update { it.copy(targetLanguageTag = tag) }
    }

    private fun update(transform: (CaptionSettings) -> CaptionSettings) {
        val next = transform(_settings.value)
        prefs.edit {
            putString(KEY_MODEL, next.model.id)
            putString(KEY_SPEECH_ENGINE, next.speechEngine.id)
            putBoolean(KEY_TRANSLATION_ENABLED, next.translationEnabled)
            putString(KEY_SOURCE_LANGUAGE, next.sourceLanguageTag)
            putString(KEY_TARGET_LANGUAGE, next.targetLanguageTag)
        }
        _settings.update { next }
    }

    private fun readSettings(): CaptionSettings =
        CaptionSettings(
            speechEngine = SpeechEngineOption.fromId(prefs.getString(KEY_SPEECH_ENGINE, null)),
            model = WhisperModelOption.fromId(prefs.getString(KEY_MODEL, null)),
            translationEnabled = prefs.getBoolean(KEY_TRANSLATION_ENABLED, false),
            sourceLanguageTag = prefs.getString(KEY_SOURCE_LANGUAGE, "en") ?: "en",
            targetLanguageTag = prefs.getString(KEY_TARGET_LANGUAGE, "zh") ?: "zh",
        )

    companion object {
        private const val KEY_MODEL = "model"
        private const val KEY_SPEECH_ENGINE = "speech_engine"
        private const val KEY_TRANSLATION_ENABLED = "translation_enabled"
        private const val KEY_SOURCE_LANGUAGE = "source_language"
        private const val KEY_TARGET_LANGUAGE = "target_language"
    }
}

package com.jeremysu0818.caption.data

data class CaptionSettings(
    val speechEngine: SpeechEngineOption = SpeechEngineOption.default,
    val model: WhisperModelOption = WhisperModelOption.default,
    val translationEnabled: Boolean = false,
    val sourceLanguageTag: String = "en",
    val targetLanguageTag: String = "zh",
    val uiLanguageTag: String = "system",
)

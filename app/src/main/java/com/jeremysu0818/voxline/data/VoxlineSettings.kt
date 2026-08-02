package com.jeremysu0818.voxline.data

data class VoxlineSettings(
    val speechEngine: SpeechEngineOption = SpeechEngineOption.default,
    val model: WhisperModelOption = WhisperModelOption.default,
    val translationEnabled: Boolean = false,
    val sourceLanguageTag: String = "en",
    val targetLanguageTag: String = "zh-TW",
    val uiLanguageTag: String = "system",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

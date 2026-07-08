package com.jeremysu0818.caption.data

data class CaptionLanguage(
    val tag: String,
    val label: String,
    val mlKitTranslateTag: String? = null,
    val whisperTag: String? = null,
    val mlKitBasicLocale: String? = null,
    val mlKitAdvancedLocale: String? = null,
) {
    val supportsMlKitTranslate: Boolean
        get() = mlKitTranslateTag != null

    fun supportsSource(engine: SpeechEngineOption): Boolean =
        when (engine) {
            SpeechEngineOption.WHISPER -> whisperTag != null
            SpeechEngineOption.MLKIT_BASIC -> mlKitBasicLocale != null
            SpeechEngineOption.MLKIT_ADVANCED -> mlKitAdvancedLocale != null
        }

    fun mlKitSpeechLocale(engine: SpeechEngineOption): String? =
        when (engine) {
            SpeechEngineOption.WHISPER -> null
            SpeechEngineOption.MLKIT_BASIC -> mlKitBasicLocale
            SpeechEngineOption.MLKIT_ADVANCED -> mlKitAdvancedLocale
        }
}

object CaptionLanguages {
    val supported = listOf(
        CaptionLanguage(tag = "af", label = "Afrikaans", mlKitTranslateTag = "af", whisperTag = "af"),
        CaptionLanguage(tag = "sq", label = "Albanian", mlKitTranslateTag = "sq", whisperTag = "sq"),
        CaptionLanguage(tag = "am", label = "Amharic", whisperTag = "am"),
        CaptionLanguage(tag = "ar", label = "Arabic", mlKitTranslateTag = "ar", whisperTag = "ar", mlKitAdvancedLocale = "ar-SA"),
        CaptionLanguage(tag = "hy", label = "Armenian", whisperTag = "hy"),
        CaptionLanguage(tag = "as", label = "Assamese", whisperTag = "as"),
        CaptionLanguage(tag = "az", label = "Azerbaijani", whisperTag = "az"),
        CaptionLanguage(tag = "ba", label = "Bashkir", whisperTag = "ba"),
        CaptionLanguage(tag = "eu", label = "Basque", whisperTag = "eu"),
        CaptionLanguage(tag = "be", label = "Belarusian", mlKitTranslateTag = "be", whisperTag = "be"),
        CaptionLanguage(tag = "bn", label = "Bengali", mlKitTranslateTag = "bn", whisperTag = "bn"),
        CaptionLanguage(tag = "bs", label = "Bosnian", whisperTag = "bs"),
        CaptionLanguage(tag = "br", label = "Breton", whisperTag = "br"),
        CaptionLanguage(tag = "bg", label = "Bulgarian", mlKitTranslateTag = "bg", whisperTag = "bg"),
        CaptionLanguage(tag = "my", label = "Burmese", whisperTag = "my"),
        CaptionLanguage(tag = "ca", label = "Catalan", mlKitTranslateTag = "ca", whisperTag = "ca"),
        CaptionLanguage(tag = "zh", label = "中文", mlKitTranslateTag = "zh"),
        CaptionLanguage(
            tag = "zh-TW",
            label = "繁體中文",
            mlKitTranslateTag = "zh",
            whisperTag = "zh",
            mlKitBasicLocale = "cmn-Hant-TW",
            mlKitAdvancedLocale = "cmn-Hant-TW",
        ),
        CaptionLanguage(
            tag = "zh-CN",
            label = "简体中文",
            mlKitTranslateTag = "zh",
            whisperTag = "zh",
            mlKitBasicLocale = "cmn-Hans-CN",
            mlKitAdvancedLocale = "cmn-Hans-CN",
        ),
        CaptionLanguage(tag = "hr", label = "Croatian", mlKitTranslateTag = "hr", whisperTag = "hr"),
        CaptionLanguage(tag = "cs", label = "Czech", mlKitTranslateTag = "cs", whisperTag = "cs"),
        CaptionLanguage(tag = "da", label = "Danish", mlKitTranslateTag = "da", whisperTag = "da", mlKitAdvancedLocale = "da-DK"),
        CaptionLanguage(tag = "nl", label = "Dutch", mlKitTranslateTag = "nl", whisperTag = "nl", mlKitAdvancedLocale = "nl-NL"),
        CaptionLanguage(tag = "en", label = "English", mlKitTranslateTag = "en", whisperTag = "en", mlKitBasicLocale = "en-US", mlKitAdvancedLocale = "en-US"),
        CaptionLanguage(tag = "eo", label = "Esperanto", mlKitTranslateTag = "eo"),
        CaptionLanguage(tag = "et", label = "Estonian", mlKitTranslateTag = "et", whisperTag = "et"),
        CaptionLanguage(tag = "fo", label = "Faroese", whisperTag = "fo"),
        CaptionLanguage(tag = "fi", label = "Finnish", mlKitTranslateTag = "fi", whisperTag = "fi"),
        CaptionLanguage(tag = "fr", label = "French", mlKitTranslateTag = "fr", whisperTag = "fr", mlKitBasicLocale = "fr-FR", mlKitAdvancedLocale = "fr-FR"),
        CaptionLanguage(tag = "gl", label = "Galician", mlKitTranslateTag = "gl", whisperTag = "gl"),
        CaptionLanguage(tag = "ka", label = "Georgian", mlKitTranslateTag = "ka", whisperTag = "ka"),
        CaptionLanguage(tag = "de", label = "German", mlKitTranslateTag = "de", whisperTag = "de", mlKitBasicLocale = "de-DE", mlKitAdvancedLocale = "de-DE"),
        CaptionLanguage(tag = "el", label = "Greek", mlKitTranslateTag = "el", whisperTag = "el"),
        CaptionLanguage(tag = "gu", label = "Gujarati", mlKitTranslateTag = "gu", whisperTag = "gu"),
        CaptionLanguage(tag = "ht", label = "Haitian Creole", mlKitTranslateTag = "ht", whisperTag = "ht"),
        CaptionLanguage(tag = "ha", label = "Hausa", whisperTag = "ha"),
        CaptionLanguage(tag = "haw", label = "Hawaiian", whisperTag = "haw"),
        CaptionLanguage(tag = "he", label = "Hebrew", mlKitTranslateTag = "he", whisperTag = "he"),
        CaptionLanguage(tag = "hi", label = "Hindi", mlKitTranslateTag = "hi", whisperTag = "hi", mlKitBasicLocale = "hi-IN", mlKitAdvancedLocale = "hi-IN"),
        CaptionLanguage(tag = "hu", label = "Hungarian", mlKitTranslateTag = "hu", whisperTag = "hu"),
        CaptionLanguage(tag = "is", label = "Icelandic", mlKitTranslateTag = "is", whisperTag = "is"),
        CaptionLanguage(tag = "id", label = "Indonesian", mlKitTranslateTag = "id", whisperTag = "id", mlKitAdvancedLocale = "id-ID"),
        CaptionLanguage(tag = "ga", label = "Irish", mlKitTranslateTag = "ga"),
        CaptionLanguage(tag = "it", label = "Italian", mlKitTranslateTag = "it", whisperTag = "it", mlKitBasicLocale = "it-IT", mlKitAdvancedLocale = "it-IT"),
        CaptionLanguage(tag = "ja", label = "Japanese", mlKitTranslateTag = "ja", whisperTag = "ja", mlKitBasicLocale = "ja-JP", mlKitAdvancedLocale = "ja-JP"),
        CaptionLanguage(tag = "jv", label = "Javanese", whisperTag = "jw"),
        CaptionLanguage(tag = "kn", label = "Kannada", mlKitTranslateTag = "kn", whisperTag = "kn"),
        CaptionLanguage(tag = "kk", label = "Kazakh", whisperTag = "kk"),
        CaptionLanguage(tag = "km", label = "Khmer", whisperTag = "km"),
        CaptionLanguage(tag = "ko", label = "Korean", mlKitTranslateTag = "ko", whisperTag = "ko", mlKitBasicLocale = "ko-KR", mlKitAdvancedLocale = "ko-KR"),
        CaptionLanguage(tag = "lo", label = "Lao", whisperTag = "lo"),
        CaptionLanguage(tag = "la", label = "Latin", whisperTag = "la"),
        CaptionLanguage(tag = "lv", label = "Latvian", mlKitTranslateTag = "lv", whisperTag = "lv"),
        CaptionLanguage(tag = "ln", label = "Lingala", whisperTag = "ln"),
        CaptionLanguage(tag = "lt", label = "Lithuanian", mlKitTranslateTag = "lt", whisperTag = "lt"),
        CaptionLanguage(tag = "lb", label = "Luxembourgish", whisperTag = "lb"),
        CaptionLanguage(tag = "mk", label = "Macedonian", mlKitTranslateTag = "mk", whisperTag = "mk"),
        CaptionLanguage(tag = "mg", label = "Malagasy", whisperTag = "mg"),
        CaptionLanguage(tag = "ms", label = "Malay", mlKitTranslateTag = "ms", whisperTag = "ms"),
        CaptionLanguage(tag = "ml", label = "Malayalam", whisperTag = "ml"),
        CaptionLanguage(tag = "mt", label = "Maltese", mlKitTranslateTag = "mt", whisperTag = "mt"),
        CaptionLanguage(tag = "mi", label = "Maori", whisperTag = "mi"),
        CaptionLanguage(tag = "mr", label = "Marathi", mlKitTranslateTag = "mr", whisperTag = "mr"),
        CaptionLanguage(tag = "mn", label = "Mongolian", whisperTag = "mn"),
        CaptionLanguage(tag = "ne", label = "Nepali", whisperTag = "ne"),
        CaptionLanguage(tag = "no", label = "Norwegian", mlKitTranslateTag = "no", whisperTag = "no"),
        CaptionLanguage(tag = "nn", label = "Nynorsk", whisperTag = "nn"),
        CaptionLanguage(tag = "oc", label = "Occitan", whisperTag = "oc"),
        CaptionLanguage(tag = "ps", label = "Pashto", whisperTag = "ps"),
        CaptionLanguage(tag = "fa", label = "Persian", mlKitTranslateTag = "fa", whisperTag = "fa"),
        CaptionLanguage(tag = "pl", label = "Polish", mlKitTranslateTag = "pl", whisperTag = "pl", mlKitBasicLocale = "pl-PL", mlKitAdvancedLocale = "pl-PL"),
        CaptionLanguage(tag = "pt", label = "Portuguese", mlKitTranslateTag = "pt", whisperTag = "pt", mlKitBasicLocale = "pt-BR", mlKitAdvancedLocale = "pt-PT"),
        CaptionLanguage(tag = "pa", label = "Punjabi", whisperTag = "pa"),
        CaptionLanguage(tag = "ro", label = "Romanian", mlKitTranslateTag = "ro", whisperTag = "ro"),
        CaptionLanguage(tag = "ru", label = "Russian", mlKitTranslateTag = "ru", whisperTag = "ru", mlKitBasicLocale = "ru-RU", mlKitAdvancedLocale = "ru-RU"),
        CaptionLanguage(tag = "sa", label = "Sanskrit", whisperTag = "sa"),
        CaptionLanguage(tag = "sr", label = "Serbian", whisperTag = "sr"),
        CaptionLanguage(tag = "sn", label = "Shona", whisperTag = "sn"),
        CaptionLanguage(tag = "sd", label = "Sindhi", whisperTag = "sd"),
        CaptionLanguage(tag = "si", label = "Sinhala", whisperTag = "si"),
        CaptionLanguage(tag = "sk", label = "Slovak", mlKitTranslateTag = "sk", whisperTag = "sk"),
        CaptionLanguage(tag = "sl", label = "Slovenian", mlKitTranslateTag = "sl", whisperTag = "sl"),
        CaptionLanguage(tag = "so", label = "Somali", whisperTag = "so"),
        CaptionLanguage(tag = "es", label = "Spanish", mlKitTranslateTag = "es", whisperTag = "es", mlKitBasicLocale = "es-ES", mlKitAdvancedLocale = "es-ES"),
        CaptionLanguage(tag = "su", label = "Sundanese", whisperTag = "su"),
        CaptionLanguage(tag = "sw", label = "Swahili", mlKitTranslateTag = "sw", whisperTag = "sw"),
        CaptionLanguage(tag = "sv", label = "Swedish", mlKitTranslateTag = "sv", whisperTag = "sv", mlKitAdvancedLocale = "sv-SE"),
        CaptionLanguage(tag = "tl", label = "Tagalog", mlKitTranslateTag = "tl", whisperTag = "tl"),
        CaptionLanguage(tag = "tg", label = "Tajik", whisperTag = "tg"),
        CaptionLanguage(tag = "ta", label = "Tamil", mlKitTranslateTag = "ta", whisperTag = "ta"),
        CaptionLanguage(tag = "tt", label = "Tatar", whisperTag = "tt"),
        CaptionLanguage(tag = "te", label = "Telugu", mlKitTranslateTag = "te", whisperTag = "te"),
        CaptionLanguage(tag = "th", label = "Thai", mlKitTranslateTag = "th", whisperTag = "th", mlKitAdvancedLocale = "th-TH"),
        CaptionLanguage(tag = "tr", label = "Turkish", mlKitTranslateTag = "tr", whisperTag = "tr", mlKitBasicLocale = "tr-TR", mlKitAdvancedLocale = "tr-TR"),
        CaptionLanguage(tag = "tk", label = "Turkmen", whisperTag = "tk"),
        CaptionLanguage(tag = "uk", label = "Ukrainian", mlKitTranslateTag = "uk", whisperTag = "uk"),
        CaptionLanguage(tag = "ur", label = "Urdu", mlKitTranslateTag = "ur", whisperTag = "ur"),
        CaptionLanguage(tag = "uz", label = "Uzbek", whisperTag = "uz"),
        CaptionLanguage(tag = "vi", label = "Vietnamese", mlKitTranslateTag = "vi", whisperTag = "vi", mlKitBasicLocale = "vi-VN", mlKitAdvancedLocale = "vi-VN"),
        CaptionLanguage(tag = "cy", label = "Welsh", mlKitTranslateTag = "cy", whisperTag = "cy"),
        CaptionLanguage(tag = "yi", label = "Yiddish", whisperTag = "yi"),
        CaptionLanguage(tag = "yo", label = "Yoruba", whisperTag = "yo"),
        CaptionLanguage(tag = "bo", label = "Tibetan", whisperTag = "bo"),
        CaptionLanguage(tag = "yue", label = "Cantonese", whisperTag = "yue"),
    ).map { language ->
        val nativeLabel = when (language.tag) {
            "zh-TW" -> "繁體中文"
            "zh-CN" -> "简体中文"
            "zh" -> "中文"
            else -> {
                val locale = java.util.Locale.forLanguageTag(language.tag)
                val displayName = locale.getDisplayName(locale)
                displayName.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                }
            }
        }
        language.copy(label = nativeLabel)
    }

    private val byTag = supported.associateBy(CaptionLanguage::tag)

    fun labelFor(tag: String): String = find(tag)?.label ?: tag

    fun find(tag: String): CaptionLanguage? = byTag[canonicalAlias(tag)]

    fun getFilteredLanguages(
        engine: SpeechEngineOption,
        translationEnabled: Boolean,
    ): List<CaptionLanguage> = sourceLanguages(engine, translationEnabled)

    fun sourceLanguages(
        engine: SpeechEngineOption,
        translationEnabled: Boolean,
    ): List<CaptionLanguage> =
        supported.filter { language ->
            language.supportsSource(engine) &&
                (!translationEnabled || language.supportsMlKitTranslate)
        }

    fun targetLanguages(): List<CaptionLanguage> =
        supported.filter { language ->
            language.mlKitTranslateTag != null && language.tag == language.mlKitTranslateTag
        }

    fun mlKitSpeechLocale(tag: String, engine: SpeechEngineOption): String? =
        find(tag)?.mlKitSpeechLocale(engine)

    fun requireMlKitSpeechLocale(tag: String, engine: SpeechEngineOption): String =
        mlKitSpeechLocale(tag, engine)
            ?: throw IllegalArgumentException("${engine.label} 不支援語言：$tag")

    fun mlKitTranslateTag(tag: String): String? = find(tag)?.mlKitTranslateTag

    fun requireMlKitTranslateTag(tag: String): String =
        mlKitTranslateTag(tag)
            ?: throw IllegalArgumentException("ML Kit Translate 不支援語言：$tag")

    fun whisperLanguageTag(tag: String): String? = find(tag)?.whisperTag

    fun requireWhisperLanguageTag(tag: String): String =
        whisperLanguageTag(tag)
            ?: throw IllegalArgumentException("Whisper 不支援語言：$tag")

    fun coerceSourceTag(
        tag: String,
        engine: SpeechEngineOption,
        translationEnabled: Boolean,
    ): String {
        val normalized = when (canonicalAlias(tag)) {
            "zh" -> "zh-TW"
            else -> canonicalAlias(tag)
        }
        return if (sourceLanguages(engine, translationEnabled).any { it.tag == normalized }) {
            normalized
        } else {
            fallbackSourceTag(engine, translationEnabled)
        }
    }

    fun coerceTargetTag(tag: String): String {
        val normalized = when (canonicalAlias(tag)) {
            "zh-TW", "zh-CN" -> "zh"
            else -> canonicalAlias(tag)
        }
        return if (targetLanguages().any { it.tag == normalized }) {
            normalized
        } else {
            DEFAULT_TARGET_TAG
        }
    }

    private fun fallbackSourceTag(
        engine: SpeechEngineOption,
        translationEnabled: Boolean,
    ): String {
        val available = sourceLanguages(engine, translationEnabled)
        return if (available.any { it.tag == DEFAULT_SOURCE_TAG }) {
            DEFAULT_SOURCE_TAG
        } else {
            available.firstOrNull()?.tag ?: DEFAULT_SOURCE_TAG
        }
    }

    private fun canonicalAlias(tag: String): String =
        when (tag) {
            "jw" -> "jv"
            else -> tag
        }

    private const val DEFAULT_SOURCE_TAG = "en"
    private const val DEFAULT_TARGET_TAG = "zh"
}

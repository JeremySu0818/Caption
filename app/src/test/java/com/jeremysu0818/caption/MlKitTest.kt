package com.jeremysu0818.caption

import com.jeremysu0818.caption.data.CaptionLanguages
import com.jeremysu0818.caption.data.SpeechEngineOption
import org.junit.Assert.assertEquals
import org.junit.Test

class MlKitTest {
    @Test
    fun mlKitTranslateTagsMatchOfficialDocs() {
        assertEquals(
            officialMlKitTranslateTags,
            CaptionLanguages.targetLanguages().mapNotNull { it.mlKitTranslateTag }.toSet(),
        )
        assertEquals(
            (officialMlKitTranslateTags - "zh") + setOf("zh-TW", "zh-CN"),
            CaptionLanguages.targetLanguages().map { it.tag }.toSet(),
        )
    }

    @Test
    fun mlKitSpeechLocalesMatchOfficialDocs() {
        val basicLocales = CaptionLanguages.supported.mapNotNull { it.mlKitBasicLocale }.toSet()
        val advancedLocales = CaptionLanguages.supported.mapNotNull { it.mlKitAdvancedLocale }.toSet()

        assertEquals(officialMlKitBasicLocales, basicLocales)
        assertEquals(officialMlKitAdvancedLocales, advancedLocales)
    }

    @Test
    fun whisperTagsMatchOfficialSources() {
        val whisperTags = CaptionLanguages.supported.mapNotNull { it.whisperTag }.toSet()
        assertEquals(officialWhisperTags, whisperTags)
    }

    @Test
    fun sourceLanguageFiltersMatchAllSixCases() {
        assertEquals(
            expectedWhisperSourceTags,
            CaptionLanguages.getFilteredLanguages(SpeechEngineOption.WHISPER, false).map { it.tag }.toSet(),
        )
        assertEquals(
            expectedWhisperTranslatedSourceTags,
            CaptionLanguages.getFilteredLanguages(SpeechEngineOption.WHISPER, true).map { it.tag }.toSet(),
        )
        assertEquals(
            expectedMlKitBasicSourceTags,
            CaptionLanguages.getFilteredLanguages(SpeechEngineOption.MLKIT_BASIC, false).map { it.tag }.toSet(),
        )
        assertEquals(
            expectedMlKitBasicSourceTags,
            CaptionLanguages.getFilteredLanguages(SpeechEngineOption.MLKIT_BASIC, true).map { it.tag }.toSet(),
        )
        assertEquals(
            expectedMlKitAdvancedSourceTags,
            CaptionLanguages.getFilteredLanguages(SpeechEngineOption.MLKIT_ADVANCED, false).map { it.tag }.toSet(),
        )
        assertEquals(
            expectedMlKitAdvancedSourceTags,
            CaptionLanguages.getFilteredLanguages(SpeechEngineOption.MLKIT_ADVANCED, true).map { it.tag }.toSet(),
        )
    }

    @Test
    fun specialCodeMappingsStayOfficial() {
        assertEquals("zh", CaptionLanguages.requireMlKitTranslateTag("zh-TW"))
        assertEquals("zh", CaptionLanguages.requireMlKitTranslateTag("zh-CN"))
        assertEquals("zh", CaptionLanguages.requireWhisperLanguageTag("zh"))
        assertEquals(null, CaptionLanguages.whisperLanguageTag("zh-TW"))
        assertEquals(null, CaptionLanguages.whisperLanguageTag("zh-CN"))
        assertEquals("jw", CaptionLanguages.requireWhisperLanguageTag("jv"))
        assertEquals("pt-BR", CaptionLanguages.requireMlKitSpeechLocale("pt-BR", SpeechEngineOption.MLKIT_BASIC))
        assertEquals("pt-PT", CaptionLanguages.requireMlKitSpeechLocale("pt-PT", SpeechEngineOption.MLKIT_ADVANCED))
        assertEquals("zh", CaptionLanguages.coerceSourceTag("zh-TW", SpeechEngineOption.WHISPER, false))
        assertEquals("zh-TW", CaptionLanguages.coerceSourceTag("zh", SpeechEngineOption.MLKIT_BASIC, false))
        assertEquals("pt-BR", CaptionLanguages.coerceSourceTag("pt", SpeechEngineOption.MLKIT_BASIC, false))
        assertEquals("pt-PT", CaptionLanguages.coerceSourceTag("pt-BR", SpeechEngineOption.MLKIT_ADVANCED, false))
        assertEquals("pt", CaptionLanguages.coerceSourceTag("pt-PT", SpeechEngineOption.WHISPER, false))
        assertEquals(
            "pt-PT",
            CaptionLanguages.compatibleSourceTag("pt-BR", SpeechEngineOption.MLKIT_ADVANCED, false),
        )
        assertEquals(
            null,
            CaptionLanguages.compatibleSourceTag("eo", SpeechEngineOption.MLKIT_ADVANCED, false),
        )
        assertEquals("zh-TW", CaptionLanguages.coerceTargetTag("zh"))
        assertEquals("zh-TW", CaptionLanguages.coerceTargetTag("zh-TW"))
        assertEquals("zh-CN", CaptionLanguages.coerceTargetTag("zh-CN"))
    }

    companion object {
        private val officialMlKitTranslateTags = setOf(
            "af", "ar", "be", "bg", "bn", "ca", "cs", "cy", "da", "de",
            "el", "en", "eo", "es", "et", "fa", "fi", "fr", "ga", "gl",
            "gu", "he", "hi", "hr", "ht", "hu", "id", "is", "it", "ja",
            "ka", "kn", "ko", "lt", "lv", "mk", "mr", "ms", "mt", "nl",
            "no", "pl", "pt", "ro", "ru", "sk", "sl", "sq", "sv", "sw",
            "ta", "te", "th", "tl", "tr", "uk", "ur", "vi", "zh",
        )

        private val officialMlKitBasicLocales = setOf(
            "en-US", "fr-FR", "it-IT", "de-DE", "es-ES",
            "hi-IN", "ja-JP", "pt-BR", "tr-TR", "pl-PL",
            "cmn-Hans-CN", "ko-KR", "cmn-Hant-TW", "ru-RU", "vi-VN",
        )

        private val officialMlKitAdvancedLocales = setOf(
            "en-US", "ko-KR", "es-ES", "fr-FR", "de-DE",
            "it-IT", "pt-PT", "cmn-Hans-CN", "cmn-Hant-TW", "ja-JP",
            "th-TH", "ru-RU", "nl-NL", "da-DK", "sv-SE",
            "pl-PL", "hi-IN", "vi-VN", "id-ID", "ar-SA", "tr-TR",
        )

        private val officialWhisperTags = setOf(
            "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr",
            "pl", "ca", "nl", "ar", "sv", "it", "id", "hi", "fi", "vi",
            "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "no",
            "th", "ur", "hr", "bg", "lt", "la", "mi", "ml", "cy", "sk",
            "te", "fa", "lv", "bn", "sr", "az", "sl", "kn", "et", "mk",
            "br", "eu", "is", "hy", "ne", "mn", "bs", "kk", "sq", "sw",
            "gl", "mr", "pa", "si", "km", "sn", "yo", "so", "af", "oc",
            "ka", "be", "tg", "sd", "gu", "am", "yi", "lo", "uz", "fo",
            "ht", "ps", "tk", "nn", "mt", "sa", "lb", "my", "bo", "tl",
            "mg", "as", "tt", "haw", "ln", "ha", "ba", "jw", "su", "yue",
        )

        private val expectedWhisperSourceTags = (officialWhisperTags - "jw") + "jv"

        private val expectedWhisperTranslatedSourceTags =
            officialWhisperTags intersect officialMlKitTranslateTags

        private val expectedMlKitBasicSourceTags = setOf(
            "en", "fr", "it", "de", "es",
            "hi", "ja", "pt-BR", "tr", "pl",
            "zh-CN", "ko", "zh-TW", "ru", "vi",
        )

        private val expectedMlKitAdvancedSourceTags = setOf(
            "en", "ko", "es", "fr", "de",
            "it", "pt-PT", "zh-CN", "zh-TW", "ja",
            "th", "ru", "nl", "da", "sv",
            "pl", "hi", "vi", "id", "ar", "tr",
        )
    }
}

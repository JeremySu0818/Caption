package com.jeremysu0818.caption.translation

import openccjava.OpenCC

class TranslationOutputConverter {
    private val traditionalChinese by lazy { OpenCC("s2twp") }
    private val simplifiedChinese by lazy { OpenCC("t2s") }

    fun convert(text: String, targetLanguageTag: String): String =
        when (targetLanguageTag) {
            "zh-TW", "zh-Hant" -> traditionalChinese.convert(text)
            "zh-CN", "zh-Hans" -> simplifiedChinese.convert(text)
            else -> text
        }
}

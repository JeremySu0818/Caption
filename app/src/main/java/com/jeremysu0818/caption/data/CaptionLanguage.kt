package com.jeremysu0818.caption.data

data class CaptionLanguage(
    val tag: String,
    val label: String,
)

object CaptionLanguages {
    val supported = listOf(
        CaptionLanguage("en", "English"),
        CaptionLanguage("zh", "中文"),
        CaptionLanguage("ja", "日本語"),
        CaptionLanguage("ko", "한국어"),
        CaptionLanguage("de", "Deutsch"),
        CaptionLanguage("es", "Español"),
        CaptionLanguage("fr", "Français"),
        CaptionLanguage("it", "Italiano"),
        CaptionLanguage("pt", "Português"),
        CaptionLanguage("ru", "Русский"),
        CaptionLanguage("th", "ไทย"),
        CaptionLanguage("vi", "Tiếng Việt"),
        CaptionLanguage("id", "Indonesia"),
    )

    fun labelFor(tag: String): String = supported.firstOrNull { it.tag == tag }?.label ?: tag
}

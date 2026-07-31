package com.jeremysu0818.caption.data

enum class ThemeMode(
    val id: String,
    val labelKey: String,
) {
    SYSTEM("system", "theme_system"),
    LIGHT("light", "theme_light"),
    DARK("dark", "theme_dark"),
    ;

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

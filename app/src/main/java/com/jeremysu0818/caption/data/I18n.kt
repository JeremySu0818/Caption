package com.jeremysu0818.caption.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.InputStream
import java.util.Locale

object I18n {
    private val _currentLocale = MutableStateFlow("en")
    val currentLocale: StateFlow<String> = _currentLocale.asStateFlow()

    private var translations = JSONObject()
    private var fallbackTranslations = JSONObject()
    private lateinit var appContext: Context
    private val rePlaceholder = Regex("\\{[^}]+\\}")

    fun init(context: Context, initialLocale: String) {
        appContext = context.applicationContext
        loadFallbackTranslations()
        setLocale(initialLocale)
    }

    fun setLocale(localeTag: String) {
        val resolved = resolveLocale(localeTag)
        _currentLocale.value = resolved
        loadTranslations(resolved)
    }

    private fun resolveLocale(localeTag: String): String {
        val tag = if (localeTag == "system" || localeTag.isBlank()) {
            Locale.getDefault().toLanguageTag()
        } else {
            localeTag
        }

        // Try exact tag (e.g., "zh-TW")
        if (assetExists("locales/$tag.json")) {
            return tag
        }

        // Handle generic fallback (e.g., if system is "zh-HK", map to "zh-TW" or "zh")
        val parsedLocale = Locale.forLanguageTag(tag)
        val lang = parsedLocale.language
        if (lang == "zh") {
            val country = parsedLocale.country.uppercase()
            if (country == "CN" || country == "SG") {
                if (assetExists("locales/zh-CN.json")) return "zh-CN"
            } else {
                if (assetExists("locales/zh-TW.json")) return "zh-TW"
            }
        }

        if (assetExists("locales/$lang.json")) {
            return lang
        }

        return "en"
    }

    private fun assetExists(path: String): Boolean {
        return try {
            appContext.assets.open(path).use { it.close() }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun loadFallbackTranslations() {
        try {
            appContext.assets.open("locales/en.json").use { inputStream ->
                val size = inputStream.available()
                val buffer = ByteArray(size)
                inputStream.read(buffer)
                val jsonStr = String(buffer, Charsets.UTF_8)
                fallbackTranslations = JSONObject(jsonStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadTranslations(locale: String) {
        try {
            appContext.assets.open("locales/$locale.json").use { inputStream ->
                val size = inputStream.available()
                val buffer = ByteArray(size)
                inputStream.read(buffer)
                val jsonStr = String(buffer, Charsets.UTF_8)
                translations = JSONObject(jsonStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to English
            if (locale != "en") {
                loadTranslations("en")
            }
        }
    }

    fun getString(key: String): String {
        return if (::appContext.isInitialized) {
            translations.optString(key, fallbackTranslations.optString(key, key))
        } else {
            key
        }
    }

    fun getString(key: String, vararg args: Any): String {
        val raw = getString(key)
        return try {
            var result = raw
            var argIndex = 0
            var matchResult = rePlaceholder.find(result)
            while (matchResult != null && argIndex < args.size) {
                result = result.replaceFirst(matchResult.value, args[argIndex].toString())
                matchResult = rePlaceholder.find(result)
                argIndex++
            }
            result
        } catch (e: Exception) {
            raw
        }
    }
}

@Composable
fun t(key: String): String {
    val currentLocale by I18n.currentLocale.collectAsState()
    return remember(key, currentLocale) { I18n.getString(key) }
}

@Composable
fun t(key: String, vararg args: Any): String {
    val currentLocale by I18n.currentLocale.collectAsState()
    return remember(key, currentLocale, *args) { I18n.getString(key, *args) }
}

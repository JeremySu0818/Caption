package com.jeremysu0818.caption.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CaptionLine(
    val id: String,
    val sourceText: String,
    val translatedText: String? = null,
    val isTranslating: Boolean = false,
    val isFinal: Boolean = true,
    val showTypewriter: Boolean = true
)

data class CaptionRuntimeState(
    val isRunning: Boolean = false,
    val status: String = "尚未啟動",
    val lines: List<CaptionLine> = emptyList(),
    val errorMessage: String? = null,
)

object CaptionRuntimeStore {
    private val _state = MutableStateFlow(CaptionRuntimeState())
    val state: StateFlow<CaptionRuntimeState> = _state.asStateFlow()
    private const val MAX_LINES = 50

    private fun upsertLine(
        lines: List<CaptionLine>,
        id: String,
        newLine: CaptionLine,
    ): List<CaptionLine> {
        val existingIndex = lines.indexOfFirst { it.id == id }
        return if (existingIndex != -1) {
            lines.toMutableList().apply {
                this[existingIndex] = newLine
            }
        } else {
            (lines + newLine).takeLast(MAX_LINES)
        }
    }

    fun setRunning(status: String) {
        _state.update {
            it.copy(isRunning = true, status = status, errorMessage = null)
        }
    }

    fun updateStatus(status: String) {
        _state.update { it.copy(status = status) }
    }

    fun addOrUpdatePartialSourceText(id: String, text: String) {
        _state.update { state ->
            val newLine = CaptionLine(id = id, sourceText = text, isFinal = false, showTypewriter = false)
            state.copy(
                isRunning = true,
                status = "即時字幕執行中",
                lines = upsertLine(state.lines, id, newLine),
                errorMessage = null,
            )
        }
    }

    fun commitSourceText(id: String, text: String, isTranslating: Boolean) {
        _state.update { state ->
            val existingLine = state.lines.firstOrNull { it.id == id }
            val translatedText = existingLine?.translatedText
            val showTypewriter = existingLine?.showTypewriter ?: true
            val newLine = CaptionLine(
                id = id,
                sourceText = text,
                translatedText = translatedText,
                isFinal = true,
                isTranslating = isTranslating,
                showTypewriter = showTypewriter
            )
            state.copy(
                isRunning = true,
                status = "即時字幕執行中",
                lines = upsertLine(state.lines, id, newLine),
                errorMessage = null,
            )
        }
    }

    fun updateTranslation(id: String, translatedText: String?) {
        _state.update { state ->
            val newLines = state.lines.map {
                if (it.id == id) it.copy(translatedText = translatedText, isTranslating = false) else it
            }
            state.copy(lines = newLines, errorMessage = null)
        }
    }

    fun setError(message: String) {
        _state.update { it.copy(status = "發生錯誤", errorMessage = message) }
    }

    fun setStopped(status: String = "已停止") {
        _state.update {
            it.copy(
                isRunning = false,
                status = status,
                lines = emptyList(),
                errorMessage = null,
            )
        }
    }
}

package com.jeremysu0818.caption.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CaptionRuntimeState(
    val isRunning: Boolean = false,
    val status: String = "尚未啟動",
    val sourceText: String = "",
    val translatedText: String? = null,
    val errorMessage: String? = null,
)

object CaptionRuntimeStore {
    private val _state = MutableStateFlow(CaptionRuntimeState())
    val state: StateFlow<CaptionRuntimeState> = _state.asStateFlow()

    fun setRunning(status: String) {
        _state.update {
            it.copy(isRunning = true, status = status, errorMessage = null)
        }
    }

    fun updateStatus(status: String) {
        _state.update { it.copy(status = status) }
    }

    fun updateCaption(sourceText: String, translatedText: String?) {
        _state.update {
            it.copy(
                isRunning = true,
                status = "字幕更新中",
                sourceText = sourceText,
                translatedText = translatedText,
                errorMessage = null,
            )
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
                sourceText = "",
                translatedText = null,
                errorMessage = null,
            )
        }
    }
}

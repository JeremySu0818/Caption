package com.jeremysu0818.caption.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.jeremysu0818.caption.overlay.FloatingCaptionWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hosts caption windows as trusted accessibility overlays. */
class CaptionAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        activeService = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
            _isConnected.value = false
        }
        super.onDestroy()
    }

    fun createCaptionWindow(onCloseRequested: () -> Unit): FloatingCaptionWindow =
        FloatingCaptionWindow(
            context = this,
            onCloseRequested = onCloseRequested,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        )

    companion object {
        @Volatile
        private var activeService: CaptionAccessibilityService? = null
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun activeOrNull(): CaptionAccessibilityService? = activeService
    }
}

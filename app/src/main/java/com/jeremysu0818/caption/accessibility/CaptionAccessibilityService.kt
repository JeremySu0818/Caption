package com.jeremysu0818.caption.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.jeremysu0818.caption.overlay.FloatingCaptionWindow

/** Hosts caption windows as trusted accessibility overlays. */
class CaptionAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    fun createCaptionWindow(onCloseRequested: () -> Unit): FloatingCaptionWindow =
        FloatingCaptionWindow(
            context = this,
            onCloseRequested = onCloseRequested,
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            contentIsTouchable = false,
        )

    companion object {
        @Volatile
        private var activeService: CaptionAccessibilityService? = null

        fun activeOrNull(): CaptionAccessibilityService? = activeService
    }
}

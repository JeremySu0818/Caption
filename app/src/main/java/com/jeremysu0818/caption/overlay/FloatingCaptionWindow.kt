package com.jeremysu0818.caption.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jeremysu0818.caption.ui.theme.CaptionTheme
import kotlin.math.roundToInt

private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun init() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

class FloatingCaptionWindow(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private var statusText by mutableStateOf("準備字幕")
    private var sourceTextState by mutableStateOf("")
    private var translatedTextState by mutableStateOf<String?>(null)

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        mainHandler.post {
            if (composeView != null) return@post

            val density = context.resources.displayMetrics.density
            val width = (context.resources.displayMetrics.widthPixels - 32.dp(density))
                .coerceAtMost(720.dp(density))
            val initialY = (context.resources.displayMetrics.heightPixels * 0.72f).roundToInt()

            val owner = OverlayLifecycleOwner().apply { init() }
            
            val params = WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 16.dp(density)
                y = initialY
            }

            val view = ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    CaptionTheme {
                        Surface(
                            modifier = Modifier.pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    params.x += dragAmount.x.roundToInt()
                                    params.y += dragAmount.y.roundToInt()
                                    windowManager.updateViewLayout(this@apply, params)
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = statusText,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (sourceTextState.isNotBlank()) {
                                    Text(
                                        text = sourceTextState,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 4
                                    )
                                }
                                translatedTextState?.takeIf { it.isNotBlank() }?.let { translated ->
                                    Text(
                                        text = translated,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 4
                                    )
                                }
                            }
                        }
                    }
                }
            }

            windowManager.addView(view, params)
            composeView = view
            lifecycleOwner = owner
        }
    }

    fun updateStatus(status: String) {
        mainHandler.post {
            statusText = status
        }
    }

    fun updateCaption(sourceText: String, translatedText: String?) {
        mainHandler.post {
            sourceTextState = sourceText
            translatedTextState = translatedText
        }
    }

    fun dismiss() {
        mainHandler.post {
            composeView?.let { windowManager.removeView(it) }
            composeView = null
            lifecycleOwner?.destroy()
            lifecycleOwner = null
        }
    }

    private fun Int.dp(density: Float): Int = (this * density).roundToInt()
}

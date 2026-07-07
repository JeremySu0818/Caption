package com.jeremysu0818.caption.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jeremysu0818.caption.data.CaptionRuntimeStore
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
                    val state by CaptionRuntimeStore.state.collectAsState()
                    CaptionTheme {
                        val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
                        val minHeightLimit = screenHeight / 6f
                        val maxHeightLimit = screenHeight * 0.6f

                        var targetHeight by remember(screenHeight) { mutableStateOf(minHeightLimit) }
                        val maxHeight by animateDpAsState(targetValue = targetHeight, label = "maxHeight")

                        var isPressing by remember { mutableStateOf(false) }
                        val barAlpha by animateFloatAsState(targetValue = if (isPressing) 0.8f else 0.2f, label = "barAlpha")

                        Box(
                            modifier = Modifier.height(maxHeight)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 20.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ) {
                                 val listState = rememberLazyListState()
                                 val linesCount = state.lines.size
                                 val isAtBottom by remember {
                                     derivedStateOf {
                                         listState.firstVisibleItemIndex <= 1
                                     }
                                 }

                                 LaunchedEffect(linesCount) {
                                     if (linesCount > 0 && isAtBottom) {
                                         listState.animateScrollToItem(0)
                                     }
                                 }

                                LazyColumn(
                                    state = listState,
                                    reverseLayout = true,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = 0.99f }
                                        .drawWithContent {
                                            drawContent()
                                            val fadeOutHeight = 16.dp(density).toFloat()
                                            val topStop = (fadeOutHeight / size.height).coerceIn(0f, 0.5f)
                                            val bottomStop = ((size.height - fadeOutHeight) / size.height).coerceIn(0.5f, 1f)
                                            val gradient = Brush.verticalGradient(
                                                0f to Color.Transparent,
                                                topStop to Color.Black,
                                                bottomStop to Color.Black,
                                                1f to Color.Transparent,
                                                startY = 0f,
                                                endY = size.height
                                            )
                                            drawRect(brush = gradient, blendMode = BlendMode.DstIn)
                                        }
                                        .padding(horizontal = 18.dp)
                                ) {
                                    items(state.lines.reversed(), key = { line -> line.id }) { line ->
                                        val isNewest = line.id == state.lines.lastOrNull()?.id
                                        Column(
                                            modifier = Modifier.animateContentSize()
                                        ) {
                                            if (isNewest && line.showTypewriter) {
                                                TypewriterText(
                                                    text = line.sourceText,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            } else {
                                                Text(
                                                    text = line.sourceText,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }

                                            if (line.isTranslating && line.translatedText == null) {
                                                Text(
                                                    text = "...",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                )
                                            } else if (line.translatedText != null && line.translatedText.isNotBlank()) {
                                                if (isNewest) {
                                                    TypewriterText(
                                                        text = line.translatedText,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                    )
                                                } else {
                                                    Text(
                                                        text = line.translatedText,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { isPressing = true },
                                            onDragEnd = { isPressing = false },
                                            onDragCancel = { isPressing = false },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                params.x += dragAmount.x.roundToInt()
                                                params.y += dragAmount.y.roundToInt()
                                                windowManager.updateViewLayout(this@apply, params)
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(28.dp)
                                        .pointerInput(screenHeight) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val down = awaitFirstDown()
                                                    isPressing = true
                                                    var dragTriggered = false
                                                    var accumulatedDrag = 0f
                                                    var pointer = down

                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val anyPressed = event.changes.any { it.pressed }
                                                        if (!anyPressed) break

                                                        val change = event.changes.firstOrNull { it.id == pointer.id } ?: break
                                                        if (change.isConsumed) break

                                                        val dragAmount = change.position.y - change.previousPosition.y
                                                        accumulatedDrag += dragAmount

                                                        if (!dragTriggered && kotlin.math.abs(accumulatedDrag) > viewConfiguration.touchSlop) {
                                                            dragTriggered = true
                                                        }

                                                        if (dragTriggered) {
                                                            change.consume()
                                                            val deltaDp = (dragAmount / density).dp
                                                            val oldHeight = targetHeight
                                                            val newHeight = (targetHeight - deltaDp).coerceIn(minHeightLimit, maxHeightLimit)

                                                            if (newHeight != oldHeight) {
                                                                targetHeight = newHeight
                                                                params.y += dragAmount.roundToInt()
                                                                windowManager.updateViewLayout(this@apply, params)
                                                            }
                                                        }
                                                    }

                                                    isPressing = false

                                                    if (!dragTriggered) {
                                                        val midpoint = (minHeightLimit + maxHeightLimit) / 2
                                                        val isAtMin = kotlin.math.abs(targetHeight.value - minHeightLimit.value) < 1f
                                                        val isAtMax = kotlin.math.abs(targetHeight.value - maxHeightLimit.value) < 1f

                                                        val nextHeight = when {
                                                            isAtMin -> maxHeightLimit
                                                            isAtMax -> minHeightLimit
                                                            targetHeight < midpoint -> minHeightLimit
                                                            else -> maxHeightLimit
                                                        }
                                                        targetHeight = nextHeight
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp, 4.dp)
                                            .background(
                                                color = Color.White.copy(alpha = barAlpha),
                                                shape = RoundedCornerShape(2.dp)
                                            )
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

    fun dismiss() {
        mainHandler.post {
            composeView?.let { windowManager.removeView(it) }
            composeView = null
            lifecycleOwner?.destroy()
            lifecycleOwner = null
        }
    }

    @Deprecated("No longer used, UI reacts to CaptionRuntimeStore directly")
    fun updateStatus(status: String) {}

    @Deprecated("No longer used, UI reacts to CaptionRuntimeStore directly")
    fun updateCaption(sourceText: String, translatedText: String?) {}

    private fun Int.dp(density: Float): Int = (this * density).roundToInt()
}

@Composable
private fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    fontWeight: FontWeight? = null,
) {
    var displayedText by remember { mutableStateOf("") }

    LaunchedEffect(text) {
        var commonPrefixLength = 0
        while (commonPrefixLength < displayedText.length &&
            commonPrefixLength < text.length &&
            displayedText[commonPrefixLength] == text[commonPrefixLength]
        ) {
            commonPrefixLength++
        }

        if (displayedText.length > commonPrefixLength) {
            displayedText = displayedText.substring(0, commonPrefixLength)
        }

        val charsToType = text.length - displayedText.length
        if (charsToType > 0) {
            val totalDuration = charsToType.toLong() * 20L
            val durationMs = totalDuration.coerceIn(200L, 800L)
            val frameDelay = 16L
            val totalFrames = (durationMs / frameDelay).coerceAtLeast(1)
            val charsPerFrame = (charsToType.toFloat() / totalFrames).coerceAtLeast(1f)

            var currentLength = displayedText.length.toFloat()
            while (currentLength < text.length) {
                kotlinx.coroutines.delay(frameDelay)
                currentLength += charsPerFrame
                val nextLength = currentLength.toInt().coerceAtMost(text.length)
                if (nextLength > displayedText.length) {
                    displayedText = text.substring(0, nextLength)
                }
            }
        }
    }

    Text(
        text = displayedText,
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = fontWeight
    )
}
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.*
import androidx.savedstate.*
import com.jeremysu0818.caption.data.CaptionLine
import com.jeremysu0818.caption.data.CaptionRuntimeStore
import com.jeremysu0818.caption.data.t
import com.jeremysu0818.caption.ui.theme.CaptionTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
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



private const val OverlayWindowAlpha = 1f

class FloatingCaptionState(
    initialX: Int,
    initialY: Int,
    val minHeightPx: Int,
    val maxHeightPx: Int
) {
    var x by mutableIntStateOf(initialX)
    var y by mutableIntStateOf(initialY)
    var heightPx by mutableIntStateOf(minHeightPx)
}

data class WindowPosition(val x: Int, val y: Int)

private class CloseTargetState {
    var isVisible by mutableStateOf(false)
    var isActive by mutableStateOf(false)

    fun hide() {
        isVisible = false
        isActive = false
    }
}

class FloatingCaptionWindow(
    private val context: Context,
    private val onCloseRequested: () -> Unit,
    private val windowType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    
    private var controlView: ComposeView? = null
    private var contentView: ComposeView? = null
    private var contentInputView: View? = null
    private var closeTargetView: ComposeView? = null
    
    private var controlLifecycle: OverlayLifecycleOwner? = null
    private var contentLifecycle: OverlayLifecycleOwner? = null
    private var closeTargetLifecycle: OverlayLifecycleOwner? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        mainHandler.post {
            if (controlView != null) return@post

            val density = context.resources.displayMetrics.density
            val screenWidthPixels = context.resources.displayMetrics.widthPixels
            val screenHeightPixels = context.resources.displayMetrics.heightPixels

            val baseWidth = (screenWidthPixels - 32 * density).coerceAtMost(720 * density)
            val windowWidthPx = (baseWidth * 0.9f).roundToInt()

            val barHeightPx = (16 * density).roundToInt()
            val closeTargetHeightPx = (112 * density).roundToInt()
            val minHeightPx = (screenHeightPixels / 6f).roundToInt()
            val maxHeightPx = (screenHeightPixels * 0.6f).roundToInt()
            val maxContentHeightPx = maxHeightPx - barHeightPx + 1

            val initialTopY = (screenHeightPixels * 0.72f).roundToInt()

            val state = FloatingCaptionState(
                initialX = (screenWidthPixels - windowWidthPx) / 2,
                initialY = initialTopY,
                minHeightPx = minHeightPx,
                maxHeightPx = maxHeightPx
            )

            val controlParams = WindowManager.LayoutParams(
                windowWidthPx,
                barHeightPx,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = state.x
                y = state.y
                alpha = OverlayWindowAlpha
            }




            val contentParams = WindowManager.LayoutParams(
                windowWidthPx,
                maxContentHeightPx,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = state.x
                y = state.y + state.heightPx - maxContentHeightPx
                alpha = OverlayWindowAlpha
            }





            val contentInputParams = WindowManager.LayoutParams(
                windowWidthPx,
                state.heightPx - barHeightPx + 1,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = state.x
                y = state.y + barHeightPx - 1
                alpha = OverlayWindowAlpha
            }

            fun updateContentInputLayout() {
                contentInputParams.x = state.x
                contentInputParams.y = state.y + barHeightPx - 1
                contentInputParams.height = state.heightPx - barHeightPx + 1
                contentInputView?.let { windowManager.updateViewLayout(it, contentInputParams) }
            }

            val controlOwner = OverlayLifecycleOwner().apply { init() }
            val contentOwner = OverlayLifecycleOwner().apply { init() }
            val closeTargetOwner = OverlayLifecycleOwner().apply { init() }
            val closeTargetState = CloseTargetState()



            val closeTargetParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                closeTargetHeightPx,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                alpha = OverlayWindowAlpha
            }

            val cView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(controlOwner)
                setViewTreeViewModelStoreOwner(controlOwner)
                setViewTreeSavedStateRegistryOwner(controlOwner)
                setContent {
                    CaptionTheme {
                        ControlBarApp(
                            onMove = { dx, dy ->
                                state.x += dx.roundToInt()
                                state.y += dy.roundToInt()
                                
                                controlParams.x = state.x
                                controlParams.y = state.y
                                windowManager.updateViewLayout(controlView, controlParams)

                                contentParams.x = state.x
                                contentParams.y = state.y + state.heightPx - maxContentHeightPx
                                windowManager.updateViewLayout(contentView, contentParams)
                                updateContentInputLayout()





                                val isOverCloseTarget =
                                    state.y + barHeightPx >= screenHeightPixels - closeTargetHeightPx
                                closeTargetState.isVisible = isOverCloseTarget
                                closeTargetState.isActive = isOverCloseTarget
                            },
                            onDragEnded = {
                                val shouldClose = closeTargetState.isActive
                                closeTargetState.hide()
                                if (shouldClose) onCloseRequested()
                            },
                            onDragCancelled = {
                                closeTargetState.hide()
                            },
                            onResize = { dy ->
                                val oldHeight = state.heightPx
                                val newHeight = (oldHeight - dy.roundToInt()).coerceIn(state.minHeightPx, state.maxHeightPx)
                                val heightDiff = newHeight - oldHeight
                                
                                state.heightPx = newHeight
                                state.y -= heightDiff
                                
                                controlParams.y = state.y
                                windowManager.updateViewLayout(controlView, controlParams)
                                updateContentInputLayout()
                            },
                            onToggleSize = {
                                val nextHeight = if (state.heightPx < (state.minHeightPx + state.maxHeightPx) / 2) {
                                    state.maxHeightPx
                                } else {
                                    state.minHeightPx
                                }
                                val heightDiff = nextHeight - state.heightPx
                                state.heightPx = nextHeight
                                state.y -= heightDiff
                                
                                controlParams.y = state.y
                                windowManager.updateViewLayout(controlView, controlParams)
                                updateContentInputLayout()
                            }
                        )
                    }
                }
            }

            val tView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(contentOwner)
                setViewTreeViewModelStoreOwner(contentOwner)
                setViewTreeSavedStateRegistryOwner(contentOwner)
                setContent {
                    CaptionTheme {
                        ContentListApp(
                            state = state,
                            barHeightPx = barHeightPx,
                        )
                    }
                }
            }

            val inputView = View(context).apply {




                setOnTouchListener { _, event ->
                    contentView?.let { target ->
                        MotionEvent.obtain(event).also { forwarded ->
                            forwarded.offsetLocation(
                                0f,
                                (contentInputParams.y - contentParams.y).toFloat()
                            )
                            target.dispatchTouchEvent(forwarded)
                            forwarded.recycle()
                        }
                    }
                    true
                }
            }

            val closeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(closeTargetOwner)
                setViewTreeViewModelStoreOwner(closeTargetOwner)
                setViewTreeSavedStateRegistryOwner(closeTargetOwner)
                setContent {
                    CaptionTheme {
                        CloseTargetApp(closeTargetState)
                    }
                }
            }

            windowManager.addView(tView, contentParams)
            windowManager.addView(inputView, contentInputParams)
            windowManager.addView(cView, controlParams)



            windowManager.addView(closeView, closeTargetParams)
            
            controlView = cView
            contentView = tView
            contentInputView = inputView
            closeTargetView = closeView
            controlLifecycle = controlOwner
            contentLifecycle = contentOwner
            closeTargetLifecycle = closeTargetOwner
        }
    }

    fun dismiss() {
        mainHandler.post {
            controlView?.let { windowManager.removeView(it) }
            closeTargetView?.let { windowManager.removeView(it) }
            contentInputView?.let { windowManager.removeView(it) }
            contentView?.let { windowManager.removeView(it) }
            controlView = null
            contentView = null
            contentInputView = null
            closeTargetView = null
            controlLifecycle?.destroy()
            contentLifecycle?.destroy()
            closeTargetLifecycle?.destroy()
            controlLifecycle = null
            contentLifecycle = null
            closeTargetLifecycle = null
        }
    }

    @Deprecated("不再使用")
    fun updateStatus(status: String) {}

    @Deprecated("不再使用")
    fun updateCaption(sourceText: String, translatedText: String?) {}
}

@Composable
fun ControlBarApp(
    onMove: (Float, Float) -> Unit,
    onDragEnded: () -> Unit,
    onDragCancelled: () -> Unit,
    onResize: (Float) -> Unit,
    onToggleSize: () -> Unit,
) {
    var isHoveringHandle by remember { mutableStateOf(false) }
    val barAlpha by animateFloatAsState(if (isHoveringHandle) 0.8f else 0.3f, label = "Alpha")
    val touchSlop = androidx.compose.ui.platform.LocalViewConfiguration.current.touchSlop
    val resizeHandleTouchWidthPx = with(LocalDensity.current) { 80.dp.toPx() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {



            val gestureState = remember {
                object {
                    var lastX = 0f
                    var lastY = 0f
                    var initialY = 0f
                    var isResizeGesture = false
                    var isDragging = false
                }
            }

            AndroidView(
                factory = { ctx -> android.view.View(ctx) },
                update = { view ->
                    view.setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                gestureState.lastX = event.rawX
                                gestureState.lastY = event.rawY
                                gestureState.initialY = event.rawY
                                gestureState.isResizeGesture =
                                    abs(event.x - view.width / 2f) <= resizeHandleTouchWidthPx / 2f
                                gestureState.isDragging = false
                                isHoveringHandle = gestureState.isResizeGesture
                                true
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                if (gestureState.isResizeGesture) {
                                    val currentY = event.rawY
                                    if (!gestureState.isDragging &&
                                        abs(currentY - gestureState.initialY) > touchSlop
                                    ) {
                                        gestureState.isDragging = true
                                    }
                                    if (gestureState.isDragging) {
                                        onResize(currentY - gestureState.lastY)
                                    }
                                    gestureState.lastY = currentY
                                } else {
                                    onMove(
                                        event.rawX - gestureState.lastX,
                                        event.rawY - gestureState.lastY
                                    )
                                    gestureState.lastX = event.rawX
                                    gestureState.lastY = event.rawY
                                }
                                true
                            }
                            android.view.MotionEvent.ACTION_UP -> {
                                if (gestureState.isResizeGesture) {
                                    isHoveringHandle = false
                                    if (!gestureState.isDragging) onToggleSize()
                                } else {
                                    onDragEnded()
                                }
                                true
                            }
                            android.view.MotionEvent.ACTION_CANCEL -> {
                                if (gestureState.isResizeGesture) {
                                    isHoveringHandle = false
                                } else {
                                    onDragCancelled()
                                }
                                true
                            }
                            else -> true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )


            Box(
                modifier = Modifier
                    .size(36.dp, 5.dp)


                    .offset(y = 5.dp)
                    .zIndex(1f)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color.White.copy(alpha = barAlpha))
            )
        }
    }
}

@Composable
private fun CloseTargetApp(state: CloseTargetState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = state.isVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            Button(
                onClick = {},
                modifier = Modifier.padding(bottom = 20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(text = t("stop"))
            }
        }
    }
}

@Composable
fun ContentListApp(
    state: FloatingCaptionState,
    barHeightPx: Int,
) {
    val captionsState by CaptionRuntimeStore.state.collectAsState()
    val density = LocalDensity.current
    val contentHeightDp = with(density) { (state.heightPx - barHeightPx + 1).toDp() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeightDp),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            CaptionContentList(
                lines = captionsState.lines,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun CaptionContentList(
    lines: List<CaptionLine>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val isAtBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty() && isAtBottom) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .graphicsLayer { alpha = 0.99f }
            .drawWithContent {
                drawContent()


                val fadeHeight = 6.dp.toPx()
                val topStop = (fadeHeight / size.height).coerceIn(0f, 0.5f)
                val bottomStop = ((size.height - fadeHeight) / size.height).coerceIn(0.5f, 1f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,


                        (topStop * 0.55f) to Color.Transparent,
                        topStop to Color.Black,
                        bottomStop to Color.Black,
                        (bottomStop + (1f - bottomStop) * 0.45f) to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        items(lines.reversed(), key = { it.id }) { line ->
            CaptionLineItem(
                line = line,
                isNewest = line.id == lines.lastOrNull()?.id
            )
        }
    }
}

@Composable
fun CaptionLineItem(line: CaptionLine, isNewest: Boolean) {
    Column(modifier = Modifier.animateContentSize()) {
        val style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        if (isNewest && line.showTypewriter) {
            TypewriterText(text = line.sourceText, style = style)
        } else {
            Text(text = line.sourceText, style = style)
        }

        if (line.isTranslating && line.translatedText == null) {
            Text(
                text = "...",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else if (!line.translatedText.isNullOrBlank()) {
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

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    fontWeight: FontWeight? = null,
) {
    var displayedText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(text) {
        val keepLength = displayedText.length.coerceAtMost(text.length)
        if (displayedText != text.substring(0, keepLength)) {
            displayedText = text.substring(0, keepLength)
        }

        val charsToType = text.length - displayedText.length
        if (charsToType > 0) {
            val frameDelay = 16L
            val durationMs = (charsToType * 20L).coerceIn(150L, 800L)
            val charsPerFrame = (charsToType.toFloat() / (durationMs / frameDelay)).coerceAtLeast(1f)

            var currentLength = displayedText.length.toFloat()
            while (currentLength < text.length) {
                delay(frameDelay)
                currentLength += charsPerFrame
                val nextLength = currentLength.toInt().coerceAtMost(text.length)
                displayedText = text.substring(0, nextLength)
            }
        }
    }

    Text(
        text = displayedText,
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = fontWeight,
    )
}

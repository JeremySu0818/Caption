package com.jeremysu0818.caption.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
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

// Keep both WindowManager layers fully opaque. The shared Compose Surface below is
// the only place that controls the visual translucency of the floating caption.
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
    private val onCloseRequested: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    
    private var controlView: ComposeView? = null
    private var contentView: ComposeView? = null
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

            val initialTopY = (screenHeightPixels * 0.72f).roundToInt()

            val state = FloatingCaptionState(
                initialX = (screenWidthPixels - windowWidthPx) / 2,
                initialY = initialTopY,
                minHeightPx = minHeightPx,
                maxHeightPx = maxHeightPx
            )

            // 視窗 1：頂部控制條 (Control Bar)，大小固定 (24dp)
            val controlParams = WindowManager.LayoutParams(
                windowWidthPx,
                barHeightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

            // 視窗 2：內容字幕區 (Content Window)，高度固定為最大高度。
            // 不使用 FLAG_NOT_TOUCHABLE，否則部分系統會把透明 overlay 的視窗 alpha 強制降到 0.8。
            // 核心修正：我們不再讓它全螢幕，而是讓它與視窗 1 使用相同的 x/y 定位機制！
            // 這樣 Android 系統會以完全相同的規則（包含狀態列、瀏海屏偏移）來排列這兩個視窗，徹底消除兩者之間的巨大縫隙！
            val contentParams = WindowManager.LayoutParams(
                windowWidthPx,
                maxHeightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = state.x
                // 緊貼在控制條下方，重疊 1 像素消除 Subpixel 微小細縫
                y = state.y + barHeightPx - 1
                alpha = OverlayWindowAlpha
            }

            val controlOwner = OverlayLifecycleOwner().apply { init() }
            val contentOwner = OverlayLifecycleOwner().apply { init() }
            val closeTargetOwner = OverlayLifecycleOwner().apply { init() }
            val closeTargetState = CloseTargetState()

            // This window is visual only, so the original control bar continues to
            // receive the drag gesture even while it overlaps the close target.
            val closeTargetParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                closeTargetHeightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
                            state = state,
                            onMove = { dx, dy ->
                                state.x += dx.roundToInt()
                                state.y += dy.roundToInt()
                                
                                controlParams.x = state.x
                                controlParams.y = state.y
                                windowManager.updateViewLayout(controlView, controlParams)

                                contentParams.x = state.x
                                contentParams.y = state.y + barHeightPx - 1
                                windowManager.updateViewLayout(contentView, contentParams)

                                // Use the control bar's position rather than the
                                // caption panel's bottom edge. This keeps the target
                                // intentional instead of activating as soon as a tall
                                // subtitle window happens to overlap it.
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

                                contentParams.y = state.y + barHeightPx - 1
                                windowManager.updateViewLayout(contentView, contentParams)
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

                                contentParams.y = state.y + barHeightPx - 1
                                windowManager.updateViewLayout(contentView, contentParams)
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
                            barHeightPx = barHeightPx
                        )
                    }
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
            windowManager.addView(cView, controlParams)
            // Add last so the close target is visually above both the caption
            // content and its control bar. FLAG_NOT_TOUCHABLE keeps the drag
            // gesture flowing through to the control bar underneath.
            windowManager.addView(closeView, closeTargetParams)
            
            controlView = cView
            contentView = tView
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
            contentView?.let { windowManager.removeView(it) }
            contentView = null
            controlView = null
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
    state: FloatingCaptionState,
    onMove: (Float, Float) -> Unit,
    onDragEnded: () -> Unit,
    onDragCancelled: () -> Unit,
    onResize: (Float) -> Unit,
    onToggleSize: () -> Unit
) {
    var isHoveringHandle by remember { mutableStateOf(false) }
    val barAlpha by animateFloatAsState(if (isHoveringHandle) 0.8f else 0.3f, label = "Alpha")
    val touchSlop = androidx.compose.ui.platform.LocalViewConfiguration.current.touchSlop

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val dragState = remember {
                object {
                    var lastX = 0f
                    var lastY = 0f
                }
            }

            // 拖曳控制區域
            AndroidView(
                factory = { ctx -> android.view.View(ctx) },
                update = { view ->
                    view.setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                dragState.lastX = event.rawX
                                dragState.lastY = event.rawY
                                true
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                val dx = event.rawX - dragState.lastX
                                val dy = event.rawY - dragState.lastY
                                onMove(dx, dy)
                                dragState.lastX = event.rawX
                                dragState.lastY = event.rawY
                                true
                            }
                            android.view.MotionEvent.ACTION_UP -> {
                                onDragEnded()
                                true
                            }
                            android.view.MotionEvent.ACTION_CANCEL -> {
                                onDragCancelled()
                                true
                            }
                            else -> true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 縮放控制區域 (中間膠囊按鈕)
            val resizeState = remember {
                object {
                    var lastY = 0f
                    var initialY = 0f
                    var isDragging = false
                }
            }

            Box(
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx -> android.view.View(ctx) },
                    update = { view ->
                        view.setOnTouchListener { _, event ->
                            when (event.actionMasked) {
                                android.view.MotionEvent.ACTION_DOWN -> {
                                    isHoveringHandle = true
                                    resizeState.initialY = event.rawX // 用 rawX 是對的，因為我們只需要獲取 Down 的起點
                                    resizeState.initialY = event.rawY
                                    resizeState.lastY = event.rawY
                                    resizeState.isDragging = false
                                    true
                                }
                                android.view.MotionEvent.ACTION_MOVE -> {
                                    val currentY = event.rawY
                                    if (!resizeState.isDragging && abs(currentY - resizeState.initialY) > touchSlop) {
                                        resizeState.isDragging = true
                                    }
                                    if (resizeState.isDragging) {
                                        val dy = currentY - resizeState.lastY
                                        onResize(dy)
                                    }
                                    resizeState.lastY = currentY
                                    true
                                }
                                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                    isHoveringHandle = false
                                    if (!resizeState.isDragging && event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                                        onToggleSize()
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .size(36.dp, 5.dp)
                        // Center the handle between the window top and the
                        // point where the top caption fade starts.
                        .offset(y = 5.dp)
                        .zIndex(1f)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color.White.copy(alpha = barAlpha))
                )
            }
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
    barHeightPx: Int
) {
    val captionsState by CaptionRuntimeStore.state.collectAsState()
    val density = LocalDensity.current

    // 高度多加 1 像素，用來補償 1 像素的重疊，確保總高度不變
    val contentHeightDp = with(density) { (state.heightPx - barHeightPx + 1).toDp() }

    // 因為內容視窗現在與控制條使用同一個坐標系移動，所以不需要再使用全螢幕偏移動畫！
    // 直接讓它靠最頂部 (Alignment.TopCenter) 畫出來，隨著視窗物理移動即可，這也是零抖動的！
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeightDp),
            shape = RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 0.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            CaptionContentList(
                lines = captionsState.lines,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
                // Leave a 2dp gap between the fade and the first/last line
                // inside the list's 8dp vertical padding.
                val fadeHeight = 6.dp.toPx()
                val topStop = (fadeHeight / size.height).coerceIn(0f, 0.5f)
                val bottomStop = ((size.height - fadeHeight) / size.height).coerceIn(0.5f, 1f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        // Keep the same 16dp fade area, but hold the text longer
                        // before fading so the edge transition is more apparent.
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

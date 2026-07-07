package com.jeremysu0818.caption.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

class FloatingCaptionWindow(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var rootView: LinearLayout? = null
    private var sourceTextView: TextView? = null
    private var translatedTextView: TextView? = null
    private var statusTextView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        mainHandler.post {
            if (rootView != null) return@post

            val density = context.resources.displayMetrics.density
            val width = (context.resources.displayMetrics.widthPixels - 32.dp(density))
                .coerceAtMost(720.dp(density))
            val initialY = (context.resources.displayMetrics.heightPixels * 0.72f).roundToInt()

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18.dp(density), 14.dp(density), 18.dp(density), 14.dp(density))
                background = GradientDrawable().apply {
                    cornerRadius = 20.dp(density).toFloat()
                    setColor(Color.argb(205, 20, 20, 24))
                    setStroke(1.dp(density), Color.argb(80, 255, 255, 255))
                }
                alpha = 0.94f
            }

            statusTextView = TextView(context).apply {
                text = "準備字幕"
                setTextColor(Color.argb(210, 255, 255, 255))
                textSize = 12f
                includeFontPadding = false
            }
            sourceTextView = TextView(context).apply {
                text = ""
                setTextColor(Color.WHITE)
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 4
                includeFontPadding = true
            }
            translatedTextView = TextView(context).apply {
                text = ""
                visibility = View.GONE
                setTextColor(Color.argb(230, 214, 232, 255))
                textSize = 17f
                maxLines = 4
                includeFontPadding = true
            }

            root.addView(statusTextView)
            root.addView(sourceTextView)
            root.addView(translatedTextView)

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

            root.setOnTouchListener(DragTouchListener(params))
            windowManager.addView(root, params)
            rootView = root
            layoutParams = params
        }
    }

    fun updateStatus(status: String) {
        mainHandler.post {
            statusTextView?.text = status
        }
    }

    fun updateCaption(sourceText: String, translatedText: String?) {
        mainHandler.post {
            sourceTextView?.text = sourceText
            val translatedView = translatedTextView ?: return@post
            if (translatedText.isNullOrBlank()) {
                translatedView.text = ""
                translatedView.visibility = View.GONE
            } else {
                translatedView.text = translatedText
                translatedView.visibility = View.VISIBLE
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
            rootView?.let { windowManager.removeView(it) }
            rootView = null
            sourceTextView = null
            translatedTextView = null
            statusTextView = null
            layoutParams = null
        }
    }

    private inner class DragTouchListener(
        private val params: WindowManager.LayoutParams,
    ) : View.OnTouchListener {
        private var startRawX = 0f
        private var startRawY = 0f
        private var startX = 0
        private var startY = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - startRawX).roundToInt()
                    params.y = startY + (event.rawY - startRawY).roundToInt()
                    windowManager.updateViewLayout(view, params)
                    return true
                }
            }
            return false
        }
    }

    private fun Int.dp(density: Float): Int = (this * density).roundToInt()
}

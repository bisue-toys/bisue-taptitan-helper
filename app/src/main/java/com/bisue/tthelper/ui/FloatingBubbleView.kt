package com.bisue.tthelper.ui

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.bisue.tthelper.R
import kotlin.math.abs

/**
 * 게임 화면 위에 상시 떠 있는 드래그 가능한 미니 플로팅 버블
 */
class FloatingBubbleView(
    private val context: Context,
    private val onBubbleClicked: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val view: View = LayoutInflater.from(context).inflate(R.layout.view_floating_bubble, null)
    private val tvBadge: TextView = view.findViewById(R.id.tvBubbleBadge)

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 50
        y = 300
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    init {
        setupTouchListener()
    }

    private fun setupTouchListener() {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        onBubbleClicked()
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun updateStatusBadge(isRunning: Boolean, badgeText: String? = null) {
        view.post {
            if (badgeText != null) {
                tvBadge.text = badgeText
            } else {
                tvBadge.text = if (isRunning) "ON" else "OFF"
                tvBadge.setTextColor(if (isRunning) 0xFF4CAF50.toInt() else 0xFF03DAC5.toInt())
            }
        }
    }

    fun show() {
        if (view.parent == null) {
            windowManager.addView(view, params)
        }
    }

    fun hide() {
        if (view.parent != null) {
            windowManager.removeView(view)
        }
    }
}

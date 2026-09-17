package com.bisue.tthelper.ui

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import com.bisue.tthelper.R
import kotlin.math.hypot

/**
 * 게임 화면 위에 상시 떠 있는 드래그 가능한 미니 플로팅 버블
 * 갤럭시 S24 Ultra 고해상도 터치 슬롭(Touch Slop) 보정 및 클릭 판정 강화
 */
class FloatingBubbleView(
    private val context: Context,
    private val onBubbleClicked: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val view: View = LayoutInflater.from(context).inflate(R.layout.view_floating_bubble, null)
    private val tvBadge: TextView = view.findViewById(R.id.tvBubbleBadge)

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.coerceAtLeast(40)

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 50
        y = 350
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var downTimestamp = 0L

    init {
        setupTouchListener()
    }

    private fun setupTouchListener() {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    downTimestamp = System.currentTimeMillis()
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val distance = hypot(dx, dy)

                    if (distance > touchSlop) {
                        isDragging = true
                        params.x = (initialX + dx).toInt()
                        params.y = (initialY + dy).toInt()
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    val clickDuration = System.currentTimeMillis() - downTimestamp
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val distance = hypot(dx, dy)

                    // 400ms 이내에 touchSlop 이하로 움직였으면 명백한 탭(클릭)으로 판정
                    if (!isDragging || (clickDuration < 400 && distance < touchSlop * 1.5)) {
                        onBubbleClicked()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    isDragging = false
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
                tvBadge.text = if (isRunning) "RUN" else "OFF"
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

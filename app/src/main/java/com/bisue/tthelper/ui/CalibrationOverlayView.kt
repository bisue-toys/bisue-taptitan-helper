package com.bisue.tthelper.ui

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.bisue.tthelper.core.HelperConfig
import com.bisue.tthelper.touch.CoordinateProfile
import com.bisue.tthelper.touch.HumanTouchEngine
import com.bisue.tthelper.touch.RelativePoint
import com.bisue.tthelper.touch.RelativeRect
import com.bisue.tthelper.vision.ImagePreprocessor
import com.bisue.tthelper.vision.OcrEngine
import com.bisue.tthelper.vision.ScreenCapturer
import com.bisue.tthelper.vision.StageParser
import kotlinx.coroutines.*

/**
 * 게임 화면 위에서 직접 층수 인식 영역(ROI) 및 핵심 터치 위치 핀을 눈으로 보며 맞추는
 * 인터랙티브 전면 캘리브레이션 오버레이
 */
class CalibrationOverlayView(
    private val serviceContext: Context,
    private val config: HelperConfig,
    private val screenCapturer: ScreenCapturer,
    private val ocrEngine: OcrEngine,
    private val touchEngine: HumanTouchEngine,
    private val onSaved: () -> Unit,
    private val onClosed: () -> Unit
) : FrameLayout(serviceContext) {

    enum class Mode {
        STAGE_ROI,
        TOUCH_PINS
    }

    private val windowManager = serviceContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val stageParser = StageParser()

    private var currentMode = Mode.STAGE_ROI

    // 캘리브레이션 핀 데이터
    data class PinItem(
        val id: Int,
        val name: String,
        val color: Int,
        var x: Float,
        var y: Float
    )

    private var currentRoi = CoordinateProfile.getStageRoi(config)
    private val pins = mutableListOf<PinItem>()
    private var selectedPin: PinItem? = null

    // UI 컴포넌트
    private lateinit var canvasView: OverlayCanvasView
    private lateinit var btnTabRoi: Button
    private lateinit var btnTabPins: Button
    private lateinit var roiControlsLayout: LinearLayout
    private lateinit var pinControlsLayout: LinearLayout
    private lateinit var tvOcrResult: TextView
    private lateinit var tvPinInfo: TextView
    private lateinit var btnTestTouch: Button
    private lateinit var btnTestOcr: Button

    private val windowParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    init {
        loadCalibrationData()
        setupUI()
    }

    private fun loadCalibrationData() {
        currentRoi = CoordinateProfile.getStageRoi(config)
        pins.clear()

        val tabPt = CoordinateProfile.getSwordmasterTab(config)
        val multPt = CoordinateProfile.getLevelupMultiplier(config)
        val upPt = CoordinateProfile.getSwordmasterUpgrade(config)
        val prestPt = CoordinateProfile.getPrestigeButton(config)

        val skillSlots = CoordinateProfile.getActiveSkillSlots(config)
        val skill1Pt = skillSlots.firstOrNull() ?: CoordinateProfile.ACTIVE_SKILL_SLOTS.first()
        val skill6Pt = skillSlots.lastOrNull() ?: CoordinateProfile.ACTIVE_SKILL_SLOTS.last()

        pins.add(PinItem(1, "1. 탭 메뉴 (소드마스터)", Color.parseColor("#FF5252"), tabPt.x, tabPt.y))
        pins.add(PinItem(2, "2. 레벨업 단위 (MAX)", Color.parseColor("#FF9800"), multPt.x, multPt.y))
        pins.add(PinItem(3, "3. 소드마스터 렙업", Color.parseColor("#FFEB3B"), upPt.x, upPt.y))
        pins.add(PinItem(4, "4. 스킬 1번", Color.parseColor("#00E5FF"), skill1Pt.x, skill1Pt.y))
        pins.add(PinItem(5, "5. 스킬 6번", Color.parseColor("#2979FF"), skill6Pt.x, skill6Pt.y))
        pins.add(PinItem(6, "6. 환생 버튼", Color.parseColor("#E040FB"), prestPt.x, prestPt.y))

        selectedPin = pins.firstOrNull()
    }

    private fun setupUI() {
        // 1. 드로잉 & 터치 캔버스 뷰
        canvasView = OverlayCanvasView(serviceContext)
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 2. 하단 일체형 컨트롤 패널 (상단은 완전히 개방하여 층수 영역 조작에 방해 없도록 함)
        val bottomCard = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE181825"))
            setPadding(dp(16), dp(12), dp(16), dp(24))
            elevation = dp(8).toFloat()
        }

        // 2-1. 모드 탭 전환 및 닫기 버튼 줄
        val tabRow = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }

        btnTabRoi = Button(serviceContext).apply {
            text = "📐 층수 영역"
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00E676"))
            setOnClickListener { switchMode(Mode.STAGE_ROI) }
        }

        btnTabPins = Button(serviceContext).apply {
            text = "📌 터치 핀"
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            setBackgroundColor(Color.parseColor("#333333"))
            val lp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.marginStart = dp(8)
            layoutParams = lp
            setOnClickListener { switchMode(Mode.TOUCH_PINS) }
        }

        val btnClose = Button(serviceContext).apply {
            text = "✕ 닫기"
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#444444"))
            val lp = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.weight = 0f
            layoutParams = lp
            setOnClickListener { hide() }
        }

        val space = View(serviceContext)
        tabRow.addView(btnTabRoi)
        tabRow.addView(btnTabPins)
        tabRow.addView(space, LinearLayout.LayoutParams(0, 1, 1f))
        tabRow.addView(btnClose)

        bottomCard.addView(tabRow)

        // 3-1. 층수 영역 컨트롤
        roiControlsLayout = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.VERTICAL
        }

        btnTestOcr = Button(serviceContext).apply {
            text = "🔍 현재 영역 실시간 OCR 테스트"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00E676"))
            textSize = 13f
            setOnClickListener { testOcrOnCurrentRoi() }
        }

        tvOcrResult = TextView(serviceContext).apply {
            text = "초록색 박스를 층수 위치로 이동한 후 [OCR 테스트]를 누르세요."
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, dp(6), 0, dp(6))
        }

        roiControlsLayout.addView(btnTestOcr)
        roiControlsLayout.addView(tvOcrResult)
        bottomCard.addView(roiControlsLayout)

        // 3-2. 핀 컨트롤
        pinControlsLayout = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        tvPinInfo = TextView(serviceContext).apply {
            text = "화면의 핀을 드래그하여 게임 버튼 위에 배치하세요."
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, dp(4), 0, dp(6))
        }

        btnTestTouch = Button(serviceContext).apply {
            text = "👆 선택된 핀 터치 테스트"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2979FF"))
            textSize = 13f
            setOnClickListener { testTouchOnSelectedPin() }
        }

        pinControlsLayout.addView(tvPinInfo)
        pinControlsLayout.addView(btnTestTouch)
        bottomCard.addView(pinControlsLayout)

        // 3-3. 공통 하단 저장/초기화 버튼 줄
        val actionRow = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        val btnReset = Button(serviceContext).apply {
            text = "🔄 기본값 초기화"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#555555"))
            textSize = 12f
            setOnClickListener { resetToDefaults() }
        }

        val btnSave = Button(serviceContext).apply {
            text = "💾 설정 저장 및 완료"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6200EE"))
            textSize = 13f
            val lp = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(8)
            layoutParams = lp
            setOnClickListener { saveCalibration() }
        }

        actionRow.addView(btnReset)
        actionRow.addView(btnSave)
        bottomCard.addView(actionRow)

        addView(bottomCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    private fun switchMode(mode: Mode) {
        currentMode = mode
        if (mode == Mode.STAGE_ROI) {
            btnTabRoi.setBackgroundColor(Color.parseColor("#00E676"))
            btnTabRoi.setTextColor(Color.BLACK)
            btnTabPins.setBackgroundColor(Color.parseColor("#333333"))
            btnTabPins.setTextColor(Color.parseColor("#AAAAAA"))

            roiControlsLayout.visibility = View.VISIBLE
            pinControlsLayout.visibility = View.GONE
        } else {
            btnTabPins.setBackgroundColor(Color.parseColor("#2979FF"))
            btnTabPins.setTextColor(Color.WHITE)
            btnTabRoi.setBackgroundColor(Color.parseColor("#333333"))
            btnTabRoi.setTextColor(Color.parseColor("#AAAAAA"))

            roiControlsLayout.visibility = View.GONE
            pinControlsLayout.visibility = View.VISIBLE
            updatePinInfoText()
        }
        canvasView.invalidate()
    }

    private fun updatePinInfoText() {
        val pin = selectedPin
        if (pin != null) {
            val pctX = String.format("%.1f", pin.x * 100)
            val pctY = String.format("%.1f", pin.y * 100)
            tvPinInfo.text = "선택됨: [${pin.name}] (X: $pctX%, Y: $pctY%)"
            btnTestTouch.isEnabled = true
        } else {
            tvPinInfo.text = "원하는 핀을 화면에서 터치해 선택하세요."
            btnTestTouch.isEnabled = false
        }
    }

    private fun testOcrOnCurrentRoi() {
        tvOcrResult.text = "화면 캡처 및 OCR 분석 중..."
        scope.launch {
            try {
                val cropped = withContext(Dispatchers.IO) {
                    screenCapturer.captureRoi(currentRoi)
                }

                if (cropped == null) {
                    tvOcrResult.text = "❌ 캡처 실패: 화면 캡처 권한이 유효한지 확인하세요."
                    return@launch
                }

                val resultText = withContext(Dispatchers.IO) {
                    val enhanced = ImagePreprocessor.enhanceForOcr(cropped)
                    val raw = ocrEngine.recognizeText(enhanced)
                    enhanced.recycle()
                    raw
                }

                val stage = StageParser.parseStageNumber(resultText)
                if (stage != null) {
                    tvOcrResult.text = "✅ 인식 성공: ${String.format("%,d", stage)}층 (원본: '$resultText')"
                    tvOcrResult.setTextColor(Color.parseColor("#00E676"))
                } else {
                    tvOcrResult.text = "⚠️ 층수 파싱 실패 (읽힌 텍스트: '$resultText') - 박스 범위를 조절하세요."
                    tvOcrResult.setTextColor(Color.parseColor("#FFC107"))
                }
            } catch (e: Exception) {
                tvOcrResult.text = "오류 발생: ${e.message}"
                tvOcrResult.setTextColor(Color.parseColor("#FF5252"))
            }
        }
    }

    private fun testTouchOnSelectedPin() {
        val pin = selectedPin ?: return
        Toast.makeText(serviceContext, "'${pin.name}' 위치 터치 테스트 중...", Toast.LENGTH_SHORT).show()

        // 일시적으로 플래그를 FLAG_NOT_TOUCHABLE로 변경하여 터치가 게임에 도달하도록 함
        val origFlags = windowParams.flags
        windowParams.flags = origFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windowManager.updateViewLayout(this, windowParams)
        } catch (e: Exception) {
            Log.e("CalibrationOverlay", "updateViewLayout error: ${e.message}")
        }

        scope.launch {
            delay(150)
            touchEngine.tapRelative(RelativePoint(pin.x, pin.y), jitterPx = 4f, postDelayMs = 200)
            delay(350)
            windowParams.flags = origFlags
            try {
                windowManager.updateViewLayout(this@CalibrationOverlayView, windowParams)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun resetToDefaults() {
        config.resetCalibrationToDefaults()
        loadCalibrationData()
        canvasView.invalidate()
        updatePinInfoText()
        tvOcrResult.text = "기본값으로 초기화되었습니다."
        tvOcrResult.setTextColor(Color.WHITE)
        Toast.makeText(serviceContext, "S24 Ultra 기본 비율로 초기화되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun saveCalibration() {
        config.hasCustomCalibration = true
        config.customRoiLeft = currentRoi.left
        config.customRoiTop = currentRoi.top
        config.customRoiRight = currentRoi.right
        config.customRoiBottom = currentRoi.bottom

        pins.forEach { pin ->
            when (pin.id) {
                1 -> {
                    config.customTabSwordmasterX = pin.x
                    config.customTabSwordmasterY = pin.y
                }
                2 -> {
                    config.customBtnMultiplierX = pin.x
                    config.customBtnMultiplierY = pin.y
                }
                3 -> {
                    config.customBtnUpgradeX = pin.x
                    config.customBtnUpgradeY = pin.y
                }
                4 -> {
                    config.customSkillSlot1X = pin.x
                    config.customSkillSlot1Y = pin.y
                }
                5 -> {
                    config.customSkillSlot6X = pin.x
                    config.customSkillSlot6Y = pin.y
                }
                6 -> {
                    config.customBtnPrestigeX = pin.x
                    config.customBtnPrestigeY = pin.y
                }
            }
        }

        Toast.makeText(serviceContext, "🎯 캘리브레이션 좌표가 저장되었습니다!", Toast.LENGTH_SHORT).show()
        hide()
        onSaved()
    }

    fun show() {
        if (parent == null) {
            loadCalibrationData()
            windowManager.addView(this, windowParams)
        }
    }

    fun hide() {
        if (parent != null) {
            scope.cancel()
            windowManager.removeView(this)
            onClosed()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private enum class DragMode {
        NONE,
        MOVE_ROI,
        RESIZE_ROI_BR,
        MOVE_PIN
    }

    // =========================================================================
    // 캔버스 드로잉 및 드래그 제스처 처리 내부 클래스
    // =========================================================================
    private inner class OverlayCanvasView(context: Context) : View(context) {

        private val dimPaint = Paint().apply {
            color = Color.parseColor("#99000000")
            style = Paint.Style.FILL
        }

        private val lightDimPaint = Paint().apply {
            color = Color.parseColor("#44000000")
            style = Paint.Style.FILL
        }

        private val roiBorderPaint = Paint().apply {
            color = Color.parseColor("#00E676")
            style = Paint.Style.STROKE
            strokeWidth = dp(3).toFloat()
            isAntiAlias = true
        }

        private val roiHandlePaint = Paint().apply {
            color = Color.parseColor("#00E676")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val pinGlowPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val pinSolidPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = dp(11).toFloat()
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        private val textBgPaint = Paint().apply {
            color = Color.parseColor("#CC181825")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val linePaint = Paint().apply {
            color = Color.parseColor("#8000E5FF")
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            pathEffect = DashPathEffect(floatArrayOf(dp(6).toFloat(), dp(4).toFloat()), 0f)
            isAntiAlias = true
        }

        private var dragMode = DragMode.NONE
        private var lastTouchX = 0f
        private var lastTouchY = 0f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            when (currentMode) {
                Mode.STAGE_ROI -> drawRoiMode(canvas, w, h)
                Mode.TOUCH_PINS -> drawPinsMode(canvas, w, h)
            }
        }

        private fun drawRoiMode(canvas: Canvas, w: Float, h: Float) {
            val rLeft = currentRoi.left * w
            val rTop = currentRoi.top * h
            val rRight = currentRoi.right * w
            val rBottom = currentRoi.bottom * h

            // 반투명 배경 + 사각형 구멍(Cutout)
            val path = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addRect(0f, 0f, w, h, Path.Direction.CW)
                addRect(rLeft, rTop, rRight, rBottom, Path.Direction.CW)
            }
            canvas.drawPath(path, dimPaint)

            // 초록색 ROI 테두리
            canvas.drawRect(rLeft, rTop, rRight, rBottom, roiBorderPaint)

            // 우하단 리사이즈 핸들
            val handleRadius = dp(16).toFloat()
            canvas.drawCircle(rRight, rBottom, handleRadius, roiHandlePaint)

            // 라벨 배지 (박스 위 또는 아래 자동 배치)
            val label = "층수 인식 영역 (드래그하여 이동 / 우하단 점으로 크기 조절)"
            val textWidth = textPaint.measureText(label)
            val badgePadding = dp(6).toFloat()
            val badgeTop = if (rTop > dp(36)) rTop - dp(26) else rBottom + dp(8)
            val badgeRect = RectF(
                rLeft,
                badgeTop,
                rLeft + textWidth + badgePadding * 2,
                badgeTop + dp(20)
            )
            canvas.drawRoundRect(badgeRect, dp(4).toFloat(), dp(4).toFloat(), textBgPaint)
            canvas.drawText(
                label,
                badgeRect.centerX(),
                badgeRect.centerY() + dp(4),
                textPaint
            )
        }

        private fun drawPinsMode(canvas: Canvas, w: Float, h: Float) {
            // 게임 화면이 잘 보이도록 옅은 반투명 처리
            canvas.drawRect(0f, 0f, w, h, lightDimPaint)

            // 스킬 1번과 6번 사이 점선 및 4개 중간 슬롯 자동 보간 표시
            val pin4 = pins.find { it.id == 4 }
            val pin5 = pins.find { it.id == 5 }
            if (pin4 != null && pin5 != null) {
                val p4x = pin4.x * w
                val p4y = pin4.y * h
                val p5x = pin5.x * w
                val p5y = pin5.y * h
                canvas.drawLine(p4x, p4y, p5x, p5y, linePaint)

                // 2, 3, 4, 5번 스킬 위치 점 그리기
                for (i in 1..4) {
                    val ratio = i / 5f
                    val ix = p4x + (p5x - p4x) * ratio
                    val iy = p4y + (p5y - p4y) * ratio
                    canvas.drawCircle(ix, iy, dp(6).toFloat(), pinGlowPaint.apply {
                        color = Color.parseColor("#4400E5FF")
                    })
                    canvas.drawCircle(ix, iy, dp(3).toFloat(), pinSolidPaint.apply {
                        color = Color.WHITE
                    })
                }
            }

            // 모든 핀 그리기
            pins.forEach { pin ->
                val px = pin.x * w
                val py = pin.y * h
                val isSelected = (pin == selectedPin)

                // 외곽 발광 원
                pinGlowPaint.color = pin.color
                pinGlowPaint.alpha = if (isSelected) 120 else 60
                val glowRadius = if (isSelected) dp(28).toFloat() else dp(20).toFloat()
                canvas.drawCircle(px, py, glowRadius, pinGlowPaint)

                // 내부 솔리드 원
                pinSolidPaint.color = pin.color
                val solidRadius = if (isSelected) dp(14).toFloat() else dp(11).toFloat()
                canvas.drawCircle(px, py, solidRadius, pinSolidPaint)

                // 흰색 테두리
                val strokePaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = dp(2).toFloat()
                    isAntiAlias = true
                }
                canvas.drawCircle(px, py, solidRadius, strokePaint)

                // 핀 번호
                val numPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = dp(11).toFloat()
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                canvas.drawText(pin.id.toString(), px, py + dp(4), numPaint)

                // 핀 라벨 배지 (핀 상단)
                val label = pin.name
                val lWidth = textPaint.measureText(label)
                val lRect = RectF(
                    px - lWidth / 2 - dp(6),
                    py - solidRadius - dp(24),
                    px + lWidth / 2 + dp(6),
                    py - solidRadius - dp(6)
                )
                canvas.drawRoundRect(lRect, dp(4).toFloat(), dp(4).toFloat(), textBgPaint)
                canvas.drawText(label, px, lRect.centerY() + dp(4), textPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y
            val w = width.toFloat()
            val h = height.toFloat()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = x
                    lastTouchY = y

                    if (currentMode == Mode.STAGE_ROI) {
                        val rLeft = currentRoi.left * w
                        val rTop = currentRoi.top * h
                        val rRight = currentRoi.right * w
                        val rBottom = currentRoi.bottom * h

                        val distToHandle = Math.hypot((x - rRight).toDouble(), (y - rBottom).toDouble()).toFloat()
                        if (distToHandle <= dp(32)) {
                            dragMode = DragMode.RESIZE_ROI_BR
                            return true
                        } else if (x in rLeft..rRight && y in rTop..rBottom) {
                            dragMode = DragMode.MOVE_ROI
                            return true
                        }
                    } else if (currentMode == Mode.TOUCH_PINS) {
                        // 가장 가까운 핀 탐색
                        val touchRadius = dp(36).toFloat()
                        val hitPin = pins.minByOrNull { pin ->
                            val px = pin.x * w
                            val py = pin.y * h
                            Math.hypot((x - px).toDouble(), (y - py).toDouble()).toFloat()
                        }

                        if (hitPin != null) {
                            val px = hitPin.x * w
                            val py = hitPin.y * h
                            val dist = Math.hypot((x - px).toDouble(), (y - py).toDouble()).toFloat()
                            if (dist <= touchRadius) {
                                selectedPin = hitPin
                                dragMode = DragMode.MOVE_PIN
                                updatePinInfoText()
                                invalidate()
                                return true
                            }
                        }
                    }
                    dragMode = DragMode.NONE
                    return super.onTouchEvent(event)
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    lastTouchX = x
                    lastTouchY = y

                    when (dragMode) {
                        DragMode.MOVE_ROI -> {
                            val dxRatio = dx / w
                            val dyRatio = dy / h
                            val boxW = currentRoi.right - currentRoi.left
                            val boxH = currentRoi.bottom - currentRoi.top

                            val newLeft = (currentRoi.left + dxRatio).coerceIn(0f, 1f - boxW)
                            val newTop = (currentRoi.top + dyRatio).coerceIn(0.04f, 0.95f - boxH)
                            currentRoi = RelativeRect(newLeft, newTop, newLeft + boxW, newTop + boxH)
                            invalidate()
                            return true
                        }

                        DragMode.RESIZE_ROI_BR -> {
                            val newRight = (x / w).coerceIn(currentRoi.left + 0.1f, 1.0f)
                            val newBottom = (y / h).coerceIn(currentRoi.top + 0.03f, 0.95f)
                            currentRoi = RelativeRect(currentRoi.left, currentRoi.top, newRight, newBottom)
                            invalidate()
                            return true
                        }

                        DragMode.MOVE_PIN -> {
                            selectedPin?.let { pin ->
                                pin.x = (x / w).coerceIn(0.02f, 0.98f)
                                pin.y = (y / h).coerceIn(0.05f, 0.95f)
                                updatePinInfoText()
                                invalidate()
                            }
                            return true
                        }

                        DragMode.NONE -> return super.onTouchEvent(event)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragMode = DragMode.NONE
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}

package com.bisue.tthelper.touch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import com.bisue.tthelper.core.TtAccessibilityService
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 게임사 제재(Ban) 방지 및 자연스러운 인간형 터치 주입 엔진
 * - 정규분포 기반 좌표 지터(Jitter)
 * - 터치 홀드 시간 난수화 (60 ~ 120ms)
 * - 단계별 반응 지연시간 난수화
 */
class HumanTouchEngine(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    private val random = Random(System.currentTimeMillis())

    /**
     * 특정 상대 좌표를 인간다운 오차를 더해 탭
     */
    suspend fun tapRelative(
        point: RelativePoint,
        jitterPx: Float = 12f,
        postDelayMs: Long = 200L
    ): Boolean {
        val (baseX, baseY) = CoordinateProfile.toPixel(point, screenWidth, screenHeight)

        // 가우시안 분포 형태의 오차 생성 (-jitterPx ~ +jitterPx)
        val deltaX = (random.nextFloat() * 2 - 1) * jitterPx
        val deltaY = (random.nextFloat() * 2 - 1) * jitterPx

        val finalX = (baseX + deltaX).coerceIn(0f, screenWidth.toFloat())
        val finalY = (baseY + deltaY).coerceIn(0f, screenHeight.toFloat())

        val holdDuration = random.nextLong(65, 115) // 사람이 누르는 시간

        val success = performTap(finalX, finalY, holdDuration)
        if (postDelayMs > 0) {
            val randomDelay = (postDelayMs * random.nextDouble(0.85, 1.25)).toLong()
            delay(randomDelay)
        }
        return success
    }

    /**
     * 연속 n회 연타 (예: 소드마스터 레벨업 버튼)
     */
    suspend fun multiTapRelative(
        point: RelativePoint,
        times: Int,
        intervalMs: Long = 120L
    ) {
        repeat(times) {
            tapRelative(point, jitterPx = 8f, postDelayMs = intervalMs)
        }
    }

    /**
     * 부드러운 스크롤/스와이프 제스처
     */
    suspend fun swipeRelative(
        start: RelativePoint,
        end: RelativePoint,
        durationMs: Long = 350L,
        postDelayMs: Long = 400L
    ): Boolean {
        val (startX, startY) = CoordinateProfile.toPixel(start, screenWidth, screenHeight)
        val (endX, endY) = CoordinateProfile.toPixel(end, screenWidth, screenHeight)

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        val service = TtAccessibilityService.instance ?: return false
        val success = service.dispatchGesture(gesture, null, null)

        if (postDelayMs > 0) {
            val randomDelay = (postDelayMs * random.nextDouble(0.9, 1.2)).toLong()
            delay(randomDelay)
        }
        return success
    }

    private fun performTap(x: Float, y: Float, durationMs: Long): Boolean {
        val service = TtAccessibilityService.instance ?: return false

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        return service.dispatchGesture(gesture, null, null)
    }
}

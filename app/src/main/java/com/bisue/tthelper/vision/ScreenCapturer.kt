package com.bisue.tthelper.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.Log
import com.bisue.tthelper.touch.CoordinateProfile

/**
 * MediaProjection 기반 화면 캡처 및 상단 층수 영역(ROI) 초경량 크롭기
 */
class ScreenCapturer(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    val screenWidth: Int,
    val screenHeight: Int,
    private val densityDpi: Int
) {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    init {
        registerMediaProjectionCallback()
        initVirtualDisplay()
    }

    private fun registerMediaProjectionCallback() {
        try {
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection 세션 중지됨")
                    release()
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))
            Log.i(TAG, "MediaProjection.Callback 등록 완료 (Android 14+ 필수 요구사항)")
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection.Callback 등록 오류: ${e.message}", e)
        }
    }

    private fun initVirtualDisplay() {
        // 2개의 버퍼를 사용하여 최신 프레임 획득
        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "TtScreenCapturer",
            screenWidth,
            screenHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
        Log.i(TAG, "VirtualDisplay 생성 완료 (${screenWidth}x${screenHeight}, dpi=$densityDpi)")
    }

    /**
     * 지정된 임의의 상대 비율 사각형(ROI)을 크롭하여 비트맵으로 반환
     */
    @Synchronized
    fun captureRoi(roiRatio: com.bisue.tthelper.touch.RelativeRect): Bitmap? {
        val reader = imageReader ?: return null
        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return null
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            // 전체 화면 비트맵 생성 (stride 보정)
            val fullBitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            fullBitmap.copyPixelsFromBuffer(buffer)

            // 대상 ROI 픽셀 사각형 계산
            val roiRect = CoordinateProfile.toPixelRect(
                roiRatio,
                screenWidth,
                screenHeight
            )

            // 안전한 경계 범위 보정
            val safeLeft = roiRect.left.coerceIn(0, screenWidth - 1)
            val safeTop = roiRect.top.coerceIn(0, screenHeight - 1)
            val safeWidth = roiRect.width().coerceAtMost(screenWidth - safeLeft)
            val safeHeight = roiRect.height().coerceAtMost(screenHeight - safeTop)

            if (safeWidth <= 0 || safeHeight <= 0) {
                fullBitmap.recycle()
                return null
            }

            // 요청된 부분만 잘라낸 비트맵
            val cropped = Bitmap.createBitmap(fullBitmap, safeLeft, safeTop, safeWidth, safeHeight)
            fullBitmap.recycle() // 전체 화면 메모리 즉시 해제

            return cropped
        } catch (e: Exception) {
            Log.e(TAG, "화면 캡처 중 오류 발생: ${e.message}")
            return null
        } finally {
            image?.close()
        }
    }

    /**
     * 상단 층수 영역(ROI)만 크롭하여 비트맵으로 반환
     */
    fun captureStageRoi(config: com.bisue.tthelper.core.HelperConfig? = null): Bitmap? {
        val roi = CoordinateProfile.getStageRoi(config)
        return captureRoi(roi)
    }

    fun release() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            Log.i(TAG, "ScreenCapturer 리소스 해제 완료")
        } catch (e: Exception) {
            Log.e(TAG, "ScreenCapturer 해제 오류: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ScreenCapturer"
    }
}

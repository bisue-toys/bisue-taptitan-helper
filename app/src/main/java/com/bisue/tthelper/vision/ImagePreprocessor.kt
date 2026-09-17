package com.bisue.tthelper.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * OCR 인식률을 극대화하기 위한 이미지 전처리기
 * - 그레이스케일 변환
 * - 대비(Contrast) 향상
 */
object ImagePreprocessor {

    fun enhanceForOcr(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(output)
        val paint = Paint()

        // 1. 그레이스케일 매트릭스
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
        }

        // 2. 대비(Contrast) 1.5배 증폭
        val contrast = 1.5f
        val scale = contrast
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return output
    }
}

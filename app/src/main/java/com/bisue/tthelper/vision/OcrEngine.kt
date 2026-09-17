package com.bisue.tthelper.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google ML Kit 온디바이스 텍스트 인식(OCR) 래퍼
 * 네트워크 통신 없이 기기 내부 NPU/CPU에서 15~30ms 내 초고속 인식
 */
class OcrEngine {

    private val recognizer = TextRecognition.getClient()

    /**
     * 비트맵 이미지에서 텍스트 인식 수행
     */
    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                continuation.resume(visionText.text)
            }
            .addOnFailureListener { exception ->
                continuation.resume("")
            }
    }

    fun close() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            // ignore
        }
    }
}

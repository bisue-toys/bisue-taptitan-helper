package com.bisue.tthelper.vision

import android.util.Log
import java.util.Locale
import java.util.regex.Pattern

/**
 * 게임 상단 OCR 텍스트에서 층수(Stage) 추출 및 다중 프레임 노이즈 필터링
 */
class StageParser {

    private var consecutiveHits = 0
    private var lastValidStage: Int = 0

    data class CheckResult(
        val parsedStage: Int?,
        val isTargetReached: Boolean,
        val consecutiveHits: Int
    )

    /**
     * OCR로 추출된 전체 텍스트에서 층수 추출 및 목표 도달 판별
     * @param rawText OCR 엔진이 인식한 원본 문자열
     * @param targetStage 사용자가 설정한 목표 층수
     * @param requiredConsecutiveHits 도달 확정을 위한 최소 연속 인식 횟수 (기본 2회)
     */
    fun evaluate(
        rawText: String,
        targetStage: Int,
        requiredConsecutiveHits: Int = 2
    ): CheckResult {
        val stage = parseStageNumber(rawText)

        if (stage == null || stage <= 0) {
            return CheckResult(null, false, consecutiveHits)
        }

        // 비정상적인 수치 급등 노이즈 필터 (예: 10,000층에서 순간적으로 80,000층으로 인식되는 경우)
        if (lastValidStage > 1000 && stage > lastValidStage * 3) {
            Log.w(TAG, "비정상적 수치 도약 감지(노이즈 추정): 이전=$lastValidStage, 감지=$stage -> 무시")
            return CheckResult(lastValidStage, false, consecutiveHits)
        }

        lastValidStage = stage

        if (stage >= targetStage) {
            consecutiveHits++
            Log.i(TAG, "목표 층수 이상 감지: 현재=$stage, 목표=$targetStage (연속 $consecutiveHits/$requiredConsecutiveHits)")
            if (consecutiveHits >= requiredConsecutiveHits) {
                return CheckResult(stage, true, consecutiveHits)
            }
        } else {
            consecutiveHits = 0
        }

        return CheckResult(stage, false, consecutiveHits)
    }

    /**
     * 환생 후 카운터 및 층수 기록 초기화
     */
    fun resetHysteresis() {
        consecutiveHits = 0
        lastValidStage = 0
    }

    companion object {
        private const val TAG = "StageParser"

        // "Stage 15,400" 또는 "15,400" 또는 "15.4K" 등 패턴 매칭
        private val STAGE_REGEX = Pattern.compile(
            """(?:stage|스테이지)?\s*([0-9oOlIsS,\.\s]+(?:\s*[kKmM])?)""",
            Pattern.CASE_INSENSITIVE
        )

        /**
         * 원본 문자열에서 정수형 층수를 추출하는 순수 파싱 함수 (유닛 테스트 가능)
         */
        fun parseStageNumber(text: String): Int? {
            if (text.isBlank()) return null

            val normalized = text.trim()
                .replace("O", "0")
                .replace("o", "0")
                .replace("I", "1")
                .replace("l", "1")
                .replace("S", "5")
                .replace("s", "5")

            val matcher = STAGE_REGEX.matcher(normalized)
            while (matcher.find()) {
                val group = matcher.group(1)?.trim() ?: continue
                val cleanGroup = group.replace(" ", "")

                try {
                    // K/k 단위 처리 (예: 15.4k -> 15400)
                    if (cleanGroup.endsWith("k", ignoreCase = true)) {
                        val numStr = cleanGroup.dropLast(1).replace(",", "")
                        val num = numStr.toDoubleOrNull() ?: continue
                        return (num * 1000).toInt()
                    }
                    // M/m 단위 처리 (예: 1.2m -> 1200000)
                    if (cleanGroup.endsWith("m", ignoreCase = true)) {
                        val numStr = cleanGroup.dropLast(1).replace(",", "")
                        val num = numStr.toDoubleOrNull() ?: continue
                        return (num * 1000000).toInt()
                    }

                    // 일반 숫자 (쉼표 제거)
                    val rawDigits = cleanGroup.replace(",", "").replace(".", "")
                    val parsed = rawDigits.toIntOrNull()
                    if (parsed != null && parsed in 1..200000) {
                        return parsed
                    }
                } catch (e: Exception) {
                    // ignore parse exception and try next match
                }
            }

            // 단순 숫자 시퀀스 폴백
            val fallbackDigits = normalized.filter { it.isDigit() }
            val fallbackInt = fallbackDigits.toIntOrNull()
            if (fallbackInt != null && fallbackInt in 1..200000) {
                return fallbackInt
            }

            return null
        }
    }
}

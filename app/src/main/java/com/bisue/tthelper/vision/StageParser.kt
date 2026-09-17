package com.bisue.tthelper.vision

import android.util.Log

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
            try {
                Log.w(TAG, "비정상적 수치 도약 감지(노이즈 추정): 이전=$lastValidStage, 감지=$stage -> 무시")
            } catch (e: Exception) {}
            return CheckResult(lastValidStage, false, consecutiveHits)
        }

        lastValidStage = stage

        if (stage >= targetStage) {
            consecutiveHits++
            try {
                Log.i(TAG, "목표 층수 이상 감지: 현재=$stage, 목표=$targetStage (연속 $consecutiveHits/$requiredConsecutiveHits)")
            } catch (e: Exception) {}
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

        /**
         * 원본 문자열에서 정수형 층수를 추출하는 순수 파싱 함수
         */
        fun parseStageNumber(text: String): Int? {
            if (text.isBlank()) return null

            // 1. "Stage", "STAGE", "스테이지" 등의 접두어를 대소문자 무시하고 먼저 제거
            val withoutPrefix = text.trim()
                .replace(Regex("""(?i)\b(?:stage|스테이지|lv|level)\b[:\s]*"""), "")
                .trim()

            // 2. 만약 "15,400 / 15,400" 처럼 슬래시가 있으면 첫 번째 층수를 취함
            val primaryToken = withoutPrefix.split("/").first().trim()

            // 3. 숫자 부분에서 OCR 오류로 들어간 문자(O/o -> 0, I/l -> 1)만 정규화
            val sanitizedToken = primaryToken
                .replace("O", "0")
                .replace("o", "0")
                .replace("I", "1")
                .replace("l", "1")

            // 4. K/M 단위 표기 처리 (예: 15.4k, 12K, 1.2M)
            val kMatch = Regex("""(?i)([0-9]+(?:\.[0-9]+)?)\s*k""").find(sanitizedToken)
            if (kMatch != null) {
                val num = kMatch.groupValues[1].toDoubleOrNull()
                if (num != null) return (num * 1000).toInt()
            }

            val mMatch = Regex("""(?i)([0-9]+(?:\.[0-9]+)?)\s*m""").find(sanitizedToken)
            if (mMatch != null) {
                val num = mMatch.groupValues[1].toDoubleOrNull()
                if (num != null) return (num * 1000000).toInt()
            }

            // 5. 일반 숫자 (콤마 제거 후 숫자만 추출)
            val digitsOnly = sanitizedToken.replace(",", "").replace(".", "").filter { it.isDigit() }
            val parsed = digitsOnly.toIntOrNull()
            if (parsed != null && parsed in 1..200000) {
                return parsed
            }

            return null
        }
    }
}

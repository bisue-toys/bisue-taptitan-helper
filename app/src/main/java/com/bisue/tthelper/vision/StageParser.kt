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

            // 0. 숫자와 혼동된 문자(O/o -> 0, I/l -> 1) 전처리 (숫자가 포함된 토큰 내 치환)
            val normalizedText = Regex("""\b[0-9OoIl.,]{2,}\b""").replace(text) { m ->
                if (m.value.any { it.isDigit() }) {
                    m.value.replace('O', '0').replace('o', '0')
                           .replace('I', '1').replace('l', '1')
                } else {
                    m.value
                }
            }

            // 1. "STAGE" 또는 "스테이지" 뒤에 바로 오는 숫자 우선 매칭
            val directStageMatch = Regex("""(?i)\b(?:stage|스테이지|lv|level)[:\s]*([0-9OoIl.,]+(?:\s*[kKmM])?)""").find(text)
            if (directStageMatch != null) {
                val candidate = directStageMatch.groupValues[1].trim()
                    .replace('O', '0').replace('o', '0')
                    .replace('I', '1').replace('l', '1')
                val parsed = parseSingleNumberCandidate(candidate)
                if (parsed != null && parsed in 1..200000) {
                    return parsed
                }
            }

            // 2. K/M 단위 표기 처리 (예: 15.4k, 12K, 1.2M)
            val kMatch = Regex("""(?i)\b([0-9]+(?:\.[0-9]+)?)\s*k\b""").find(normalizedText)
            if (kMatch != null) {
                val num = kMatch.groupValues[1].toDoubleOrNull()
                if (num != null) return (num * 1000).toInt()
            }

            val mMatch = Regex("""(?i)\b([0-9]+(?:\.[0-9]+)?)\s*m\b""").find(normalizedText)
            if (mMatch != null) {
                val num = mMatch.groupValues[1].toDoubleOrNull()
                if (num != null) return (num * 1000000).toInt()
            }

            // 3. 텍스트 내의 모든 개별 숫자 토큰 추출 (보스 타이머 0:30, 웨이브 5/5 등 분리)
            // 슬래시 앞의 첫 번째 토큰 선별
            val textBeforeSlash = normalizedText.split("/").first().trim()

            // 콤마(,)가 포함된 천단위 숫자 또는 연속된 숫자 토큰 검색
            val numberRegex = Regex("""\b\d{1,3}(?:,\d{3})+\b|\b\d{1,6}\b""")
            val candidates = numberRegex.findAll(textBeforeSlash)
                .mapNotNull { match ->
                    val clean = match.value.replace(",", "").toIntOrNull()
                    if (clean != null && clean in 1..200000) clean else null
                }
                .toList()

            if (candidates.isNotEmpty()) {
                // 상단 화면에서 층수는 보통 웨이브(5)나 초(30)보다 큰 숫자이므로 가장 큰 유효 후보 선택
                return candidates.maxOrNull()
            }

            return null
        }

        private fun parseSingleNumberCandidate(token: String): Int? {
            val cleanToken = token.trim()
            val kMatch = Regex("""(?i)([0-9]+(?:\.[0-9]+)?)\s*k""").find(cleanToken)
            if (kMatch != null) {
                return kMatch.groupValues[1].toDoubleOrNull()?.let { (it * 1000).toInt() }
            }
            val mMatch = Regex("""(?i)([0-9]+(?:\.[0-9]+)?)\s*m""").find(cleanToken)
            if (mMatch != null) {
                return mMatch.groupValues[1].toDoubleOrNull()?.let { (it * 1000000).toInt() }
            }
            val digits = cleanToken.replace(",", "").replace(".", "").filter { it.isDigit() }
            return digits.toIntOrNull()
        }
    }
}

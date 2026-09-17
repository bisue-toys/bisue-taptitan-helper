package com.bisue.tthelper

import com.bisue.tthelper.vision.StageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StageParserTest {

    private lateinit var parser: StageParser

    @Before
    fun setUp() {
        parser = StageParser()
    }

    @Test
    fun testParseStandardStageText() {
        assertEquals(15400, StageParser.parseStageNumber("Stage 15,400"))
        assertEquals(15400, StageParser.parseStageNumber("STAGE 15400"))
        assertEquals(12345, StageParser.parseStageNumber("스테이지 12,345"))
    }

    @Test
    fun testParseStageWithSlashProgress() {
        assertEquals(15400, StageParser.parseStageNumber("15,400 / 15,400"))
    }

    @Test
    fun testParseStageWithKNotation() {
        assertEquals(15400, StageParser.parseStageNumber("Stage 15.4k"))
        assertEquals(12000, StageParser.parseStageNumber("12K"))
    }

    @Test
    fun testParseOcrConfusions() {
        // 'O' 대신 '0', 'l' 대신 '1' 등 OCR 혼동 문자 정규화 검증
        assertEquals(15400, StageParser.parseStageNumber("Stage 154OO"))
        assertEquals(15410, StageParser.parseStageNumber("Stage 154lO"))
    }

    @Test
    fun testParseEmptyOrInvalid() {
        assertNull(StageParser.parseStageNumber(""))
        assertNull(StageParser.parseStageNumber("Game Over"))
    }

    @Test
    fun testHysteresisRequiresTwoConsecutiveHits() {
        val targetStage = 15000

        // 1회 도달 시: isTargetReached는 false여야 함 (다중 프레임 검증)
        val result1 = parser.evaluate("Stage 15,100", targetStage, requiredConsecutiveHits = 2)
        assertEquals(15100, result1.parsedStage)
        assertFalse("1회 감지 시에는 환생이 트리거되지 않아야 함", result1.isTargetReached)
        assertEquals(1, result1.consecutiveHits)

        // 2회 연속 도달 시: isTargetReached가 true로 전이
        val result2 = parser.evaluate("Stage 15,101", targetStage, requiredConsecutiveHits = 2)
        assertEquals(15101, result2.parsedStage)
        assertTrue("2회 연속 감지 시 환생이 트리거되어야 함", result2.isTargetReached)
        assertEquals(2, result2.consecutiveHits)
    }

    @Test
    fun testSpikeNoiseRejection() {
        // 이전 유효 층수 등록
        parser.evaluate("Stage 10,000", 15000)

        // 순간적으로 이펙트 노이즈로 85,000층으로 튀었을 때
        val resultSpike = parser.evaluate("Stage 85,000", 15000)
        assertEquals("급격한 비정상 도약은 직전 유효 층수로 필터링되어야 함", 10000, resultSpike.parsedStage)
        assertFalse(resultSpike.isTargetReached)
    }
}

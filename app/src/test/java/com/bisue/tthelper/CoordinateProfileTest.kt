package com.bisue.tthelper

import com.bisue.tthelper.touch.CoordinateProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateProfileTest {

    @Test
    fun testRelativeCoordinatesWithinBounds() {
        val points = listOf(
            CoordinateProfile.TAB_SWORDMASTER,
            CoordinateProfile.BTN_LEVELUP_MULTIPLIER,
            CoordinateProfile.BTN_SWORDMASTER_UPGRADE,
            CoordinateProfile.BTN_SKILL_1_HEAVENLY,
            CoordinateProfile.BTN_SKILL_2_DEADLY,
            CoordinateProfile.BTN_SKILL_3_MIDAS,
            CoordinateProfile.BTN_SKILL_4_FIRESWORD,
            CoordinateProfile.BTN_SKILL_5_WARCRY,
            CoordinateProfile.BTN_SKILL_6_CLONE,
            CoordinateProfile.BTN_PRESTIGE,
            CoordinateProfile.BTN_CONFIRM_PRESTIGE,
            CoordinateProfile.CLOSE_TAB_TAP
        ) + CoordinateProfile.ACTIVE_SKILL_SLOTS

        for (p in points) {
            assertTrue("X coordinate must be in [0, 1]: ${p.x}", p.x in 0f..1f)
            assertTrue("Y coordinate must be in [0, 1]: ${p.y}", p.y in 0f..1f)
        }
    }

    @Test
    fun testPixelConversionForS24UltraQhd() {
        val width = 1440
        val height = 3120

        val (x, y) = CoordinateProfile.toPixel(CoordinateProfile.TAB_SWORDMASTER, width, height)
        assertTrue(x > 0 && x < width)
        assertTrue(y > 0 && y < height)

        val bounds = CoordinateProfile.toPixelBounds(CoordinateProfile.STAGE_ROI_RATIO, width, height)
        val left = bounds[0]
        val top = bounds[1]
        val right = bounds[2]
        val bottom = bounds[3]
        assertTrue(left >= 0)
        assertTrue(right <= width)
        assertTrue(top >= 0)
        assertTrue(bottom <= height)
        assertTrue(right > left)
        assertTrue(bottom > top)
    }

    @Test
    fun testActiveSkillSlotsAreSix() {
        assertEquals(6, CoordinateProfile.ACTIVE_SKILL_SLOTS.size)
    }
}

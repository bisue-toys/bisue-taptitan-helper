package com.bisue.tthelper.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Tap Titans 2 헬퍼 전역 설정 및 상태 저장소
 */
class HelperConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tt_helper_prefs", Context.MODE_PRIVATE)

    var targetStage: Int
        get() = prefs.getInt(KEY_TARGET_STAGE, 10000)
        set(value) = prefs.edit().putInt(KEY_TARGET_STAGE, value).apply()

    var autoRecastSkills: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECAST_SKILLS, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RECAST_SKILLS, value).apply()

    var runSkillSetupOnStart: Boolean
        get() = prefs.getBoolean(KEY_RUN_SKILL_SETUP_ON_START, true)
        set(value) = prefs.edit().putBoolean(KEY_RUN_SKILL_SETUP_ON_START, value).apply()

    var recastIntervalSeconds: Int
        get() = prefs.getInt(KEY_RECAST_INTERVAL, 90) // 기본 90초마다 스킬 재시전
        set(value) = prefs.edit().putInt(KEY_RECAST_INTERVAL, value).apply()

    var isAutomationRunning: Boolean = false

    var currentDetectedStage: Int = 0

    var hasCustomCalibration: Boolean
        get() = prefs.getBoolean(KEY_HAS_CUSTOM_CALIBRATION, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_CUSTOM_CALIBRATION, value).apply()

    // 층수 ROI 사각형 (상대 비율 0.0 ~ 1.0)
    var customRoiLeft: Float
        get() = prefs.getFloat(KEY_ROI_LEFT, 0.18f)
        set(value) = prefs.edit().putFloat(KEY_ROI_LEFT, value).apply()

    var customRoiTop: Float
        get() = prefs.getFloat(KEY_ROI_TOP, 0.045f)
        set(value) = prefs.edit().putFloat(KEY_ROI_TOP, value).apply()

    var customRoiRight: Float
        get() = prefs.getFloat(KEY_ROI_RIGHT, 0.82f)
        set(value) = prefs.edit().putFloat(KEY_ROI_RIGHT, value).apply()

    var customRoiBottom: Float
        get() = prefs.getFloat(KEY_ROI_BOTTOM, 0.105f)
        set(value) = prefs.edit().putFloat(KEY_ROI_BOTTOM, value).apply()

    // 터치 핀 좌표 (상대 비율 0.0 ~ 1.0)
    var customTabSwordmasterX: Float
        get() = prefs.getFloat(KEY_PIN_TAB_X, 0.08f)
        set(value) = prefs.edit().putFloat(KEY_PIN_TAB_X, value).apply()

    var customTabSwordmasterY: Float
        get() = prefs.getFloat(KEY_PIN_TAB_Y, 0.94f)
        set(value) = prefs.edit().putFloat(KEY_PIN_TAB_Y, value).apply()

    var customBtnMultiplierX: Float
        get() = prefs.getFloat(KEY_PIN_MULT_X, 0.86f)
        set(value) = prefs.edit().putFloat(KEY_PIN_MULT_X, value).apply()

    var customBtnMultiplierY: Float
        get() = prefs.getFloat(KEY_PIN_MULT_Y, 0.475f)
        set(value) = prefs.edit().putFloat(KEY_PIN_MULT_Y, value).apply()

    var customBtnUpgradeX: Float
        get() = prefs.getFloat(KEY_PIN_UPGRADE_X, 0.82f)
        set(value) = prefs.edit().putFloat(KEY_PIN_UPGRADE_X, value).apply()

    var customBtnUpgradeY: Float
        get() = prefs.getFloat(KEY_PIN_UPGRADE_Y, 0.54f)
        set(value) = prefs.edit().putFloat(KEY_PIN_UPGRADE_Y, value).apply()

    var customSkillSlot1X: Float
        get() = prefs.getFloat(KEY_PIN_SKILL1_X, 0.12f)
        set(value) = prefs.edit().putFloat(KEY_PIN_SKILL1_X, value).apply()

    var customSkillSlot1Y: Float
        get() = prefs.getFloat(KEY_PIN_SKILL1_Y, 0.825f)
        set(value) = prefs.edit().putFloat(KEY_PIN_SKILL1_Y, value).apply()

    var customSkillSlot6X: Float
        get() = prefs.getFloat(KEY_PIN_SKILL6_X, 0.88f)
        set(value) = prefs.edit().putFloat(KEY_PIN_SKILL6_X, value).apply()

    var customSkillSlot6Y: Float
        get() = prefs.getFloat(KEY_PIN_SKILL6_Y, 0.825f)
        set(value) = prefs.edit().putFloat(KEY_PIN_SKILL6_Y, value).apply()

    var customBtnPrestigeX: Float
        get() = prefs.getFloat(KEY_PIN_PRESTIGE_X, 0.50f)
        set(value) = prefs.edit().putFloat(KEY_PIN_PRESTIGE_X, value).apply()

    var customBtnPrestigeY: Float
        get() = prefs.getFloat(KEY_PIN_PRESTIGE_Y, 0.86f)
        set(value) = prefs.edit().putFloat(KEY_PIN_PRESTIGE_Y, value).apply()

    fun resetCalibrationToDefaults() {
        prefs.edit()
            .remove(KEY_HAS_CUSTOM_CALIBRATION)
            .remove(KEY_ROI_LEFT)
            .remove(KEY_ROI_TOP)
            .remove(KEY_ROI_RIGHT)
            .remove(KEY_ROI_BOTTOM)
            .remove(KEY_PIN_TAB_X)
            .remove(KEY_PIN_TAB_Y)
            .remove(KEY_PIN_MULT_X)
            .remove(KEY_PIN_MULT_Y)
            .remove(KEY_PIN_UPGRADE_X)
            .remove(KEY_PIN_UPGRADE_Y)
            .remove(KEY_PIN_SKILL1_X)
            .remove(KEY_PIN_SKILL1_Y)
            .remove(KEY_PIN_SKILL6_X)
            .remove(KEY_PIN_SKILL6_Y)
            .remove(KEY_PIN_PRESTIGE_X)
            .remove(KEY_PIN_PRESTIGE_Y)
            .apply()
    }

    companion object {
        const val TT2_PACKAGE_NAME = "com.gamehivecorp.taptitans2"
        private const val KEY_TARGET_STAGE = "target_stage"
        private const val KEY_AUTO_RECAST_SKILLS = "auto_recast_skills"
        private const val KEY_RUN_SKILL_SETUP_ON_START = "run_skill_setup_on_start"
        private const val KEY_RECAST_INTERVAL = "recast_interval"

        private const val KEY_HAS_CUSTOM_CALIBRATION = "has_custom_calibration"
        private const val KEY_ROI_LEFT = "roi_left"
        private const val KEY_ROI_TOP = "roi_top"
        private const val KEY_ROI_RIGHT = "roi_right"
        private const val KEY_ROI_BOTTOM = "roi_bottom"

        private const val KEY_PIN_TAB_X = "pin_tab_x"
        private const val KEY_PIN_TAB_Y = "pin_tab_y"
        private const val KEY_PIN_MULT_X = "pin_mult_x"
        private const val KEY_PIN_MULT_Y = "pin_mult_y"
        private const val KEY_PIN_UPGRADE_X = "pin_upgrade_x"
        private const val KEY_PIN_UPGRADE_Y = "pin_upgrade_y"
        private const val KEY_PIN_SKILL1_X = "pin_skill1_x"
        private const val KEY_PIN_SKILL1_Y = "pin_skill1_y"
        private const val KEY_PIN_SKILL6_X = "pin_skill6_x"
        private const val KEY_PIN_SKILL6_Y = "pin_skill6_y"
        private const val KEY_PIN_PRESTIGE_X = "pin_prestige_x"
        private const val KEY_PIN_PRESTIGE_Y = "pin_prestige_y"

        @Volatile
        private var instance: HelperConfig? = null

        fun getInstance(context: Context): HelperConfig =
            instance ?: synchronized(this) {
                instance ?: HelperConfig(context.applicationContext).also { instance = it }
            }
    }
}

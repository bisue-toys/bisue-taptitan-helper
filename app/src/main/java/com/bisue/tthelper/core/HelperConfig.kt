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

    companion object {
        const val TT2_PACKAGE_NAME = "com.gamehivecorp.taptitans2"
        private const val KEY_TARGET_STAGE = "target_stage"
        private const val KEY_AUTO_RECAST_SKILLS = "auto_recast_skills"
        private const val KEY_RUN_SKILL_SETUP_ON_START = "run_skill_setup_on_start"
        private const val KEY_RECAST_INTERVAL = "recast_interval"

        @Volatile
        private var instance: HelperConfig? = null

        fun getInstance(context: Context): HelperConfig =
            instance ?: synchronized(this) {
                instance ?: HelperConfig(context.applicationContext).also { instance = it }
            }
    }
}
